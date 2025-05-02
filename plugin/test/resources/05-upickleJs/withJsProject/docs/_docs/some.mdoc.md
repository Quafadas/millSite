# Some doc


```scala mdoc:js sc:nocompile
// This wont work until the PR to mdoc is merged and released
org.scalajs.dom.window.setInterval(() => {
  node.innerHTML = new java.util.Date().toString
}, 1000)
println("This should appear in the browser console")
```

```scala mdoc:js sc:nocompile
import org.scalajs.dom
val child = dom.document.createElement("div")
child.textContent = Writey2.writeMe
node.appendChild(child)
```
