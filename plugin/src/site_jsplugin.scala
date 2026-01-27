package io.github.quafadas.millSite

import coursier.maven.MavenRepository
import io.github.quafadas.millSite.ClasspathHelp.*
import mill.*
import mill.api.PathRef
import mill.api.Result
import mill.api.Task.Simple
import mill.scalajslib.*
import mill.scalajslib.api.ESModuleImportMapping
import mill.scalajslib.api.ModuleKind
import mill.scalalib.*
import upickle.default.*

/** JSON representation of ESM import mappings for Mdoc JS processing.
  *
  * @param imports
  *   Map from import prefix to replacement URL/path
  */
case class EsmMap(imports: Map[String, String]) derives ReadWriter

/** A Mill module trait providing Scala.js integration for Mdoc documentation.
  *
  * This module enables interactive Scala.js code examples in documentation. When mixed into your build, it configures
  * Mdoc to compile and link Scala.js code blocks, producing JavaScript that runs in the browser.
  *
  * ==Features==
  *   - Compiles Scala.js code blocks in `.mdoc.md` files
  *   - ES Module output for modern browser compatibility
  *   - Import map support for external JavaScript dependencies
  *   - Automatic scalajs-dom dependency inclusion
  *
  * ==Usage==
  * Override `jsSiteModule` in your [[MdocModule]] to enable Scala.js support:
  *
  * {{{n object docs extends MdocModule { override val jsSiteModule = new SiteJSModule { override def scalaVersion =
  * "3.3.1" override def scalaJSVersion = "1.16.0" override def moduleDeps = Seq(myJsLibrary) } } }}}
  *
  * @see
  *   [[MdocModule]] for the parent documentation module
  * @see
  *   [[https://scalameta.org/mdoc/docs/js.html Mdoc Scala.js documentation]]
  */
