package millSite

/**
  * A fake
  */

object UserApi {

  /**
    * This is the API that a user is expected to interact with.
    *
    * Mill is a scala 2.13 construct so we can't actually cross compile it and have it in scala 3. See the plugin module, for the actual code. Instead this is a copy paste of the intended "user facing" parts of the API
    *
    * The plugin itself overrides some of mill's standard targets, to make them more convienient to produce a static site.
    */

  trait SiteModule {

    /**
     * If true (default), it will generate API docs, for each module included in the standard "dependsOn" task.
     */

    def transitiveDocs: Boolean

    /**
      * The version of mdoc to use, default 2.5.1
      */
    def scalaMdocVersion: String

    /**
      * Uses [https://browsersync.io](browser-sync) to serve the site locally.
      */

    def serveLocal() : Unit

    /**
      * Runs mdoc, for docs in the "docs/_doc" directory.
      */

    def mdoc: PathRef
  }

    /**
  * This class is a cheap facade for mill.PathRef - as mill is scala 2.13, I can't import the mill API into the scala3 doc site.
  */

  type PathRef = String

}

