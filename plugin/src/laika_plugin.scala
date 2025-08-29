package io.github.quafadas.millSite


import laika.api._
import laika.format._
import laika.io.syntax._
import cats.effect.IO
import laika.io.model.RenderedTreeRoot
import cats.effect.unsafe.implicits.global
import laika.config.LaikaKeys
import laika.helium.config.*
import laika.ast.Path.Root
import laika.theme.ThemeProvider
import laika.helium.Helium.*
import laika.helium.Helium
import laika.helium.config.*
import mill.api.*
import mill.scalalib.*
import mill.api.Task.Simple
import mill.api.BuildCtx
import laika.config.SyntaxHighlighting


trait LaikaModule extends Module {

  def laikaUnidocDeps: Seq[JavaModule] = Seq.empty

  val unidocs: UnidocModule

  def includeApi: Simple[Boolean]= Task{laikaUnidocDeps.nonEmpty}

  def inputDir: Simple[PathRef] = Task.Source(super.moduleDir / "docs")

  def baseUrl: Simple[String] = unidocs.unidocSourceUrl().getOrElse("!!!no path!!!")

  def repoUrl: Simple[String] = Task { "https://github.com/example/repo" }

  def latestVersion: Simple[String] = Task { "0.0.0" }


  def configValues: Simple[Seq[(String, String)]] = Task {
    Seq(
      "version.latest" -> latestVersion(),
      LaikaKeys.siteBaseURL.toString() -> baseUrl()
    )
  }

  def helium = Task.Worker {
    val repoLink =
      IconLink.external(repoUrl(), HeliumIcon.github)
    val apiLink = if (includeApi()) Seq(IconLink.internal(Root / "api/index.html", HeliumIcon.api)) else Seq.empty

    Helium.defaults
      .site.topNavigationBar(
        homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home),
        navLinks = apiLink :+ repoLink
      )

      .site.inlineJS(
"""const sse = new EventSource("/refresh/v1/sse");
sse.addEventListener("message", (e) => {
const msg = JSON.parse(e.data);

if ("KeepAlive" in msg) console.log("KeepAlive");

if ("PageRefresh" in msg) location.reload();
});
"""
      )

  }

  def stageSite = Task {
    os.copy(inputDir().path, Task.dest, mergeFolders = true)
    if(includeApi()) {
      val apiSite = unidocs.unidocSite()
      os.copy(apiSite.path, Task.dest / "api", mergeFolders = true)
    } else {
      ()
    }
    PathRef(Task.dest)
  }

  def generateSite =
    Task{
      BuildCtx.withFilesystemCheckerDisabled {

      val heliumB = helium().build

      val transformer = Transformer
        .from(Markdown)
        .to(HTML)


      val transformerWithValues =
        configValues()
          .foldLeft(transformer) { (t, kv) => t.withConfigValue(kv._1, kv._2) }


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

}
