# Local JEI Java8 compatibility repair

The inspected `jei_1.12.2-4.22.0.1034.jar` is a valid ZIP, but includes one
misplaced build output, `java/mezz/jei/config/Tags.class`, compiled for Java21
(class major65). Forge1.12.2's ASM scanner rejects it. This explains the
`ASMModParser` / ignored-JAR warning; it is separate from the Damage Indicators
permission-packet exception that caused the observed Singleplayer disconnect.

`tools/repair_local_jei.py` accepts only the exact inspected SHA256 input. It
prepares a new private archive, removes that one entry, preserves all512other
entries byte for byte and verifies CRC, inventory and Java8 class compatibility.
It refuses unknown inputs, duplicate entries, signed archives and overwrites.
The original input remains unchanged. Neither archive is committed or licensed
for redistribution by this tool.

Actual local preparation passed. Source SHA256:
`e069347966155efd050fe18f060162a0406c194528c7c16974263c8879c3a8a2`.
Candidate SHA256:
`76a99584874c2f383a70c6f37a85478779848663901554a959ebbb37cea59684`.
The removed359-byte entry SHA256 is
`d9aa6ebfc84c52bd0e01f5547221adbd794107299086055392f047697b8b8c13`.

The candidate has not been activated in the owner's running client. Next,
use it in a new/stopped client, confirm the ASM warning is absent, then check
actual JEI catalog/search/recipe behavior with the matching Core build.
Archive verification alone does not accept the in-game JEI experience.
