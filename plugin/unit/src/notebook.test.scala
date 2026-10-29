package io.github.quafadas.millSite

import mill.api.Discover
import mill.api.Task.Simple
import mill.scalalib.UnidocModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester
import mill.util.TokenReaders.*
import utest.*

object NotebookConverterTests extends TestSuite:

  private val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  private def notebook(cells: ujson.Value*): ujson.Value =
    ujson.Obj(
      "nbformat" -> 4,
      "nbformat_minor" -> 5,
      "metadata" -> ujson.Obj("kernelspec" -> ujson.Obj("language" -> "scala")),
      "cells" -> ujson.Arr(cells*)
    )

  private def codeCell(source: String, outputs: ujson.Value*): ujson.Value =
    ujson.Obj(
      "cell_type" -> "code",
      "execution_count" -> 1,
      "metadata" -> ujson.Obj(),
      "source" -> source,
      "outputs" -> ujson.Arr(outputs*)
    )

  private def stdout(text: String): ujson.Value =
    ujson.Obj("output_type" -> "stream", "name" -> "stdout", "text" -> text)

  private def displayData(data: (String, ujson.Value)*): ujson.Value =
    ujson.Obj("output_type" -> "display_data", "metadata" -> ujson.Obj(), "data" -> ujson.Obj.from(data))

  private val vegaScripts = Seq(
    s"<script src=\"https://cdn.jsdelivr.net/npm/vega@${Versions.vegaVersion}/build/vega.min.js\"></script>",
    s"<script src=\"https://cdn.jsdelivr.net/npm/vega-lite@${Versions.vegaLiteVersion}/build/vega-lite.min.js\"></script>",
    s"<script src=\"https://cdn.jsdelivr.net/npm/vega-embed@${Versions.vegaEmbedVersion}/build/vega-embed.min.js\"></script>"
  ).mkString("\n")

  private val plotlyScript = s"<script src=\"https://cdn.plot.ly/plotly-${Versions.plotlyVersion}.min.js\"></script>"

  private def convert(nb: ujson.Value, opts: NotebookOptions = NotebookOptions()): ConvertedNotebook =
    NotebookConverter.convert(nb, "nb", opts)

  def tests: Tests = Tests {
    test("fixture notebook") {
      val nb = ujson.read(os.read(resourceFolder / "notebook_basic" / "docs" / "example.ipynb"))
      val result = NotebookConverter.convert(nb, "example", NotebookOptions())
      val md = result.markdown

      test("title comes from the first heading, followed by the download link") {
        assert(md.startsWith("# Example\n\n<p class=\"nb-download\"><a href=\"example.ipynb\" download>"))
      }
      test("code input is fenced with the kernel language") {
        assert(md.contains("```scala\nval answer = 6 * 7\nprintln(\"hello from stdout\")\nanswer\n```"))
      }
      test("consecutive stdout streams are merged, stderr kept separate") {
        assert(md.contains("nb-stdout\"><code class=\"text\">hello from stdout\nsecond stdout line</code></pre>"))
        assert(md.contains("nb-stderr\"><code class=\"text\">a warning on stderr</code></pre>"))
      }
      test("text/plain result") {
        assert(md.contains("<pre class=\"nb-output nb-result\"><code class=\"text\">res0: Int = 42</code></pre>"))
      }
      test("errors have ANSI codes stripped and the header isn't repeated") {
        assert(!md.contains("\u001b"))
        assert(md.contains("java.lang.Exception: boom\n  ammonite.$sess.cmd1$.&lt;init&gt;(cmd1.sc:1)"))
        assert(md.split("java.lang.Exception: boom", -1).length == 2)
      }
      test("remove-input keeps outputs only, and images become assets") {
        assert(!md.contains("plot()"))
        assert(md.contains("@:style(nb-output nb-image)\n![](example_files/cell3_out0.png)\n@:@"))
        val png = result.assets.toMap.apply(os.sub / "example_files" / "cell3_out0.png")
        assert(png.take(4).toSeq == Seq(0x89, 'P', 'N', 'G').map(_.toByte))
      }
      test("html output is passed through, blank lines included") {
        assert(md.contains("<div class=\"nb-output nb-html\">\n<table class=\"dataframe\">"))
        assert(md.contains("</tr>\n\n  <tr>"))
      }
      test("remove-cell") {
        assert(!md.contains("SHOULD_NOT_APPEAR"))
      }
      test("hide-input uses the details directive") {
        assert(md.contains("@:nbDetails(Show code)\n```scala\nval hidden = \"HIDDEN_INPUT\"\n```\n@:@"))
      }
      test("remove-output keeps the input only") {
        assert(md.contains("val quiet = 1"))
        assert(!md.contains("REMOVED_OUTPUT"))
      }
      test("markdown attachments are extracted and the reference rewritten") {
        assert(md.contains("![logo](example_files/cell8_logo.png)"))
        assert(!md.contains("attachment:"))
        assert(result.assets.map(_._1).contains(os.sub / "example_files" / "cell8_logo.png"))
      }
      test("unknown MIME types fall back to text/plain and are reported") {
        assert(md.contains("IntSlider(value=0)"))
        assert(result.skippedMimeTypes == Seq("application/vnd.jupyter.widget-view+json"))
      }
      test("raw cells without a markdown or html format are dropped") {
        assert(!md.contains("RAW_CELL_NOT_RENDERED"))
      }
    }

    test("fences are longer than any backtick run in the code") {
      val code = "val md = \"\"\"\n```scala\nval x = 1\n```\n\"\"\""
      val md = convert(notebook(codeCell(code))).markdown
      assert(md.contains(s"````scala\n$code\n````"))
    }

    test("nbformat 3 is rejected with a helpful error") {
      val nb = ujson.Obj("nbformat" -> 3, "nbformat_minor" -> 0, "worksheets" -> ujson.Arr())
      val e = assertThrows[NotebookConverter.NotebookException](convert(nb))
      assert(e.getMessage.contains("'nb'"))
      assert(e.getMessage.contains("nbformat 3"))
    }

    test("a title is added when the notebook doesn't start with a heading") {
      val md = NotebookConverter.convert(notebook(codeCell("1 + 1")), "my_notebook", NotebookOptions()).markdown
      assert(md.startsWith("# my\\_notebook\n"))
    }

    test("keepSource = false drops the download link") {
      val md = convert(notebook(codeCell("1 + 1")), NotebookOptions(keepSource = false)).markdown
      assert(!md.contains("Download notebook"))
    }

    test("language falls back when the notebook declares none") {
      val nb = notebook(codeCell("x = 1"))
      nb("metadata") = ujson.Obj()
      assert(convert(nb).markdown.contains("```text\nx = 1\n```"))
      assert(convert(nb, NotebookOptions(fallbackLanguage = "python")).markdown.contains("```python\nx = 1\n```"))
    }

    test("showPrompts adds In [n]:") {
      val nb = notebook(codeCell("1 + 1"))
      assert(!convert(nb).markdown.contains("In [1]:"))
      assert(
        convert(nb, NotebookOptions(showPrompts = true)).markdown.contains("<div class=\"nb-prompt\">In [1]:</div>")
      )
    }

    test("long outputs are truncated") {
      val nb = notebook(codeCell("noisy()", stdout((1 to 10).map(i => s"line $i\n").mkString)))
      val md = convert(nb, NotebookOptions(maxOutputLines = 3)).markdown
      assert(md.contains("line 1\nline 2\nline 3\n… (truncated)</code>"))
      assert(!md.contains("line 4"))
    }

    test("carriage returns overwrite the line, as in a terminal") {
      val md = convert(notebook(codeCell("progress()", stdout("10%\r50%\r100%\ndone\n")))).markdown
      assert(md.contains("<code class=\"text\">100%\ndone</code>"))
    }

    test("output text is HTML-escaped") {
      val md = convert(notebook(codeCell("html()", stdout("<b>not bold</b> & ${raw}\n")))).markdown
      assert(md.contains("&lt;b&gt;not bold&lt;/b&gt; &amp; ${raw}"))
    }

    test("raw cells render when their format is markdown or html") {
      def raw(format: String, source: String) =
        ujson.Obj("cell_type" -> "raw", "metadata" -> ujson.Obj("format" -> format), "source" -> source)
      val md = convert(
        notebook(raw("text/markdown", "RAW_MD"), raw("text/html", "<p>RAW_HTML</p>"), raw("text/x-rst", "RAW_RST"))
      ).markdown
      assert(md.contains("RAW_MD"))
      assert(md.contains("<div class=\"nb-raw\">\n<p>RAW_HTML</p>\n</div>"))
      assert(!md.contains("RAW_RST"))
    }

    test("hide-input falls back to plain html when the code would close the directive") {
      val cell = codeCell("val s = \"\"\"\n@:@\n\"\"\"")
      cell("metadata") = ujson.Obj("tags" -> ujson.Arr("hide-input"))
      val md = convert(notebook(cell)).markdown
      assert(!md.contains("@:nbDetails"))
      assert(md.contains("<details class=\"nb-hidden\"><summary>Show code</summary><pre><code class=\"scala\">"))
    }

    test("stripAnsi removes colour and hyperlink sequences") {
      val s = "\u001b[0;31mred\u001b[0m \u001b]8;;https://example.com\u0007link\u001b]8;;\u0007"
      assert(NotebookConverter.stripAnsi(s) == "red link")
    }

    test("vega-lite outputs are drawn with vega-embed, loaded once at the top of the page") {
      val spec = ujson.Obj("mark" -> "bar", "title" -> "</script><b>&</b>")
      val chart = displayData("application/vnd.vegalite.v5+json" -> spec, "text/plain" -> "VEGA_FALLBACK")
      val md = convert(notebook(codeCell("plot()", chart), codeCell("plot()", chart))).markdown

      assert(md.contains(s"Download notebook</a></p>\n\n$vegaScripts\n\n```scala"))
      assert(md.split("vega-embed.min.js", -1).length == 2)
      assert(
        md.contains(
          "<div class=\"nb-output nb-vega\">\n<div id=\"nb-vis-0-0\"></div>\n<script>vegaEmbed(\"#nb-vis-0-0\""
        )
      )
      assert(md.contains("vegaEmbed(\"#nb-vis-1-0\""))
      assert(md.contains("\"title\":\"\\u003c/script\\u003e\\u003cb\\u003e\\u0026\\u003c/b\\u003e\""))
      assert(!md.contains("</script><b>"))
      assert(!md.contains("VEGA_FALLBACK"))
    }

    test("vega specs stored as a JSON string are parsed") {
      val chart = displayData("application/vnd.vega.v5+json" -> "{\"mark\": \"line\"}")
      val md = convert(notebook(codeCell("plot()", chart))).markdown
      assert(md.contains("vegaEmbed(\"#nb-vis-0-0\", {\"mark\":\"line\"})"))
    }

    test("plotly outputs are drawn with Plotly, ahead of their require.js html fallback") {
      val fig = ujson.Obj("data" -> ujson.Arr(ujson.Obj("type" -> "scatter")), "layout" -> ujson.Obj())
      val chart =
        displayData("application/vnd.plotly.v1+json" -> fig, "text/html" -> "<script>require(['plotly'])</script>")
      val md = convert(notebook(codeCell("plot()", chart))).markdown

      assert(md.contains(s"Download notebook</a></p>\n\n$plotlyScript\n\n```scala"))
      assert(md.contains("<div class=\"nb-output nb-plotly\">\n<div id=\"nb-vis-0-0\"></div>\n<script>{"))
      assert(md.contains("const fig = {\"data\":[{\"type\":\"scatter\"}],\"layout\":{}};"))
      assert(md.contains("Plotly.newPlot(\"nb-vis-0-0\""))
      assert(!md.contains("require("))
      assert(!md.contains("vega"))
    }

    test("convertTree names the notebook when a conversion fails") {
      val dir = os.temp.dir()
      val chart = displayData("application/vnd.vega.v5+json" -> "{not json")
      os.write(dir / "broken.ipynb", ujson.write(notebook(codeCell("plot()", chart))))
      val e = assertThrows[NotebookConverter.NotebookException](NotebookConverter.convertTree(dir, NotebookOptions()))
      assert(e.getMessage.startsWith("broken.ipynb: "))
    }

    test("pages without charts load no libraries") {
      val md = convert(notebook(codeCell("1 + 1", stdout("2\n")))).markdown
      assert(!md.contains("<script"))
    }

    test("javascript outputs run as inline scripts, ahead of html") {
      val js = "document.getElementById(\"target\").textContent = \"</SCRIPT> ok\";"
      val out = displayData("application/javascript" -> js, "text/html" -> "<p>HTML_FALLBACK</p>")
      val md = convert(notebook(codeCell("draw()", out))).markdown
      assert(md.contains("<script>\ndocument.getElementById(\"target\").textContent = \"<\\/SCRIPT> ok\";\n</script>"))
      assert(!md.contains("HTML_FALLBACK"))
    }
  }
