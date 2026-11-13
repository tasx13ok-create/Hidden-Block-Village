# Combat hotbar checkpoint

Rack 0.1.3 / HUD 0.15.0 is implemented and verified: 11 build areas, 36 artifacts, 2,392 native assertions and 13 pure hold checks. See docs/combat-hotbar-013.md and release/evidence/combat-hotbar-013-verification.json. Both retained banks, server cursor operations, six real hand techniques and crafted garment admission are covered. Final source snapshot 940775d1cbdbc2de4be0ef82d4c9d1a31e25a363dc44c0cf7a581893c719b6e4.

The owner's separate preview on 25584 remains open on the previous 2,391-check candidate. Physical F timing and the entire graphical drag flow remain manual acceptance. Do not replace live JARs.

This worker owns Rack/HUD, CombatHotbarNativeChecks, CombatAbilityNativeChecks, RackNativeChecks038 integration and the small ScrollEffects.mode correction. Other Core/Residents/gate/tooling work stays excluded from the checkpoint. The gate private fixture's step helper was corrected to drain its own zero-health corpses as well as isDead entities; its owner should retain this test-only change. RecoveryNativeChecks currently calls CombatHotbarNativeChecks directly AND through RackNativeChecks038; remove the direct duplicate when integrating the later Core work.

Source checkpoint 850ae32c23cd7da3055199cc3e84de1eb4788902 is saved on GitHub branch codex/combat-hotbar-equipment-20261113. A fresh Git fetch verified its exact tree 4568c4d707936feda8187aef30efdef2df0afbd8. The separate publication index preserved the shared checkout and its staged work. The complete Sonnet armor/weapon addendum is in collaboration/sonnet-art (06/07/08 plus the instruction and equipment JSON). No private bytecode/source payloads are published.
