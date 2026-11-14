# Unfinished icon/model intake

The owner supplied `naruto_icons_v2.zip` and `naruto_bbmodels.zip` and clarified that Claude is still working on them. Independent exact snapshots, CRC/hash/path checks, parsed metadata, a name mapping and a diagnostic preview are preserved privately under ignored `local/asset-intake-20261113`. No asset payload was promoted or published.

Observed snapshot: 1,786 individual 24x24 RGBA icons plus a contact sheet; 301 distinct pixel images. Names cover 592 current item entries, 74 HUD entries, all 27 arsenal weapons and all six lesson/technique pairs. Name coverage does not establish distinct complete art. The 27 weapon icons contain 24 unique images; six hand cast icons contain three. Rasengan/fire/lava share one identical image and Chidori/lightning stride another in this snapshot.

All 1,772 parsed Blockbench models are `java_block` models with a single thin 16x16x0.1 textured plate. Every embedded texture matches the icon pixels. These are item icon wrappers, not shaped weapon/armor meshes. No worn armor UV textures were provided. All 26 charged-image entries are single 24x24 frames; the current charged-item contract expects 32x64 two-frame strips and animation metadata. These gaps remain work in progress.

The ZIPs supplied no author/license fields or license/readme files; owner attribution to Claude is recorded without inventing provenance for every embedded image. Originals and exact copies are preserved; no scripts/instructions inside the archives were executed. A later revision must be re-audited rather than silently replacing the snapshots.

Snapshot identities:

```json
[
  {
    "name": "naruto_icons_v2.zip",
    "bytes": 1493646,
    "sha256": "67d7d64d06ea677af350b7767c2dee7b3142b8a6902476364135c08cd19c6428"
  },
  {
    "name": "naruto_bbmodels.zip",
    "bytes": 2644216,
    "sha256": "62a3f68c08b0635326e8282486a967871e1030b3dc44337d5346dda501a142aa"
  }
]
```
