# Notebooks

Jupyter notebooks (`.ipynb`) in `docs/` render as pages, next to your Markdown. They get the Helium theme, the navigation and the table of contents like any other page.

Rendering follows GitHub's model: the page shows the outputs already stored in the notebook, and nothing is executed. There is no Python or Jupyter dependency at build time. Run the notebook yourself and save it before building the site.

See the <a href="example_notebook.html">example notebook</a> for what a rendered notebook looks like.

## How it works

While staging the site, each `foo.ipynb` is converted to `foo.md`, with any images written to `foo_files/`. Laika then renders it with the rest of the site, so it ends up at `foo.html`.

- The page title is the notebook's first `# ` heading. If the notebook doesn't open with one, the file name is used instead.
- `.ipynb_checkpoints` directories are ignored.
- A notebook can't share its name with a Markdown page in the same folder (`foo.ipynb` with `foo.md` or `foo.mdoc.md`). The build fails with an error naming both files.
- Link to a notebook from Markdown by its `.md` name, e.g. `[analysis](analysis.md)`.
- Live reload works as usual with `mill -w site.serve`. Save the notebook and the page refreshes.

Only nbformat 4 notebooks are supported, which covers anything written by Jupyter in the last decade.

## Configuration

Rendering is on by default. Both settings live on `SiteModule` (or `LaikaModule` if you use it directly):

```scala
import io.github.quafadas.millSite.NotebookOptions

object site extends SiteModule:
  // Show `In [n]:` prompts, and truncate long outputs sooner
  override def notebookOptions = Task(NotebookOptions(showPrompts = true, maxOutputLines = 100))

  // Or turn the feature off: notebooks are then copied to the site as plain files
  override def renderNotebooks = Task(false)
```

| Option | Default | Meaning |
|---|---|---|
| `keepSource` | `true` | Keep the `.ipynb` in the site and add a "Download notebook" link at the top of the page |
| `showPrompts` | `false` | Prefix code cells with `In [n]:` |
| `maxOutputLines` | `500` | Truncate longer text outputs, adding a `… (truncated)` line. Zero or less disables truncation |
| `fallbackLanguage` | `"text"` | Code fence language when the notebook's metadata doesn't name one |

The code language comes from the notebook's `kernelspec`, so Almond notebooks are highlighted as Scala and IPython notebooks as Python.

## Outputs

Each output is rendered using the first MIME type it has from this list:

| MIME type | Rendered as |
|---|---|
| `application/vnd.vegalite.v5+json` (also v4 and v3), `application/vnd.vega.v5+json` | An interactive chart, drawn by [vega-embed](https://github.com/vega/vega-embed) |
| `application/vnd.plotly.v1+json` | An interactive chart, drawn by [Plotly](https://plotly.com/javascript/) |
| `application/javascript` | A script, run on the page, as Jupyter does |
| `text/html` | Raw HTML, as-is |
| `image/svg+xml` | An image, written to `foo_files/` |
| `image/png`, `image/jpeg`, `image/gif` | An image, written to `foo_files/` |
| `text/markdown` | Markdown, as part of the page |
| `text/latex` | The LaTeX source, as text (there is no maths rendering yet) |
| `text/plain` | Text |

Text streams (stdout and stderr) and errors are shown as text too. Consecutive writes to the same stream are merged, and terminal colour codes are stripped.

Outputs with none of these types are left out, and the build logs one warning per notebook listing the types it skipped. Interactive widgets (`application/vnd.jupyter.widget-view+json`) need a running kernel, so they render as their `text/plain` fallback if they have one.

### Charts

Vega and Plotly charts are drawn in the browser from the spec stored in the notebook. The JavaScript libraries are loaded from a CDN (jsDelivr for Vega, `cdn.plot.ly` for Plotly), at pinned versions, and only on pages that contain a chart of that kind. Readers therefore need to be online to see charts.

Charts are preferred over a `text/html` fallback, because the HTML that kernels write for Plotly usually expects the classic notebook's `require.js`, which a static page doesn't have.

Some libraries draw charts with a Javascript output instead, for example dedav4s's `almond_js` target. Those work too: the script runs on the page and loads whatever it needs itself.

## Cell tags

Cell tags follow the [Jupyter Book](https://jupyterbook.org/en/stable/content/metadata.html) conventions. Add them in Jupyter through the property inspector, or by editing the cell's metadata.

| Tag | Effect |
|---|---|
| `remove-cell` | The cell doesn't appear at all |
| `remove-input` | Only the outputs appear |
| `remove-output` | Only the code appears |
| `hide-input` | The code is collapsed behind a "Show code" toggle, and the outputs show as usual |

Markdown cells are rendered as written, including attached images. Raw cells appear only when their format is `text/markdown` or `text/html`.

## Styling

Outputs carry CSS classes you can target from your own stylesheet:

| Class | On |
|---|---|
| `nb-output` | Every output |
| `nb-stream`, `nb-stdout`, `nb-stderr` | Text written to stdout or stderr |
| `nb-result` | Plain text results |
| `nb-error` | Errors |
| `nb-html` | HTML outputs |
| `nb-vega`, `nb-plotly` | Charts |
| `nb-image` | Images |
| `nb-latex` | LaTeX source |
| `nb-prompt` | `In [n]:` prompts |
| `nb-hidden` | The `<details>` element around a hidden input |
| `nb-download` | The "Download notebook" link |

## Caveats

- HTML and Javascript outputs are included verbatim, and their scripts run when the page loads. That's fine for your own notebooks, but don't publish notebooks you don't trust.
- Markdown cells are parsed by Laika like any other page, so Laika's substitution and directive syntax applies in their prose. Code cells and outputs are unaffected.
- Other HTML outputs that load JavaScript through `require.js`, as Jupyter's classic notebook interface does, won't run on a static page.
