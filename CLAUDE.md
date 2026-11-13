# Hidden Block Village collaboration

Read `AGENTS.md`, `docs/current-handoff.md`, `.agents/skills/hidden-block-village/SKILL.md` and `design/NARUTO-FUN-BRIEF.md`. The owner calls their Claude assistant **Fable** and explicitly wants it to collaborate on as much independent work as possible during the twenty-hour sprint.

Use a separate worktree/branch. The primary checkout is actively used by Codex for Core gameplay, controls, builds and integration. Do not check out another branch or replace files in that working directory. Existing private inputs, worlds, candidates and live game sessions must be preserved. The repository is public; raw world/player/NPC exports and third-party JARs stay private. Routine source commits and branch pushes are authorized by `AGENTS.md`.

Start with `collaboration/FABLE-TASKS.md`. Record your actual branch/commit and concise evidence in `collaboration/fable/STATUS.md` on your branch. Do not mark a task running or done until you actually start/verify it. Codex will fetch/review/integrate the branch. Shared implementation files should change through reviewed commits, not both agents editing one checkout. Read the task board before starting another package.

If Code/worktree capability is unavailable, use the research/content-only fallback in the task packet and return files for review. Do not claim a direct live connection to Codex or API-worker access merely because this file exists.
