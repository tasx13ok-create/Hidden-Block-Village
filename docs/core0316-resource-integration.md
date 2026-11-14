# Core 0.3.16 resource integration candidate

This work connects the project's actions to NarutoMod's actual chakra pool when
that mod is installed. Saved resolve is the backend only when NarutoMod is
absent. An installed but unready or unsupported provider does not grant a second
spendable pool. This document describes a candidate, not a deployed release.

The original adapter binds the exact public absolute-double debit API, verifies
the observed debit, handles an exact-zero balance through the public clear API,
and settles reservations once. Refunds add at most the actual reserved debit to
the same account; independent gains/spending are preserved. Server, player,
connection, world, account and action context remain authoritative. Fake,
unregistered, stale and off-thread callers cannot reserve resources.

Callers include dodge, arts, ward skills, weapon techniques, hand techniques,
the five starter nature moves, Lightning Armor, forms, companions, focus gear,
food, pills, wells and melee resource gains. Accepted effects keep their cost
when later feedback fails. Rejected effects refund their reservation. Upkeep
defers while another action settles, then catches up within its existing bound.
The legacy automatic regeneration loop runs only when NarutoMod is absent.

HUD 0.18.0 reads an owner-only resource packet containing raw doubles, provider,
availability and cost scale. It displays the shared resource without a second
resolve bar, preserves values above100, and does not display stale/unready data
as a spendable zero-to100 pool. Costs retain their fractional scale in tooltips.
The existing ability wire remains compatible; its integer cost field represents
base units. Hotbar appearance and physical loadout behavior remain separate.

## Evidence and open gates

The first complete component candidate passed all11source areas/36artifacts,
source SHA256 `dc1d3a7229b4d58ee7e8e339abd4689016ddba28e4a4c7b4e799311a93deef4b`,
report SHA256 `42f9b7a8466d32a334a460152186e2991097d0aee416b59c2fe91b8ccd18d1ba`.
Core candidate hash is `0e24748aaff98167acbc09dcc896bbbb850198e777ffad3d0679d0d0b44809d5`;
HUD candidate hash is `4b371322178fd584fb0f196f188a66002517fdd81364fa56453c8ac046a65a6e`.
The build ran from a fresh D-drive export whose compiled-source snapshot matched
the canonical checkout. The successful build reported no input drift.

Separate checks passed46resource contract assertions,42HUD projection/cost/
readiness assertions,127actual packet-codec assertions, and30actual-client-cache
checks using an isolated client API fixture. The cache class also compiles
against the real prepared Minecraft/Forge client API. These checks are not a
graphical or full-pack native acceptance pass.

This first compiled candidate is superseded for acceptance by an explicit
session-invalidation correction in progress: actual Magma respawn can reuse the
same player object. Object identity alone cannot invalidate a payment retained
across that lifecycle. Rebuild and run the corrected native fixture before
accepting the candidate. The native verifier must also require all previous
0.3.15 checks plus the new resource fixture for0.3.16.

Base Naruto/Spring coexistence is a separate gate. Their original caches were
observed losing alternating debits. An original private delegation prototype
fixes the bounded910balance case, including both tick orders, but packet,
lifecycle, regeneration/exhaustion and independent Sage-pool checks remain.
No prototype is installed in the owner session. See
`naruto-chakra-cache-collision.md` for the measured original problem.

Full-pack fresh-character chakra awakening, actual accepted/canceled native
jutsu interactions, original bosses/NPC rewards, map-specific academy/mission
placement, five campaign walkthroughs and hosted acceptance remain unfinished.
Registry/profile counts and these infrastructure checks do not establish their
completion. The existing owner test profiles and original inputs are preserved.

## Corrected compiled candidate and native failures

The lifecycle-corrected candidate also passed all11areas/36artifacts with no
compiled-source drift: source `c1651743f1f832587f8b362579ddd90ea1d59b3c9e435dbfa8c73ce6a89ef136`,
report `224ab58f97fe9d74a7b728941a9456c9ea7ad72c31a619e5b4792fcd00e1519c`,
Core `e417b9f00540e3a9ac97d85aa533a0e15f7eff913f8b22a55e3939c2541ae473`.
Its two native attempts both reported failure after134assertions. First, an old
temporary fixture player was unregistered; official login/logout was added and
the original reentry/potion assertions then passed. The second run exposed a
real legacy fallback regression: rejected effects leave the detached original
NBT account charged, although the replacement account is preserved. That fix
is ongoing in the canonical checkout. No failed assertion was removed.

This compiled source is saved on `codex/core0316-resource-integration` for
collaboration and continuation. It is a work-in-progress branch. It is not a
deployment-ready release, and neither runtime failure is a passing acceptance
gate. The stable owner profiles were not upgraded to this candidate.

## Detached fallback refund correction

The next full11area/36artifact build also passed, source
`991520318b82236d87a66876136edda5361f117cb7252b7db55744d7ab547d9c`,
report `b66a753bb2765590631a872a2bfca5f30f6f5c773310d115230e60164e1083c6`,
Core `60fb144db8caf324b30477cd0f41d87ef9cd220a5aa4f89388e82eefc2ae8346`.
Captured fallback NBT accounts can now receive their own once-only bounded
refund without touching a replacement account. Native Naruto refunds retain
the strict live-account checks. All56resource component assertions pass.

The next native run passed the unchanged detached-account assertion and then
FAILED on UUID mutation cleanup: a callback changes the player's UUID before
the transaction removes its runtime guard. Cleanup must use the original
captured owner key. That production correction is ongoing; no assertion was
relaxed and no native/full-pack/deployment acceptance is claimed.

## Verified Core0.3.16 native checkpoint

The immutable owner-key cleanup correction now passes all11source areas/
36artifacts and all3,649disposable Forge native assertions, including every
inherited0.3.15check and the new shared-resource/codec/session cases. Source is
`231c762c6f012e92718e46a450038c811df536e3afdb57dbbc9e6499d64ec657`;
build report `493f922884cf1304e1136aeaedc881f5a3b3e8f8253328ae60e9434bed1008c5`;
native report `404caf607c80a184753ac491ec320f2b67ca9d8b60c98cb33192f4f54bbb451a`.
Core is `3c2a6fed37c66d1bb63af4e617e4c1eae2eef02fc9704a316950722a9465c17b`.
The runtime exited0and was removed. All63resource component assertions pass.
Earlier failed candidates above are preserved history, not the current result.

The owner explicitly chose the new0.3.16label on the accepted0.3.15base.
This accepts the standalone Forge cohort; full54/Magma/client/Creative/hosting
are separate gates. Actual Magma's Damage0callback occurs before absorption
deduction, requiring a paired Focus/Scroll observation correction before full
pack acceptance. Base/Spring delegation client proof and the remaining Claude
visual/menu work are still under integration. No full Creative setup has been
launched yet. It must follow the completed reviewed merge and fresh acceptance.
