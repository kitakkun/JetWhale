# Review guidance

When reviewing a pull request:

- Report a defect only when its failure scenario can actually happen. Trace it through the real
  call path first: what callers already check, and what the external tool, platform or library
  actually does. Do not suggest guards, fallbacks or validation for inputs that cannot reach the code.
- A breaking change that the PR description states is deliberate. Do not suggest compatibility
  shims or migration paths for it.
- If a finding rests on an assumption you could not verify, name the assumption.
- The repository's rules are in `AGENTS.md` and `agents/rules/*.md`; review against them.
