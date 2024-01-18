package io.github.quafadas.millSite

import mill._
import mill.scalalib._

object ClasspathHelp {
  val separator: Char = java.io.File.pathSeparatorChar
  def toArgument(p: Agg[os.Path]): String =
    p.iterator.mkString(s"$separator")
}
