/** A Mill module trait that provides integration with the Laika documentation generator.
  *
  * This module enables building static documentation sites from Markdown sources, with optional API documentation
  * integration via Scaladoc. It uses the Helium theme for styling and supports live reload functionality during
  * development.
  *
  * ==Features==
  *   - Markdown to HTML transformation using Laika
  *   - GitHub Flavored Markdown support
  *   - Syntax highlighting for code blocks
  *   - Configurable Helium theme with top navigation bar
  *   - Optional API documentation integration via [[UnidocModule]]
  *   - Server-Sent Events (SSE) based live reload support
  *
  * ==Usage==
  * Mix this trait into your Mill build module and configure the required settings:
  *
  * {{{
  * object docs extends LaikaModule {
  *   override def repoUrl = Task("https://github.com/your/repo")
  *   override def latestVersion = Task("1.0.0")
  *   val unidocs = new UnidocModule { ... }
  * }
  * }}}
  *
  * ==Directory Structure==
  * By default, documentation sources are expected in a `docs` subdirectory relative to the module directory. The
  * generated site will include:
  *   - Transformed Markdown files as HTML
  *   - API documentation under `/api/` (if enabled)
  *
  * @see
  *   [[laika.helium.Helium]] for theme configuration options
  * @see
  *   [[UnidocModule]] for API documentation generation
  */
package io.github.quafadas.millSite

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import laika.api.*
import laika.ast.LengthUnit
import laika.ast.Path.Root
import laika.config.LaikaKeys
import laika.config.SyntaxHighlighting
import laika.format.*
import laika.helium.Helium
import laika.helium.config.*
import laika.io.model.RenderedTreeRoot
import laika.io.syntax.*
import mill.api.*
import mill.api.BuildCtx
import mill.api.Task.Simple
import mill.scalalib.*

