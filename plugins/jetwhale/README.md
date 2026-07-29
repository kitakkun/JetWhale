# JetWhale plugin for Claude Code

Skills for developing [JetWhale](https://github.com/kitakkun/JetWhale) host plugins.

| Skill | What it covers |
|---|---|
| `/jetwhale:plugin-qa` | Driving a host plugin's real UI through the debug tool's MCP server — screenshots, gestures, persisted state, restart restore, and a headless debuggee to drive it against |

## Install

```
/plugin marketplace add kitakkun/JetWhale
/plugin install jetwhale@jetwhale
/reload-plugins
```

JetWhale is a large repository and the marketplace lives inside it, so adding it the plain way
clones the whole thing. To fetch only what the plugin needs, add it from the CLI with `--sparse`:

```bash
claude plugin marketplace add kitakkun/JetWhale --sparse .claude-plugin plugins
```

Either way, installing copies just this directory into `~/.claude/plugins/cache`.

## Why the skill lives in the JetWhale repository

A QA skill is only useful while it is true, and what it describes — MCP tool names, the QA agent's
control API, which ports the launch tasks accept — moves with the code. Keeping it here means a
change to the host and the change to its documented workflow land in the same commit, reviewed
together. A separate repository would let the two drift, and a QA guide that quietly lies is worse
than none.

## Requirements

The skill assumes your plugin module applies the `com.kitakkun.jetwhale.host` Gradle plugin and sets
`jetwhalePlugin.hostVersion`; that is what provides the `runJetWhale` and `runJetWhaleQaAgent` tasks
it drives. See the [plugin development guide](https://github.com/kitakkun/JetWhale/tree/main/docs).
