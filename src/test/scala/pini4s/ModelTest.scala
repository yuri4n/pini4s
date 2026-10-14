package pini4s

import java.util.concurrent.atomic.AtomicInteger
import kyo.*
import kyo.test.*

class ModelTest extends Test[Any]:
    "building a set effect does not invoke a custom setter" in {
      for
          calls  <- Sync.defer(AtomicInteger(0))
          ref    <- Signal.initRef(1)
          model   = Model[Int](
                      ref,
                      next =>
                        val _ = calls.incrementAndGet()
                        ref.set(next),
                    )
          action  = model.set(2)
          before <- Sync.defer(calls.get())
          _      <- action
          after  <- Sync.defer(calls.get())
          value  <- ref.get
      yield
          assert(before == 0)
          assert(after == 1)
          assert(value == 2)
    }

    "a ref model writes its source and reads later source changes" in {
      for
          ref   <- Signal.initRef(1)
          model  = Model(ref)
          _     <- model.set(2)
          value <- ref.get
          _     <- ref.set(3)
          shown <- model.value.current
      yield
          assert(value == 2)
          assert(shown == 3)
    }

    "a custom setter can reject or transform a requested value" in {
      for
          ref      <- Signal.initRef(2)
          requests <- Signal.initRef(Vector.empty[Int])
          model     = Model[Int](
                        ref,
                        next =>
                          requests.updateAndGet(_ :+ next).andThen(
                            if next < 0 then Kyo.unit else ref.set(next.min(10)),
                          ),
                      )
          _        <- model.set(-1)
          rejected <- model.value.current
          _        <- model.set(20)
          accepted <- model.value.current
          seen     <- requests.get
      yield
          assert(rejected == 2)
          assert(accepted == 10)
          assert(seen == Vector(-1, 20))
    }
end ModelTest
