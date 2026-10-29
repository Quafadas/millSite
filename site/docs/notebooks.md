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
| `text/html` | Raw HTML, as-is |
| `image/svg+xml` | An image, written to `foo_files/` |
| `image/png`, `image/jpeg`, `image/gif` | An image, written to `foo_files/` |
| `text/markdown` | Markdown, as part of the page |
| `text/latex` | The LaTeX source, as text (there is no maths rendering yet) |
| `text/plain` | Text |

Text streams (stdout and stderr) and errors are shown as text too. Consecutive writes to the same stream are merged, and terminal colour codes are stripped.

Outputs with none of these types are left out, and the build logs one warning per notebook listing the types it skipped. Interactive widgets (`application/vnd.jupyter.widget-view+json`) need a running kernel, so they render as their `text/plain` fallback if they have one. Vega, Vega-Lite and Plotly specs aren't rendered yet.

## Cell tags

Cell tags follow the [Jupyter Book](https://jupyterbook.org/en/stable/content/metadata.html) conventions. Add them in Jupyter through the property inspector, or by editing the cell's metadata.

| Tag | Effect |
|---|---|
| `remove-cell` | The cell doesn't appear at all |
| `remove-input` | Only the outputs appear |
| `remove-output` | Only the code appears |
| `hide-input` | The code is collapsed behind a "Show code" toggle, and the outputs show as usual |

The underscore spellings used with nbconvert's `TagRemovePreprocessor`, such as `remove_output`, work too.

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
| `nb-image` | Images |
| `nb-latex` | LaTeX source |
| `nb-prompt` | `In [n]:` prompts |
| `nb-hidden` | The `<details>` element around a hidden input |
| `nb-download` | The "Download notebook" link |

## Caveats

- HTML outputs are included verbatim, scripts and all. That's fine for your own notebooks, but don't publish notebooks you don't trust.
- Markdown cells are parsed by Laika like any other page, so Laika's substitution and directive syntax applies in their prose. Code cells and outputs are unaffected.
- Outputs that load JavaScript through `require.js`, as Jupyter's classic notebook interface does, won't run on a static page.
