# Reference cache

A shared, greppable store holding the **full documentation and source trees** of pinned
third-party dependencies. Any claim about a third-party library, framework, protocol, or
language API must be checked against it, never against memory. Never cache a stray single
page — the whole value is being able to grep the tree. The one exception is a specification
with no upstream repository at all; see `Adding entries`.

## Where it lives

`.reference-cache/` at the repo root: a symlink to a shared store.

## Establishing it — try in this order

1. **`repo-init`, if available** — it may not be on PATH; ask the user if it is not.
   Idempotent, and does the whole job: resolves where the shared store lives, symlinks
   `.reference-cache` at the repo root to it, and appends `/.reference-cache` to
   `.git/info/exclude`.
2. **Otherwise ask whether a shared store already exists.** If it does, reproduce that layout
   by hand: symlink to it, then append `/.reference-cache` to `.git/info/exclude`.
3. **If there is none, maintain a repo-local one** — a real directory at the repo root with a
   `.gitignore` containing `*` inside it.

The ignore mechanics differ between those two shapes, and getting it wrong leaks the cache
into `git status`. Inside a **shared store** a `.gitignore` is inert — git never walks a path
outside the worktree — so only `info/exclude` works there. Inside a **repo-local** directory
the `.gitignore` is the right tool.

`info/exclude` is neither committed nor carried by a clone, so every fresh clone needs step 1
or 2 re-run. A linked worktree does not: `info` is a common-dir path (only
`info/sparse-checkout` is per-worktree), so the exclude entry is already shared — there, only
the symlink needs recreating.

## Layout

One entry per `<source>/<name>@<version>/`. `<source>` is the GitHub owner for a project, or
the standards body for a specification, so two projects sharing a name never collide. Each
entry holds a `SOURCE` file recording the full command chain that produced the tree — the
clone, then every deletion — so a later session can reproduce it and tell a fetched reference
from a guess.

## Adding entries

The store is read-only. Unlock only what you must write to — a single entry, or the one or two
directory levels you have to create in (`chmod u+w <store>`, no `-R`) — never `chmod -R` the
whole store, which would reset entries you are not touching. Locking is the last step.

1. Resolve the dependency to an EXACT version from the project's lock/build files
   (`go.mod`/`go.sum`, `package-lock.json`, `requirements.txt`, `pom.xml`, `Cargo.lock`, …).
   Unversioned guesses are worse than no cache. A reference that is in no lockfile and whose
   upstream publishes no tag still needs pinning: clone the default branch, resolve `HEAD` to a
   commit, and name the entry `@<short SHA>`. Never name an entry after a moving ref.
2. **List what you intend to cache (`<source>/<name>@<version>` → repo + the full command
   chain) and wait for approval before writing anything to disk.** Never write unapproved
   entries: the store is shared by every project on the machine.
3. Clone the WHOLE tree for that exact version — source AND documentation, so either can be
   grepped: `git clone --depth 1 --branch <tag> <repo>`. Do not hand-pick subtrees. Stay within
   the repo, the cache store, and the network — never go rummaging through machine-wide
   toolchain caches (`~/.m2`, `~/.gradle`, `~/.cargo`, …).

   **Exception — a specification with no upstream repository.** Some standards predate
   repository-based drafting (RFC 6750, published 2012). When no upstream repo exists,
   download the document itself and record the exact URL in `SOURCE` together with the fact
   that no repository was available. If a repository does exist — most modern RFCs are
   drafted in one — clone it: a downloaded copy is not an acceptable substitute.
4. **Delete what answers no question about the library's behaviour** — CI configuration,
   packaging scripts, wrapper binaries, editor and container config, `.git`. The entry is a
   reference to read, not a working checkout. Judge each tree on its own; this is a principle,
   not a checklist.

   Binary files go by default — images, recordings, prebuilt archives, fonts. Nothing greps
   them and no reviewer may execute them, so keeping one is the rare case: justify it in
   `SOURCE` or delete it.

   One category is not a judgment call: anything an agent would load as instructions or
   execute merely by reading the tree — `CLAUDE.md`, `AGENTS.md`, `.claude/settings.json` —
   goes, however much it looks like content. A file that is only *quoted* as an example of
   such a thing stays; the test is whether the harness would act on it in place.
5. **Write `SOURCE`** — the full command chain: the clone, then every deletion in order, so a
   later session can reproduce the tree exactly.
6. **Lock last.** `chmod -R a-w <store>/<source>/<name>@<version>`, then `chmod a-w` each level
   you unlocked. Never leave anything writable.

## Using it

Search the store first; go to the web only for what it does not cover. A missing entry is a
reason to propose a new one — and when you fall back on memory anyway, say so in place: an
unmarked recollection is indistinguishable from something you read.
