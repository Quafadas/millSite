# Documentation Site Plugin for Mill

A Scala 3 Mill plugin that makes it easy to publish documentation sites for libraries. Built for Mill 1.x.

## Project Overview

This plugin combines several documentation tools into a cohesive workflow:
- **Mdoc** - Typesafe, compiled Scala code examples in markdown
- **Laika** - Markdown to HTML conversion with theming
- **Unidoc** - API documentation generation
- **Scala.js** - Interactive code examples in the browser

## Source Files

| File | Purpose |
|------|---------|
| `plugin/src/site_plugin.scala` | Main `SiteModule` trait - combines all features into a single entry point with live-reload |
| `plugin/src/mdoc_plugin.scala` | `MdocModule` - processes `.mdoc.md` files, compiling Scala code blocks |
| `plugin/src/laika_plugin.scala` | `LaikaModule` - converts markdown to HTML using Laika |
| `plugin/src/site_jsplugin.scala` | `SiteJsModule` - Scala.js integration for interactive examples |
| `plugin/src/versions.scala` | Dependency version constants |
| `plugin/src/quickchange.scala` | File watching utilities for live-reload |
| `plugin/src/classpath.help.scala` | Classpath resolution helpers |

## Commands

```bash
# Compile the plugin
mill plugin.compile

# Run unit tests
mill plugin.unit

# Format code
scalafmt
```

## Test Resources

Test fixtures are in `plugin/unit/resources/`:
- `simple_site/` - Basic site with mdoc
- `laika_basic/` - Laika-only conversion
- `mdoc_basic/` - Mdoc processing tests
- `mdoc_js/` - Scala.js integration tests
- `unidoc_example/` - API documentation tests

## Working Effectively

1. Run tests after each change: `mill plugin.unit`
2. Run formatter after changes: `scalafmt`
3. The plugin uses Mill's module system - traits extend `ScalaModule`
4. Live reload is handled via `sjsls` (Scala.js Live Server)

## Key Concepts

- Files ending in `.mdoc.md` are processed by Mdoc (Scala code is compiled and output inserted)
- The `SiteModule` orchestrates: mdoc → laika → copy API docs → serve
- Unidoc generates API documentation which is copied into the site output

