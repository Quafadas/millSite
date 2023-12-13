
// build.sc
// import $file.plugins

import $ivy.`io.github.quafadas::mill.site.mdoc::0.0.0-2-00d2bf-DIRTY63389791`

import mill._
import mill.scalalib._

import mill.site.SiteModule

object simples extends SiteModule {



}

def verify()  = T.command {
  val res : Path = simple.mdoc().path / "some.mdoc.md"
  if (!os.exists(res))
    throw new Exception(s"Expected mdoc file <${res.toIO.getAbsolutePath}> does not exist")

  val content = os.read.lines(res)
  if (!content.exists(_.contains("""// foo: String = "foo"""")))
    throw new Exception(s"Generated md file does not contain expected REPL output")



}