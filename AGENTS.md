# pini4s: rules for agents and contributors

## Purpose and API boundary

pini4s is Pinia for Scala: its purpose is shared application state. Keep the
library small and independent of a UI renderer. The current API is an
explicit `StateStore` and an optional Nuxt-style keyed `useState` helper.

Keep these architectural decisions in future changes:

- Shared stores belong in pini4s. Its production dependency is `kyo-core`.
- Reuse native Kyo signals, UI bindings, typed Scala callbacks, `Env`, and
  `Layer`. Do not add widget, model, emit, or UI framework APIs to pini4s.
- The extra behavior here is an explicit keyed registry: one native
  `SignalRef` per string key, with one exact value type enforced per key;
  lazy successful initialization once even under concurrent lookup; retry
  after initialization failure; and separate state for separate registries.
  Keep this distinction from native Kyo clear.
- A component owns its local state by default. Shared state is an explicit
  option, supplied by a parent or application store.
- Stores are optional. Local component state needs no pini4s. An ordinary
  named Scala store can group state and actions and use Kyo `Env` or `Layer`
  without pini4s. Use keyed `useState` only when consumers need shared
  registry lookup.
- Follow Vue and Pinia conventions where they help a typed Scala API.
  Reuse Kyo's native capabilities before adding a new abstraction. Keep
  pini4s focused on shared registry behavior inspired by Pinia.
- Use explicit stores with application or test lifetimes. Do not introduce a
  process-wide state registry or make the store own component resources.
- Support composition with Kyo Env as well as Scala contextual parameters.
  Resolve a store before capturing it in deferred component callbacks, or
  provide its Env at callback execution. Test the actual deferred operation.
- Compare API alternatives with small executable spikes and independent
  reviews. Distinguish source-location Frame metadata from application services.

## Toolchain and checks

- Use JDK 25, Scala 3.9.0, sbt 1.13.0, and Kyo 1.0.0-RC7.
- Check `java -version` before running sbt. Kyo needs JDK 25 at compile time
  and at runtime.
- Run `sbt test` from this checkout. Tests use the Kyo test runner and fork a
  JVM. Compiler warnings fail the build.
- Run only one sbt process at a time when this checkout is also used by a
  parent build. The processes share generated output and locks.
- Preserve lazy initialization, concurrent lookup, retry after a failed
  initializer, exact key types, and independent stores in implementation
  changes. Add tests for changes to these guarantees.
- Keep tests independent of terminal and browser runtimes.
- When used as a submodule, validate the consuming build after changing a
  public API. Commit and review changes in each repository separately.
