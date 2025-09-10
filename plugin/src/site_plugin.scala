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

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

trait SiteModule extends Module:

  lazy val updateServer = Topic[IO, Unit].unsafeRunSync()

  lazy val defaultInternalDocDir = super.moduleDir / "docs"

  def unidocDeps: Seq[JavaModule] = Seq.empty

  def unidocTitle = Task("Unidoc Title Here")

  def repoLink = Task { "<repo-link>" }

  def latestVersion: Simple[String] = Task {
    VcsVersion.vcsState().lastTag.getOrElse("0.0.0").replace("v", "")
  }

  def resources = Task.Sources { super.moduleDir / "resources" }

  def forkArgs: Simple[Seq[String]] = Task{Seq.empty[String]}

  def pathToImportMap: T[Option[PathRef]] = None

  def scalaVersion = "3.7.2"

  def mdocSiteVariables: Simple[Seq[(String, String)]] = Task{Seq("VERSION" -> latestVersion())}

  val mdocModule : MdocModule = new MdocModule {
    override def scalaVersion: Simple[String] = SiteModule.this.scalaVersion
    override def mdocDir = defaultInternalDocDir
    override def pathToImportMap: Simple[Option[PathRef]] = SiteModule.this.pathToImportMap()

    override def siteVariables: Simple[Seq[(String, String)]] = mdocSiteVariables()

    override def forkArgs: Simple[Seq[String]] = Task{super.forkArgs() ++ SiteModule.this.forkArgs()}
    override def docDir: Simple[PathRef] = Task.Source(mdocDir)

    override def moduleDeps: Seq[JavaModule] = unidocDeps

    override def compileResources = Task{super.compileResources() ++ SiteModule.this.resources()}
    override def resources = Task{ super.resources() ++ SiteModule.this.resources()}
  }


  val laika = new LaikaModule {
    override val unidocs = new UnidocModule{
        override def scalaVersion: Simple[String] = SiteModule.this.scalaVersion
        override def moduleDeps: Seq[JavaModule] = laikaUnidocDeps
        override def unidocDocumentTitle = unidocTitle()


      }
    override def inputDir: Simple[PathRef] = mdocModule.mdoc2()
    override def laikaUnidocDeps: Seq[JavaModule] =  unidocDeps

    override def repoUrl: Simple[String] = repoLink()

    override def latestVersion: Simple[String] = SiteModule.this.latestVersion()

  }

  def siteGen = Task{
    val mdocs = mdocModule.mdoc2()
    val site = laika.generateSite()
    updateServer.publish1(println("publishing update")).unsafeRunSync()
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
    val port_ = port()
    val sitePathOnly_ = sitePathOnly()
    BuildCtx.withFilesystemCheckerDisabled {
      LiveServerConfig(
            baseDir = None, // typically this would be a build tool here
            // outDir = Some(assets().path.toString()),
            port = com.comcast.ip4s.Port.fromInt(port_).getOrElse(throw new IllegalArgumentException(s"invalid port: ${port_}")),
            indexHtmlTemplate = Some(sitePathOnly_),
            buildTool = io.github.quafadas.sjsls.NoBuildTool(),
            openBrowserAt = "/index.html",
            preventBrowserOpen = !openBrowser(),
            customRefresh = Some(updateServer)
          )
    }
  }

  def serve = Task.Worker{
    // Let's kill off anything that is a zombie on the port we want to use
    val p = port()
    BuildCtx.withFilesystemCheckerDisabled {
      new RefreshServer(lcs())
    }
  }

  class RefreshServer(lcs: LiveServerConfig) extends AutoCloseable {
    val server  = io.github.quafadas.sjsls.LiveServer.main(lcs).allocated

    server.map(_._1).unsafeRunSync()

    override def close(): Unit = {
      // This is the shutdown hook for http4s
      println("Shutting down server...")
      server.map(_._2).flatten.unsafeRunSync()
    }
  }

end SiteModule