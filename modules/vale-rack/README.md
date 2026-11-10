# Rack0.1.1 source-only candidate for Core0.3.5

Not built, launched, or natively verified. Frozen Rack010 runtime31dc780f and its
source archives remain untouched. Root must supply the new Core035 dev/runtime,
then build and verify the new matching pair. Do not apply033/010 proof to it.

The only gameplay-policy delta accepts a Core ItemSword subclass when its actual
registered item is the exact object under the same short key in Gear.ITEMS and
Item.REGISTRY. Existing server accepts() still requires RewardLedger.valid.
The three explicit Core throwable IDs remain, now with the same exact identity
guard. Non-Core Sword/Axe/Bow/Shield behavior, count/NBT limits, all request
guards, container semantics, storage schema1 and public RackClientApi1.0.0 are
unchanged. Public client signatures and C2S/S2C Java source are byte-identical.

Core generic items, armor, food, scrolls and non-sword tool/bow/shield classes do
not qualify under the Core branch. A bare crafted specialist is not owner-issued;
the new Core must securely bind its genuine native crafted output first.

Module/handshake/resource/build metadata is0.1.1; API stays1.0.0. Build uses an
external new Core035 dev JAR through -PvaleCoreDevJar. No compiled dependencies,
game binaries, private test carriers, native launchers or worlds are included.
See REQUIRED-VERIFICATION.json and SOURCE-MANIFEST.json. LICENSE is MIT.
