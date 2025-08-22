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

  def unidocDeps: Seq[JavaModule] = Seq.empty

  val mdocModule : MdocModule = new MdocModule {
    override def scalaVersion: Simple[String] = "3.7.2"
    override def mdocDir = defaultInternalDocDir
    override def docDir: Simple[PathRef] = Task.Source(mdocDir)

    override def moduleDeps: Seq[JavaModule] = unidocDeps
  }


  val laika = new LaikaModule {
    override def inputDir: Simple[PathRef] = mdocModule.mdocT()
    override def laikaUnidocDeps: Seq[JavaModule] =  unidocDeps
  }

  def siteGen = Task{
    val mdocs = mdocModule.mdocT()
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

  def dezombify = Task{
    val p = port()
    val osName = System.getProperty("os.name").toLowerCase
    if (osName.contains("win")) {
      // Windows: try PowerShell Get-NetTCPConnection, fallback to netstat/taskkill
      val ps = s"""
      |if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
      |  $$pids = Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
      |  if ($$pids) { $$pids | ForEach-Object { Stop-Process -Id $$_ -Force } }
      |} else {
      |  $$lines = netstat -ano | Select-String ":$p\\s"
      |  $$pids = $$lines | ForEach-Object { ($$_ -split '\\s+')[-1] } | Select-Object -Unique
      |  if ($$pids) { $$pids | ForEach-Object { taskkill /F /PID $$_ } }
      |}
      |""".stripMargin
      os.proc("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps).call(check = false)
    } else {
      // macOS/Linux: use lsof if available, fallback to fuser
      val sh = s"""
      |if command -v lsof >/dev/null 2>&1; then
      |  pids=$$(lsof -ti tcp:$p 2>/dev/null)
      |  if [ -n "$$pids" ]; then kill -9 $$pids; fi
      |elif command -v fuser >/dev/null 2>&1; then
      |  fuser -k -TERM $p/tcp || true
      |  fuser -k -KILL $p/tcp || true
      |fi
      |""".stripMargin
      os.proc("sh", "-lc", sh).call(check = false)
    }
  }

  def serve = Task.Worker{
    // Let's kill off anything that is a zombie on the port we want to use
    dezombify()

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