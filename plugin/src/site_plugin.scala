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
import io.github.quafadas.sjsls.LiveServer.LiveServerConfig
import cats.effect.ExitCode
import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import mill.api.BuildCtx
implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

trait SiteModule extends Module:

  lazy val updateServer = Topic[IO, Unit].unsafeRunSync()

  lazy val defaultInternalDocDir = super.moduleDir / "docs"

  val mdocModule : MdocModule = new MdocModule {
    override def scalaVersion: Simple[String] = "3.7.2"
    override def mdocDir = defaultInternalDocDir
    override def docDir: Simple[PathRef] = Task.Source(mdocDir)
  }

  val unidocs: UnidocModule = new UnidocModule{
    override def scalaVersion: Simple[String] = "3.7.2"
    override def unidocDocumentTitle: Simple[String] = "Unidoc Title [hint: override def unidocDocumentTitle: Simple[String] ]"
  }

  val laika = new LaikaModule {
    override def inputDir: Simple[PathRef] = mdocModule.mdoc()
    override def baseUrl: Simple[String] =
      unidocs.unidocSourceUrl().getOrElse("no path")

  }

  def siteGen = Task{
    println("running site gen")
    val mdocs = mdocModule.mdoc()
    // val api = unidocs.unidocSite()
    val site = laika.generateSite()
    updateServer.publish1(println("publishing update"))
    site
  }

  def sitePathOnly = Task {
    siteGen().path.toString
  }

  def port = Task {
    8080
  }

  def openBrowser= Task {
    true
  }

  def logLevel = Task {
    "debug"
  }

  // def assets = Task {
  //   println("site assets")
  //   os.copy.over(laika.assets().path, Task.dest)
  //   PathRef(Task.dest)
  // }

  def lcs = Task.Worker{
    LiveServerConfig(
          baseDir = None, // typically this would be a build tool here
          // outDir = Some(assets().path.toString()),
          port = com.comcast.ip4s.Port.fromInt(port()).getOrElse(throw new IllegalArgumentException(s"invalid port: ${port()}")),
          indexHtmlTemplate = Some(sitePathOnly()),
          buildTool = io.github.quafadas.sjsls.None(),
          openBrowserAt = "/index.html",
          preventBrowserOpen = !openBrowser()
        )
  }

  def serve = Task.Worker{
    BuildCtx.withFilesystemCheckerDisabled {
    // assets()
    val lcs_ = lcs()
    println(lcs_)
    val attempt = Try{
      io.github.quafadas.sjsls.LiveServer.main(lcs_).useForever
        .as(ExitCode.Success)
        .unsafeToFuture()
    }

    attempt match {
      case Success(ls) =>
        println(s"Server started successfully. Browse at - http://localhost:${port()}/")
        ls
      case Failure(e) =>
        println(s"Failed to start server: ${e}")
        Future(ExitCode.Error)
    }
    }
  }

end SiteModule