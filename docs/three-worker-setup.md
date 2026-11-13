# Codex, Fable and an optional API reviewer

The owner requested three cooperating workers. Current assignments are in [the board](../collaboration/BOARD.md). Existing working code, private inputs and live games remain protected by `AGENTS.md`.

## Fable in Claude Desktop

Open Claude Desktop's **Code** tab, select the `Hidden-Block-Village` Git checkout, and enable **Worktree before starting**. This gives Fable an isolated working copy; the app documents that worktree mode is off by default. See [Anthropic's current desktop instructions](https://code.claude.com/docs/en/desktop#run-a-session-in-its-own-worktree). The same isolation principle is described in [OpenAI's worktree documentation](https://learn.chatgpt.com/docs/environments/git-worktrees).

Paste this task:

> You are Fable, working with Codex on Hidden Block Village. Read CLAUDE.md, AGENTS.md, docs/current-handoff.md, collaboration/BOARD.md and collaboration/FABLE-TASKS.md. Work on your own branch/worktree. Start implementing the v17 dungeon audit/import package, then continue independent NPC/mission/environment content. Preserve original inputs and live games. Do not edit Codex's active Core gameplay files. Build, test and push a reviewable branch checkpoint; report branch/commit and actual evidence in collaboration/fable/STATUS.md. Do not stop at another plan.

Give that session folder access to the original v17 inputs and the recovered world when needed. The large verified recovery copy is under `D:/HiddenBlockVillage-Sprint-20261113/recovered-world`; check its receipt before copying it into another candidate. Raw worlds/NPC/player files do not go to GitHub. If your Claude app lacks Code, a Chat/Cowork session can prepare original content or a patch in a separate folder for review; it should not concurrently edit the active checkout.

This packet does not create a direct Claudeâ€“Codex chat connection. Fable publishes branch commits and status; Codex reviews and integrates them. The owner can relay its branch name. No external messages have been sent by this setup.

## Optional OpenAI API worker

`tools/api_review_worker.py` is a bounded reviewer, not an unreviewed autonomous shell agent. It reads explicitly selected tracked public text, makes no tool calls, writes proposals only under ignored `local/api-worker/`, and never applies its own output. The default is a dry run with **no network**. An API key in the local process environment, explicit `--execute` and an authorized total cap are all required for generation. No key is stored in this repository.

Example dry run, using a proposed $15 cap:

```powershell
python tools/api_review_worker.py --task collaboration/API-REVIEW-TASK.md --file modules/vale-orders/src/main/java/village/vale/AcademyCampaign.java --budget-usd 15
```

Only after the owner authorizes that worker cap and configures `OPENAI_API_KEY` locally, add `--execute` to make a paid call. Never paste the API key into a chat or committed file. The owner has authorized a **$40 total worker cap**, then explicitly deferred API setup. The worker remains off: no key is configured and no paid request has run. Do not restart API setup until the owner resumes it. The remaining account balance is not additional spending authorization.

The runner uses the official [Responses API](https://developers.openai.com/api/reference/python/resources/responses/methods/create) and [input-token counting endpoint](https://developers.openai.com/api/docs/guides/token-counting). A durable worst-case reservation and single-worker lock prevent this runner's concurrent requests from exceeding its configured authorization. Failed/ambiguous calls retain their reservation and are not automatically retried. Usage is logged; conservative debits are not an invoice or an account-wide cap. Other applications' spending is outside this ledger.

The initial reviewed model choices are `gpt-6.1-sol` and `gpt-6.1-terra`, standard service, capped short review contexts, and bounded output. Conservative input rates reserve for the published higher cache-write rate, without requesting caching. Rates were checked on 13 November 2026; recheck [official pricing](https://developers.openai.com/api/docs/pricing) before changing models/tier or relying on a later price. No built-in tools, searches or other billable services are enabled by this runner.
