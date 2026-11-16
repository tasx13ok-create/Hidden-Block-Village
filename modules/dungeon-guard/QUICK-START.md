# Dungeon Guard: easy setup

**Your main command is `/dungeon`.** It opens a clickable chat menu with setup, spawn and dungeon information. Open chat with **T** to click its buttons. `/dungeon setup` shows the guided setup steps.

## 1. Install one JAR

Stop the server and back up your world. Put **Dungeon-Guard-1.12.2-1.2.0.jar** in the server's `mods` folder and remove any older Dungeon Guard JAR, then start the server.

Keep your existing world and its `dungeon-guard` folder. Saved dungeons, labels, spawn settings and clean copies carry over. **You do not need to capture existing dungeons again.**

Players who want the illustrated dungeon/boss banner put that **same JAR** in their Minecraft 1.12.2 Forge client `mods` folder. Players without it can still connect and receive plain area titles.

Join as an operator to do the setup below.

## 2. Choose the spawn Waystone

Stand near your existing spawn Waystone in the Overworld, look directly at it, and run:

```text
/dungeon setspawn
```

Leave a safe floor and clear standing space beside it. This becomes the main world spawn and the destination for `/dungeon spawn`. Existing personal beds still work as personal respawn points.

If your Waystone is already bound from an earlier version, keep that setting and continue.

## 3. Mark the whole dungeon

Have the dungeon fully built and undamaged first. If you are using a schematic, paste its clean version before enabling protection. The saved clean copy comes from the blocks already in your world.

Get your selection stick:

```text
/dungeon wand
```

Hold the supplied stick, then:

1. **Left-click a low corner block** of the dungeon.
2. **Right-click the diagonally opposite high corner block.**

Those two corners make a box. Include the floors, walls, roof and air space where the fight should happen. The marked stick selects blocks safely without breaking them or opening containers.

Use `/dungeon selection` to check your selection. Use `/dungeon clear` to start over. Both corners must be in the same dimension.

## 4. Save it and choose its element

For a fire dungeon, run:

```text
/dungeon create fire Fire Dungeon
```

`fire` is the element; `Fire Dungeon` is the name players see. You can use spaces in the name. You can also type just `/dungeon create fire` to use the default name.

Elements are **fire, water, wind, earth, lightning, sand, ice**, or **general** for an area without an elemental theme. The setup menu helps you choose.

**Keep everyone out and wait for the Ready message.** The mod saves and checks the clean copy before opening the dungeon. Use `/dungeon` to check progress while it works.

Repeat these steps for each new dungeon. Each one needs its own selected area.

## 5. Add a boss room, if you want one

Use the same stick to select the boss chamber's two corners. The entire chamber must be inside a saved dungeon.

Type `/dungeon boss`, add a space, then type the actual boss's name. For example, if you want the banner to say **Ember Guardian**:

```text
/dungeon boss Ember Guardian
```

The mod finds the containing dungeon and adds the boss label. Select that exact chamber again and run the command with a different name to rename its boss label.

This sets the room's banner. Your existing Naruto, NPC or dungeon system handles spawning the actual boss.

## After setup

Players can fight inside the dungeon. After the **last player leaves**, the mod waits briefly and rebuilds the saved clean version. Walking out, teleporting away and disconnecting all count. Another player still inside keeps the fight active.

Outside terrain remains protected. Operators can mine and build normally. The clean copy includes chest contents, so saved loot returns after a reset.

For everyday use:

- **`/dungeon`** — open the menu and check dungeon progress.
- **`/dungeon spawn`** — return to the spawn Waystone.
- **`/dghud off` / `/dghud on`** — hide or show your illustrated banner.

The old `/dg` commands still work. Detailed settings, compatibility limits and maintenance instructions are in `README.md`.
