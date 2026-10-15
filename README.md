# pini4s

Pinia-inspired shared state for Scala and Kyo. The current API is a small,
explicit store of named `SignalRef` values, with an optional Nuxt-style
`useState("key")(initial)` helper. The library depends on `kyo-core` only.

pini4s adds keyed reference reuse within an explicit application registry.
Kyo already provides signals, UI bindings, effects, and dependency injection.
Components use those APIs directly and own their local state by default.
pini4s has no UI module or renderer dependency.

Stores are optional. A component with local state does not need pini4s. A
named store can be an ordinary Scala class supplied through Kyo `Env` or
`Layer`, also without pini4s. Use this registry when separate consumers need
to find the same shared signal by key.

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
In contrast, each evaluation of Kyo's `Signal.initRef(0)` allocates a new
reference. Both APIs return the same native Kyo `SignalRef` type.
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

If the application already passes one `DashboardStore` to its consumers,
it can construct that store directly with Kyo and skip the keyed registry:

```scala
def newDashboardStore: DashboardStore < Sync =
  Signal.initRef(0).map(new DashboardStore(_))

val dashboardLayer: Layer[DashboardStore, Sync] = Layer(newDashboardStore)
```

Consumers can then request `Env[DashboardStore]` through Kyo's existing
dependency APIs. The class groups state and actions; pini4s is needed only
for the optional keyed lookup behavior.

## Native Kyo UI

A stored reference works directly with Kyo UI's native bindings, such as
`input.value(ref)` for a string or `checkbox.checked(ref)` for a boolean.
Applications that use Kyo UI can write:

```scala
import kyo.UI.*

def searchField(using StateStore): UI < Sync =
  useState("search.query")("").map(query => input.value(query))

def selectCpu(store: DashboardStore): UI =
  val select: Int => Unit < Async = index => store.selectTab(index)
  button("CPU").onClick(select(0))
```

Custom components can accept ordinary typed callbacks such as
`Int => Unit < Async`, or a function from an application event enum. Native
bindings and these callbacks need no pini4s-specific model or event API.

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
by one component, use Kyo's `Signal.initRef` directly.

The store owns references only. Effects and resources retain their usual
Kyo `Scope` lifetimes. Component effects and lifecycle belong to the UI
runtime. Native Kyo UI bindings consume the stored `SignalRef` directly;
pini4s itself needs only `kyo-core`.

## Development

Use JDK 25, Scala 3.9.0, and sbt 1.13.0. Kyo is pinned to 1.0.0-RC7.

```sh
sbt test
```

Tests use Kyo's test runner, and compiler warnings fail the build. The
store tests cover repeated and concurrent lookup, lazy initialization,
retry after initialization failure, exact types, and independent stores.

This working draft has no release or published artifact yet.
