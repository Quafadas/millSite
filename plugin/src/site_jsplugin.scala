package io.github.quafadas.millSite

import mill._
import mill.scalalib._
import mill.scalajslib._

import mill.api.Result
import mill.util.Jvm.createJar
import mill.api.PathRef

import coursier.maven.MavenRepository
import scala.util.Try
import mill.scalalib.publish.PomSettings
import mill.scalalib.publish.License
import mill.scalalib.publish.VersionControl
import os.SubPath
import ClasspathHelp._
import mill.scalajslib.api.ESFeatures
import mill.scalajslib.api.ESVersion
import mill.scalajslib.api.ModuleKind
import mill.api.Task.Simple
import mill.scalajslib.api.ESModuleImportMapping
import upickle.default.*

case class EsmMap(imports: Map[String, String]) derives ReadWriter


val repos = "https://packages.schroders.com/artifactory/maven"

trait SiteJSModule extends ScalaJSModule {

  override def repositories: T[Seq[String]] = Task{
    super.repositories() ++
    Seq(
    )
  }

  def mdocVersion: Task[String] = Task { Versions.mdocVersion }
  def domVersion: Task[String] = Task { Versions.domVersion }
  // def scalaJsCompilerVersion = "2.13.14"


  def mdocJsImportMap = Task {
    val defined = scalaJSImportMap()
    val out = EsmMap(defined.collect { case ESModuleImportMapping.Prefix(prefix, replacement) => (prefix, replacement) }.toMap)
    val dest = Task.dest / "mdoc_js_import_map.json"
    os.write(dest, upickle.default.write(out))
    PathRef(dest)
  }

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

  // /** Replace waskith JSToolsClasspath???
  //   *
  //   * @return
  //   */
  def jsclasspath = Task {
    toArgument(runClasspath().map(_.path))
  }

  def jsLinkerClassPath = Task( linkerLibs().map(_.path))

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

  override def moduleKind: Simple[ModuleKind] = ModuleKind.ESModule

  protected def linkerDependency = Task {
    val sjs = scalaJSVersion()
    artifactScalaVersion() match {
      case "3" =>
        Seq(
          mvn"org.scala-js:scalajs-linker_2.13:$sjs",
          mvn"org.scalameta:mdoc-js-worker_3:${mdocVersion()}"
        )
      case other => ???
    }
  }

  def linkerLibs = Task {
    defaultResolver().classpath(linkerDependency())
  }

  def mdocJSDependency = Task {
    val mdocV = mdocVersion()
    val dep = artifactScalaVersion() match {
      case "3"   => Seq(mvn"org.scalameta:mdoc-js-worker_3:$mdocV")
      case other => Seq(mvn"org.scalameta:mdoc-js-worker_$other:$mdocV")
    }
  }

  override def scalacPluginMvnDeps = super.scalacPluginMvnDeps() ++ Seq(
    mvn"org.scala-js:scalajs-compiler_2.13.14:${scalaJSVersion()}"
  )

  def mdocDep = Task {
    artifactScalaVersion() match {
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
      case other => ???
    }

  }

  def mdocDepBound = Task {
    mdocDep().map(Lib.depToBoundDep(_, scalaVersion()))
  }

  def mDocLibs = Task { Lib.resolveDependencies(repositories().map(MavenRepository(_)), mdocDepBound(), false) }
}
