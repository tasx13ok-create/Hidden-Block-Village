# Shinobi Immersion 0.1.0

This is a standalone add-on. It depends on nothing; when the Naruto Core (`valeorders`) is present, it bridges to Core's `MoveCost` by reflection, so movement spends real NarutoMod chakra through the verified escrow. Without Core, movement costs a little hunger instead.

| Feature | Side | Config (`shinobi-immersion.cfg`) |
|---|---|---|
| **Water-walking.** Stand and run on water while not sneaking (sneak to dive). Costs chakra each second, and you sink when it runs out. Ripples and soft splashes. | both, paid on the server | `waterWalk`, `waterWalkCost` |
| **Wall-running.** Sprint-jump into a wall and hold jump to run up it for under a second, once per jump. Dust and scuff sounds. | both, paid on the server | `wallRun`, `wallRunCost` |
| **Underwater sound.** World sounds are muffled (quieter and lower) while your head is underwater. | client | `underwaterSound` |
| **Cave echo.** A quiet delayed echo plays in enclosed spaces. Music, UI and weather are untouched, and echoes never echo. | client | `caveEcho` |
| **Compass bar.** Cardinal directions, 15° ticks, a spawn or bed marker and nearby players (128 blocks) with distance. Hidden in menus, with F1 and on the debug screen. | client | `compassBar` |

**Checks:** 38 pure checks (`gradlew check`). The client run loaded it with Core 0.3.16 and the armor mod with no exceptions, wrote the config and showed the compass rendering.

**Not yet verified in game:** surface water-walking (the probe pool was deep) and wall-running. Both need a hands-on test.
