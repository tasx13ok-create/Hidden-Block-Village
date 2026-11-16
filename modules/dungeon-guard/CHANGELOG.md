# Dungeon Guard changes

## 1.1.1

Compatibility and HUD corrections following the 1.1.0 release. Exact completed
checks and artifact hashes are recorded in the accompanying verification receipt.

- Prevent Kamui's block procedure from creating or transferring block drops
  before the protection decision. Protected blocks stay intact; permitted arena
  destruction follows the configured block-loot policy.
- Reserve the actual rendered boss health-bar stack before placing the area
  banner, so the scroll and fox no longer cover those bars.
- Read available public Field HUD layout bounds and use a compact text card
  when the artwork cannot fit without overlapping known reserved HUD areas.
- Keep space for Shinobi Immersion's top compass strip when that mod is present.
- Apply bounded OP edit checks to the verified Magma vanilla-command wrapper,
  including container preflight. A present wrapper without the verified hook
  stops startup. Exact-bytecode tests are separate from live Magma acceptance.
- Preserve existing dungeon baselines, labels, operator restrictions and the
  spawn binding. This update does not require recapturing existing dungeons.

## 1.1.0

- Added the transparent fox-shinobi area banner with server-configured dungeon
  elements, dungeon titles, boss areas and boss names.
- Added optional client rendering and `/dghud` preferences. Clients without the
  addon receive ordinary Minecraft entry titles.
- Replaced the former global building window with actor-scoped OP building and
  native `/setblock`, `/fill` and `/clone` permission checks.
- Verified the production JAR with 171 transformer assertions and 219 native
  assertions on standalone Forge 14.23.5.2859 / Java 8.

## 1.0.0

- Added protected world terrain, occupied destructible dungeon regions,
  bounded clean-baseline capture/restoration and interrupted-reset recovery.
- Added last-player exit detection, dimension/teleport/logout handling and
  spawn binding to an existing Overworld Waystone.
