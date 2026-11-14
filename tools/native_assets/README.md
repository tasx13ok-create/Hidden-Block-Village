# Private original-model reference repair

This is an original tool and synthetic test set. It is not a reskin and never
installs a resource pack, changes `options.txt`, alters a JAR, launches Minecraft,
or touches a saved world. The generated pack is **private** because it contains
the owner's supplied original model JSON geometry. Publish only this tool, its
synthetic tests and reviewed safe hash reports. Never publish the generated ZIP,
the private manifest or native JSON bodies.

The original tool, synthetic tests and reviewed safe hash evidence are maintained
in the repository. Generated native geometry packs and private input manifests
remain outside Git. The coordinator released bounded CPU/headless checks after
packaging. The tool does not launch a GPU client, activate a pack or change an
owner profile. Coordinate a later disposable real-client bake independently.

## Narrow repair contract

The tool requires the complete pinned installed mod JAR set, one real vanilla
resource JAR, an exact log snapshot and pinned options showing no enabled packs.
It rejects changed pins, duplicate/case-ambiguous resources, active external
packs, unknown custom loaders, dynamic overrides, oversized/malformed JSON and
missing inherited texture keys. It does not infer resource priority from sorted
JAR names. Every resource relevant to a repaired model must have one owner.

A candidate must have a log-proven missing ordinary parent and nonempty own
`elements`. The default mode additionally requires nonempty own `textures`
and complete local face/alias closure. The explicit child-context extension
handles ordinary Blockbench geometry with empty parent texture maps only after
all incoming JSON item/block wrappers and override contexts have closed against
their **existing** original child bindings. No map is invented or written.
Builtin/generated/entity, every `builtin/`
parent, the standard generated/handheld parents and virtual `forge:` parents
are never classified as ordinary missing files. The complete input vanilla JAR
must contain the standard item/generated and block/cube_all resources.

The only mutation is deletion of one root `parent` member by byte range. Every
other original byte survives exactly. The tool separately checks semantic
equality of every other field and records geometry/UV/texture/display hashes.
It does not redirect a parent back to its own geometry: that creates a cycle.
No PNG, OGG, class or other artwork payload is read or copied into the output.
Texture existence/identity is proven by archive pins and ZIP-entry metadata;
an actual atlas/model bake is still a separate gate.

Missing `jiton`, `crystal_senbon`, `kkg_paper`, sound definitions and crop-age
variants are outside this parent-removal operation. They remain unresolved
unless a later exact, reviewed reference fix is independently implemented.
A differently named paper PNG is not permission to guess a replacement.

## Reproducible checks

1. Prepare a new private manifest with `prepare_manifest.py`, explicitly passing
   `--profile`, `--recipe`, `--log`, `--diagnostic`, `--workspace` and `--output`.
   The recipe is read only to identify the classpath's actual vanilla resource
   JAR. No launch credential or other argument is saved or printed.
2. Optionally run the proof driver in `--mode graph` using that private resource
   manifest. The real Gson/ResourceLocation/native blockstate parsers enumerate
   all supplied model/blockstate JSON. The report emits thin reference metadata,
   not geometry. Add its `{path,sha256}` pin as `context_graph` and set
   `context_mode:"all_incoming_wrappers"` to request the explicit extension.
   The graph retains parse errors. Unknown/ambiguous incoming contexts, cycles,
   unresolved shared-child/override textures and unexplained leaves are held.
   Opaque unrelated source errors are retained and scanned read-only for every
   affected ID and escaped path; they are not silently reported as parsed.
3. Run `python tools/native_asset_repair.py --manifest PRIVATE.json --output
   NEW-WORKSPACE-DIRECTORY`. This produces a private deterministic ZIP, safe
   per-model proof metadata and a private native-proof input file. ZIP entries
   use stored bytes and fixed metadata for reproducibility across zlib versions;
   every generated member is read back and hashed. Existing
   output directories are refused and incomplete outputs are retained.
4. Run the original synthetic unittest file. It uses only authored synthetic
   JSON and synthetic archive member bytes, never supplied native fixtures.
5. Run `native_model_proof_driver.py` with the private proof input, pinned real
   `--prepared-api`, `--expected-api-sha256`, private launch `--recipe`, Java8
   `--java-home`, disposable `--workspace` and NEW `--output`. It compiles and
   executes only `village.assetproof.NativeModelProof` with a 256MiB heap.
   This invokes the actual prepared Minecraft `ModelBlock.deserialize`, typed
   geometry, camera transforms, `resolveTextureName` and hierarchy checks.
   For shared geometry, the verifier assigns the actual original parent objects
   to each original child through `getParentFromMap`; it does not inject merged
   texture maps. Every effective shape, UV, transform and resolved face texture
   is compared before/after in each incoming context.
   Its class origin is checked against the exact real API hash; no substitute
   ModelBlock/parser stub can satisfy that gate.
   `native_checker_negatives.py` separately runs that same real checker against
   authored synthetic fixtures: one positive and eight required failures for
   UV changes, texture-map changes, hidden child/pixel overrides, omitted child
   contexts, false declared texture closure, wrong providers and wrong API origin.
   This does not copy native model geometry into negative fixtures.
6. Supply an independently reviewed, pinned **actual-runtime registry export**
   if one is available. Exact schema: `schema:1`, `kind:"actual_runtime_export"`,
   `provider_sha256:{archiveLabel:hash}`, `items:[exact IDs]`, `blocks:[exact IDs]`.
   Set the manifest's `registry` to its `{path,sha256}` pin. The tool checks
   registered static item/blockstate selectors and provider hashes. It does not
   authenticate the producer merely because a JSON file claims a runtime origin;
   that claim requires its real fixture receipt. `export_stopped_registry.py`
   can extract exact item/block IDs from a stopped disposable fixture's saved
   FML registry, requiring the pinned successful native result/run receipts,
   unchanged provider JARs and a recovery marker. It exports no Data/player tags
   and writes no world file. Asset filenames alone do not prove registry or
   actual ModelRegistryEvent callback selectors. Unknown/missing exports and
   unproved native callback ABI bindings remain explicit holds.
7. Request a coordinator-reviewed bake in a NEW stopped/disposable real client.
   Do not enable the pack in the owner's active session. Confirm complete model
   and texture dependency closure, unchanged original appearance, and no new
   missing-resource/cycle errors. Pure parser checks are not graphical evidence.

All emitted reports keep `activation_allowed:false`. Review the actual registry
receipt, native parser result and new real-client bake before any later manual
promotion. The original input archives must retain their exact hashes throughout.

The current private result has 38 parent-only model JSON candidates and one
geometry held for a missing child texture key. These cover 37 of the original
39 missing model references: medical and the absent Spring jiton item JSON stay
open. Nothing has been activated. The actual native callback selector ABI is
also still a hold; static registry-name/file conventions do not establish it.
