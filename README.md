# pini4s

Pinia-inspired shared state for Scala and Kyo. The current API is a small,
explicit store of named `SignalRef` values, with an optional Nuxt-style
`useState("key")(initial)` helper. The library depends on `kyo-core` only.

pini4s handles shared application state. Component APIs belong in
[kyo-ui-state](https://github.com/yuri4n/kyo-ui-state): its core provides typed
emits and model bindings, and its optional UI module provides components.
Its Scala package is `kyo.ui.state`; its core artifact is
`io.github.yuri4n::kyo-ui-state`.
Components own their local state by default and can opt into a shared store.
There is no UI module or renderer dependency in pini4s.

Stores are optional. A component with local state does not need pini4s. For
a few shared values, keyed `useState` calls are enough. A named store is
useful when related state and actions should have one application API.

## Shared state

Create one store when constructing your app, then pass it as a contextual
value to the parts of the app that share state:

```scala
import kyo.*
import pini4s.*

def counter(using StateStore): SignalRef[Int] < Sync =
  useState("counter")(0)

def appState: SignalRef[Int] < Sync =
  StateStore.init.map { store =>
    given StateStore = store
    counter
  }
```

The helper delegates to `store.useState("counter")(0)`, so callers can also
use the store directly.

`useState("counter")(0)` is a lazy lookup: the first use initializes the
reference and every later use in that store returns the same reference.
Initialization runs inside the effect, once even when fibers race to create
the key. Later initializers are not evaluated. A failed initializer leaves
the key available for retry.

Each key has one exact type, including generic arguments. Reusing a key as
both `List[Int]` and `List[String]` fails with a descriptive error.

## Named stores

A named store is an ordinary Scala class that groups state and actions:

```scala
import kyo.*
import pini4s.*

final class DashboardStore(val currentTab: SignalRef[Int]):
  def selectTab(index: Int): Unit < Sync =
    currentTab.set(index)

def useDashboardStore(using StateStore): DashboardStore < Sync =
  useState("dashboard.currentTab")(0).map(new DashboardStore(_))
```

Call `useDashboardStore` with the application's `given StateStore`, as in
the shared-state example above. Callers read `store.currentTab` and request
changes with `store.selectTab(index)`. The action returns a Kyo effect; the
state changes when that effect executes.

Repeated calls create small wrappers around the same shared reference. A
different `StateStore` gives independent dashboard state. This pattern uses
the existing API and needs no store-definition macro or base class.

## Kyo Env

Applications can provide the store through `Env` instead of contextual Scala
parameters. The same store implementation works with either approach:

```scala
def useDashboardStoreFromEnv: DashboardStore < (Sync & Env[StateStore]) =
  Env.use[StateStore](store => useDashboardStore(using store))

def dashboardState: DashboardStore < Sync =
  StateStore.init.map(store => Env.run(store)(useDashboardStoreFromEnv))
```

Resolve the store during component setup, then capture the returned store or
signals in event handlers. Those handlers can run after the `Env.run` above has
finished. Wrapping UI construction in `Env.run` does not itself provide values
to an unexecuted callback; an action that still requires `Env` needs its own
handler when invoked. For child fibers, handle the complete spawn-and-join
computation, or provide dependencies inside the fiber before returning its
handle. Handling only the spawn can leave `Env` required by the handle's `get`.

Application code can let Kyo derive `Frame` automatically, as these examples
do. Library helpers propagate it to preserve caller source locations. A
`Frame` describes a code location; application stores and services are the
dependencies that are useful to provide through `Env`.

## Store lifetime

The store is explicit. Components capture it through ordinary Scala
contextual parameters, including when their bodies run later. State survives
component unmounts while the store is reachable. Create a fresh store for
each app run or test; there is no process-wide registry. For state owned only
by one component, use the consuming UI core's local-state API or Kyo's
`Signal.initRef` directly.

The store owns references only. Effects and resources retain their usual
Kyo `Scope` lifetimes. Component effects and lifecycle belong to the UI
runtime. A `kyo.ui.state` model binding can use a stored `SignalRef` without
making pini4s depend on that binding API.

## Development

Use JDK 25, Scala 3.9.0, and sbt 1.13.0. Kyo is pinned to 1.0.0-RC7.

```sh
sbt test
```

Tests use Kyo's test runner, and compiler warnings fail the build. The
store tests cover repeated and concurrent lookup, lazy initialization,
retry after initialization failure, exact types, and independent stores.

This working draft has no release or published artifact yet.
