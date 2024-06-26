package io.github.quafadas.millSite

import mill._
import mill.scalalib._
import mill.scalajslib._

import mill.api.Result
import mill.util.Jvm.createJar
import mill.api.PathRef
import mill.scalalib.api.CompilationResult
import de.tobiasroeser.mill.vcs.version.VcsVersion
import scala.util.Try
import mill.scalalib.publish.PomSettings
import mill.scalalib.publish.License
import mill.scalalib.publish.VersionControl
import os.SubPath
import ClasspathHelp._
import mill.scalajslib.api.ESFeatures
import mill.scalajslib.api.ESVersion

trait SiteJSModule extends ScalaJSModule {

  def mdocVersion: Target[String] = T { "2.5.2" }
  def domVersion: Target[String] = T { "2.8.0" }
  def scalaJsCompilerVersion = "2.13.12"

  override def ivyDeps = T {
    super.ivyDeps() ++ Agg(
      ivy"org.scala-js::scalajs-dom::${domVersion()}"
      // ivy"org.scala-js:scalajs-library_2.13:${scalaJSVersion()}" shoudl be covered by mandatory ivyDeps
    ) ++ super.mandatoryIvyDeps()
  }

  /** Does this do anything?
    */

  override def esFeatures: T[ESFeatures] =
    ESFeatures.Defaults.copy(esVersion = ESVersion.ES2021)

  /** Replace with JSToolsClasspath???
    *
    * @return
    */
  val jsclasspath = T {
    toArgument(runClasspath().map(_.path))
  }

  val jsLinkerClassPath = T {
    toArgument(linkerLibs().map(_.path))
  }

  def mdocJsProperties: T[PathRef] = T {
    val mdocPropsFile = T.dest / "mdoc.properties"

    val jsScalacOptions: String = artifactScalaVersion() match {
      case "3" => "-scalajs"
      case _ =>
        scalaJsCompilerResolved().map(_.path).map(p => s"-Xplugin:$p").head
    }

    val mdocProps: Map[String, String] = Map(
      "js-scalac-options" -> jsScalacOptions,
      "js-linker-classpath" -> toArgument(linkerLibs().map(_.path)),
      "js-classpath" -> toArgument(runClasspath().map(_.path)),
      "js-module-kind" -> "NoModule",
      "js-out-prefix" -> "_assets/js"
    )
    os.write(
      mdocPropsFile,
      mdocProps.map { case (k, v) => s"$k=$v" }.mkString("\n")
    )
    PathRef(T.dest)
  }

  protected def linkerDependency = T.task {
    val sjs = scalaJSVersion()
    artifactScalaVersion() match {
      case "3" =>
        Agg(
          ivy"org.scala-js:scalajs-linker_2.13:$sjs",
          ivy"org.scalameta:mdoc-js-worker_3:${mdocVersion()}"
        )
      case other =>
        Agg(
          ivy"org.scala-js:scalajs-linker_2.13:$sjs",
          ivy"org.scalameta:mdoc-js-worker_2.13:${mdocVersion()}"
        )
    }
  }

  /** Follows mdocs documentation, i.e. intransitive
    */

  def scala2JsCompilerIntransitive: Task[Agg[BoundDep]] = T.task {
    val sjs = scalaJSVersion()
    artifactScalaVersion() match {
      case "3" => Agg[BoundDep]()
      case other =>
        Agg(
          Lib.depToBoundDep(
            ivy"org.scala-js:scalajs-compiler_2.13.12:$sjs"
              .exclude("*" -> "*"),
            scalaVersion()
          )
        )
    }
  }

  def scalaJsCompilerResolved: Task[Agg[PathRef]] = T {
    resolveDeps(scala2JsCompilerIntransitive)
  }

  protected def linkerDepBound: T[Agg[BoundDep]] =
    linkerDependency().map(Lib.depToBoundDep(_, scalaVersion()))

  def linkerLibs: Target[Agg[PathRef]] = T { resolveDeps(linkerDepBound) }

  def mdocJSDependency = T.task {
    val mdocV = mdocVersion()
    val dep = artifactScalaVersion() match {
      case "3"   => Agg(ivy"org.scalameta:mdoc-js-worker_3:$mdocV")
      case other => Agg(ivy"org.scalameta:mdoc-js-worker_$other:$mdocV")
    }
  }

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.scala-js:scalajs-compiler_2.13.12:${scalaJSVersion()}"
  )

  def mdocDep: T[Agg[Dep]] = T {
    artifactScalaVersion() match {
      case "3" =>
        Agg(
          ivy"org.scalameta::mdoc-js:${mdocVersion()}",
          ivy"org.scalameta::mdoc:${mdocVersion()}"
            .exclude("org.scala-lang" -> "scala3-compiler_3")
            .exclude("org.scala-lang" -> "scala3-library_3"),
          ivy"org.scala-lang::scala3-compiler:${scalaVersion()}",
          ivy"org.scala-lang:scala3-library:${scalaVersion()}",
          ivy"org.scala-lang::tasty-core:${scalaVersion()}",
          ivy"org.scala-lang.modules::scala-xml:2.1.0"
        )
      case other =>
        Agg(
          ivy"org.scalameta:mdoc-js_2.13:${mdocVersion()}",
          ivy"org.scala-lang:scala-compiler:${scalaVersion()}",
          ivy"org.scalajs:scalajs-dom_sjs1_2.13:2.8.0",
          ivy"org.scalameta:mdoc-js-worker_2.13:${mdocVersion()}"
        )
    }

  }

  def mdocDepBound: T[Agg[BoundDep]] =
    mdocDep().map(Lib.depToBoundDep(_, scalaVersion()))

  def mDocLibs: Target[Agg[PathRef]] = T { resolveDeps(mdocDepBound) }
}
