
// build.sc
import $file.plugins

//import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.5`

import mill._
import mill.scalalib._

import mill.site.SiteModule


 object simples extends SiteModule {

}

def verify()  = T.command {
  val site = simples.siteGen()
  assert(os.exists(site / "docs" / "some.html" ))
}