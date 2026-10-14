package pini4s

import kyo.*

/** A controlled value: read `value` and request a change with `set`.
  *
  * `Model(ref)` connects both directions to one `SignalRef`, like Vue's
  * `v-model`. A custom setter can validate, transform or reject a requested
  * change; the component continues to render the supplied signal. Setting a
  * model never creates a second copy of the state.
  *
  * `set(next)` builds an effect: the setter runs only when that effect
  * executes, including when it is passed to a UI event handler.
  */
final class Model[A] private (
    val value: Signal[A],
    write: A => Unit < Async,
):
    /** Requests a change when the returned effect executes. */
    def set(next: A)(using Frame): Unit < Async = Sync.defer(write(next))
end Model

object Model:
    /** Uses a reactive source with a custom setter. */
    def apply[A](value: Signal[A], set: A => Unit < Async): Model[A] =
      new Model(value, set)

    /** Uses a mutable signal as a two-way binding. */
    def apply[A](ref: SignalRef[A])(using Frame): Model[A] =
      Model(ref, next => ref.set(next))
end Model
