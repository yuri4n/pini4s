package pini4s

import java.util.concurrent.atomic.AtomicInteger
import kyo.*
import kyo.test.*

class StateStoreTest extends Test[Any]:
    "the first successful initializer runs once and later uses share updates" in {
      for
          runs   <- Sync.defer(AtomicInteger(0))
          store  <- StateStore.init
          lookup  = store.useState("count")(runs.incrementAndGet())
          before <- Sync.defer(runs.get())
          first  <- lookup
          _      <- first.set(7)
          second <- store.useState("count")(runs.incrementAndGet())
          value  <- second.get
          count  <- Sync.defer(runs.get())
      yield
          assert(before == 0)
          assert(first eq second)
          assert(value == 7)
          assert(count == 1)
    }

    "different keys and stores keep independent state" in {
      for
          one   <- StateStore.init
          two   <- StateStore.init
          first <- one.useState("count")(1)
          other <- one.useState("other")(2)
          next  <- two.useState("count")(3)
          _     <- first.set(8)
          a     <- other.get
          b     <- next.get
      yield
          assert(a == 2)
          assert(b == 3)
          assert(!(first eq next))
    }

    "a key rejects a different type including erased generic arguments" in {
      for
          store   <- StateStore.init
          runs    <- Sync.defer(AtomicInteger(0))
          _       <- store.useState("count")(1)
          scalar  <- Abort.run[Throwable](store.useState("count")("wrong"))
          _       <- store.useState("items")(List(1))
          generic <- Abort.run[Throwable](
                       store.useState("items") {
                         val _ = runs.incrementAndGet()
                         List("wrong")
                       },
                     )
          count   <- Sync.defer(runs.get())
      yield
          assert(typeMismatch(scalar, "count"))
          assert(typeMismatch(generic, "items"))
          assert(count == 0)
    }

    "a failed initializer leaves the key available for retry" in {
      for
          store <- StateStore.init
          error <- Sync.defer(IllegalStateException("failed initialization"))
          first <- Abort.run[Throwable](
                     store.useState[Int]("retry")(throw error),
                   )
          ref   <- store.useState("retry")(4)
          value <- ref.get
      yield
          val failed = first match
              case Result.Failure(cause) => cause eq error
              case _                    => false
          assert(failed)
          assert(value == 4)
    }

    "concurrent first uses initialize one reference" in {
      for
          store  <- StateStore.init
          runs   <- Sync.defer(AtomicInteger(0))
          gate   <- Promise.init[Unit, Any]
          fibers <- Kyo.foreach(1 to 32)(_ =>
                      Fiber.init(
                        gate.get.andThen(
                          store.useState("shared")(runs.incrementAndGet()),
                        ),
                      ),
                    )
          _      <- gate.completeUnitDiscard
          refs   <- Kyo.foreach(fibers)(_.get)
          count  <- Sync.defer(runs.get())
      yield
          assert(count == 1)
          assert(refs.forall(_ eq refs.head))
    }

    "the contextual helper shares state with a deferred child fiber" in {
      def child(using StateStore, Frame): SignalRef[Int] < Sync =
        useState("count")(0)
      for
          store <- StateStore.init
          first <- store.useState("count")(2)
          fiber <- Fiber.init(child(using store, summon[Frame]))
          next  <- fiber.get
          _     <- next.set(7)
          value <- first.get
      yield
          assert(first eq next)
          assert(value == 7)
    }

    private def typeMismatch[A](result: Result[Throwable, A], key: String): Boolean =
      result match
          case Result.Failure(e: IllegalArgumentException) =>
            e.getMessage.contains(key) && e.getMessage.contains("type")
          case _ => false
end StateStoreTest
