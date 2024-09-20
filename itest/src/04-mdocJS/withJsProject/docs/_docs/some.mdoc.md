# Some doc


```scala mdoc:js sc:nocompile
// This wont work until the PR to mdoc is merged and released
org.scalajs.dom.window.setInterval(() => {
  node.innerHTML = new java.util.Date().toString
}, 1000)
println("This should appear in the browser console")
```

```scala mdoc:js sc:nocompile

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

node.id = "appContainer"
val iconVar = Var("hi")

render(dom.document.querySelector("#appContainer"),
  div(
    input(

    )
  )
)
```