trait SiteJSModule extends ScalaJSModule:

  /** The version of Mdoc to use for Scala.js documentation processing.
    *
    * Defaults to [[Versions.mdocVersion]]. Override to use a different version.
    *
    * @return
    *   the Mdoc version string
    */
  def mdocVersion: Task[String] = Task(Versions.mdocVersion)

  /** The version of scalajs-dom library to include.
    *
    * scalajs-dom provides typed facades for browser DOM APIs. Defaults to [[Versions.domVersion]].
    *
    * @return
    *   the scalajs-dom version string
    */
  def domVersion: Task[String] = Task(Versions.domVersion)
  // def scalaJsCompilerVersion = "2.13.14"

  /** Generates a JSON import map file for Mdoc Scala.js processing.
    *
    * Converts the [[scalaJSImportMap]] configuration into a JSON file that Mdoc uses to resolve ES module imports. This
    * enables using external JavaScript libraries via CDN or custom paths.
    *
    * @return
    *   PathRef to the generated `mdoc_js_import_map.json` file
    */
  def mdocJsImportMap = Task {
    val defined = scalaJSImportMap()
    val out = EsmMap(defined.collect { case ESModuleImportMapping.Prefix(prefix, replacement) =>
      (prefix, replacement)
    }.toMap)
    val dest = Task.dest / "mdoc_js_import_map.json"
    os.write(dest, upickle.default.write(out))
    PathRef(dest)
  }

  /** Maven dependencies for Scala.js documentation code.
    *
    * Automatically includes scalajs-dom for browser API access. Override to add additional Scala.js libraries available
    * in documentation code examples.
    *
    * @return
    *   sequence of Maven dependencies
    */
  override def mvnDeps = Task {
    super.mvnDeps() ++ Seq(
      mvn"org.scala-js::scalajs-dom::${domVersion()}"
      // mvn"org.scala-js:scalajs-library_2.13:${scalaJSVersion()}" shoudl be covered by mandatory ivyDeps
    ) ++ super.mandatoryMvnDeps()
  }

  // // /** Does this do anything?
  // //   */

  // // override def esFeatures: T[ESFeatures] =
  // //   ESFeatures.Defaults.copy(esVersion = ESVersion.ES2021)

  /** The classpath for Scala.js compilation as a path-separator-joined string.
    *
    * Used by Mdoc to locate Scala.js libraries when compiling JS code blocks.
    *
    * @return
    *   classpath string suitable for command-line arguments
    */
  def jsclasspath = Task {
    toArgument(runClasspath().map(_.path))
  }

  /** The classpath for the Scala.js linker.
    *
    * Contains the scalajs-linker and mdoc-js-worker JARs needed to link compiled Scala.js code into JavaScript.
    *
    * @return
    *   sequence of paths to linker libraries
    */
  def jsLinkerClassPath = Task(linkerLibs().map(_.path))

  /** Generates the `mdoc.properties` file for Scala.js configuration.
    *
    * Creates a properties file containing all Scala.js-related settings that Mdoc needs to compile and link JS code
    * blocks:
    *   - `js-scalac-options` - Scala compiler options including `-scalajs`
    *   - `js-linker-classpath` - Path to Scala.js linker libraries
    *   - `js-classpath` - Classpath for JS compilation
    *   - `js-module-kind` - Module format (ESModule by default)
    *   - `import-map-path` - Path to the ESM import map JSON
    *
    * @return
    *   PathRef to the directory containing `mdoc.properties`
    */
  def mdocJsProperties = Task {
    val mdocPropsFile = Task.dest / "mdoc.properties"

    val paths = linkerLibs()

    val mdocProps: Map[String, String] = Map(
      "js-scalac-options" -> (List("-scalajs") ++ scalacOptions()).mkString(" "),
      "js-linker-classpath" -> toArgument(paths.map(_.path)),
      "js-classpath" -> toArgument(runClasspath().map(_.path)),
      "js-module-kind" -> moduleKind().toString(),
      "import-map-path" -> mdocJsImportMap().path.toIO.getAbsolutePath
      // "js-out-prefix" -> "_assets/js"
    )
    os.write(
      mdocPropsFile,
      mdocProps.map { case (k, v) => s"$k=$v" }.mkString("\n")
    )
    PathRef(Task.dest)
  }

  /** The JavaScript module format for output.
    *
    * Defaults to ES Modules for modern browser compatibility. ES Modules support `import`/`export` syntax and work with
    * import maps.
    *
    * @return
    *   the module kind (ESModule by default)
    */
  override def moduleKind: Simple[ModuleKind] = ModuleKind.ESModule

  /** Maven dependencies for the Scala.js linker.
    *
    * Resolves the scalajs-linker and mdoc-js-worker for the configured Scala.js version. Currently only supports Scala
    * 3.
    *
    * @return
    *   sequence of linker dependencies
    */
  protected def linkerDependency = Task {
    val sjs = scalaJSVersion()
    artifactScalaVersion() match
      case "3" =>
        Seq(
          mvn"org.scala-js:scalajs-linker_2.13:$sjs",
          mvn"org.scalameta:mdoc-js-worker_3:${mdocVersion()}"
        )
      case _ => ???
    end match
  }

  /** Resolved classpath for Scala.js linker dependencies.
    *
    * @return
    *   resolved linker library JARs
    */
  def linkerLibs = Task {
    defaultResolver().classpath(linkerDependency())
  }

  /** Maven dependency for the Mdoc Scala.js worker.
    *
    * The worker handles JavaScript code block compilation within Mdoc.
    *
    * @return
    *   sequence containing the mdoc-js-worker dependency
    */
  def mdocJSDependency = Task {
    val mdocV = mdocVersion()
    artifactScalaVersion() match
      case "3"   => Seq(mvn"org.scalameta:mdoc-js-worker_3:$mdocV")
      case _ => ???
  }

  /** Scala compiler plugins required for Scala.js compilation.
    *
    * Adds the scalajs-compiler plugin to enable Scala.js output.
    *
    * @return
    *   sequence of compiler plugin dependencies
    */
  override def scalacPluginMvnDeps = super.scalacPluginMvnDeps() ++ Seq(
    mvn"org.scala-js:scalajs-compiler_2.13.18:${scalaJSVersion()}"
  )

  /** Maven dependencies for Mdoc with Scala.js support.
    *
    * Includes mdoc, mdoc-js, and the Scala 3 compiler/library. Excludes bundled Scala compiler to use the version from
    * [[scalaVersion]].
    *
    * @return
    *   sequence of Mdoc dependencies
    */
  def mdocDep = Task {
    artifactScalaVersion() match
      case "3" =>
        Seq(
          mvn"org.scalameta::mdoc-js:${mdocVersion()}",
          mvn"org.scalameta::mdoc:${mdocVersion()}"
            .exclude("org.scala-lang" -> "scala3-compiler_3")
            .exclude("org.scala-lang" -> "scala3-library_3"),
          mvn"org.scala-lang::scala3-compiler:${scalaVersion()}",
          mvn"org.scala-lang:scala3-library:${scalaVersion()}",
          mvn"org.scala-lang::tasty-core:${scalaVersion()}",
          mvn"org.scala-lang.modules::scala-xml:2.1.0"
        )
      case _ => ???

  }

  /** Mdoc dependencies bound to the current Scala version.
    *
    * Converts [[mdocDep]] to bound dependencies for resolution.
    *
    * @return
    *   sequence of bound Mdoc dependencies
    */
  def mdocDepBound = Task {
    mdocDep().map(Lib.depToBoundDep(_, scalaVersion()))
  }

  /** Resolved classpath for Mdoc libraries.
    *
    * Resolves all Mdoc dependencies including the Scala.js worker.
    *
    * @return
    *   resolved Mdoc library JARs
    */
  def mDocLibs = Task(Lib.resolveDependencies(repositories().map(MavenRepository(_)), mdocDepBound(), false))
end SiteJSModule
