# MCP workflows

Status: experimental, in `tools/mcp-workflow`. User guide: [MCP Workflows](../guide/mcp-workflow.md).

## Problem

An AI agent driving JetWhale over MCP is good at finding out how to reach a state and check it. It
is a poor way to check the same thing again every day: each run re-derives the steps, costs tokens,
and can take a different path. QA needs the opposite — the same calls, the same checks, a report CI
can read — and wants it for flows that span more than JetWhale (seed data through a backend, check
the app).

## Shape

A workflow is a list of standard MCP tool calls against named servers. Nothing in the format or the
runner knows about JetWhale; JetWhale is one server among others, reached like any other.

```
            ┌──────────── run ────────────┐
workflow ──►│ runner ── MCP client ──┬───►│ JetWhale host (SSE)
 (YAML)     └────────────────────────┼────┘ backend MCP server (stdio / HTTP)
                                     │
  agent ──► record proxy ────────────┘   (forwards, records, exports a workflow)
  agent ──► serve ── runner ──► …        (one tool per workflow)
```

- **Runner** — connects to each server on first use (stdio, SSE, Streamable HTTP via the MCP Kotlin
  SDK), renders placeholders, calls, checks, saves, and reports (console, JUnit XML, Markdown with
  images).
- **Recorder** — an MCP server that lists the upstream servers' tools as its own and forwards every
  call, recording it; it exports the recording as a workflow.
- **Serve** — an MCP server with one tool per workflow file.

It is a JVM application with no dependency on any JetWhale module, laid out to move to a repository
of its own.

### Format

YAML, versioned (`version: 1`; any other version is refused by name). JSON files are read too, as
the YAML subset they are. YAML won on editing: comments, no quoting of every key, and a diff that
reads line by line. The one cost found in use is flow style: `{ x: $.list[0] }` does not parse, since
`[` opens a list there — paths in flow style need quotes.

Free-form arguments cannot be decoded by kaml directly, so the YAML tree is converted to JSON first:
plain scalars are typed as YAML would type them, quoted scalars stay strings (so `"8080"` stays a
string). kaml keeps a scalar's position but not its quoting, so the converter reads the quote from
the source line.

The ideas taken from Arazzo (OpenAPI's workflow format) are small ones: named outputs of a step
(`save`), success criteria per step (`expect`) and per workflow (`defaults.expect`), and workflow
`outputs`. Arazzo's own format is built around HTTP operations and would not describe MCP calls
without a layer of indirection; following it more closely was not worth that.

### Paths

A subset of JSONPath: members, indices (negative from the end), `[*]`, equality filters joined by
`&&`, and `.first()` — an extension that picks one match of a filter, because the point of a filter
here is almost always to find one item. A library was not used: the subset is ~150 lines, its
behaviour is fully specified by the tests, and the extension would have needed one anyway.

## What we tried

Every run below was against a JetWhale host built from this branch (ports 5092/5455/7092) with the
desktop demo app connected, unless noted.

1. **Expected values were not templated.** The first in-process end-to-end test compared
   `equals: "${email}"` against the literal text. Expectations are now rendered like arguments, and
   validation reports expectations that refer to variables nothing defines.

2. **kotlin-logging printed on stdout.** Its start-up banner went to standard output, which in
   `record` and `serve` over stdio is the MCP channel. It is disabled with a JVM flag in the launcher,
   and SLF4J gets a no-op binding.

3. **Tools enabled mid-run were not callable.** Enabling a plugin and calling its tool in the same run
   failed with "tool not found": JetWhale builds a connection's tool list when it opens (it declares
   `listChanged: false`). A step option `reconnect: true` opens a new connection before the step.
   Reconnecting automatically on "not found" was rejected: it would key off error text, which varies
   by server.

4. **A mistaken call passed.** A click with the wrong action name returned `{"error": "invalid
   action…"}` as a normal result — JetWhale reports caller mistakes as payloads so an agent can read
   them. The runner only failed on `isError`. Rather than learn one server's convention,
   `defaults.expect` states it once per workflow.

5. **`null` versus missing.** The storage tools always send `"error": null`, so `exists: false`
   failed on every call. `exists` now treats JSON `null` as absent, which is what an API means by it
   far more often than not.

6. **Cleanup was skipped after a failure.** A failed network-mock run left its mock rule installed in
   the app, affecting the next run. `always: true` runs a step even after a failure.

7. **Paths could not use variables.** A storage check wanted `$.fileRoots[?(@.name == '${root}')]`.
   Paths in `expect` and `save` are templated now.

8. **Recordings tied constants to listings.** The first recording turned the literal `"Settings"` into
   a variable saved from `listNavKeyTypes` at `$.keyTypes[2].serialName`, because the word also
   appeared there — correct by accident, and broken as soon as the list order changes. Only values
   that look generated are traced now: numbers of 10 and up, strings of 6+ characters with a digit
   and no spaces, and anything under an id-like key.

9. **Recordings broke when the app restarted.** A replay after restarting the demo failed: the
   recorded filter `sessionName == 'Mac OS X'` matched the old, inactive session first, because
   JetWhale keeps it listed under the same name. The export now adds the liveness flag the item had
   (`isActive`, `current`, …) to the filter and judges uniqueness with it; the path subset gained
   `&&` for this. The recording then replayed across a restart with a new session id and new node
   ids.

10. **Recordings had no checks.** A recorder cannot guess what a call was meant to prove.
    `workflow_recording_expect` lets the recording agent attach a check to its last call, and warns
    when the check already fails on the recorded result. A read-only call with a check survives
    `dropReads`.

11. **Serve mode** ran the navigation and network flows as single tool calls from another workflow,
    returned the navigator screenshot inline, and set `isError` for a flow that failed.

Final state of the examples against the live host: network mock (11 steps, ~0.4 s, offline thanks to
the mock), navigation round trip with a screenshot (~0.4 s), storage checks, a flow spanning the
bundled accounts server over stdio and JetWhale over SSE, and the recorded flow — all passing.

## Not done

- **Parallel steps, loops and branches.** QA flows seen so far are linear; `wait` covers the main
  reason for a loop. Adding control flow turns the format into a language.
- **A host UI** for running and editing workflows. The CLI and serve mode come first; a UI would sit
  on `serve`.
- **Import from the host's MCP call history.** The recorder covers any client and any server, where
  the history covers JetWhale only; an import can come later if the history proves to be where flows
  are found.
- **Reconnecting after a server restart.** A run keeps one connection per server; a server that
  restarts mid-run is expected to fail the remaining steps rather than be reconnected to. Not tried.

## Suggested next steps for QA use

1. Keep flows in the app repository (`qa/flows/*.yaml`), with the `.mcp.json` ports in CI config.
2. Run them in CI against a headless host (`runHeadless`) and the app under test, publishing
   `junit.xml` and `report.md`.
3. Record new flows with an agent through `record`, attach checks while recording, and review the
   export like code.
4. Give agents `serve` alongside JetWhale's own server, so a regression check is one call.
