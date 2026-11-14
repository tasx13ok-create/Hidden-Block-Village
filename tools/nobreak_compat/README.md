# NoBreak 1.4 compatibility tool for Magma 1.12.2

This original tool adapts the owner's exact NoBreak 1.4 input for the tested
Magma 1.12.2 `8cc35ed` platform. No original or patched third-party JAR is stored
here. The original input remains untouched; all output goes to a new private
candidate. Original NoBreak authorship remains jojodmo.

The required input SHA256 is
`981fad52e429de342d585f30289784510b3d3dd466fc2ced0110930249cfdf1f`
(6,787 bytes). Other inputs are rejected. Two precise instructions change in
`BreakListener`: the absent `Material.isInteractable()` virtual call becomes
the original static `InteractionCompat.isInteractable(Material)` helper, and
the absent `Material.FARMLAND` field becomes its 1.12 equivalent `Material.SOIL`.
Every other original ZIP entry, including plugin/config metadata, stays
byte-identical. No third-party method body is copied into these source files.

The helper uses public `CraftMagicNumbers.getBlock(Material)` reflection and
the native block activation signature. It compares parameter and declaring
Class identities because Magma remaps reflected method/type names for Bukkit
plugins. This detects activation overrides in mod blocks too. It never invokes
activation. If exact-platform reflection is unavailable, only the explicitly
listed vanilla material names are used as a fallback; unknown mod materials
then return false. This is conservative activation classification, not full
modern Bukkit semantic parity.

```powershell
python -B tools/nobreak_compat/patch_nobreak_112.py <private>/NoBreak.jar <fresh-private>/NoBreak-1.4-1.12-compat.jar --java-home <Java8-home> --magma <Magma-1.12.2-8cc35ed-server.jar> --asm <asm-debug-all-5.2.jar>
```

Java sources resolve relative to the tool's own `compat-src` directory. Java 8
compilation happens in a temporary directory beside the private output. The
tool checks ZIP CRCs, retained entry identities and Java 8 class versions before
publishing with an atomic exclusive hard link on the same NTFS volume. It
refuses an existing candidate or receipt. Keep both the original and generated
JARs private; a matching hash does not grant redistribution permission.

[verification.json](verification.json) records actual native results: four
clean isolated boots, 146 assertions, unchanged source09 production 54-mod
inputs, and zero NoBreak ABI errors for the final candidate. Bukkit dispatch
uses synthetic OP/non-OP senders; no real player login or graphical acceptance
is claimed. Chest, furnace and workbench interactions, original Varied
Commodities table classification, air-click baseline, break/place permissions,
commands, shutdown persistence, reset-on-restart and direct native-world writes
are covered. The actual original table inherits base Block activation and is
classified non-interactive; this is a native API observation, not a furniture
gameplay claim. The original JAR loaded despite `api-version: 1.14` but threw a
missing-method error during normal interactions, including while disabled.

Use this server configuration to preserve ordinary Minecraft building:

```yaml
enabled: false
reset on restart: true
reset to: false
enable messages: false
```

NoBreak cancels supported Bukkit events. Actual direct Bukkit and Forge World
block-state writes bypassed it even when enabled. No named jutsu cast was
tested; do not present this plugin as universal jutsu or world protection.

The author's [NoBreak page](https://www.spigotmc.org/resources/nobreak.63732/)
documents commands and permissions. Its compatibility advertising does not
replace these exact-version checks or establish a redistribution license.
