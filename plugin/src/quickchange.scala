package io.github.quafadas.millSite

import mill.api.PathRef

case class QuickChange(docs: Seq[PathRef], base: PathRef, staticAssets: PathRef)
object QuickChange {
  implicit val rw: upickle.default.ReadWriter[QuickChange] =
    upickle.default.macroRW[QuickChange]
}
