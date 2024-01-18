import $file.plugins
import $file.SimpleModule
import mill._
import mill.scalalib._
import io.github.quafadas.millSite._
import ClasspathHelp._

object foo extends SimpleModule.SimpleModule

// Single module setup
object simpleJs extends SiteJSModule {
  override def scalaVersion = "3.3.1"
  override def scalaJSVersion = "1.14.0"
}

object withJsProject extends SiteModule {
  override def scalaVersion = "3.3.1"
  override val jsSiteModule = simpleJs
  def moduleDeps = Seq(foo)
}

def verify() = T.command {

  val mdocProps = simpleJs.mdocJsProperties()
  // println(os.read(mdocProps.path / "mdoc.properties"))
  assert(os.exists(mdocProps.path / "mdoc.properties"))

  val mdocOut = withJsProject.mdoc().path
  assert(os.exists(mdocOut / "_docs" / "some.mdoc.md"))
  assert(os.exists(mdocOut / "_docs" / "_assets" / "js" / "some.mdoc.md.js")) // subRelTo puts these in the right place.
  assert(os.exists(mdocOut / "_docs"/ "_assets" / "js" / "mdoc.js"))

  val site = withJsProject.live()
}
