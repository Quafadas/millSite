package io.github.quafadas.millSite

import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.util.matching.Regex

import upickle.default.*

/** Options controlling how Jupyter notebooks (`.ipynb`) are rendered into site pages.
  *
  * @param keepSource
  *   keep the `.ipynb` file in the generated site and add a "Download notebook" link at the top of the page
  * @param showPrompts
  *   prefix each code cell with an `In [n]:` execution prompt
  * @param maxOutputLines
  *   text outputs (streams, errors, plain text results) longer than this are truncated, with a trailing `… (truncated)`
  *   line. Zero or less disables truncation.
  * @param fallbackLanguage
  *   language used for code fences when the notebook declares none in its metadata
  */
case class NotebookOptions(
    keepSource: Boolean = true,
    showPrompts: Boolean = false,
    maxOutputLines: Int = 500,
    fallbackLanguage: String = "text"
) derives ReadWriter

/** The result of converting a single notebook.
  *
  * @param markdown
  *   the page content, as Markdown for Laika
  * @param assets
  *   extracted images, as paths relative to the notebook's folder paired with their content
  * @param skippedMimeTypes
  *   output MIME types present in the notebook that have no renderer, so were left out of the page
  */
case class ConvertedNotebook(
    markdown: String,
    assets: Seq[(os.SubPath, Array[Byte])],
    skippedMimeTypes: Seq[String]
)

/** Converts Jupyter notebooks (nbformat 4) to Markdown pages, rendering the outputs already stored in the notebook.
  * Like GitHub, nothing is executed.
  *
  * ==Laika notes==
  * Findings from probing Laika 1.3's Markdown parser, which shape the output below:
  *   - Laika does not follow CommonMark for HTML blocks. It parses a block from an opening tag to its matching closing
  *     tag, so blank lines inside the block are fine, and `<script>` and `<style>` survive verbatim.
  *   - Markdown nested inside an HTML block is ''not'' parsed. A fenced code block inside `<div>` or `<details>` is
  *     emitted as literal backticks, so we can't wrap fences in HTML.
  *   - The `@:style(...)` directive can put classes on Markdown blocks, but a line reading `@:@` inside the body closes
  *     the directive early, and directives don't nest. Arbitrary output text is therefore emitted as escaped `<pre>`
  *     HTML rather than as a fenced block inside a directive.
  *   - `${...}` and `@:` are taken literally inside code fences and raw HTML blocks. In Markdown prose they are Laika
  *     substitutions and directives, exactly as in any other page on the site.
  *
  * Hidden inputs use the `@:nbDetails` directive, registered by [[NotebookDirectives]], so the code keeps its syntax
  * highlighting.
  */
