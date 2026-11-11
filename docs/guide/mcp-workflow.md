# MCP Workflows <Badge type="warning" text="experimental" />

`mcp-workflow` runs **workflows**: fixed sequences of MCP tool calls, with checks, written in a YAML
file you keep next to your code. It is meant for QA flows — put the app into a state, act on it,
check the result — that you want to run the same way every time, without an AI agent deciding each
step.

It speaks plain MCP. JetWhale's tools are one server among others: a workflow can create test data
through a backend's MCP server and then check the app through JetWhale in the same run.

Three ways to use it:

| Command | What it does |
|---|---|
| `run` | Runs workflows and reports each step; exit code 1 if any step failed. For CI and for replaying a flow by hand. |
| `record` | Stands in front of real servers as an MCP server. An AI agent works through it as usual; every call is recorded, and the recording is saved as a workflow that replays without the agent. |
| `serve` | Serves each workflow as one MCP tool, so an agent runs a whole checked flow in one call. |

`validate` checks a workflow without running it, and `tools` lists what the configured servers offer.

## Building it

In this repository:

```shell
./gradlew :tools:mcp-workflow:installDist
tools/mcp-workflow/build/install/mcp-workflow/bin/mcp-workflow --help
```

## Servers

Servers are declared as in `.mcp.json` — `command` (+ `args`, `env`) for stdio, `url` with `type`
`sse` or `http` (Streamable HTTP) for remote ones. Pass a config file with `--mcp-config`, or declare
servers in the workflow itself; the workflow's own entries win:

```json
{ "mcpServers": { "jetwhale": { "type": "sse", "url": "http://localhost:7080/sse" } } }
```

Keeping machine-specific ports in the config file and out of the workflow lets the same workflow run
against a host on another port.

## Writing a workflow

```yaml
version: 1
name: Detail screen round trip
inputs:
  itemId:
    default: item-42
defaults:
  # JetWhale answers a mistaken call with {"error": ...} rather than a tool error;
  # stating it once here checks every step for it.
  expect:
    - path: $.error
      exists: false
steps:
  - id: session
    call: jetwhale.listSessions
    save:
      sessionId: "$[?(@.isActive == true)].first().sessionId"

  - id: push
    call: com.kitakkun.jetwhale.nav3.pushNavKey
    args:
      sessionId: "${sessionId}"
      key: { type: Detail, itemId: "${itemId}" }
    expect:
      - path: $.applied
        equals: true

  - id: back
    always: true        # runs even if a step above failed
    call: com.kitakkun.jetwhale.nav3.popBackStack
    args: { sessionId: "${sessionId}", count: 1 }
```

### Steps

| Field | Meaning |
|---|---|
| `call` | Tool name. |
| `server` | Server to call; may be left out when there is only one. |
| `args` | Arguments, as JSON/YAML. Any string may hold `${...}` placeholders. |
| `save` | Variables to keep, each a path into the result. |
| `expect` | Checks on the result (below). |
| `wait` | `{ timeout: 5s, interval: 250ms }` — repeat the call until every check holds. Use it for state that settles on the next frame instead of sleeping. |
| `retries` | Extra attempts after an error or a failed check, without waiting. |
| `timeout` | Longest one call may take (default 30s). |
| `continueOnFailure` | Report a failure and go on. |
| `always` | Run even after an earlier step failed — for teardown such as removing a mock the flow installed. |
| `reconnect` | Open a new connection before this step. Needed after enabling a plugin: JetWhale fixes a connection's tool list when it opens, so a newly enabled plugin's tools are only callable on a new connection. |

`defaults` gives every step `expect` checks (skipped by a step that expects `error: true`) and a
`timeout`. `vars` holds constants; `outputs` lists values to print after the run.

### Results, paths and placeholders

A path reads the tool's structured content, or else its text content parsed as JSON (JetWhale's
tools return JSON text), or else the plain text.

Paths are a small subset of JSONPath:

- `$.a.b`, `$['odd key']`, `$.list[0]`, `$.list[-1]` (from the end)
- `$.list[*].id` — every item
- `$.nodes[?(@.text == 'Settings')]` — items where a field equals (`==`) or differs from (`!=`) a
  literal; `&&` joins conditions
- `.first()` — the first of a filter's matches: `$.nodes[?(@.text == 'Settings')].first().id`

`${name}` is replaced by a variable. A string that is only a placeholder keeps the variable's type
(a saved number stays a number); a placeholder inside longer text is spliced in as text.
`${node.bounds.left}` reads into a saved object and `${env.TOKEN}` reads the environment.
Placeholders work in `args`, in expected values and in paths.

::: tip Quote paths in flow style
Inside `{ ... }`, YAML reads `[` as the start of a list, so `save: { x: $.list[0] }` does not parse.
Quote the path, or use block style.
:::

### Checks

Each `expect` entry has a `path` (default `$`) and one or more of: `equals`, `notEquals`,
`contains` (array element, object key or substring), `matches` (regex), `exists`, `gt`, `gte`,
`lt`, `lte`, `length`. `exists: false` also holds for a JSON `null`, since APIs send `"error": null`
and leave `error` out to mean the same. `error: true` / `false` checks whether the tool reported an
error; a step with no `error` check fails on a tool error.

## Running

```shell
mcp-workflow run flows/*.yaml --mcp-config .mcp.json \
  --input itemId=item-7 --report-dir build/qa-report
```

The report directory gets `junit.xml` for CI, `report.md` with every step's arguments and result
(the quickest way to see why a check failed), and `artifacts/` with any images tools returned —
`jetwhale.screenshot` makes a good piece of evidence at the end of a flow.

## Recording a flow from an agent

```shell
mcp-workflow record --mcp-config .mcp.json --port 7093
```

Point the agent at `http://127.0.0.1:7093/sse` instead of the real server (without `--port`, the
recorder speaks stdio, for clients that launch servers). It lists the real server's tools as its
own, plus three to manage the recording:

- `workflow_recording_expect` — attach a check to the last call, e.g. `{"path": "$.applied",
  "equals": true}`. It warns when the check already fails on the recorded result.
- `workflow_recording_save` — write the workflow: `{"path": "flows/x.yaml", "name": "…",
  "dropReads": true}`.
- `workflow_recording_clear` — start over from here.

Values that change between runs are what make a naive recording fail on replay. The export handles
them:

- An argument that an earlier result returned — a session id, a node id, a created record's id —
  becomes a variable saved from that result.
- A list item is found again by a field that identifies it (`text`, `name`, `testTag`, …) rather
  than its position, plus a liveness flag it had (`isActive`, `current`, …), since JetWhale keeps a
  restarted app's old session under the same name.
- Only values that look generated are traced this way; a constant such as `"Settings"` that also
  appears in some earlier listing stays a literal.
- `dropReads` leaves out read-only calls whose results nothing uses, and keeps a read that carries a
  check.

Review the result like any code before committing it: the recorder cannot know which checks matter,
and an `...Id` it could not trace becomes an input with the recorded value as its default.

## Serving flows as tools

```shell
mcp-workflow serve flows/ --mcp-config .mcp.json --port 7094
```

Each file becomes a tool named `workflow_<file name>`, whose arguments are the workflow's inputs.
The result lists each step as passed, failed or skipped, the outputs and the artifacts (the last two
images inline), with `isError` set when a step failed. An agent can then check "the login flow still
works" in one call and spend its own steps on what needs judgement.

## Examples

`tools/mcp-workflow/examples/jetwhale-demo/` holds flows for the demo app: a mocked network response
reaching the app, a navigation round trip with a screenshot, storage checks, a flow that spans an
accounts server and JetWhale, and a recorded flow.