end NotebookConverterTests

object NotebookSiteTests extends TestSuite:

  private val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

  trait NotebookBuild extends TestRootModule with LaikaModule:
    val unidocs = new UnidocModule:
      override def scalaVersion: Simple[String] = Config.scalaVersion
      override def unidocDocumentTitle: Simple[String] = "Notebook Test API"
  end NotebookBuild

  def tests: Tests = Tests {
    test("notebooks render as pages") {
      object build extends NotebookBuild:
        lazy val millDiscover = Discover[this.type]
      end build

      // Guard against the fixture losing its checkpoint directory, which would make the checks below vacuous.
      assert(os.exists(resourceFolder / "notebook_basic" / "docs" / ".ipynb_checkpoints"))

      UnitTester(build, resourceFolder / "notebook_basic").scoped { eval =>
        val Right(result) = eval(build.generateSite).runtimeChecked
        val site = result.value.path
        val html = os.read(site / "example.html")

        assert(html.contains("res0: Int = 42"))
        assert(html.contains("<table class=\"dataframe\">"))
        assert(html.contains("table-cell-1"))
        assert(html.contains("*not emphasis*"))
        assert(html.contains("<pre class=\"nb-output nb-stream nb-stdout\">"))
        assert(html.contains("<details class=\"nb-hidden\"><summary>Show code</summary>"))
        assert(html.contains("<p class=\"nb-output nb-image\"><img src=\"example_files/cell3_out0.png\""))
        assert(html.contains("pre.nb-output"))
        assert(
          html.contains(
            s"<script src=\"https://cdn.jsdelivr.net/npm/vega@${Versions.vegaVersion}/build/vega.min.js\"></script>"
          )
        )
        assert(html.contains(s"<script src=\"https://cdn.plot.ly/plotly-${Versions.plotlyVersion}.min.js\"></script>"))
        // Laika indents raw HTML lines when rendering, so check the pieces separately
        assert(html.contains("<div id=\"nb-vis-11-0\"></div>"))
        assert(html.contains("<script>vegaEmbed(\"#nb-vis-11-0\""))
        assert(html.contains("Plotly.newPlot(\"nb-vis-12-0\""))
        assert(html.contains("<div id=\"nb-js-target\"></div>"))
        assert(html.contains("document.getElementById(\"nb-js-target\").textContent = `JS_RAN ${1 + 1}`;"))
        assert(!html.contains("</script><b>not markup</b>"))
        assert(!html.contains("VEGA_FALLBACK"))
        assert(!html.contains("require(['plotly']"))
        assert(!os.read(site / "index.html").contains("vega-embed.min.js"))
        assert(!html.contains("\u001b"))
        assert(!html.contains("SHOULD_NOT_APPEAR"))

        assert(os.exists(site / "example_files" / "cell3_out0.png"))
        assert(os.exists(site / "example_files" / "cell8_logo.png"))

        // keepSource defaults to true
        assert(os.exists(site / "example.ipynb"))
        assert(html.contains("<a href=\"example.ipynb\" download>Download notebook</a>"))

        // the index links to the notebook by its .md name
        assert(os.read(site / "index.html").contains("href=\"example.html\""))

        val outputs = os.walk(site)
        assert(!outputs.exists(_.last == NotebookConverter.CheckpointDir))
        assert(!outputs.filter(os.isFile(_)).exists(f => os.read(f).contains("CHECKPOINT_MARKER")))
      }
    }

    test("keepSource = false removes the notebook from the site") {
      object build extends NotebookBuild:
        override def notebookOptions: Simple[NotebookOptions] = NotebookOptions(keepSource = false)
        lazy val millDiscover = Discover[this.type]
      end build

      UnitTester(build, resourceFolder / "notebook_basic").scoped { eval =>
        val Right(result) = eval(build.generateSite).runtimeChecked
        val site = result.value.path
        assert(os.exists(site / "example.html"))
        assert(!os.exists(site / "example.ipynb"))
        assert(!os.read(site / "example.html").contains("Download notebook"))
      }
    }

    test("renderNotebooks = false copies notebooks as plain files") {
      object build extends NotebookBuild:
        override def renderNotebooks: Simple[Boolean] = false
        lazy val millDiscover = Discover[this.type]
      end build

      UnitTester(build, resourceFolder / "notebook_basic").scoped { eval =>
        val Right(staged) = eval(build.stageSite).runtimeChecked
        assert(os.exists(staged.value.path / "example.ipynb"))
        assert(!os.exists(staged.value.path / "example.md"))
      }
    }

    test("a notebook next to a markdown page of the same name fails the build") {
      object build extends NotebookBuild:
        lazy val millDiscover = Discover[this.type]
      end build

      UnitTester(build, resourceFolder / "notebook_clash").scoped { eval =>
        val Left(failure) = eval(build.stageSite).runtimeChecked
        assert(failure.toString.contains("foo.ipynb clashes with foo.md"))
      }
    }
  }
end NotebookSiteTests
