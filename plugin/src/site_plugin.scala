package io.github.quafadas.millSite

import mill.*
import mill.scalalib.*
import os.Path
import mill.api.Task.Simple
import fs2.concurrent.Topic
import cats.effect.IO
// import mill.scalajslib.*
// import coursier.maven.MavenRepository
// import mill.api.Result
// import mill.util.Jvm.createJar
// import mill.define.PathRef
// import mill.scalalib.api.CompilationResult
// // import de.tobiasroeser.mill.vcs.version.VcsVersion
// import scala.util.Try
// import mill.scalalib.publish.PomSettings
// import mill.scalalib.publish.License
// import mill.scalalib.publish.VersionControl
// import os.SubPath
// import ClasspathHelp.*
import cats.effect.unsafe.implicits.global
import io.github.quafadas.sjsls.LiveServerConfig
import cats.effect.ExitCode
import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import mill.api.BuildCtx
import mill.util.VcsVersion
import mill.scalajslib.api.ESModuleImportMapping

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

/** The main Mill module trait for generating documentation sites.
  *
  * `SiteModule` is the primary entry point for building documentation sites.
  * It orchestrates [[MdocModule]], [[LaikaModule]], and [[UnidocModule]] to produce
  * a complete static site with:
  *  - Typesafe Scala code examples (via Mdoc)
  *  - Markdown to HTML transformation (via Laika)
  *  - API documentation (via Unidoc/Scaladoc)
  *  - Live-reload development server
  *
  * ==Features==
  *  - Single entry point combining all documentation tools
  *  - Built-in live-reload server for rapid documentation iteration
  *  - Site variable substitution for version numbers and links
  *  - Optional Scala.js support for interactive examples
  *  - Configurable Helium theme with navigation
  *
  * ==Usage==
  * Mix this trait into your Mill build:
  *
  * {{{n
  * object site extends SiteModule {
  *   override def scalaVersion = "3.3.1"
  *   override def unidocDeps = Seq(myLibrary)
  *   override def repoLink = Task("https://github.com/you/repo")
  * }
  * }}}
  *
  * ==Commands==
  *  - `mill site.siteGen` - Generate the complete site
  *  - `mill site.serve` - Start live-reload development server
  *
  * @see [[MdocModule]] for Mdoc configuration
  * @see [[LaikaModule]] for Laika/theme configuration
  * @see [[UnidocModule]] for API documentation configuration
  */
