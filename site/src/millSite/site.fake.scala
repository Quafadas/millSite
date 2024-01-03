package io.github.quafadas.millSite

  /**
   *
   * See 'Doc' section.
   *
   * We have to trick scalas static site generator, into thinkingt hat there is actual source code in the project. Which is why this class exists.
   *
   * However, mill itself is on 2.13 (so this plugin is too), and hence we can't compile the _actual_ plugin and show the API here - because static site generator, is scala3.
   *
   * Generating scaladoc here is useful, to test it's features.
   *
    */
object UserApi {
}
