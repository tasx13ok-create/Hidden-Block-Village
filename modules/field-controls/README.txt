FIELD SETTINGS & CONTROLS 1.4.0 — SIMPLE LAYOUT CANDIDATE

CURRENT INTEGRATION

field-controls-1.4.0-simple-candidate.jar is selected for the reviewed Vale 0.3.8 client and passes the integrated source build. Actual graphical input/settings verification and the Essential open/return handoff remain pending. Essential 1.5.1 is separately retained in the matching client package; it is not part of this component's source delivery.

Current package and installation: ../../docs/client-and-bisect-038.md.
Integrated build evidence: ../../release/candidate038-component-builds.json.

HISTORICAL COMPONENT-DELIVERY NOTES

The following text records the earlier standalone candidate. Its test-kit and 0.3.3 upload status describe that earlier delivery; current runtime acceptance is stated above and in the integration documentation.

Built candidate, not activated in the verified test kit. Native visual/input verification and a live Essential handoff remain pending. No Minecraft/GPU process was launched for this revision.

One shared navigation row: Controls / Game / Video / Sound / HUD. Options and F8 open the keybinds page directly. The overview grid and binding sidebars are removed from the active navigation. Category and filter selectors replace the long lists of navigation buttons. The keybind list takes the full width; F1 expands contextual help.

Layouts has Action/MMO/Classic previews, mod defaults and saved layouts. Mouse preferences live under Game. HUD tools live under HUD. More contains advanced native/mod screens and section defaults. A key's Mod default action is inside its capture dialog instead of a reset button on every row. Searching checks all installed actions; the default action group is labeled Basics to distinguish it from the real Essential mod.

A dedicated Essential button keeps a fixed space in the header. Its optional bridge inspects the installed Essential API without initializing/opening screens during detection; a click constructs its own real settings GUI and opens it through Essential's GUI API. Essential remains client-only and is not bundled here. Missing/unsupported API leaves the reserved button disabled. No chat-command/server fallback, emulated Essential screen or automatic consent is used.

Existing binding IDs, profile schema, preset allocator, backup/file verification/rollback, protected movement and focused-cast API are retained. Cross-page navigation guards a dirty draft with Apply/Discard/Keep editing. The old 1.3 release and public upload were not modified.

VERIFICATION
Offline Java 8 production compilation and SRG remapping passed. Existing checks: 5,681 controls/settings/cast/catalog assertions; 279,360 menu assertions across 4,260 viewport/mode combinations. New simple-layout/optional-GUI checks: 23,009 assertions, including 639 screen sizes, no sidebar, non-overlapping controls, reserved Essential space and metadata-only discovery/no implicit screen opening. These are headless checks, not native gameplay proof. The full build log accompanies this artifact.

GAPS
No native 1.4 screenshots, click-flow test or real Essential 1.5.1 live open/return test was run. Optional API compatibility was checked against the installed JAR signatures and isolated fixtures, not every Essential version. Third-party pages retain their own layout/save behavior. Candidate is withheld from Root's verified 0.3.3 upload until integration/native verification.
