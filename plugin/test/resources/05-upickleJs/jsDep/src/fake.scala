import upickle.default.*

object Writey2 {

  def writeMe = upickle.default.write(
    Map(
      "fooly" -> "barly"
    )
  )
}
