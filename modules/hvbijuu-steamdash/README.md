# Bijuu Steam Dash Punch (`hvbijuu_steamdash` 2.0.0)

A cut-scene dash attack for the owner's Hidden Village Bijuu Armor (`hvbijuu` 0.16.2-rc1, "Bijuu Shinobi Arts 8K v3"). It ships inside that jar as a second `@Mod`. Everything in this directory is original: the code, the eleven synthesized sounds and the ink shader. No hvbijuu class, texture or decompiled body is here, and the owner's jar stays private (see "Build").

## How it plays

To channel it, wear the full Kurama set and carry a crate-issued Kokuo piece anywhere in your inventory, or the reverse. Aim at an enemy within 35 blocks and use **B** ("Steam Dash Punch" in Controls).

| Input | Result |
| --- | --- |
| Tap B | Tier 1 dash (50 resonance) |
| Hold B 0.35 s | Tier 2: 1.25x damage, 1.15x launch, wider tunnel (65 resonance) |
| Hold B 1 s | Tier 3: **pierces every enemy on the line**. Each one is frozen in turn and knocked aside, and the farthest takes the finish. Bores a 5x5 tunnel and makes a bigger crater (80 resonance). While you charge, your armor and tails flare white-hot, steam and gold swirl tighter and the ground cracks under you. |
| B while they fly | **Relay:** blink 3 blocks ahead of the flying body and punch it back the way it came (up to two, 15 resonance each) |
| Sneak + B while they fly, or B after two relays | **Tail pin and spike:** you appear above and behind them, they hang frozen while nine tails spear the ground around them, then you slam them down at 75° into a crater (20 resonance) |
| B on a wall with nothing in sight | **Springboard:** off the wall at 1.4x with a cracked wall and a steam ring (10 resonance) |

### The sequence

| Stage | What happens |
| --- | --- |
| Overheat telegraph | High-velocity steam and smoke vent from the shoulders and tail base, gold dust spirals, the armor glows white-hot, a fire hiss plays at pitch 1.8 and the FOV pulls in. |
| Blink | A golden afterimage stays where you stood, plus a sonic boom, firework blast and sonic ring. |
| Sonic tunnel | 5 blocks/tick through anything soft. Four golden trail clones follow, with steam and flame wind-tunnel rings and a white-and-gold mach cone over your fist. |
| Afterimage barrage | The four clones dart in and punch, one per tick (2 damage each, a jab sound and a flash each). |
| Hit-stop | The real punch lands (16 damage). Both fighters freeze for 3 ticks (4 at tier 3): a white frame, then an ink-silhouette frame, and the Kurama tails whip toward the target. |
| Bullet time | For about a second around the hit, nearby mobs update on only every fourth tick, and arrows and loose objects keep a quarter of each step. A blue-white shell and a time-warp sound mark it. Players are never slowed. |
| Release | Explosion, anvil, door break and a cone blast out of their back. A ground shock ripple rolls out, the sky flashes and the thunder arrives late for far-away listeners. |
| Boil launch | They fly along your look. The steam drives them about 50 blocks (0.93 per tick), smashing everything in their hitbox's path. Rubble flies back at you. |
| Wall splatter | When the boil fades or they hit something they can't break: a blast that doesn't break terrain and a permanent spiderweb of cobblestone and cracked bricks. |
| Molten scars | Afterwards, the rock lining the tunnel and crater glows as magma for 8 s. It vents steam pillars that pop anyone standing on them into the air, then cools to cobblestone and andesite. |

### Cut-scene camera

While you perform the move, a free camera cuts between composed shots:
- charge and telegraph: a low front three-quarter angle
- dash: a dolly zoom behind the shoulder
- barrage and hit-stop: a whip orbit
- launch: a chase camera off to the side
- relay: a quick orbit
- splatter: the wall
- pin: a ground-level shot looking up
- crater: straight down

Letterbox bars frame it and the HUD hides. Positions never pass through walls, and your view comes back when it ends. Everything is optional in `config/hvbijuu_steamdash.cfg`.

