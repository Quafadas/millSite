package io.github.quafadas.millSite

import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.testkit.TestRootModule

trait SimpleModule extends ScalaModule with PublishModule {
  override def scalaVersion = Task("3.3.5")
  override def publishVersion: mill.T[String] = "0.0.0"
  override def pomSettings = PomSettings(
    "iTest.desc",
    "iTest.org",
    "https://github/fake/iTest.url",
    Seq(License.`Apache-2.0`),
    VersionControl.github("testOwner", "testProject"),
    Seq()
  )
}