object NotebookConverter:

  /** Raised when a notebook can't be converted. The message is meant for the user. */
  class NotebookException(message: String) extends RuntimeException(message)

  /** Name of the directory Jupyter writes autosave checkpoints into, next to each notebook. */
  val CheckpointDir = ".ipynb_checkpoints"

  /** CSS for the classes emitted by the converter, using Helium's colour variables so it follows light and dark mode.
    */
  val css: String =
    """pre.nb-output {
      |  background-color: var(--primary-light);
      |  border-left: 3px solid var(--primary-medium);
      |  border-radius: 0 5px 5px 0;
      |}
      |pre.nb-output code {
      |  color: var(--text-color);
      |}
      |pre.nb-output.nb-stderr {
      |  background-color: var(--messages-warning-light);
      |  border-left-color: var(--messages-warning);
      |}
      |pre.nb-output.nb-error {
      |  background-color: var(--messages-error-light);
      |  border-left-color: var(--messages-error);
      |}
      |div.nb-output.nb-html, div.nb-raw {
      |  overflow-x: auto;
      |  margin: 0 0 var(--block-spacing);
      |  padding-left: 12px;
      |  border-left: 3px solid var(--primary-medium);
      |}
      |.nb-output.nb-image img {
      |  max-width: 100%;
      |}
      |.nb-prompt {
      |  font-family: var(--code-font);
      |  font-size: var(--small-font-size);
      |  color: var(--primary-color);
      |  margin-bottom: 2px;
      |}
      |details.nb-hidden > summary {
      |  cursor: pointer;
      |  color: var(--primary-color);
      |  font-size: var(--small-font-size);
      |  margin-bottom: var(--block-spacing);
      |}
      |p.nb-download {
      |  font-size: var(--small-font-size);
      |}
      |""".stripMargin

  /** ECMA-48 CSI sequences (colours, cursor movement) and OSC sequences (e.g. hyperlinks). */
  private val AnsiEscape: Regex = "\u001b(?:\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001b]*(?:\u0007|\u001b\\\\))".r

  private val BacktickRun: Regex = "`+".r

  private val ImageTypes: Seq[(String, String)] =
    Seq("image/png" -> "png", "image/jpeg" -> "jpg", "image/gif" -> "gif")

  /** MIME types we can render, in order of preference. */
  private val RenderableTypes: Seq[String] =
    Seq("text/html", "image/svg+xml") ++ ImageTypes.map(_._1) ++ Seq("text/markdown", "text/latex", "text/plain")

  /** Removes ANSI escape sequences, as found in IPython tracebacks and coloured terminal output. */
  def stripAnsi(s: String): String = AnsiEscape.replaceAllIn(s, "")

  /** Wraps `content` in a backtick fence one longer than the longest backtick run inside it, so it can't close early.
    */
  def fence(language: String, content: String): String =
    val longestRun = BacktickRun.findAllIn(content).map(_.length).maxOption.getOrElse(0)
    val ticks = "`" * math.max(3, longestRun + 1)
    s"$ticks$language\n${content.stripSuffix("\n")}\n$ticks"
  end fence

  /** Converts a parsed notebook to Markdown plus image assets.
    *
    * @param nb
    *   the notebook JSON
    * @param baseName
    *   the notebook's file name without `.ipynb`, used for asset paths, the download link, and as the page title when
    *   the notebook doesn't open with a `# ` heading
    * @param opts
    *   rendering options
    * @throws NotebookException
    *   if the notebook isn't nbformat 4
    */
  def convert(nb: ujson.Value, baseName: String, opts: NotebookOptions): ConvertedNotebook =
    val root = nb.objOpt.getOrElse(fail(baseName, "the file is not a JSON object"))
    root.get("nbformat").flatMap(_.numOpt) match
      case Some(v) if v == 4 => ()
      case Some(other)       => fail(baseName, s"nbformat ${other.toInt} is not supported, only nbformat 4")
      case None              => fail(baseName, "no nbformat field, so this doesn't look like a Jupyter notebook")
    end match

    val ctx = Context(baseName, opts, language(root, opts))
    val cells = root.get("cells").flatMap(_.arrOpt).getOrElse(Seq.empty)
    val body = cells.zipWithIndex.flatMap((cell, idx) => convertCell(cell, idx, ctx)).filter(_.trim.nonEmpty)

    val (title, rest) = body.headOption.map(_.stripLeading) match
      case Some(first) if first.startsWith("# ") =>
        val (heading, remainder) = first.span(_ != '\n')
        (heading.trim, remainder.dropWhile(_ == '\n') +: body.tail)
      case _ => (s"# ${escapeMarkdown(baseName)}", body)

    val download =
      if opts.keepSource then
        Seq(s"""<p class="nb-download"><a href="${escapeHtml(
            urlEncode(s"$baseName.ipynb")
          )}" download>Download notebook</a></p>""")
      else Seq.empty

    val markdown = (title +: download ++: rest).filter(_.nonEmpty).mkString("", "\n\n", "\n")
    ConvertedNotebook(markdown, ctx.assets.toSeq, ctx.skipped.toSeq.sorted)
  end convert

  /** Converts every notebook under `root` in place, for staging the site.
    *
    * Each `foo.ipynb` becomes `foo.md`, with any images written under `foo_files/`. `.ipynb_checkpoints` directories
    * are deleted. The `.ipynb` itself is kept or removed according to [[NotebookOptions.keepSource]].
    *
    * @return
    *   warnings for the user, one per notebook that had outputs we couldn't render
    * @throws NotebookException
    *   if a notebook is invalid, or sits next to a Markdown page of the same name
    */
  def convertTree(root: os.Path, opts: NotebookOptions): Seq[String] =
    os.walk(root, skip = _.last == CheckpointDir).filter(os.isDir(_)).appended(root).foreach { dir =>
      val checkpoints = dir / CheckpointDir
      if os.isDir(checkpoints) then os.remove.all(checkpoints)
      end if
    }

    val notebooks = os.walk(root).filter(p => os.isFile(p) && p.ext == "ipynb").sortBy(_.toString)
    notebooks.flatMap { nbPath =>
      val rel = nbPath.relativeTo(root).toString
      val baseName = nbPath.last.stripSuffix(".ipynb")
      val dir = nbPath / os.up

      val clashes = Seq(s"$baseName.md", s"$baseName.mdoc.md").filter(n => os.exists(dir / n))
      if clashes.nonEmpty then
        throw NotebookException(
          s"$rel clashes with ${clashes.mkString(" and ")} in the same folder: both would become the same page. Rename or remove one of them."
        )
      end if

      val json =
        try ujson.read(os.read(nbPath))
        catch
          case e: ujson.ParsingFailedException => throw NotebookException(s"$rel is not valid JSON: ${e.getMessage}")

      val converted =
        try convert(json, baseName, opts)
        catch case e: NotebookException => throw NotebookException(s"$rel: ${e.getMessage}")

      os.write.over(dir / s"$baseName.md", converted.markdown)
      converted.assets.foreach((sub, bytes) => os.write.over(dir / sub, bytes, createFolders = true))
      if !opts.keepSource then os.remove(nbPath)
      end if

      Option.when(converted.skippedMimeTypes.nonEmpty)(
        s"$rel: skipped outputs with unsupported MIME types: ${converted.skippedMimeTypes.mkString(", ")}"
      )
    }
  end convertTree

  private class Context(val baseName: String, val opts: NotebookOptions, val language: String):
    val assetDir: String = s"${baseName.replaceAll("[^A-Za-z0-9._-]", "_")}_files"
    val assets = scala.collection.mutable.ArrayBuffer.empty[(os.SubPath, Array[Byte])]
    val skipped = scala.collection.mutable.SortedSet.empty[String]

    /** Records an asset and returns the path to reference it by, relative to the page. */
    def addAsset(fileName: String, bytes: Array[Byte]): String =
      val path = s"$assetDir/$fileName"
      assets += os.SubPath(path) -> bytes
      path
    end addAsset
  end Context

  private def fail(baseName: String, reason: String): Nothing =
    throw NotebookException(s"Can't render notebook '$baseName': $reason")

  private def language(root: collection.Map[String, ujson.Value], opts: NotebookOptions): String =
    val meta = root.get("metadata")
    def field(section: String, key: String) =
      meta.flatMap(_.objOpt).flatMap(_.get(section)).flatMap(_.objOpt).flatMap(_.get(key)).flatMap(_.strOpt)
    field("kernelspec", "language")
      .orElse(field("language_info", "name"))
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .getOrElse(opts.fallbackLanguage)
  end language

  /** A notebook text field: either a string or an array of lines that already carry their `\n`. */
  private def text(v: ujson.Value): String = v match
    case ujson.Str(s)   => s
    case ujson.Arr(arr) => arr.iterator.map(_.strOpt.getOrElse("")).mkString
    case ujson.Null     => ""
    case other          => other.render()

  private def field(obj: ujson.Value, key: String): Option[ujson.Value] = obj.objOpt.flatMap(_.get(key))

  private def objectOf(v: Option[ujson.Value]): collection.Map[String, ujson.Value] =
    v.flatMap(_.objOpt).getOrElse(collection.Map.empty[String, ujson.Value])

  private def metadataString(cell: ujson.Value, key: String): Option[String] =
    field(cell, "metadata").flatMap(field(_, key)).flatMap(_.strOpt)

  /** Cell tags, normalised to Jupyter Book's hyphenated spelling so nbconvert's `remove_output` style works too. */
  private def tags(cell: ujson.Value): Set[String] =
    field(cell, "metadata")
      .flatMap(field(_, "tags"))
      .flatMap(_.arrOpt)
      .map(_.flatMap(_.strOpt).map(_.replace('_', '-')).toSet)
      .getOrElse(Set.empty)

  private def escapeMarkdown(s: String): String = s.replaceAll("""([\\`*_\[\]<>])""", "\\\\$1")

  private def escapeHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private def urlEncode(s: String): String =
    new java.net.URI(null, null, s, null).toASCIIString

  /** Escaped `<pre>` block. Laika passes it through verbatim, whatever the text contains. */
  private def preBlock(classes: String, language: String, content: String): String =
    s"""<pre class="nb-output $classes"><code class="$language">${escapeHtml(content.stripSuffix("\n"))}</code></pre>"""

  /** Prepares terminal text for display: drops ANSI codes, applies `\r` overwrites, and truncates. */
  private def terminalText(s: String, opts: NotebookOptions): String =
    val lines = stripAnsi(s)
      .replace("\r\n", "\n")
      .stripSuffix("\n")
      .split("\n", -1)
      .map(l => l.substring(l.lastIndexOf('\r') + 1))
    val kept =
      if opts.maxOutputLines > 0 && lines.length > opts.maxOutputLines then
        lines.take(opts.maxOutputLines) :+ "… (truncated)"
      else lines
    kept.mkString("\n")
  end terminalText

  private def convertCell(cell: ujson.Value, idx: Int, ctx: Context): Seq[String] =
    val cellTags = tags(cell)
    if cellTags.contains("remove-cell") then Seq.empty
    else
      field(cell, "cell_type").flatMap(_.strOpt) match
        case Some("markdown") => Seq(markdownCell(cell, idx, ctx))
        case Some("raw")      => rawCell(cell).toSeq
        case Some("code")     => codeCell(cell, idx, cellTags, ctx)
        case _                => Seq.empty
    end if
  end convertCell

  private def markdownCell(cell: ujson.Value, idx: Int, ctx: Context): String =
    val source = text(field(cell, "source").getOrElse(ujson.Null))
    val attachments = objectOf(field(cell, "attachments"))
    attachments.foldLeft(source) { case (src, (name, bundle)) =>
      val data = objectOf(Some(bundle))
      val extracted = ImageTypes
        .collectFirst {
          case (mime, ext) if data.contains(mime) => (Base64.getMimeDecoder.decode(text(data(mime))), ext)
        }
        .orElse(data.get("image/svg+xml").map(svg => (text(svg).getBytes(StandardCharsets.UTF_8), "svg")))

      extracted match
        case Some((bytes, ext)) =>
          val safeName = name.replaceAll("[^A-Za-z0-9._-]", "_")
          val fileName = s"cell${idx}_${if safeName.contains('.') then safeName else s"$safeName.$ext"}"
          val path = ctx.addAsset(fileName, bytes)
          src.replace(s"attachment:$name", path).replace(s"attachment:${urlEncode(name)}", path)
        case None =>
          ctx.skipped ++= data.keys
          src
      end match
    }
  end markdownCell

  private def rawCell(cell: ujson.Value): Option[String] =
    val format = metadataString(cell, "format").orElse(metadataString(cell, "raw_mimetype"))
    val source = text(field(cell, "source").getOrElse(ujson.Null))
    format match
      case Some("text/markdown") => Some(source)
      case Some("text/html")     => Some(s"""<div class="nb-raw">\n$source\n</div>""")
      case _                     => None
    end match
  end rawCell

  private def codeCell(cell: ujson.Value, idx: Int, cellTags: Set[String], ctx: Context): Seq[String] =
    val source = text(field(cell, "source").getOrElse(ujson.Null))

    val input =
      if cellTags.contains("remove-input") || source.trim.isEmpty then Seq.empty
      else
        val prompt =
          if ctx.opts.showPrompts then
            val count = field(cell, "execution_count").flatMap(_.numOpt).map(_.toInt.toString).getOrElse(" ")
            Seq(s"""<div class="nb-prompt">In [$count]:</div>""")
          else Seq.empty
        prompt :+ (if cellTags.contains("hide-input") then hiddenInput(source, ctx) else fence(ctx.language, source))

    val outputs =
      if cellTags.contains("remove-output") then Seq.empty
      else
        val raw = field(cell, "outputs").flatMap(_.arrOpt).map(_.toSeq).getOrElse(Seq.empty)
        convertOutputs(raw, idx, ctx)

    input ++ outputs
  end codeCell

  /** A collapsed code input. Falls back to plain escaped HTML in the unlikely case the code would close the directive.
    */
  private def hiddenInput(source: String, ctx: Context): String =
    if source.linesIterator.exists(_.trim == "@:@") then
      s"""<details class="nb-hidden"><summary>Show code</summary><pre><code class="${ctx.language}">${escapeHtml(
          source.stripSuffix("\n")
        )}</code></pre></details>"""
    else s"@:nbDetails(Show code)\n${fence(ctx.language, source)}\n@:@"

  private def convertOutputs(outputs: Seq[ujson.Value], cellIdx: Int, ctx: Context): Seq[String] =
    val indexed = outputs.zipWithIndex.toList

    /** Consecutive stream outputs to the same stream are merged, as Jupyter displays them. */
    def loop(rest: List[(ujson.Value, Int)], acc: List[String]): List[String] = rest match
      case Nil => acc.reverse
      case (out, outIdx) :: tail =>
        field(out, "output_type").flatMap(_.strOpt) match
          case Some("stream") =>
            val name = field(out, "name").flatMap(_.strOpt).getOrElse("stdout")
            val (same, after) = tail.span((o, _) =>
              field(o, "output_type").flatMap(_.strOpt).contains("stream") &&
                field(o, "name").flatMap(_.strOpt).getOrElse("stdout") == name
            )
            val combined = (out +: same.map(_._1)).map(o => text(field(o, "text").getOrElse(ujson.Null))).mkString
            val cls = if name == "stderr" then "nb-stderr" else "nb-stdout"
            loop(after, preBlock(s"nb-stream $cls", "text", terminalText(combined, ctx.opts)) :: acc)

          case Some("error") =>
            loop(tail, errorOutput(out, ctx) :: acc)

          case Some("execute_result") | Some("display_data") =>
            loop(tail, displayOutput(out, cellIdx, outIdx, ctx).toList ::: acc)

          case _ => loop(tail, acc)
        end match
    end loop

    loop(indexed, Nil)
  end convertOutputs

  private def errorOutput(out: ujson.Value, ctx: Context): String =
    def str(key: String) = field(out, key).flatMap(_.strOpt).getOrElse("")
    val header = Seq(str("ename"), str("evalue")).filter(_.nonEmpty).mkString(": ")
    val traceback = stripAnsi(
      field(out, "traceback").flatMap(_.arrOpt).map(_.flatMap(_.strOpt).mkString("\n")).getOrElse("")
    )
    // IPython repeats `ename: evalue` at the end of the traceback, so only add it when it's missing.
    val full =
      if traceback.trim.isEmpty then header
      else if header.isEmpty || traceback.contains(header) then traceback
      else s"$header\n$traceback"
    preBlock("nb-error", "text", terminalText(full, ctx.opts))
  end errorOutput

  private def displayOutput(out: ujson.Value, cellIdx: Int, outIdx: Int, ctx: Context): Option[String] =
    val data = objectOf(field(out, "data"))
    ctx.skipped ++= data.keys.filterNot(RenderableTypes.contains)

    def image(ext: String, bytes: Array[Byte]): String =
      val path = ctx.addAsset(s"cell${cellIdx}_out$outIdx.$ext", bytes)
      s"@:style(nb-output nb-image)\n![]($path)\n@:@"
    end image

    RenderableTypes.find(data.contains).map { mime =>
      val value = data(mime)
      mime match
        case "text/html"     => s"""<div class="nb-output nb-html">\n${text(value).stripSuffix("\n")}\n</div>"""
        case "image/svg+xml" => image("svg", text(value).getBytes(StandardCharsets.UTF_8))
        case "text/markdown" => text(value)
        case "text/latex"    => preBlock("nb-latex", "latex", text(value))
        case "text/plain"    => preBlock("nb-result", "text", terminalText(text(value), ctx.opts))
        case _ =>
          val ext = ImageTypes.collectFirst { case (`mime`, e) => e }.getOrElse("bin")
          image(ext, Base64.getMimeDecoder.decode(text(value)))
      end match
    }
  end displayOutput

end NotebookConverter
