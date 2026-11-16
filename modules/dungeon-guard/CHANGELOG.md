# Dungeon Guard changes

## 1.2.0

Simpler setup with the same saved-world format and protection rules.

- Added `/dungeon` as an alias and a clickable chat menu with setup guidance.
- Added a selection wand made from a marked vanilla stick: left-click the
  first corner and right-click the opposite corner without damaging either.
- Added `/dungeon setspawn` for the existing spawn Waystone.
- Use a server-side ray trace for look-at Waystone binding, including the
  existing `bindspawn` command, so it works on a dedicated server.
- Explicitly allow ordinary players to reach the public menu, spawn and
  information commands; all setup actions retain separate operator checks.
- Added `/dungeon create <element> [display name...]` to capture the selected
  clean dungeon and set its banner in one step. IDs are generated automatically
  and never reuse retained baseline files.
- Save the region and its display label together only after the clean baseline
  is durably saved and verified. Tell the owner when the dungeon is Ready.
- Added `/dungeon boss <boss name...>` to label the selected boss chamber and
  find its enclosing saved dungeon automatically.
- Replaced raw internal states in normal status output with Ready, In use,
  Saving original and Rebuilding. Existing `/dg` commands remain supported.
- Clear selections on logout, dimension change and server restart. Setup still
  requires a real connected operator; a wand grants no editing permission.
- Insert the wand only into an empty inventory slot. A full Creative inventory
  receives a clear refusal instead of a false success message.
- Preserve existing captures, boss labels, spawn binding and the transparent
  fox-shinobi banner. No recapture is needed for this upgrade.

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
