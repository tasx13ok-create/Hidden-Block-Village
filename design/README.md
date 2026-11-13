# Hidden Block Village design workspace

The owner requests a deeply immersive Naruto-inspired shinobi game that still plays as Minecraft, with original implementations of the supplied mod pack's functionality, substantial enhancements, and much more ambitious dungeons. This is a production specification and execution queue, not a claim that the requested game is complete.

Read [the updated Naruto identity and fun brief](NARUTO-FUN-BRIEF.md) first for combat, equipment, world reactions or progression decisions. It updates the earlier pacing priorities and connects the research to concrete implementation criteria.

Read in this order:

1. [Master plan](MASTER-PLAN.md): experience, scope, architecture, content delivery and acceptance.
2. [Systems specification](SYSTEMS-SPEC.md): combat, character progression, clans, modes, companions, economy, NPCs, UI and multiplayer contracts.
3. [Mod rebuild matrix](MOD-REBUILD-MATRIX.md): every one of the 49 supplied JARs and the replacement/compatibility disposition.
4. [Dungeon atlas](DUNGEON-ATLAS.md): five authored campaigns, architecture, encounters, lighting, terrain and production checks.
5. [Implementation prompt](IMPLEMENTATION-PROMPT.md): reusable instructions for continuing the actual build.
6. [Reference research](REFERENCE-RESEARCH.md): creator material, evidence limits and footage-review queue.
7. [Academy campaign](ACADEMY-CAMPAIGN.md): the first ten-mission normal-player implementation route.

[backlog.json](backlog.json) gives each work package an owner, prerequisite, delivery artifact and acceptance obligation. [reference-mod-inventory.json](reference-mod-inventory.json) binds this plan to the actual supplied `mods.zip`. Use `python tools/validate_design.py` to check inventory coverage and task dependencies. No third-party JARs or extracted artwork are included here.

The versioned project skill is [hidden-block-village](../.agents/skills/hidden-block-village/SKILL.md). A local copy can be installed in the user's Codex skills folder. The repository remains authoritative across PCs; read `AGENTS.md` and `docs/current-handoff.md` at the start of work.
