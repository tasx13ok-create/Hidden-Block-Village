FIELD HUD 0.14.1 — CURRENT VALE CANDIDATE

The current integrated build produces field-hud-0.14.1.jar. With Vale Rack 0.1.2, an empty focused slot stows the held weapon and a filled slot equips or swaps its real stored stack. Actual matching-client input, appearance and shader acceptance remain pending.

Current behavior and evidence: ../../docs/gameplay-038.md and ../../release/candidate038-verification.json.
Client/server installation: ../../docs/client-and-bisect-038.md.

HISTORICAL 0.14 SOURCE-DELIVERY NOTES

The following text records the earlier source delivery and its evidence. Its statements about preserving source bytes and leaving the delivery unbuilt apply only to that earlier delivery. Version 0.14.1 has subsequent changes and integrated builds; the historical assertions do not establish acceptance of the current client.

FIELD HUD0.14 — PRODUCTION SOURCE DELIVERY

Six vertical slots display real server-owned weapons outside normal36 when Vale Rack is installed on client and server. Existing focus/wheel selects a physical index; existing R/use requests the native mainhand exchange. HUD settings opens the actual storage Container. Native models/count/glint/durability, stale-context and menu guards, reduced motion, compact geometry and original scroll/menu styling remain. Without Vale Rack the previous local shortcut/editor behavior remains.

This source-only delivery preserves every src/main Java/resource byte from the original verified0.14 source. Four ordinary headless test/preview sources are omitted, as are their Gradle-only verification tasks. No private native probe, QA/camera driver, binary/runtime, player data, held icon-chat material or Discord work is included. Original validation source45560b5e and all frozen runtime/evidence artifacts are unchanged.

Build: gradle --no-daemon assemble reobfJar. Java8, Forge1.12.2, MCP stable39, RFG2.0.3. Production runtime93e48a7c passed69,438 headless checks and the separate native storage pass2251 assertions/1713GL0 samples. This packaging variant itself was not rebuilt. Native clean7-mod proof has no external shaders/minimap; earlier utility/shader proof is separate.
