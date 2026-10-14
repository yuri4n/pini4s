package pini4s

import kyo.*

/** A small owner of named state. Create one store for each app or test, then
  * pass it as a `given StateStore` to components that use [[useState]].
  * The same key returns the same reference, including after a component
  * unmounts. Different stores are independent; there is no global store.
  *
  * A store owns only references, not effects or resources. Its state remains
  * available while the store is reachable. Put a fresh [[StateStore.init]]
  * inside the app's construction to keep separate runs independent.
  */
final class StateStore private ():
    private final case class Entry(tag: Tag[Any], ref: SignalRef[?])

    private val entries = scala.collection.mutable.Map.empty[String, Entry]

    /** Gets the state for `key`, initializing it on its first use. `initial`
      * is evaluated inside the effect, at most once for a successful key,
      * even when several fibers first use the key together. Keep it short
      * and synchronous. A failed initializer leaves the key uninitialized.
      *
      * Each key has one exact type, including type arguments. Reusing it
      * with another type fails with an `IllegalArgumentException` before a
      * reference is returned or the new initializer is evaluated.
      */
    def useState[A](key: String)(initial: => A)(using
        tag: Tag[A],
        canEqual: CanEqual[A, A],
        frame: Frame,
    ): SignalRef[A] < Sync =
      Sync.Unsafe.defer {
        entries.synchronized {
          entries.get(key) match
              case Some(entry) =>
                require(
                  entry.tag =:= tag,
                  s"pini4s: state key '$key' has type ${entry.tag.show}, requested ${tag.show}",
                )
                entry.ref.asInstanceOf[SignalRef[A]]
              case None =>
                val ref = Signal.SignalRef.Unsafe.init(initial).safe
                entries.update(key, Entry(tag.erased, ref))
                ref
        }
      }
end StateStore

object StateStore:
    /** Creates an empty store. Allocate it once per app or test. */
    def init(using Frame): StateStore < Sync = Sync.defer(new StateStore())
end StateStore
