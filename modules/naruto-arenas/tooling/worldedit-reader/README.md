# Original WorldEdit reader compatibility probe

This original source calls the installed WorldEdit Forge 6.1.10 public API
`SchematicFormat.MCEDIT.load(File)` and reads the resulting full 62,500,000-cell
`CuboidClipboard`. It checks 500×250×500 dimensions, the origin/offset,
22 anchor/seal block and metadata probes per arena, and every
generated chest's complete typed JNBT payload. It creates no world, server,
client, edit session or paste. These sources are outside all Gradle source sets.

Run on Windows with Python and the private Java 8/dependency fixture already
configured in `tools/arenas/native.py`. No third-party implementation, JAR or
compiled class is included here. From the repository root:

```powershell
python modules/naruto-arenas/tooling/worldedit-reader/run_probe.py D:/HiddenBlockVillage-Arenas-20261114/build-06/manifest.json all 3072m
```

Use `mist` instead of `all` to select that arena. The optional final argument is
the maximum Java heap. Compilation, logs, subprocess receipts and JSON results
go under ignored `local/arena-worldedit-reader/`; input schematics are read only
and hashed before and after. The runner uses one clipboard per Java process,
`-Xms128m`, Serial GC and the requested `-Xmx`, while measuring Windows peak
working set and sampled private memory. A process has a 240-second bound.

The observed final06 run used these exact private inputs:

| Input | SHA256 |
| --- | --- |
| `worldedit-forge-mc1.12.2-6.1.10-dist.jar` | `cd4768ded9e2b527e3fc093fdf4ce07f058187de7961bdc0d7fa821c1b756f75` |
| Java 8 `java.exe` | `2941c4fe4ceef54e40556c1cc13991ce7f56b627856ecb8506ffdc49ad258706` |
| `guava-21.0.jar` | `972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480` |
| `gson-2.8.0.jar` | `c6221763bd79c4f1c3dc7f750b5f29a0bb38b367b81314c4f71896e340c40825` |

Review the runner receipts and existing native fixture pins before running
against a different cohort.

[The safe final06 evidence](../../../../release/evidence/arenas-worldedit-reader-final06.json)
records five actual successful full loads: 110 block/metadata probes and 25 typed
chest comparisons, with unchanged inputs. Each load took 5.50–9.35 seconds at
`-Xmx3072m`; observed peak process working set was about 2.706 GB. Minimum heap
was not measured. This standalone result does not establish server memory needs,
live paste performance, placement safety or graphical acceptance.

The Java source is identical to the observed private probe. The checked-in
runner only adapts repository discovery and output paths so future runs keep
generated files outside this source directory; the historical evidence retains
the exact original private runner hash.
