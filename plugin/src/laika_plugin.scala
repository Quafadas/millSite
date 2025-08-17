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
import mill.api.*
import mill.scalalib.*
import mill.api.Task.Simple

trait LaikaPlugin extends Module {

  def docDir = super.moduleDir / "docs"

  def inputDir: Simple[PathRef] = Task.Source(docDir)

  def baseUrl: Simple[String] = Task("https://my-docs/site")

  def generateSite = 
    Task{
      val transformer = Transformer
        .from(Markdown)
        .to(HTML)
        .withConfigValue(LaikaKeys.siteBaseURL, baseUrl())
        .using(Markdown.GitHubFlavor)
        .parallel[IO]
        .withTheme(
          laika.helium.Helium.defaults
          .site
            .topNavigationBar(
              homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home))
          .site.internalJS(Root / "refresh.js")
            .build
        ).build

      val res: IO[RenderedTreeRoot[IO]] = transformer.use { t =>
          t.fromDirectory(docDir.toString())
            .toDirectory(Task.dest.toString())
            .transform
        }
      res.unsafeRunSync()

      PathRef(Task.dest)

    }
    
}
