---
name: reference-brief
description: Investigate a technical task end to end — interview the user, cache the pinned references, run real experiments, put the result through five independent review lenses — and write a purely technical brief of working example code and key-point explanations that a reader with no prior context can follow on its own. The interview steers the work but is never written anywhere.
disable-model-invocation: true
---

# Reference brief

The brief must be complete, verified and self-contained. Everything this skill writes may
become public.

`/reference-brief [topic]` — the topic only seeds the interview.

## The interview is never written down

What the user says in the interview lives only in this conversation. It never goes into a
file, a log, a commit message, a branch name, a code comment or a subagent prompt. If context
is lost and a detail is unclear, ask the user again; never reconstruct it from memory and
never write it down to keep it.

What reaches the disk is technical only: `spec.md`, example code, tests, the brief, and
experiments and logs under `work/`, which is git-ignored and never pushed.

## Phase 1 — Interview

Ask with AskUserQuestion (at most four questions per call, as many rounds as needed); where
it is unavailable, ask in a plain message and end the turn. Cover:
- the task in the user's own words; what must work at the end;
- language, runtime, framework and exact versions, or the lock or build file that pins them;
  the environment the code runs in;
- what the reader of the brief can run, read or browse — this sets how explicit it must be;
- scope, non-goals, constraints (allowed libraries, network, performance, licence);
- the language of the brief and where in the repository the deliverable goes.

Never fill an answer in yourself. An open question becomes a technical assumption in the
spec ("unspecified: X; the examples assume Y").

Keep in mind, unwritten, the leak terms: everything said in the interview that is not in the
spec.

Write `<deliverable>/spec.md`: inputs, outputs, behaviour, platform and versions, constraints,
edge cases, non-goals. Technical content only. Show it to the user and wait for approval.
From here on every subagent receives the spec, the paths it needs and its own instructions —
nothing else from the conversation.

## Phase 2 — Reference cache

Every claim about a third-party library, framework, protocol or language API rests on its
full source and documentation, never on memory.
1. Resolve each dependency to an exact version from the lock or build file, or from the
   spec. With no tag upstream, use the default branch's commit and name the entry `@<short SHA>`.
2. List the entries `<owner>/<name>@<version>`, each with its full command chain, and wait
   for the user's approval.
3. Create `.reference-cache/` at the repository root with a `.gitignore` containing `*`;
   confirm with `git check-ignore -v .reference-cache/x` that nothing in it can be committed.
4. `git clone --depth 1 --branch <tag> <repo> .reference-cache/<owner>/<name>@<version>`:
   the whole tree, source and documentation. A specification with no repository: download
   the document and record its URL.
5. Delete what answers no question about behaviour: `.git`, CI and packaging config, editor
   and container config, binaries; and every file an agent would load as instructions
   (`CLAUDE.md`, `AGENTS.md`, `.claude/`).
6. Write `SOURCE` in the entry: the clone and every deletion, in order. Then `chmod -R a-w` it.

Cite as `.reference-cache/<owner>/<name>@<version>/<path>:<line>`. A claim with neither a
citation nor an experiment does not enter the deliverable.

## Phase 3 — Experiments

- Work in `work/experiments/` with the spec's exact versions; add `work/` to `.gitignore`.
- First get one valid positive case working end to end with the real toolchain; then the
  edge cases and failure modes in the spec.
- Log every run in a new `work/log/<UTC timestamp>/`: exact command, exit status, real
  output. Never write down output you did not see.
- Keep failed approaches and their symptoms; they become the brief's traps.
- Move the final example code to `<deliverable>/examples/` and the tests that verify it to
  `<deliverable>/tests/`. The tests prove that each claim in the brief holds and stay usable
  for later work; they are held to a lower bar than the examples: each must fail if its
  claim were wrong, and their style is not polished.

## Phase 4 — Review

