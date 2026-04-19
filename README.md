# JetWhale

JetWhale is a next-generation, extensible debugging tool inspired
by [Flipper](https://github.com/facebook/flipper).

It is built with Kotlin and Jetpack Compose, making it especially familiar and approachable for
Kotlin / Android developers.
Thanks to its Kotlin-first design, JetWhale can be introduced with a minimal learning curve.

> [!NOTE]
> This project is under active development.
> We welcome feedback as we work toward a stable release.
> Please note that the Plugin SDK APIs are not yet finalized and may change in the future.

## Features

- 🐳 **Powerful Debugging Platform**
    - Provides a modern and rich debugging experience powered by **Kotlin** and **Jetpack Compose**
    - Supports debugging **multiple sessions simultaneously**
    - Debugging tools are implemented as **plugins**, which can be dynamically loaded at runtime as
      JAR files

- ⚙️ **Easy Integration and Customization**
    - DSL-based APIs allow you to quickly set up and configure JetWhale in your application
    - Customize the debugging experience by creating your own plugins using familiar **Kotlin**
      and **Jetpack Compose** paradigms

- 🛜 **Type-safe Communication with kotlinx.serialization**
    - Leverages **kotlinx.serialization** to enable type-safe communication between the debugger
      and debuggees

- ✅ **Multiplatform Support**
    - Supports **Android**, **Desktop(JVM)**, **iOS**(Simulator Only), and **Web** (Js, WasmJs)
      debuggees

- 🤖 **MCP Server Support** *(Experimental)*
    - JetWhale exposes a built-in **MCP (Model Context Protocol) HTTP+SSE server**, allowing AI
      agents (e.g. Claude) to interact with debuggee apps directly
    - Built-in tools include `screenshot`, `click`, `type`, `scroll`, `drag`, and
      `getAccessibilityTree`
    - Plugins can expose their own custom MCP tools by implementing `JetWhaleMcpCapablePlugin`
