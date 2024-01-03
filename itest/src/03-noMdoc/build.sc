// build.sc
import $file.plugins

//import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.5`

import mill._
import mill.scalalib._

import io.github.quafadas.millSite.SiteModule

object simples extends SiteModule {
  override def scalaVersion = T("3.3.1")
}

def verify() = T.command {
  val site = simples.siteGen()
  assert(os.exists(site / "docs" / "some.html"))
}
