# Core 0.3.12 hand techniques

Integrates Fable / Claude's original procedural hand-technique presentation through commit `e837307e1d40b6a74733d1b303d7f48b8c8bcf0e`. The reviewed reference contact sheet is `release/evidence/vfx-hand-techniques-20261113.png`; it records Fable's rendering fixture, separately from local Windows playtest evidence.

## Playing the six techniques

Select the issued technique item and right-click normally. The hand effect lasts up to ten seconds. Left-click an eligible enemy for the contact effect. Rasengan and Chidori consume their held mode on an accepted strike. F5 changes between first-person, rear and front views. Sneak + right-click is practice, rather than activation. Cooldowns and chakra still apply; allow a mode to expire before selecting the next.

| Stable lesson key | Display name | Presentation / existing gameplay adaptation |
| --- | --- | --- |
| spiral_palm | Rasengan | Rotating blue sphere and bands; one accepted contact with knockback |
| storm_lance | Chidori | Braced lightning hand; one accepted contact and brief slowing |
| cinder_edge | Cinder Edge | Fire hand and contact burning |
| gale_edge | Gale Edge | Wind crescents and contact push |
| molten_mantle | Molten Mantle | Two lava hands and the existing defensive mode |
| storm_stride | Storm Stride | Body lightning and the existing movement/defensive mode |

Rasengan and Chidori are display-name changes: existing registry/save/receipt keys remain unchanged. These are gameplay adaptations, not a claim of every canonical behavior. In particular the hand-mode Storm Stride remains separate from the more involved Core 0.3.11 Lightning Chakra Armor kit.

Normal players acquire and learn the owned lessons, then practice their reusable techniques. The private local preview deliberately starts a synthetic level-eight character with these six lessons mastered and valid server-issued items, so the owner can inspect the effects immediately. The setup is outside the production JAR and does not alter ordinary progression or original worlds.

## Integration requirements

The carrier is non-persistent cosmetic state. The server remains responsible for mode duration, resource/cooldown settlement and accepted contact. Cosmetic spawn/sound failures must not grant a free active mode. A cancelled or zero-loss contact must not consume Rasengan/Chidori or apply secondary effects. Client lookups must not retain disconnected worlds; renderer state must remain consistent after its custom pass.

Version-bound build, native and Windows graphical results are recorded in the accompanying verification receipt when complete. Compiling source and Fable's contact sheet alone do not establish local graphical acceptance, the full 49-mod roster, a migrated production world or hosted deployment.

Observed verification: all 11 source areas / 36 artifacts, 2,058 native assertions (including 354 new hand checks), 1,002 presentation math checks and 16 orchestration tests (one skip) pass. The exact 12-JAR OptiFine/JEI client joined its nine-JAR loopback server, and six owned techniques / two practice husks were issued successfully. Windows manual VFX acceptance remains unobserved because desktop control was stopped. See `release/evidence/core0312-verification.json`.
