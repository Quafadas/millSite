// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
// Run integration tests with mill
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.1`

import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version._
import mill.{Agg, PathRef, T}
import mill.define.{Cross, Module, Target}
import mill.modules.Util
import mill._
import mill.scalalib._
import mill.scalalib.publish._

object plugin extends ScalaModule with PublishModule {

  def millPlatform: T[String] = "0.11"

  def millVersion: T[String] = "0.11.0"

  def scalaVersion: T[String] = "2.13.12"

  def scalaArtefactVersion: T[String] =
    scalaVersion.map(_.split("\\.").take(2).mkString("."))

  override def artifactName = "mill_scala3_site_mdoc"

  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-main:${millVersion()}",
    ivy"com.lihaoyi::mill-scalalib:${millVersion()  }"
  )

  def artifactSuffix = s"_mill${millPlatform()}_${scalaArtefactVersion()}"

  def publishVersion = VcsVersion.vcsState().format()

  override def pomSettings = T {
    PomSettings(
      description = "Mill plugin for mdoc, static site generation",
      organization = "io.github.quafadas",
      url = "https://github.com/Quafadas/mill_scala3_mdoc_site",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("quafadas", "mill_scala3_mdoc_site"),
      developers =
        Seq(Developer("quafadas", "Simon Parten", "https://github.com/quafadas"))
    )
  }

}


object itest extends MillIntegrationTestModule {

  def millTestVersion = "0.11.0"

  def pluginsUnderTest = Seq(plugin)

}

object site extends ScalaModule {

  def scalaVersion: T[String] = "3.3.1"

  def sitePath : T[os.Path] = T{docJar().path / os.up / "javadoc" }

  def sitePathString : T[String] = T{sitePath().toString()}

  def serveLocal() = T.command {
    os.proc("npm", "browser-sync", "start", "--server", "--ss", sitePathString(), "-w")
    .call(stdout = os.Inherit)
  }

}
