---
layout: home

hero:
  name: JetWhale
  text: Extensible debugging for Kotlin & Compose apps
  tagline: A next-generation debugging platform inspired by Flipper — built with Kotlin and Jetpack Compose.
  image:
    src: /icon.svg
    alt: JetWhale
  actions:
    - theme: brand
      text: Get Started
      link: /guide/getting-started
    - theme: alt
      text: What is JetWhale?
      link: /guide/what-is-jetwhale
    - theme: alt
      text: GitHub
      link: https://github.com/kitakkun/JetWhale

features:
  - icon: 🐳
    title: Powerful Debugging Platform
    details: A modern debugging experience powered by Kotlin and Jetpack Compose, with multiple simultaneous debug sessions and runtime-loadable JAR plugins.
  - icon: ⚙️
    title: Easy Integration
    details: DSL-based APIs let you set up JetWhale in your app in minutes. ADB auto port mapping wires up Android devices automatically.
  - icon: 🛜
    title: Type-safe Communication
    details: kotlinx.serialization powers type-safe messaging between the debugger and your app.
  - icon: ✅
    title: Multiplatform
    details: Debug Android, Desktop (JVM), iOS (Simulator), and Web (JS / WasmJS) apps.
  - icon: 🤖
    title: MCP Server (Experimental)
    details: A built-in MCP server lets AI agents interact with your app — screenshot, click, type, scroll, drag, and accessibility-tree inspection.
  - icon: 🔥
    title: Hot-Reloadable Plugins
    details: Build your own plugins in your own repository and develop them against a real host with hot reload — no restarts.
---
