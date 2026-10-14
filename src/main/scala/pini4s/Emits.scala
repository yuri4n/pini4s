package pini4s

import kyo.*

/** A component's typed event callback, similar to Vue's `$emit`.
  *
  * Calling `emit(event)` builds an effect. Its handler runs when that effect
  * executes, and any asynchronous work or failure belongs to the caller.
  * There are no subscriptions or global event buses.
  */
final class Emits[-E] private[pini4s] (handler: E => Unit < Async):
    /** Sends one event when the returned effect executes. */
    def apply(event: E)(using Frame): Unit < Async =
      Sync.defer(handler(event))
end Emits

/** Connects a component's events to its parent's callback. Use an enum or
  * sealed trait for `E` to give each event a checked payload type.
  */
def useEmits[E](handler: E => Unit < Async): Emits[E] = new Emits(handler)
