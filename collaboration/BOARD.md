# Twenty-hour sprint work board

Start: 2026-11-13 17:32:41 UTC. Deadline: 2026-11-14 13:32:41 UTC. Source of truth: GitHub branch commits plus exact-hash build evidence. This board defines assignments, not claims that external workers have started.

| Worker | Current responsibility | State | File boundary |
| --- | --- | --- | --- |
| Codex | Core combat/power kits, weapon/input integration, runtime authority, native acceptance and final merge/release | Core 0.3.11 source/1,704 native assertions pass; exact OptiFine/JEI client joined with visible enrollment, optional kit and codex activation. Dungeon runtime and copied-world acceptance continue | `modules/vale-orders`, Controls/HUD integration, shared build/release tools and main handoff |
| Fable (owner's Claude assistant) | v17 dungeon tooling/integration, authored NPC/mission content and environmental detail | Observed pushed branch `claude/upbeat-ride-nyxq23` at `1450679`; 37 world-tool tests and 12 content tests pass on Windows, five actual v17 audits ran; runtime adapter remains open | First package: `tools/world_merge/`, `tests/test_world_merge.py`, `content/expeditions/`, `tests/test_validate_expeditions.py`, `tools/validate_expeditions.py`, `docs/dungeons/`, `collaboration/fable/`; propose shared-module changes separately |
| Optional OpenAI API worker | Focused public-source research, adversarial code reviews, regression scenarios and small patch proposals | Deferred at owner request; $40 cap authorized, no key configured and no paid requests | Review outputs only, under ignored local queue; reviewed changes applied by Codex |

Do not let all three workers independently redesign the same subsystem. Package outputs include changed files, base/branch/commit, tests actually run, observed defects and remaining work. Keep original Naruto identity and fast enjoyable play central; neither technical audits nor more prose substitute for a playable result.

Integration: fetch the worker branch, inspect its diff and evidence, preserve concurrent work, integrate a coherent package, run checks appropriate to the combined result, then publish. Cross-module/API changes need a brief contract proposal before incompatible implementations diverge. A static file is not an automatic messaging channel; keep statuses honest.
