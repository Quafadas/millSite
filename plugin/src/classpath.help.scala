package io.github.quafadas.millSite

import mill._
import mill.scalalib._

private object ClasspathHelp {
  val separator: Char = java.io.File.pathSeparatorChar
  def toArgument(p: Seq[os.Path]): String =
    p.iterator.mkString(s"$separator")
}
