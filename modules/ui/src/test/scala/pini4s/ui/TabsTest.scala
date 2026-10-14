package pini4s.ui

import kyo.*
import kyo.UI.*
import kyo.UI.Ast.*
import kyo.test.*
import pini4s.Model

/** Exercises the public Kyo UI tree and its handlers without a renderer. */
class TabsTest extends Test[Any]:
    private val labels = Seq("One", "Two", "Three")

    private def panel(index: Int)(using Frame): UI = span(s"[panel=$index]")

    private def nodes(ui: UI)(using Frame): Vector[UI] < Sync =
      ui match
          case reactive: Reactive[?] => reactive.signal.current.map(nodes)
          case fragment: Fragment[?] => children(fragment.children)
          case element: Element => children(element.children).map(element +: _)
          case _ => Vector(ui)

    private def children(values: Chunk[UI])(using Frame): Vector[UI] < Sync =
      Kyo.foreach(values)(nodes).map(_.flatten.toVector)

    private def buttons(ui: UI)(using Frame): Vector[Button] < Sync =
      nodes(ui).map(_.collect { case button: Button => button })

    private def text(ui: UI)(using Frame): String < Sync =
      nodes(ui).map(_.collect { case Text(value) => value }.mkString)

    private def click(ui: UI, index: Int)(using Frame): Unit < Async =
      buttons(ui).map(_(index).attrs.onClick.get.unit)

    private def key(ui: UI, index: Int, value: Keyboard)(using Frame): Unit < Async =
      buttons(ui).map(
        _(index).attrs.onKeyDown.get(KeyboardEvent(value, Modifiers.none, Absent)).unit,
      )

    "two ui evaluations of one widget own separate state and focus groups" in {
      val widget = new Tabs(labels, panel = panel)
      val allocate = widget.ui
      for
          first  <- allocate
          second <- allocate
          a      <- buttons(first)
          b      <- buttons(second)
          _      <- click(first, 1)
          before <- text(second)
          after  <- text(first)
      yield
          assert(a.head.attrs.focusGroup != b.head.attrs.focusGroup)
          assert(after.contains("[panel=1]"))
          assert(before.contains("[panel=0]"))
    }

    "models control rejection, external changes, and typed request events" in {
      for
          selected <- Signal.initRef(0)
          events   <- Signal.initRef(Vector.empty[Int])
          widget    = new Tabs(
                        labels,
                        model = Some(Model[Int](
                          selected,
                          next => if next == 2 then Kyo.unit else selected.set(next),
                        )),
                        onEvent = { case TabsEvent.Change(next) =>
                          events.updateAndGet(_ :+ next).unit
                        },
                        panel = panel,
                      )
          ui       <- widget.ui
          _        <- click(ui, 1)
          accepted <- text(ui)
          _        <- click(ui, 2)
          rejected <- text(ui)
          _        <- selected.set(2)
          external <- text(ui)
          emitted  <- events.get
      yield
          assert(accepted.contains("[panel=1]"))
          assert(rejected.contains("[panel=1]"))
          assert(external.contains("[panel=2]"))
          assert(emitted == Vector(1, 2))
    }

    "arrows select relative to the focused button even when selection differs" in {
      for
          ui      <- new Tabs(labels, initialTab = -1, panel = panel).ui
          initial <- text(ui)
          _       <- key(ui, 0, Keyboard.ArrowRight)
          right   <- text(ui)
          _       <- key(ui, 0, Keyboard.ArrowLeft)
          wrapped <- text(ui)
      yield
          assert(initial.contains("[panel=2]"))
          assert(right.contains("[panel=1]"))
          assert(wrapped.contains("[panel=2]"))
    }

    "runtime adapters retain supplied state across view reconstruction" in {
      val first = new Tabs(labels, panel = panel)
      val next = new Tabs(labels, panel = panel)
      for
          state  <- Signal.initRef(first.initial)
          ui      = first.view(state)
          _      <- click(ui, 2)
          before <- state.get
          rebuilt = next.view(state)
          shown  <- text(rebuilt)
          after  <- state.get
      yield
          assert(shown.contains("[panel=2]"))
          assert(after == before)
          assert(first.initial.focusGroup != next.initial.focusGroup)
    }
end TabsTest
