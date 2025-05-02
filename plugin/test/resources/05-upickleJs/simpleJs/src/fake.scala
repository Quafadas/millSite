import upickle.default.*

object Writey {

  def writeMe = upickle.default.write(
    Map(
      "foo" -> "bar"
    )
  )
}
