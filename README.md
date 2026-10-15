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

The current registry shares signals only: this helper creates a new
`DashboardStore` object on each call and does not run a whole group's setup
once. For a group of related composables, including references, computed
values, actions, subscriptions, and resources, construct one typed object
under the application's Kyo `Scope` and provide that exact object through
`Env`. Keep that scope open for the application run. Components then share
the group without owning its cleanup; application shutdown closes its
effects. Each app, test, or SSR request needs its own provider.

A dedicated definition API for such shared groups is still being compared
with native Kyo composition and state-effect approaches. The existing
`StateStore` does not own or dispose those effects.

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
With Nix and flakes enabled, the development shell supplies the JDK and sbt:

```sh
nix develop
sbt check
```

The pinned shell targets Linux on x86_64 or aarch64 and macOS on Apple
Silicon. This nixpkgs revision does not support Intel macOS; use the manual
JDK 25 and sbt setup below on that platform.

With direnv and nix-direnv installed, `direnv allow` enables the same shell
when entering this checkout. Without Nix, install JDK 25 and sbt, make sure
`JAVA_HOME` points to that JDK, and run the same sbt commands. The first run
downloads dependencies; later runs reuse Nix and sbt caches.

Tests use Kyo's test runner, and compiler warnings fail the build. The
store tests cover repeated and concurrent lookup, lazy initialization,
retry after initialization failure, exact types, and independent stores.
`sbt check` runs those tests and compiles the console demo. `sbt test` runs
only the library tests.

## Runnable distribution

The root project remains a library. A separate, nonpublishing `demo` project
uses sbt-native-packager to package a console example with its dependencies:

```sh
sbt demo/Universal/stage demo/Universal/packageBin
demo/target/universal/stage/bin/pini4s-demo
```

Expected output:

```text
pini4s-demo: shared=7 independent=0
```

The example checks that two lookups of the same key share a reference and
observe its update, while a fresh store keeps independent state. It uses
the actual `StateStore` and needs no UI renderer or terminal.

`stage` writes a runnable directory under `demo/target/universal/stage`.
`packageBin` writes its ZIP distribution under `demo/target/universal`.
The generated launcher works after extracting that ZIP with JDK 25 available
through `JAVA_HOME` or `PATH`; the archive contains the application and its
dependency jars, and uses the installed JDK. It does not require sbt to run.
CI checks both the staged launcher and the extracted ZIP, including an
extraction path that contains spaces, and keeps the ZIP as a build artifact.
It also evaluates the Nix shell for all declared systems without building
the tools or allowing lockfile updates.

This working draft has no release or published artifact yet.
