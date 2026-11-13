# Standing project instructions

## GitHub is the shared source of truth

The owner explicitly requests that work on this project always be saved into this GitHub repository so it can be continued across PCs. This preference applies to future sessions on any computer.

- Work in a checkout of `tasx13ok-create/Hidden-Block-Village`. At session start, read these instructions and the current project status, inspect local changes, and fetch remote updates before editing. Integrate updates without discarding local work or rewriting shared history.
- Save original source, build and release tools, tests, reviewed licensing/provenance records, and project documentation in this repository.
- Commit and push meaningful completed checkpoints and an end-of-session handoff. The owner's standing instruction authorizes routine commits and pushes for this project; do not require a repeated request merely to sync authorized work. Follow any applicable branch protection and review requirements.
- Record what changed, checks actually run and their results, unresolved blockers, and the exact next steps in `docs/current-handoff.md`. Keep it current so another PC or a fresh chat can resume without the original conversation. Distinguish observed results from historical records and unverified claims.
- Verify the pushed commit is on GitHub before saying work is saved across PCs. If pushing is blocked, retain local work and report the unsynced files/commits and blocker clearly.
- Do not rely on chat history or local-only memory as the sole record of decisions or progress. Do not claim this file automatically transfers complete chats or unpushed PC files.

## Preserve private inputs and original work

This repository is public. Never commit credentials, access tokens, authentication profiles, raw chat exports, private player data, world saves, large runtime archives, reference decompilations, or third-party artwork/distributions without reviewed permission. Follow the repository's existing distribution boundaries and ignore rules. Record safe metadata and references for private inputs instead.

Preserve original saved worlds, source archives, rollback files, and incomplete candidates. Track licensing and provenance accurately; do not assume a source rewrite or component hash alone establishes redistribution permission. Do not label a release deployment-ready until its version-specific acceptance checks pass.

## Current game direction and execution plan

The owner requests an immersive Naruto-inspired shinobi game inside Minecraft, original functional replacements/enhancements for the supplied 49-mod pack, and five substantially enhanced dungeon campaigns. Preserve Minecraft building, movement and tangible world play. The maintained entry point is `design/README.md`; the full continuing brief is `design/IMPLEMENTATION-PROMPT.md`. Use the project skill at `.agents/skills/hidden-block-village/SKILL.md` for broad project continuation and read only the relevant detailed specifications for a narrow task.

Keep reference behavior, intended enhancements, implemented behavior and verified results distinct. Every supplied JAR has an exact identity and disposition in the design ledger. Do not silently declare full parity from item/profile counts or treat a planned work package as delivered. Validate changed reference mappings/backlog with `python tools/validate_design.py`.

Preserve Mist's 200x200 real-water layout; Earth/Leaf, Cloud and Sand retain fractured torn-ground bases; Fire/Nether is excluded from that terrain treatment and has a larger proposed citadel. Recover/diff later v17 work before replacing it. Keep the owner's active inspection session available while independent development continues.
