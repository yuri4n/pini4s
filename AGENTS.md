# pini4s: rules for agents and contributors

## Purpose and API boundary

pini4s is Pinia for Scala: its purpose is shared application state. Keep the
library small and independent of a UI renderer. The current API is an
explicit `StateStore` and an optional Nuxt-style keyed `useState` helper.

Keep these architectural decisions in future changes:

- Shared stores belong in pini4s. Its production dependency is `kyo-core`.
- Component APIs belong in the separate
  [kyo-ui-state repository](https://github.com/yuri4n/kyo-ui-state). Its core owns
  `Model`, `Emits`, and `useEmits`; its optional UI module owns `Tabs`.
  Its Scala package is `kyo.ui.state`, with artifact
  `io.github.yuri4n::kyo-ui-state`; the namespace does not imply upstream
  Kyo ownership.
  Do not add these APIs or a UI module to pini4s.
- A component owns its local state by default. Shared state is an explicit
  option, supplied by a parent or application store.
- Stores are optional. Local component state needs no pini4s. Use keyed
  `useState` for a few shared values; use an ordinary Scala class to group
  related state and actions when a named store helps the application.
- Follow Vue and Pinia conventions where they help a typed Scala API.
  Component APIs inspired by Vue belong in kyo-ui-state; store APIs inspired
  by Pinia belong here.
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
