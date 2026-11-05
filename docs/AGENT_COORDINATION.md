# Shared chat coordination

Both chats must use **this same checkout** for live coordination. This is a local file board, not a connection between ChatGPT conversations. A chat cannot see another chat's private conversation, and different workspaces do not share these files automatically. Do not run subagents or computer-use tools; the owner opens the separate chats.

Before planning or editing, run:

```sh
node scripts/agent-board.cjs status
```

Use a stable name per chat, such as `noble-main` and `noble-fast`. Claim every file/directory you will edit **before** editing. Prefer narrow scopes. Directory claims cover descendants. Overlapping claims are rejected, including your own existing claims. Claims are advisory, not filesystem enforcement; editors and Git do not automatically respect them.

```sh
node scripts/agent-board.cjs claim --agent noble-fast --scope path/to/file.ts --scope path/to/tests.ts --message "Task and expected result"
node scripts/agent-board.cjs say --agent noble-fast --message "Progress, blockers, or a request to the other chat"
node scripts/agent-board.cjs release --agent noble-fast --id CLAIM_ID --message "Done: files, checks, limitations, and next step"
```

Read status before each new scope, after a context resume, before shared validation/builds, and before publishing a completion. `say` records a message; it does not wake or notify the other chat. Poll at useful milestones. Never overwrite a claimed file. Choose another task or post a request and wait for the owning chat to release it. Do not infer approval from silence. Do not release another agent's claim. Ownership names prevent accidental releases, not malicious impersonation.

Claims do not expire automatically: an idle chat can still have uncommitted edits. If a chat disappears, ask the owner before taking over. A stopped writer's `.context/agent-board/write.lock` can block the board; verify that process has stopped before removing only the empty lock directory. Preserve `state.json`. Writes serialize through the lock; state replaces atomically. `status` reads canonical state; `BOARD.md` is a convenience snapshot.

Before `npm ci`, builds or tests that write shared generated files, claim the dependency/cache/output directories involved or agree on one validation owner through the board. Do not reset, clean, checkout, commit or stage another chat's changes. Existing dirty files do not imply ownership. Read existing diffs, preserve them, and describe only your own changes. Neither chat should package an APK per feature; preserve the owner's batch-build instruction.

Store brief non-sensitive messages only: task, files, test results, pending work. No credentials, tokens, environment values, personal media, or private queries. Runtime state and readable board live in ignored `.context/agent-board/`; no network service or account setup is needed. Keep lasting verified findings in project docs, with ownership claimed first.

Paste this into the second chat:

> Work in /workspaces/Honorable-mobile-app. Read AGENTS.md and docs/AGENT_COORDINATION.md. Run `node scripts/agent-board.cjs status` first. Identify as `noble-fast`, claim narrow file scopes before editing, and avoid every active claim. Post progress/results using `say`, then release claims with checks and limitations. Preserve existing dirty work; work solo, with no computer-use tools. Choose a separate task from noble-main. This board works only if both chats use the same checkout.

## Browser group chat

Open the private workspace page at https://fluffy-spork-vpqgp7wgp9jwc69x6-4180.app.github.dev . The URL can change after workspace recreation. It reads the same canonical board every two seconds while visible. Owner messages are saved through the existing locked CLI. It shows active claims, recent agent updates and handoffs; on phones use the active-tasks button. “Invite an agent” includes a full copyable prompt that explains coordination and asks the second agent to begin a separate, unclaimed app task. The page does not invoke a model or wake an agent; agents must check the board themselves. Ordinary ChatGPT conversations without access to this checkout cannot participate automatically.

Run `node tools/agent-chat/server.cjs` or workspace task **Noble: team chat** to restart it on port4180. Keep the forwarded port **private**, since the page exposes project progress and can write owner messages. Do not publish the board or make the port public. Private forwarding uses GitHub workspace access; there is no independent chat-account login or cross-workspace synchronization. Page messages never accept an agent identity from the browser, only owner. Agent names/claims remain advisory, not authentication.

Verification: `node --test tools/agent-chat/server.test.cjs` (isolated board), `node tools/agent-chat/browser.test.cjs` (isolated board plus read-only live capture). Browser fixtures never write to the real board. API rejects cross-origin/invalid/oversized messages and serves only three explicit static assets plus the board API. All message text renders as text, not HTML. Native app media/queries are not read. No external assets/services are used.
