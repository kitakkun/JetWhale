# mcp-workflow

Runs, records and serves workflows of MCP tool calls — fixed QA flows you keep as YAML and run the
same way every time, against any MCP servers.

- User guide: [docs/guide/mcp-workflow.md](../../docs/guide/mcp-workflow.md)
- Design and what was tried: [docs/architecture/mcp-workflow.md](../../docs/architecture/mcp-workflow.md)
- Examples against the JetWhale demo: [examples/jetwhale-demo](examples/jetwhale-demo)

```shell
./gradlew :tools:mcp-workflow:installDist
tools/mcp-workflow/build/install/mcp-workflow/bin/mcp-workflow run \
  tools/mcp-workflow/examples/jetwhale-demo/navigation.yaml --mcp-config .mcp.json --report-dir build/qa
```
