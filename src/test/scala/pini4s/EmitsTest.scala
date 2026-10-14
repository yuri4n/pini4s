package pini4s

import java.util.concurrent.atomic.AtomicInteger
import kyo.*
import kyo.test.*

class EmitsTest extends Test[Any]:
    enum Event derives CanEqual:
        case Change(index: Int)
        case Close

    "building an emission defers the callback and execution calls it once" in {
      for
          calls  <- Sync.defer(AtomicInteger(0))
          emit    = useEmits[String] { _ =>
                      val _ = calls.incrementAndGet()
                      Kyo.unit
                    }
          action  = emit("saved")
          before <- Sync.defer(calls.get())
          _      <- action
          after  <- Sync.defer(calls.get())
      yield
          assert(before == 0)
          assert(after == 1)
    }

    "typed events retain their cases and payloads" in {
      for
          received <- Signal.initRef(Vector.empty[Event])
          emit      = useEmits[Event](event =>
                        received.updateAndGet(_ :+ event).unit,
                      )
          _        <- emit(Event.Change(2))
          _        <- emit(Event.Close)
          events   <- received.get
      yield assert(events == Vector(Event.Change(2), Event.Close))
    }

    "emitting waits for an asynchronous callback" in {
      for
          received <- Signal.initRef(0)
          emit      = useEmits[Int](next =>
                        Async.sleep(1.millis).andThen(received.set(next)),
                      )
          _        <- emit(3)
          value    <- received.get
      yield assert(value == 3)
    }

    "a callback that throws fails the emission effect" in {
      for
          error  <- Sync.defer(IllegalStateException("event failed"))
          emit    = useEmits[Event](_ => throw error)
          action  = emit(Event.Close)
          result <- Abort.run[Throwable](action)
      yield
          val caught = result match
              case Result.Failure(cause) => cause eq error
              case _                    => false
          assert(caught)
    }
end EmitsTest
