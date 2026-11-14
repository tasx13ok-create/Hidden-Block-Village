# Ninja Health: server plugin1.0.2

Original Java8 Bukkit plugin for the inspected Magma1.12.2 server and installed
NarutoMod. No client JAR is needed. The original native XP/AI/chakra, other
health modifiers and Core progression are preserved.

Native Ninja XP health bonus is `floor(getBattleXp(player)/175)` health points;
one health point is half a Minecraft heart. NarutoMod already supplies its own
`ninja.maxhealth` XP modifier. This plugin leaves that native modifier intact
and counterbalances it with one stable additive modifier, so the XP component
is the requested total instead of a second stacked progression curve. Other
health bonuses/multipliers and the engine's attribute bounds still apply.
Maximum health grows; the scheduled updater does not repeatedly heal players.

Enemies within24blocks get a ten-segment colored nameplate plus actual current/
maximum HP. Vanilla monsters, the five exact native arena bosses and creatures
actively targeting players are supported. Each update tracks at most256actors.
The original name/visibility is backed up in bounded prefixed scoreboard tags;
restoration checks the last rendered-name hash and preserves external renames.
No armor stands, new entities or forced chunks provide the display. This uses
normal Minecraft nameplates; a real-client graphical pass remains pending.

## Install

1. Stop the server. Remove older `Ninja-Health-*.jar` versions only.
2. Place `Ninja-Health-1.0.2.jar` in the SERVER `plugins/` folder and restart.
3. In game, `/ninjahealth status` shows native Ninja XP, total XP health bonus
   and actual maximum HP. At175XP the bonus is1HP; at350 it is2HP.
4. F7 or `/fieldhud` opens our HUD settings. Turn off Target health to hide its
   old screen panel. Damage Indicators' period-key settings separately control
   its portrait. Colorful Health Bar changes player hearts, not enemy nameplates.

`plugins/Ninja-Health/config.yml` has `xp-health` and `overhead-health` toggles;
operators can use `/ninjahealth reload`. Growth modifiers persist under one
UUID for reconnect/restart deduplication. Before uninstalling, disable XP health
and let affected players join while the plugin is present; disable overhead
health and visit affected loaded areas to restore saved nameplates, then remove
it. Offline profiles and unloaded chunks are not rewritten blindly.

## Actual checks

Runtime1.0.2 SHA256:
`cfb5eead0e217844e5926c8cf47d91dff508de792690f73e3f082c0f7f830af3`.
Actual Java8 compile, archive CRC and every entry hash PASS. The fresh private
53-production-mod Magma fixture passed26real native/CraftPlayer checks with clean
exit0: XP0/174/175/349/350 produced maximum HP20/20/21/21/22, stable UUID retries,
other health modifiers, genuine logout/new-body login, native Gaara and zombie
nameplate data, accepted zombie damage, name/visibility restoration, external
renames and owned-tag cleanup. This is server-side name/watchers/attribute proof;
no owner world, real player, rendered client, shader or hosted acceptance is claimed.
Earlier1.0.0/1.0.1candidates and the first failed native report remain private.

The native proof source under `nativechecks/` is original QA, NEVER runtime
plugin content. Exact private inputs/reports and hashes are recorded in
`release/evidence/ninja-health-102-native.json`.

## Rebuild

```powershell
python tools/ninja_health_plugin/build.py --java8-home <JAVA8_HOME> --magma <PINNED_MAGMA_JAR> --output <NEW_PRIVATE_PATH>/Ninja-Health-1.0.2.jar
```

The builder requires an actualJava8 compiler and the inspected Magma API SHA256
`2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`.
It packages only original plugin classes and two YAML files, refuses an existing
artifact path and retains scratch/failed candidates. Third-party JARs, profiles,
worlds, credentials and decompilations are not redistributed through Git.
