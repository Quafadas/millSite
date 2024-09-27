package io.github.quafadas.millSite

import mill.api.PathRef

/** @param docs
  *   \- Governs caching
  * @param base
  *   \- the path to a docs only site
  * @param staticAssets
  */
case class QuickChange(docs: Seq[PathRef], base: PathRef, staticAssets: PathRef)
object QuickChange {
  implicit val rw: upickle.default.ReadWriter[QuickChange] =
    upickle.default.macroRW[QuickChange]
}