trait SiteModule extends Module:

  /** Optional Scala.js module for interactive code examples.
    *
    * Override with a [[SiteJSModule]] instance to enable Scala.js
    * code blocks in documentation. When `None`, JS code blocks are disabled.
    */
  lazy val jsSiteModule = Option.empty[SiteJSModule]

  /** Topic for publishing live-reload update notifications.
    *
    * Used internally by the development server to trigger browser
    * refresh via Server-Sent Events when documentation changes.
    */
  lazy val updateServer = Topic[IO, Unit].unsafeRunSync()

  /** Default directory for documentation source files.
    *
    * Points to `docs/` under the module directory. This is used
    * as the input for Mdoc processing.
    */
  lazy val defaultInternalDocDir = super.moduleDir / "docs"

  /** Modules to include in API documentation generation.
    *
    * List the modules whose sources should be documented with Scaladoc.
    * If empty, API documentation generation is skipped.
    *
    * @return sequence of modules to document
    */
  def unidocDeps: Seq[JavaModule] = Seq.empty

  /** Title for the generated API documentation.
    *
    * Displayed in the Scaladoc header and navigation.
    *
    * @return the API documentation title
    */
  def unidocTitle = Task("Unidoc Title Here")

  /** URL to the project's source repository.
    *
    * Used for the GitHub icon link in the site navigation bar.
    * Override with your actual repository URL.
    *
    * @return the repository URL string
    */
  def repoLink = Task("<repo-link>")

  /** The latest release version of your library.
    *
    * Defaults to the most recent Git tag (without the 'v' prefix).
    * Used in site templates and available as the `VERSION` site variable.
    *
    * @return the version string
    */
  def latestVersion: Simple[String] = Task {
    VcsVersion.vcsState().lastTag.getOrElse("0.0.0").replace("v", "")
  }

  /** Resource directories to include in the generated site.
    *
    * Files in these directories are copied to the site output.
    * Defaults to a `resources/` subdirectory under the module.
    *
    * @return sequence of resource directory sources
    */
  def resources = Task.Sources(super.moduleDir / "resources")

  /** JVM arguments passed to forked processes.
    *
    * Override to add memory settings, system properties, or other JVM flags
    * for Mdoc or other forked tool execution.
    *
    * @return sequence of JVM arguments
    */
  def forkArgs: Simple[Seq[String]] = Task(Seq.empty[String])

  /** Optional path to an ES module import map file.
    *
    * Used for Scala.js import resolution when using external JS dependencies.
    *
    * @return optional PathRef to the import map JSON file
    */
  def pathToImportMap: T[Option[PathRef]] = None

  /** The Scala version for documentation compilation.
    *
    * This must be overridden - there is no default. The version should match
    * the Scala version used by the library being documented.
    *
    * @return the Scala version string (e.g., "3.3.1")
    */
  def scalaVersion: Simple[String] = Task(???) // force the user to set this themselves.

  /** Site variables for Mdoc substitution.
    *
    * Key-value pairs available in Markdown files using `@@KEY@@` syntax.
    * By default includes `VERSION` set to [[latestVersion]].
    *
    * @return sequence of (key, value) pairs
    */
  def mdocSiteVariables: Simple[Seq[(String, String)]] = Task(Seq("VERSION" -> latestVersion()))

  /** ES module import mappings for Scala.js.
    *
    * Configures how ES module imports are resolved when using Scala.js
    * code blocks. Useful for mapping Scala.js facade imports to CDN URLs.
    *
    * @return sequence of import mappings
    */
  def scalaJSImportMap: Simple[Seq[ESModuleImportMapping]] = Task {
    Seq.empty[ESModuleImportMapping]
  }

  /** The Mdoc module for processing `.mdoc.md` files.
    *
    * Pre-configured [[MdocModule]] that inherits settings from this `SiteModule`:
    *  - Uses [[scalaVersion]] and [[scalaJSImportMap]]
    *  - Reads from [[defaultInternalDocDir]]
    *  - Applies [[mdocSiteVariables]] for substitution
    *  - Includes [[unidocDeps]] as module dependencies
    *  - Adds [[resources]] to compilation resources
    */
  val mdocModule: MdocModule = new MdocModule:

    override val jsSiteModule = SiteModule.this.jsSiteModule.getOrElse(
      new SiteJSModule:
        override def scalaVersion: Simple[String] = SiteModule.this.scalaVersion
        override def scalaJSVersion: Simple[String] = Task(Versions.scalaJsVersion)
        override def scalaJSImportMap: Simple[Seq[ESModuleImportMapping]] = SiteModule.this.scalaJSImportMap()
    )
    override def scalaVersion: Simple[String] = SiteModule.this.scalaVersion
    override def mdocDir = defaultInternalDocDir

    override def siteVariables: Simple[Seq[(String, String)]] = mdocSiteVariables()

    override def forkArgs: Simple[Seq[String]] = Task(super.forkArgs() ++ SiteModule.this.forkArgs())
    override def docDir: Simple[PathRef] = Task.Source(mdocDir)

    override def moduleDeps: Seq[JavaModule] = unidocDeps

    override def compileResources = Task(super.compileResources() ++ SiteModule.this.resources())
    override def resources = Task(super.resources() ++ SiteModule.this.resources())

  /** The Laika module for Markdown to HTML transformation.
    *
    * Pre-configured [[LaikaModule]] that:
    *  - Takes input from [[mdocModule.mdoc2]] (processed Mdoc output)
    *  - Includes API docs from [[unidocDeps]] via nested [[UnidocModule]]
    *  - Uses [[repoLink]] for GitHub navigation icon
    *  - Applies [[latestVersion]] for template substitution
    */
  val laika = new LaikaModule:
    override val unidocs = new UnidocModule:
      override def scalaVersion: Simple[String] = SiteModule.this.scalaVersion
      override def moduleDeps: Seq[JavaModule] = laikaUnidocDeps
      override def unidocDocumentTitle = unidocTitle()

      // Default to temporary directories to avoid triggering full scaladoc/unidoc
      // unless a user has explicitly provided modules to document via `moduleDeps`.
      override def unidocLocal: Simple[PathRef] = Task {
        if moduleDeps.nonEmpty then super.unidocLocal() else PathRef(os.temp.dir())
      }

      override def unidocSite: Simple[PathRef] = Task {
        if moduleDeps.nonEmpty then super.unidocSite() else PathRef(os.temp.dir())
      }

    override def inputDir: Simple[PathRef] = mdocModule.mdoc2()
    override def laikaUnidocDeps: Seq[JavaModule] = unidocDeps

    override def repoUrl: Simple[String] = repoLink()

    override def latestVersion: Simple[String] = SiteModule.this.latestVersion()

  /** Generates the complete documentation site.
    *
    * This is the main task for site generation. It:
    *  1. Processes `.mdoc.md` files through [[mdocModule]]
    *  2. Transforms Markdown to HTML via [[laika]]
    *  3. Includes API documentation (if [[unidocDeps]] is non-empty)
    *  4. Notifies the live-reload server of changes
    *
    * @return PathRef to the generated site directory
    */
  def siteGen = Task {
    val mdocs = mdocModule.mdoc2()
    val site = laika.generateSite()
    updateServer.publish1(println("publishing update")).unsafeRunSync()
    site
  }

  /** Returns the path to the generated site as a string.
    *
    * Convenience task that extracts the path from [[siteGen]]
    * for use in server configuration.
    *
    * @return absolute path string to the site directory
    */
  def sitePathOnly = Task {
    siteGen().path.toString
  }

  /** The port number for the live-reload development server.
    *
    * Defaults to 8080. Override to use a different port.
    *
    * @return the server port number
    */
  def port = Task {
    8080
  }

  /** Whether to automatically open a browser when starting the server.
    *
    * Defaults to `true`. Set to `false` to disable auto-open.
    *
    * @return true to open browser, false otherwise
    */
  def openBrowser = Task {
    true
  }

  /** Log level for the development server.
    *
    * Controls verbosity of server output. Defaults to "debug".
    *
    * @return the log level string
    */
  def logLevel = Task {
    "debug"
  }

  // def assets = Task {
  //   println("site assets")
  //   os.copy.over(laika.assets().path, Task.dest)
  //   PathRef(Task.dest)
  // }

  /** Creates the live server configuration.
    *
    * Configures [[io.github.quafadas.sjsls.LiveServerConfig]] with:
    *  - Port from [[port]]
    *  - Site directory from [[sitePathOnly]]
    *  - Browser auto-open controlled by [[openBrowser]]
    *  - Live-reload via [[updateServer]] topic
    *
    * @return configured LiveServerConfig for the development server
    */
  def lcs = Task.Anon {
    val port_ = port()
    val sitePathOnly_ = sitePathOnly()
    BuildCtx.withFilesystemCheckerDisabled {
      LiveServerConfig(
        baseDir = None, // typically this would be a build tool here
        // outDir = Some(assets().path.toString()),
        port =
          com.comcast.ip4s.Port.fromInt(port_).getOrElse(throw new IllegalArgumentException(s"invalid port: ${port_}")),
        indexHtmlTemplate = Some(sitePathOnly_),
        buildTool = io.github.quafadas.sjsls.NoBuildTool(),
        openBrowserAt = "/index.html",
        preventBrowserOpen = !openBrowser(),
        customRefresh = Some(updateServer)
      )
    }
  }

  /** Starts the live-reload development server.
    *
    * Launches an HTTP server that:
    *  - Serves the generated site from [[siteGen]]
    *  - Watches for changes and triggers browser refresh
    *  - Opens a browser automatically (unless [[openBrowser]] is false)
    *
    * The server runs as a Mill worker task and stays alive until stopped.
    * Access the site at `http://localhost:<port>/index.html`.
    *
    * @return the RefreshServer worker instance
    */
  def serve = Task.Worker {
    // Let's kill off anything that is a zombie on the port we want to use
    val p = port()
    BuildCtx.withFilesystemCheckerDisabled {
      new RefreshServer(lcs())
    }
  }

  /** Wrapper class for the live-reload HTTP server.
    *
    * Manages the lifecycle of the sjsls LiveServer, providing
    * automatic cleanup via AutoCloseable.
    *
    * @param lcs the server configuration
    */
  class RefreshServer(lcs: LiveServerConfig) extends AutoCloseable:
    val server = io.github.quafadas.sjsls.LiveServer.main(lcs).allocated

    server.map(_._1).unsafeRunSync()

    override def close(): Unit =
      // This is the shutdown hook for http4s
      println("Shutting down server...")
      server.map(_._2).flatten.unsafeRunSync()
    end close
  end RefreshServer

end SiteModule