### Safety

- **Protection.** Every terrain change asks protection first: Forge `BreakEvent` with the attacker, and the `setBlockState` result, which Dungeon Guard gates.
- **What can't break.** Tile entities, liquids and blocks harder than 3.0 are never broken.
- **Molten scars** only touch natural ground, and they cool on world unload.
- **No drops.** Nothing drops as an item, and the rubble and ripple blocks are client-only.
- **Block budget:** 450 per dash, plus 250 per tier.

## Build

```
./gradlew -p modules/hvbijuu-steamdash -PhvbJar=<owner jar> reobfJar
python3 modules/hvbijuu-steamdash/assemble.py <owner jar> modules/hvbijuu-steamdash/build/libs/hvb-steamdash-private-1.jar <output jar>
```

`assemble.py` copies every original entry byte for byte (the 8K textures are untouched) and refuses to overwrite any. It adds the classes, `sounds.json`, eleven `.ogg` files and the shader, appends the lang lines, and adds a second `mcmod.info` entry.

**Pure rules:**

```
javac -d out src/village/bijuu/DashMath.java tests/village/bijuu/DashChecks.java && java -cp out village.bijuu.DashChecks
```

**Sounds:** `python3 tools/synth.py <out dir>` (needs numpy and ffmpeg).

## Client QA and smooth video

`qa/` is a client-only acceptance mod. It needs `-Dhvb.dashqa=true` and drives the real key binding through four scenes:
1. A tap dash with a bystander mob and an arrow in the bubble, through a mountain and a ridge, ending in a cliff splatter and molten scars.
2. A full overcharge through a line of three.
3. Relay, relay, then the tail pin and spike.
4. The springboard.

It writes `DASH-QA-RESULT.json`.

Each scene runs at 1/5 game speed for recording. The client tick timer and the integrated server tick are slowed together, and the effect clock follows game time, so nothing drifts. A magenta sync frame marks each scene, and every sound the client plays is logged with its game time to `DASH-QA-SOUNDS.json`.

`tools/make_media.py` cuts each scene at its sync frame and speeds it up 5x into a 60 fps clip with about 60 unique frames per second. It mixes the logged sounds into stereo with Minecraft's attenuation and camera-relative panning, then adds a 1/2-speed impact replay and a showreel. In a container with no sound card, start OpenAL with `ALSOFT_DRIVERS=null` so the sound engine still runs.

The dev client uses readable (MCP) names, but hvbijuu's `ReferenceTails` reflects on SRG names. QA therefore runs on a copy made by `qa/qa-devnames.py`; the release jar keeps the SRG names.

## Test world

`qa/src/village/bijuu/ArenaBuilder.java` builds the owner's "Steam Dash Arena" save in three client passes:

| Pass | Property | What it does |
| --- | --- | --- |
| Build | `-Dhvb.buildarena=true` | Creates a deep-stone superflat world. Writes the `arena:*` functions into the save. Builds the hub, signs, pads and reset buttons. Issues the armor through the mod's own `bijuureward` console command, so the ledger serials are real. Equips one set and stores the rest in the hub chest. |
| Finish | `-Dhvb.finisharena=true` | Reopens the world so its functions load, then runs `arena:reset_all` to build the stations and spawn the dummies. |
| Verify | `-Dhvb.testarena=true` | Runs on a copy. Checks the armor and dummies, performs a real dash at the mountain station, and checks that RESET ARENA rebuilds the terrain. |

`qa/package-arena.py` drops the QA-only mods from `level.dat` and removes the test player's files. It then zips the save with a README and an optional rapid-testing config.

Stations, with S, E, W and N measured from the hub:
- **S: mountain run.** A target dummy, mountain, ridge and cliff, plus a pig pen to show bullet time.
- **E: overcharge line.** Three dummies in a row and a wall.
- **W: juggle field.** One dummy in open ground.
- **N: springboard wall.**
