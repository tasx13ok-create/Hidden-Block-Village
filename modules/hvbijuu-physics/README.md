# Bijuu Armor tail physics, pass 2

This replaces `village.bijuu.TailPhysics` in Hidden Village Bijuu Armor 0.16.2-rc1. The public API is unchanged, so no callers change. The source was recovered from the owner's jar (decompiled with the owner's approval) and edited here, because the original source is not in this repository. Only the tail collision and inertia solver changes; models, textures and every other class are untouched.

## What changed

- **Adaptive sub-steps (2–6).** These follow root and tip speed, so fast swings and jumps no longer tunnel.
- **Verlet momentum.** Each segment keeps its own velocity between ticks. rc1 clamped velocity to ±0.45 and scaled it by 0.8 every tick; a smooth tanh limit replaces that. Tails trail starts, whip on turns and jumps, and follow through after the body stops.
- **Travelling pose goal.** The pose goal trails behind the root along the chain (`TRAIL`), and the pose spring is softened (`INERTIA_SPRING`), so motion travels down the tail.
- **Root impulse.** Sudden starts, stops, jumps and landings kick a damped wave down the chain.
- **Bending limit (`BEND`).** A tail can curl but cannot fold back through itself.
- **Follow-the-leader length lock.** Each sub-step ends with this pass, so momentum never stretches segments.
- **Contact friction (`FRICTION`).** Resting tails stay on the ground instead of skating.
- **Rest damping.** Tails settle quickly once the body stops.
- **Finite-state guard.** A non-finite result resets cleanly to the animated pose.

The tunables are public statics: `ITERATIONS`, `FRICTION`, `BEND`, `MAX_SPEED`, `INERTIA_SPRING` and `TRAIL`.

## Bench (`tests/TailBench.java`)

The bench runs a 12-segment tail through a scripted sequence: run, weave, jump, land and rest, on a floor with a block behind the player. Results for the four beast profiles tested (0, 2, 5 and 8), rc1 vs pass 2:

| Metric | rc1 | pass 2 |
|---|---|---|
| Max segment stretch | 11–15% | 0.2–1.1% |
| Penetration into blocks | 0 | 0 |
| Folds | 0 | 0 |
| Peak tip speed (whip) | 0.31–0.33 | 0.58–0.75 |
| Motion after settling | 0 | 0 |
| Rest slide | 0 | 0 |

Trailing is now real momentum rather than stretch. The patched jar also ran cleanly in the real 1.12.2 client through run, strafe, jump, land and settle for all nine sets.

## Build

```
javac -cp HiddenVillage-BijuuArmor-0.16.2-rc1.jar -d out src/village/bijuu/TailPhysics.java
jar uf HiddenVillage-BijuuArmor-0.16.2-rc1.jar -C out village/bijuu
```

The jar itself is not committed. Merge this file into the mod's real source when it is available.
