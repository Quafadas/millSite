// build.sc
import $file.plugins
import $file.SimpleModule
//import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.5`

import mill._
import mill.scalalib._

import io.github.quafadas.millSite.SiteModule

object simples extends SimpleModule.SimpleModule

object site extends SiteModule {
  override def scalaVersion = T("3.3.1")
  def moduleDeps = Seq(simples)
}

def verify() = T.command {
  val makesite = site.live()
  assert(os.exists(makesite / "docs" / "some.html"))
}
