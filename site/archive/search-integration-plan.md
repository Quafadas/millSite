# Search Integration Plan: Protosearch + Mill Site Plugin

## Overview

Add full-text search to the Mill documentation site plugin using [Protosearch](https://github.com/cozydev-pink/protosearch), the Scala-ecosystem search library used by Typelevel. The search index is built at site-generation time; the search UI runs entirely client-side via Scala.js.

Reference implementation: [typelevel/typelevel.github.com/build.scala](https://github.com/typelevel/typelevel.github.com/blob/main/build.scala)

---

## Current Architecture (Relevant Parts)

| File | Role |
|------|------|
| `plugin/src/laika_plugin.scala` | `LaikaModule` trait — markdown → HTML via Laika/Helium |
| `plugin/src/site_plugin.scala` | `SiteModule` — top-level orchestration |
| `plugin/src/site_jsplugin.scala` | `SiteJSModule` — existing Scala.js support (mdoc) |
| `plugin/src/versions.scala` | Centralised dependency version constants |
| `build.mill` | Plugin build + `site` module for this plugin's own docs |

The key method to understand is `LaikaModule.generateSite` in `laika_plugin.scala`. It currently uses a single `Transformer` (parse + render in one step) to produce HTML. We need to split this into separate parse and render phases so we can produce both HTML **and** the search index from one parse of the document tree.

---

## Phase 1 — Dependency

### 1.1 Add protosearch to plugin `mvnDeps`

In `build.mill`, add to the `plugin` module's `mvnDeps`:

```scala
mvn"pink.cozydev::protosearch-laika:0.0-7f79720-SNAPSHOT"
```

Also add the Sonatype snapshot repository so Mill can resolve it:

```scala
override def repositoriesTask = Task {
  super.repositoriesTask() ++ Seq(
    coursier.maven.MavenRepository("https://central.sonatype.com/repository/maven-snapshots")
  )
}
```

> **Note**: `protosearch-laika` is currently **SNAPSHOT only** — no stable release exists. The exact commit hash (`7f79720`) should be tracked. When protosearch cuts a stable release, switch to it. This is the same hash used by Typelevel's own site.

### 1.2 Add version constant

In `plugin/src/versions.scala`:

```scala
val protosearchVersion = "0.0-7f79720-SNAPSHOT"
```

---

## Phase 2 — Search Index Generation

### 2.1 Refactor `generateSite` to split parse and render

The current `generateSite` uses `Transformer.from(Markdown).to(HTML)` which does not let us reuse the parsed tree. Replace it with the explicit `MarkupParser` → `Renderer` pipeline so we can feed the same parsed `DocumentTreeRoot` to both an HTML renderer and an index renderer.

Before:
```scala
val transformer = Transformer.from(Markdown).to(HTML)...
```

After (schematic):
```scala
import pink.cozydev.protosearch.analysis.{IndexFormat, IndexRendererConfig}

val parserResource  = MarkupParser.of(Markdown).using(...).parallel[IO].build
val htmlResource    = Renderer.of(HTML).withTheme(heliumTheme).parallel[IO].build
val indexResource   = Renderer.of(IndexFormat.default).parallel[IO].build

(parserResource, htmlResource, indexResource).tupled.use { (parser, html, idx) =>
  parser.fromDirectory(stageSite().path.toString()).parse.flatMap { tree =>
    html.from(tree).toDirectory(dest.toString()).render *>
    idx.from(tree).toFile((dest / "search" / "searchIndex.idx").toString()).render
  }
}
```

The index file ends up at `<site-output>/search/searchIndex.idx`.

### 2.2 Gate behind a flag

Add to `LaikaModule`:

```scala
/** Set to true to generate a protosearch index alongside the HTML site. */
def enableSearch: Simple[Boolean] = Task(false)
```

Only build the index renderer when `enableSearch()` is `true` so that users who don't need search don't pull in the protosearch dependency at runtime.

### 2.3 Verify with `IndexConfig` (for live-preview compatibility)

The Laika preview server uses `BinaryRendererConfig`, not raw `Renderer`. If live-reload support for the search index during `serve` is wanted, use `IndexConfig.default` (a `BinaryRendererConfig`) instead of `IndexFormat` directly — see the [Laika Integration docs](https://cozydev-pink.github.io/protosearch/02-tutorial/03-laika-integration.html).

---

## Phase 3 — Search UI

Two options in order of implementation effort. Do 3a first to validate the index is correct, then replace it with 3b.

### 3a — Quick: `SearchUI.standalone` (Bulma-based)

One-line change in the `helium` task inside `LaikaModule`:

```scala
import pink.cozydev.protosearch.ui.SearchUI

// Change:
val heliumTheme = helium().build
// To:
val heliumTheme = helium().build.extendWith(SearchUI.standalone)
```

`SearchUI.standalone` is a Laika `ThemeProvider` extension that injects a complete search widget using Bulma CSS (already used by Helium). It automatically looks for the index at `/search/searchIndex.idx`.

Use this to confirm end-to-end: index generates, widget renders, results return correctly.

### 3b — Preferred: Custom Scala.js widget with WebAwesome

Replace 3a with a widget we own, written in Scala.js, using [WebAwesome](https://webawesome.com/) components instead of Bulma.

#### New file: `plugin/src/search_jsplugin.scala`

Define a new `SearchJsModule` trait (analogous to `SiteJSModule`):

```scala
trait SearchJsModule extends ScalaJSModule:
  // Dep on protosearch jsinterop — cross-compiled to Scala.js
  override def mvnDeps = Task {
    super.mvnDeps() ++ Seq(
      mvn"pink.cozydev::protosearch-jsinterop:${Versions.protosearchVersion}"
    )
  }
  override def moduleKind = Task(ModuleKind.ESModule)
```

The module compiles `SearchWidget.scala` (see below) and its output (`search.mjs`) is copied into the site output.

#### `SearchWidget.scala` (inside the Scala.js module)

Responsibilities:
1. Add a search icon button to Helium's existing `.top-nav` DOM element
2. On click, fetch `/search/searchIndex.idx` (binary), load it via `JsInterop`
3. Render a `<wa-dialog>` containing `<wa-input>` + results list
4. On input change, query the index and populate the results

Skeleton:
```scala
import pink.cozydev.protosearch.JsInterop
import org.scalajs.dom
import org.scalajs.dom.{fetch, document}

object SearchWidget:
  def init(indexPath: String): Unit =
    val btn = document.createElement("wa-icon-button")
    btn.setAttribute("name", "magnifying-glass")
    document.querySelector(".top-nav").appendChild(btn)
    // ... open dialog, load index, wire up search
```

WebAwesome components (`wa-dialog`, `wa-input`, `wa-menu`, `wa-menu-item`) are loaded via CDN `<link>` / `<script>` in a Laika template or injected via `helium().site.inlineJS(...)` — no bundling required.

#### Plugin integration

Add to `LaikaModule`:

```scala
/** Override with a SearchJsModule to enable the custom Scala.js search widget. */
def searchJsModule: Option[SearchJsModule] = None
```

When non-empty, `generateSite` must:
1. Call `searchJsModule.get.fastLinkJS()` (or `fullLinkJS` for release)
2. Copy the output `.mjs` to `<site-output>/search.mjs`
3. Inject the WebAwesome CDN link and the module initialiser via `helium().site.inlineJS`:

```scala
helium()
  .site.inlineJS(
    """<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace/dist/themes/light.css">
      |<script type="module" src="https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace/dist/shoelace.js"></script>
      |<script type="module">
      |  import { SearchWidget } from '/search.mjs';
      |  SearchWidget.init('/search/searchIndex.idx');
      |</script>""".stripMargin
  )
```

> **Note on WebAwesome vs Shoelace**: WebAwesome is the commercial successor to Shoelace. The free tier of WebAwesome is sufficient. Check the CDN URL when implementing — it may differ from the Shoelace CDN.

---

## Phase 4 — Tests

Add a test fixture `plugin/unit/resources/search_basic/` mirroring `laika_basic/` but with `enableSearch = true`. The test asserts:

1. `searchIndex.idx` exists in the output directory
2. The file is non-empty (non-zero bytes)

The test lives in `plugin/unit/src/` alongside the existing `laika.test.scala`.

---

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| `protosearch-laika` is SNAPSHOT — API may change | Pin exact commit hash in `Versions.scala`; review on update |
| Helium DOM structure may change between Laika versions — Scala.js nav injection could break | Use `SearchUI.standalone` (3a) as the fallback; add a Laika version constraint note in `LaikaModule` docs |
| WebAwesome CDN availability | Document how to self-host; the CDN approach is fine for docs sites |
| `fullLinkJS` vs `fastLinkJS` — ES module output may vary | Test with `fastLinkJS` in dev; use `fullLinkJS` when `generateSite` is called for production |

---

## Suggested Implementation Order

1. **Phase 1** — add dep, confirm compile
2. **Phase 2** — refactor `generateSite` and generate index, confirm `searchIndex.idx` is produced
3. **Phase 3a** — `SearchUI.standalone`, confirm search works end-to-end in a browser
4. **Phase 4** — add test
5. **Phase 3b** — Scala.js widget, replace 3a

Steps 1–4 can be done without any Scala.js work and give a fully working search. Step 5 is additive.
