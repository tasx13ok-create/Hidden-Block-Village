# One Naruto settings hub

The inventory Settings button now opens the canonical Naruto settings hub. F7,
F8, `/fieldhud`, `/fieldcontrols`, `/fieldsettings` and the pause-menu Options
route open the same hub. The former Advanced HUD screen is a compatibility
redirect, rather than a separate settings menu.

The hub has six sections: Look, HUD, HUD details, Controls, Game and Shinobi
record. The former advanced HUD preferences are available here. Controls keeps
the quick dodge/settings/inventory bindings and opens the complete binding
editor for every installed action, search, conflicts, presets, profiles and
mouse preferences. That editor retains its staged Apply/Discard/Undo behavior.
Game preferences likewise retain their transactional editor as a child screen.
Video, shaders, sound, resource packs, language, skin layers and installed-mod
screens use the actual installed Minecraft/Forge screens.

The bindings and preferences child screens use the Naruto paper-and-ink theme.
Their Settings navigation returns to the hub. Existing configuration files,
registry identifiers, inventory slots and hotbar layout remain compatible.
Field HUD is version 0.17.0 and Field Controls is version 1.5.0.

## Verification

Both modules built their actual Forge 1.12.2 stable-39 production, development
and sources JARs with Java 8. All nine existing geometry, gesture, bindings,
profile recovery, transactional settings, focus and menu check programs passed.
The changed six-tab geometry is covered at the supported screen sizes. Selected
UI inputs were unchanged throughout the successful component build.

The first controls build failed because the host Java 8 default test heap tried
to allocate about 8 GB while the owner's full game was open. Verification tasks
now cap their heaps at 512 MB; the retry passed. This cap applies to verification
tasks, not Minecraft's runtime heap.

See `release/evidence/unified-naruto-settings.json` for exact artifact and input
identities. This is component-build acceptance. The new screens have not yet
been graphically exercised in the complete modpack. The earlier 0.3.15 client
exited before staging; its files and server were preserved.
A separate 58-JAR Menu-017 client now contains these exact two new UI artifacts
and 56 byte-identical other JARs. It joined the unchanged 0.3.15 server and its
shaded world/HUD were observed. Server handshake independently confirmed HUD
0.17.0 and Controls 1.5.0. Input was refused because the owner was interacting;
the following observation confirmed the owner playing, so automation stopped.
No settings click was sent. Verify
inventory/F7/F8/pause entry, mouse and keyboard navigation, shader-child return,
binding persistence and Apply/Discard before full graphical acceptance.