trait LaikaModule extends Module:

  /** Sequence of Mill modules whose sources should be included in API documentation.
    *
    * Override this to include modules in the generated Scaladoc. If empty, API documentation generation is skipped.
    *
    * @return
    *   Seq of JavaModule instances to document
    */
  def laikaUnidocDeps: Seq[JavaModule] = Seq.empty

  /** The UnidocModule instance responsible for generating API documentation.
    *
    * This must be defined in your build to enable API documentation generation.
    */
  val unidocs: UnidocModule

  /** Whether to include API documentation in the generated site.
    *
    * Returns `true` if [[laikaUnidocDeps]] is non-empty, meaning there are modules to document. The API docs will be
    * placed under `/api/` in the site.
    *
    * @return
    *   true if API documentation should be generated and included
    */
  def includeApi: Simple[Boolean] = Task(laikaUnidocDeps.nonEmpty)

  /** The source directory containing Markdown documentation files.
    *
    * Defaults to a `docs` subdirectory relative to the module directory. Files in this directory (and subdirectories)
    * will be processed by Laika.
    *
    * @return
    *   PathRef to the documentation source directory
    */
  def inputDir: Simple[PathRef] = Task.Source(super.moduleDir / "docs")

  /** The base URL for the generated site.
    *
    * Used by Laika for generating absolute URLs. Defaults to the unidoc source URL.
    *
    * @return
    *   the base URL string for the site
    */
  def baseUrl: Simple[String] = unidocs.unidocSourceUrl().getOrElse("!!!no path!!!")

  /** The URL of the project's source repository.
    *
    * Used to generate the GitHub icon link in the navigation bar. Override this with your actual repository URL.
    *
    * @return
    *   the repository URL string
    */
  def repoUrl: Simple[String] = Task("https://github.com/example/repo")

  /** The latest version of the documented library.
    *
    * This value is made available in Laika templates as `version.latest`. Override this to display the current version
    * in your documentation.
    *
    * @return
    *   the version string
    */
  def latestVersion: Simple[String] = Task("0.0.0")

  /** Configuration values to inject into Laika templates.
    *
    * These key-value pairs are available in Laika's substitution references. By default includes:
    *   - `version.latest` - the library version
    *   - `laika.siteBaseURL` - the base URL for the site
    *
    * @return
    *   sequence of (key, value) pairs for template substitution
    */
  def configValues: Simple[Seq[(String, String)]] = Task {
    Seq(
      "version.latest" -> latestVersion(),
      LaikaKeys.siteBaseURL.toString() -> baseUrl()
    )
  }

  /** Worker task that builds the Helium theme configuration.
    *
    * Configures the Laika Helium theme with:
    *   - Top navigation bar with home, API docs (if enabled), and GitHub links
    *   - Content layout with 85vw content width and 15vw navigation
    *   - Inline JavaScript for Server-Sent Events live reload support
    *
    * Override this to customize the theme appearance.
    *
    * @return
    *   the configured Helium theme builder
    */
  def helium = Task.Worker {
    val repoLink =
      IconLink.external(repoUrl(), HeliumIcon.github)
    val apiLink = if includeApi() then Seq(IconLink.internal(Root / "api/index.html", HeliumIcon.api)) else Seq.empty

    Helium.defaults.site
      .topNavigationBar(
        homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home),
        navLinks = apiLink :+ repoLink
      )
      .site
      .layout(
        contentWidth = LengthUnit.vw(85),
        navigationWidth = LengthUnit.vw(15)
      )
      .site
      .inlineJS(
        """const sse = new EventSource("/refresh/v1/sse");
sse.addEventListener("message", (e) => {
const msg = JSON.parse(e.data);

if ("KeepAlive" in msg) console.log("KeepAlive");

if ("PageRefresh" in msg) location.reload();
});
"""
      )

  }

  /** Stages the documentation site by copying sources and API docs to a build directory.
    *
    * This task:
    *   1. Copies the contents of [[inputDir]] to the destination
    *   2. If [[includeApi]] is true, copies the generated API documentation to `/api/`
    *
    * The staged site is ready for Laika transformation.
    *
    * @return
    *   PathRef to the staged site directory
    */
  def stageSite = Task {
    BuildCtx.withFilesystemCheckerDisabled {
      os.copy(inputDir().path, Task.dest, mergeFolders = true)
      if includeApi() then
        val apiSite = unidocs.unidocSite()
        os.copy(apiSite.path, Task.dest / "api", mergeFolders = true)
      else ()
      end if
      PathRef(Task.dest)
    }
  }

  /** Generates the final HTML site from Markdown sources using Laika.
    *
    * This is the main entry point for site generation. It:
    *   1. Builds the Helium theme configuration
    *   2. Creates a Laika transformer with GitHub Flavored Markdown and syntax highlighting
    *   3. Injects configuration values for template substitution
    *   4. Transforms all Markdown files from [[stageSite]] to HTML
    *
    * The transformation runs using Cats Effect IO for parallel processing.
    *
    * @return
    *   PathRef to the generated site directory containing HTML files
    */
  def generateSite =
    Task {
      BuildCtx.withFilesystemCheckerDisabled {

        val heliumB = helium().build

        val transformer = Transformer
          .from(Markdown)
          .to(HTML)

        val transformerWithValues =
          configValues()
            .foldLeft(transformer)((t, kv) => t.withConfigValue(kv._1, kv._2))

        val built = transformerWithValues
          .using(Markdown.GitHubFlavor, SyntaxHighlighting)
          .withRawContent
          .parallel[IO]
          .withTheme(heliumB)
          .build

        val res: IO[RenderedTreeRoot[IO]] = built.use { t =>
          t.fromDirectory(stageSite().path.toString())
            .toDirectory(Task.dest.toString())
            .transform
        }
        res.unsafeRunSync()

        // if(includeApi()) {
        //   val apiSite = unidocs.unidocSite()
        //   os.copy(apiSite.path, Task.dest / "api", mergeFolders = true)
        // } else {
        //   ()
        // }

        PathRef(Task.dest)
      }
    }
end LaikaModule
