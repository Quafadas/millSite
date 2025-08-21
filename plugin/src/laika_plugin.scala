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


trait LaikaModule extends Module {

  def inputDir: Simple[PathRef] = Task.Source(super.moduleDir / "docs")

  def baseUrl: Simple[String] = Task("https://my-docs/site")

  def helium = Task.Worker {
    Helium.defaults
      .site.topNavigationBar(
        homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home),
        navLinks = Seq(
          IconLink.external("https://example.com", HeliumIcon.github)
        )
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

  def generateSite =
    Task{
      println("Generate Site")
      BuildCtx.withFilesystemCheckerDisabled {
      val transformer = Transformer
        .from(Markdown)
        .to(HTML)
        .withConfigValue(LaikaKeys.siteBaseURL, baseUrl())
        .using(Markdown.GitHubFlavor)
        .parallel[IO]
        .withTheme(helium().build)
        .build

      val res: IO[RenderedTreeRoot[IO]] = transformer.use { t =>
          t.fromDirectory(inputDir().path.toString())
            .toDirectory(Task.dest.toString())
            .transform
        }
      res.unsafeRunSync()


      PathRef(Task.dest)
      }
    }

}
