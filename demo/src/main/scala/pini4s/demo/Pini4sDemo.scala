package pini4s.demo

import kyo.*
import pini4s.StateStore

/** A console-only packaging smoke check using the actual shared registry. */
object Pini4sDemo extends KyoApp:
    run {
      for
          store       <- StateStore.init
          first       <- store.useState("counter")(0)
          second      <- store.useState("counter")(99)
          _           <- first.set(7)
          shared      <- second.get
          otherStore  <- StateStore.init
          independent <- otherStore.useState("counter")(0)
          isolated    <- independent.get
          _           <- Sync.defer {
                           require(first eq second, "same key must return the same reference")
                           require(shared == 7, "shared references must observe updates")
                           require(!(first eq independent), "different stores must own different references")
                           require(isolated == 0, "a new store must keep independent state")
                           println(s"pini4s-demo: shared=$shared independent=$isolated")
                         }
      yield ()
    }
end Pini4sDemo
