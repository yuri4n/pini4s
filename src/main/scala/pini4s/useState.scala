package pini4s

import kyo.*

/** Named state shared by callers with the same [[StateStore]]. The first use
  * of a key evaluates `initial`; later uses return its existing reference.
  * Allocate a store once per app and pass it as a `given StateStore`.
  */
def useState[A](key: String)(initial: => A)(using
    store: StateStore,
    tag: Tag[A],
    canEqual: CanEqual[A, A],
    frame: Frame,
): SignalRef[A] < Sync = store.useState(key)(initial)
