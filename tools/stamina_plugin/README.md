# Naruto Stamina1.0.0

Original Bukkit plugin for the exact Magma1.12.2 runtime. It maintains online, current, living Survival/Adventure players at food20, saturation20 and exhaustion0 every20ticks. The HIGHEST food event listener cancels decreases only. Creative, Spectator, offline and stale player objects are excluded. Disable cancels the task and removes its listeners. `enabled` defaults to true in the generated plugin configuration.

This changes food stamina. It calls no health, damage, native chakra, XP or permission APIs. Existing server-owned Dodge cost0 is a separate Core configuration setting; it is not a chakra refill or cooldown change.

Build with an actual Java8 JDK and the separately supplied, unchanged Magma JAR:

```text
python tools/stamina_plugin/build.py --java8-home <jdk8> --magma <Magma-1.12.2-8cc35ed-server.jar> --output <new-output>/Naruto-Stamina-1.0.0.jar
```

The builder pins the exact compile-input SHA256, refuses output overwrites and verifies Java8 bytecode, CRC and the three-entry original-class/resource allowlist. It bundles no Magma, Minecraft or upstream plugin classes. It does not install or start a server. Place the built JAR in the server plugins folder for the next server start.

The accepted candidate is2341bytes, SHA256 `f0b29317497180285166bb7a4e2a177c736d96c981dcf3430cab80dc9eb756db`. A fresh isolated Magma fixture passed22actual CraftPlayer assertions: exact20tick recurrence, food/saturation/exhaustion restoration, mode/session exclusions, event cancellation rules and disable cleanup. Its process exited0; production mod hashes and the owner's live session were unchanged. The native non-chakra-mutation observation used a fresh non-ninja account at BXP0/chakra0/max0; it is not a test of a nonzero initialized pool. Full-game and hosted acceptance are separate.
