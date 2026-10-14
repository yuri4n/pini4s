# pini4s

Small state stores and model bindings built on Kyo `SignalRef`. The library
depends on `kyo-core` only and can be used with any kyo-ui renderer.

The optional `pini4s-ui` module adds components built from plain Kyo UI. It
depends on `kyo-ui`, with no dependency on tui4s or any renderer.

## Named state

Create one store when constructing your app, then pass it as a contextual
value to the parts of the app that share state:

```scala
import kyo.*
import pini4s.*

def counter(using StateStore, Frame): SignalRef[Int] < Sync =
  useState("counter")(0)

def appState(using Frame): SignalRef[Int] < Sync =
  StateStore.init.map { store =>
    given StateStore = store
    counter
  }
```

`useState("counter")(0)` is a lazy lookup: the first use initializes the
reference and every later use in that store returns the same reference.
Initialization runs inside the effect, once even when fibers race to create
the key. Later initializers are not evaluated. A failed initializer leaves
the key available for retry.

Each key has one exact type, including generic arguments. Reusing a key as
both `List[Int]` and `List[String]` fails with a descriptive error.

The store is explicit. Components capture it through ordinary Scala
contextual parameters, including when their bodies run later. State survives
component unmounts while the store is reachable. Create a fresh store for
each app run or test; there is no process-wide registry. Use `Signal.initRef`
directly for state owned only by one component.

## Model bindings

`Model(ref)` supplies a reactive value and a setter, like a Vue `v-model`:

```scala
def binding(using Frame): Model[Int] < Sync =
  Signal.initRef(0).map(Model(_))
```

A custom setter can validate, transform or reject requested changes:

```scala
def bounded(ref: SignalRef[Int])(using Frame): Model[Int] =
  Model(ref, next => ref.set(next.max(0).min(100)))
```

Consumers render `model.value` and request changes with `model.set(next)`.
The source signal remains authoritative, so a rejected change does not
silently update a second local copy. Setters may perform asynchronous Kyo
effects. Building a `model.set(next)` effect does not call the setter; the
setter runs when the effect executes.

The store owns references only. Effects and resources retain their usual
Kyo `Scope` lifetimes.

## Component events

`useEmits` connects typed component events to a parent callback, like Vue's
`defineEmits` and instance `$emit` (singular). Use an enum for event names and
payloads:

```scala
enum TabsEvent:
  case Change(index: Int)
  case Close

def selectTab(
    index: Int,
    onEvent: TabsEvent => Unit < Async,
)(using Frame): Unit < Async =
  val emit = useEmits[TabsEvent](onEvent)
  emit(TabsEvent.Change(index))
```

Building `emit(event)` does not call the callback. Executing the returned
effect calls it once, waits for asynchronous work, and propagates failures.
The callback belongs to that component; there is no global bus or
subscription lifecycle.

Use events to notify a parent about an interaction. Use `Model` when the
parent supplies the value and decides whether to accept a requested change.
A component can own its local state and emit changes, or accept a model for
controlled state.

## Kyo UI components

`pini4s.ui.Tabs` owns its selection by default. Its panel is a scoped slot,
its events are typed, and a parent can supply a model:

```scala
import kyo.UI.*
import pini4s.ui.{Tabs, TabsEvent}

def tabs(using Frame): UI < Sync =
  new Tabs(
    Seq("CPU", "Memory", "Network"),
    heading = h1("Dashboard"),
    panel = index => div(s"Selected: $index"),
  ).ui

def sharedTabs(using StateStore, Frame): UI < Sync =
  useState("dashboard.currentTab")(0).map { selected =>
    new Tabs(
      Seq("CPU", "Memory", "Network"),
      model = Some(Model(selected)),
      panel = index => div(s"Selected: $index"),
    ).ui
  }
```

Each evaluation of `ui` creates independent local state and a unique focus
group, including when the same allocation effect is run twice. Keep the
returned UI during reactive updates. For a renderer with a component mount
lifecycle, allocate `initial` at mount and retain its `SignalRef[Tabs.State]`
while calling `view` with new props. Drop that reference on unmount. Shared
model state belongs to its store and can survive the component.

Arrow keys select relative to the focused button. Tab changes focus only;
Enter and Space activate the focused button through the renderer's normal
Kyo UI event handling. A controlled model may reject a request; the event is
still emitted and the displayed value remains the parent's signal.

The pure Kyo UI tests evaluate the tree and its handlers without a terminal.
The tui4s example adds its own mount adapter and terminal integration tests.
Browser rendering and a ZIO-facing adapter are not yet validated here. Kyo's
existing ZIO bridge can be used by an adapter without changing this state API.

## Development

Use JDK 25, Scala 3.9.0, and sbt 1.13.0. Kyo is pinned to 1.0.0-RC7.

```sh
sbt test ui/test
```

This working draft has no release or published artifact yet.
