OPTIONAL ORIGINAL RESIDENTS SERVER BRIDGE0.1.0

Server only; never install on clients. Requires Residents0.2.x. Does not replace,
modify or duplicate Root's separate Vale Server Bridge0.2.0.

Magma's Bukkit wrapper can attach an extra permission gate to a level0 Forge
command. This small optional companion adjusts only the residentchoose wrappers
after verifying the registered native command is our ResidentChooseCommand.
It changes no permission-node defaults and does not touch valeresidents authoring,
Core vale administration, operators, wildcard permissions or third-party commands.
Dialogue's player/actor/token/time/range/action checks and Core reward/cost gates
still run after this public entry. Pure Forge requires no companion.

No Bukkit, Minecraft, Noppes or other upstream implementation/assets are bundled.
The optional Bukkit API is accessed reflectively only at server startup; this
original code is covered by LICENSE. Exact Magma/ordinary-player dispatch and
restart scope is recorded in the joint verification, not inferred from metadata.

Build: Gradle9.5/RFG2.0.3/Java8, -PresidentApiJar=<Residents0.2 MCP dev classes/JAR>
and reobfJar. The compile-only resident API must not be bundled in this companion.
