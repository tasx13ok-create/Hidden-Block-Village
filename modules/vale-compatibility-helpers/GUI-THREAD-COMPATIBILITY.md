# Client lifecycle0.1.2: original Quest/Factions thread adapter

The shutdown guard and Quest adapter remain installed. The new Factions hook is restricted to the privately supplied CustomNPCs SpringAddon JAR SHA-256 `9002f091bd06dd08bbb0c93b57a9279729b6716813094954a5d57cef101b4e0b`, metadata version1.12. Exact class identities below distinguish this variant.

| Class under `micdoodle8.mods.galacticraft.api.client.tabs` | Source class SHA-256 |
| --- | --- |
| InventoryTabQuests | d5a51df78c0a0917a50f88ffd9cb76c0490388ee50c6337ce33f0a60069ff4a9 |
| InventoryTabFactions | caec4cc2f949776fe1332f1c9cbe50452cb4e09076fd9d3f017de953b4dee8a4 |

For each exact class, only public concrete instance `onTabClicked()V` with exactly one `INVOKESTATIC CustomNPCsScheduler.runTack(Ljava/lang/Runnable;)V` is rewritten. The replacement has the identical operand-stack ABI. The original entire Runnable, including GUI construction and display, runs immediately on the Minecraft thread or is queued through addScheduledTask. Every other method is independently compared unchanged in tests. Original third-party JAR bytes remain unchanged. Source/side/layout checks reject unsupported forms; no certificate/signer policy is weakened. The common transformer does not resolve client game classes; only the dispatch helper is client-only.

Actual 0.3.14 graphical diagnosis found the Client thread waiting for LWJGL's shared input/display monitor while pool-12-thread-1 held it inside WindowsDisplay.nSetTitle, reached through Essential window title update, Minecraft screen display and InventoryTabFactions' lambda. Two thread dumps agreed. Quest hook had loaded earlier. The failed client was preserved and stopped after evidence capture; shader compilation had completed without observed shader ERROR markers. This establishes a concrete Factions off-thread path; the new patch still requires actual graphical acceptance.

Read-only tab audit found AbstractTab, InventoryTabQuests, InventoryTabFactions, InventoryTabVanilla and TabRegistry. Only Quest/Factions tab callbacks invoke the one-argument scheduler. Vanilla does not. Other scheduler callers are Client packet sending, server/data/interaction handlers and the scheduler itself; ItemNpcWand uses a distinct delayed two-argument ABI. They are not patched. This is a bounded call-site audit, not a claim that every third-party GUI path is safe.

Standalone Java8 checks passed **57 bytecode/source +40 dispatch/integration assertions** against both exact private classes and original synthetic Java8 lambda fixtures. Both GUI constructors remain in untouched original lambdas; worker invocation queues before construction, main-thread draining constructs/displays correctly, direct main invocation runs immediately, unrelated scheduler calls remain unchanged. ASM BasicVerifier and JVM -Xverify:all pass. API doubles/private classes are never gameplay artifacts. Historical 0.1.1 provenance is retained separately from this original 0.1.2 change.

For a NEW verified runtime keep InventoryGuiEnabled=true and require both VALE_NPC_GUI_THREAD_HOOK class markers, actual join, manual inventory Quest and Factions tab navigation, continued responsiveness, disconnect and clean shutdown. Do not mutate accepted historical profiles or claim manual acceptance from these tests.
