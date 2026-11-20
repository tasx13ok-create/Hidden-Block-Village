# Bijuu armor and native Naruto arts

Wear all four server-issued pieces of one beast. An operator can issue a set with `/bijuureward <player> kurama set`, substituting any beast registry name. The supplied pack profile binds **Activate Bijuu Armor** to **G**.

The active costs 40 armor resonance and 5% of maximum AHZNB chakra (minimum 10). The armor retains its 30-second default cooldown and Matatabi's combat requirement. The optional provider adapter reads the server ninja account directly. The native chakra payment needs a positive remaining balance. A denied payment leaves resonance and the active cooldown untouched.

Landing player-attributed ninjutsu or senjutsu damage, or directly striking with a supported Naruto sword, grants up to 8 resonance and the effect below. The cap is 100. The eight-second interval belongs to the wearer across all targets and sets. Native jutsu retain their original resource costs, XP requirements, cast controls and cooldowns. These short effects are pack-specific adaptations.

| Set | On a qualifying hit | Duration |
| --- | --- | --- |
| Shukaku | Sand Guard grants Absorption I | 3 s |
| Matatabi | Flame Step grants Speed II | 2.5 s |
| Isobu | Shell Guard grants Resistance I | 3 s |
| Son Goku | Lava Mantle grants Fire Resistance I | 5 s |
| Kokuo | Boil Rush grants Speed III | 1.75 s |
| Saiken | Slime Bind applies Slowness II to the target | 2.5 s |
| Chomei | Wing Lift grants Jump Boost II | 3.5 s |
| Gyuki | Ink Veil applies Blindness I to the target | 2 s |
| Kurama | Chakra Recovery grants Regeneration I | 3.25 s |

The damage path accepts the same two damage types AHZNB uses to identify its jutsu. Damage must resolve to the wearer as its owner. Ally checks and the original armor reward ledger apply. Sword support requires a matching melee attack event and an actual registered sword. A transformation, a cosmetic, a heal, or a cast that misses has no qualifying hit. Addons using custom damage attribution need individual coverage.

## Private build and test

Run `build.py <clear-visor-jar> <new-jar> <javac> <srg-merged-minecraft-jar> <recompiled-forge-userdev-jar> <installed-AHZNB-jar>`. Keep the input and output JARs private. The source uses Forge 1.12.2 SRG names and Java 8 bytecode. The new JAR replaces the previous hvbijuu JAR in a client/server pair.

Compile `qa/ArtsProbe.java` for Java 8 against the new JAR, the same development classpath and authlib. Package the probe separately and launch it in a disposable Forge server with the installed Naruto providers. It creates a synthetic server player, verifies the issued-equipment rejection, then disables the issue requirement in the fixture for independent move trials. It tests native damage settlement, weapon events and all nine server activations. The probe stops its test server on completion. It must stay out of a gameplay pack.
