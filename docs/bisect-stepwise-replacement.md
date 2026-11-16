# Stepwise server replacements

Prepared private ZIPs are in `D:/Bisect server upload/Hidden Block Village - Folder Replacements`.
These are normal independent ZIPs. Each extracts directly into the server root,
the directory containing `server.properties`. They are not multipart volumes.

Keep the Bisect server stopped throughout these four steps:

1. `01 - mods.zip`: replace the old `mods` folder, then upload and unarchive this
   ZIP at server root. It contains the accepted 53 server mod JARs, including
   Naruto-Core0.3.16 with the real-client dodge capability correction.
2. `02 - config.zip`: replace the old `config` folder and unarchive at server root.
   It contains 66 accepted configuration files, including zero dodge cost.
3. `03 - plugins.zip`: replace the old `plugins` folder and unarchive at server
   root. It includes the accepted CrazyCrates/NoBreak setup and the enabled
   Naruto-Stamina1.0.0 plugin. NoBreak starts disabled so ordinary building works.
4. `04 - server settings.zip`: unarchive at server root and replace `spigot.yml`.
   This ZIP contains that one file; it does not replace connection settings,
   operators, whitelist, worlds or player records.

After all four, start the server with Magma1.12.2 and 64-bit Java8. Confirm
Naruto-Stamina enables in the server log. The plugin maintains full food and
saturation for current Survival/Adventure players; dodge costs zero. Native
jutsu chakra and dodge cooldowns remain separate.

No world reupload or client update is required for this final stamina change.
Players use the separate clean `Hidden Block Village.zip` CurseForge client.
The full server/world ZIPs contain private records and are not player downloads.

All four replacement archives passed exact source-manifest, CRC and SHA256
readback. The stamina plugin passed 22 real native/CraftPlayer assertions in a
disposable fixture. Actual hosted startup and gameplay acceptance remain pending;
these checks do not establish that every campaign or original-mod asset works.
