
import mill._
import mill.scalalib._
import mill.scalalib.publish._

trait SimpleModule extends ScalaModule with PublishModule {
  override def scalaVersion = T("3.3.1")
  override def publishVersion: mill.T[String] = "0.0.0"
  override def pomSettings = PomSettings(
    "iTest.desc",
    "iTest.org",
    "https://github/fake/iTest.url",
    Seq(License.`Apache-2.0`),
    VersionControl.github("testOwner","testProject"),
    Seq()
  )
}