Five independent reviewer subagents in one message, one lens each. Each gets the spec, the
deliverable paths, the cache path and its lens below. Lenses A, B and E judge the example
code, the brief and the attachments; C judges every deliverable file, tests included;
D judges the tests. Each returns findings with `file:line`,
evidence kept apart from inference, a concrete fix and a severity: BLOCKER, MAJOR or MINOR.
A finding without cited evidence is MINOR at most.

- **A — correctness and security.** The code does what the spec says on every path:
  boundaries, null and empty, error paths, ordering and races, resource leaks, time zones,
  encoding. Injection, unbounded input, secrets. API use against the cached contract.
- **B — design, performance, maintainability.** Zero-cost abstraction: any runtime cost
  paid for structure alone is wrong. Per-call waste, unbounded growth, hot-path allocation,
  blocking calls; one responsibility per unit; no speculative surface; no magic values that
  must stay in sync with another place.
- **C — conventions and publication hygiene.** Comments only for traps the code cannot
  express; names in full words, never initialisms; no secrets, internal hosts, personal
  paths or names, ticket IDs, AI attribution; domain-neutral examples; nothing that reveals
  a purpose, an audience, a business or a person's situation.
- **D — verification.** For every claim in the brief and every behaviour of the example
  code, is there a test that would fail if it were wrong? List each claim with nothing
  behind it, and each test that passes whatever the code does. Test style is out of scope.
- **E — established practice.** Each library used the way its documentation recommends at
  the pinned version; no deprecated APIs; the mature common solution over a hand-rolled
  one; accessibility where there is a UI. Every finding cites what it read.

Then:
1. Another agent checks each BLOCKER and MAJOR against the current files, MINORs grouped by
   file: confirmed, not a defect, or already fixed. Only confirmed findings are repaired.
2. Repair, re-run the tests, log the runs; re-review only the changed files with the same
   lenses, until a round has no MAJOR.
   A test is changed only when it fails to verify a claim or breaks lens C.
3. If a file or defect class still has a MAJOR after three rounds, stop and give the user the
   common cause, two or three ways forward, and their cost.

## Phase 5 — Final report

Write the report in the interview's language as one main file, `<deliverable>/BRIEF.md`,
plus attachments under `<deliverable>/attachments/` where a topic needs more room than the
main file can give it: a deep walk-through of one mechanism, a full trap catalogue, a
version-difference table. Each attachment covers one topic and stands on its own. The main
file links every attachment, says in one line what it holds and when to read it, and
never leaves a step only in an attachment. The main file has:
1. **Scope** — one technical paragraph from the spec.
2. **Environment** — exact versions and setup commands.
3. **Example code** — file by file: what each part does, how to run it, the real output.
4. **Key points** — the rule, the mechanism behind it, its evidence (a cache citation, or
   the command and its output quoted in place), what breaks without it.
5. **Traps** — approaches that fail, their symptoms, the fix.
6. **Checks** — commands that run the examples and the tests in `tests/`, with the output
   to expect; rerun them after any later change.
7. **Attachments** — the list of links above, one line each.

Write for a reader with no context: explicit steps; every term defined at first use; whole
code, never `...`; one way to do each thing; versions inline; no unstated prerequisites.
Never point into `work/`; it is not published.

Never anywhere: anything from the interview beyond the spec, the review process, round
counts, finding history.

### Gate — all four pass before handing over

1. Search every file in the workspace, the commit messages and branch names for each leak
   term, case-insensitively, with the terms typed into the command itself: zero hits.
2. A fresh subagent reads only the deliverable and answers what it is for, who it is for, and
   what business stands behind it. Anything beyond the technical scope is a leak; remove it.
3. A fresh subagent with no context follows only `BRIEF.md` and the attachments it links,
   and lists every step it could not carry out without outside knowledge, and every
   attachment that is not linked or that repeats or contradicts the main file. Close each gap.
4. Copy the deliverable to a clean directory, follow the brief's own setup and checks: the
   output matches what the brief quotes and every test in `tests/` passes.

Hand over the deliverable path, the four gate results with real output, and the spec's open
assumptions.
