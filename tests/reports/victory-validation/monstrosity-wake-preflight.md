# Monstrosity native wake prerequisite

Read-only review of installed `L_Ender's Cataclysm 1.21.1-3.33.jar` (SHA-256 `371956D0C2E3B56729EE93B955E936875D210E372FE734CDB74326D89EF79875`). The first maximum-kit victory observed all combat states but did not observe wake state 2. This document proposes native initialization for a follow-up; it does not claim that follow-up has run.

`Netherite_Monstrosity_Entity.defineSynchedData` defaults `IS_AWAKEN` to false. Its `finalizeSpawn` sets it true for COMMAND, SPAWN_EGG, DISPENSER and every reason classified by `MobSpawnType.isSpawner`. Thus a spawner reason is not a way to preserve sleep. The installed Soul Blacksmith pool points to `cataclysm:monstrosity`; that native structure template contains the entity with `is_Awaken=0`.

The actual proximity transition is implemented by `NMDoNothingGoal`. It runs while unawakened unless a living target is within squared distance 255 and visible. When the running goal stops, its native `stop()` sets awake true and attack state 2. A registered native `InternalStateGoal` then runs the 40-tick wake transition and returns to state 0. Accepted native damage can also set the awake flag, but no damage or direct phase assignment is necessary for the proximity path.

Minimal precombat setup for the follow-up:

1. Create a fresh entity and finalize it with the native STRUCTURE reason, preserving current native/configured attributes. Omit the harness's explicit `setIsAwaken(true)` call; do not assign attack state 1 or 2.
2. Start the player outside the native wake threshold, such as 24 blocks away, with ordinary terrain and line of sight available. Allow normal server ticks so the sleep goal actually starts. The existing fixture's player `cz-14` and boss `cz+12` positions are already about 26 blocks apart; the player offset alone is not their separation.
3. Use ordinary movement to approach inside `sqrt(255)` (about 15.97 blocks). The controller may keep attacks disabled for this approach. Starting immediately inside that threshold can prevent the goal's start/stop transition; holding ranged spacing outside the threshold will not wake it by proximity.
4. Capture native state 2 and its transition to 0, then continue ordinary combat. Earlier Drain/berserk/flare and victory evidence stays separate until the follow-up completes.

The old raw setup note stating that awakening was not skipped is contradicted by its COMMAND initialization and observed missing state 2. Raw logs remain unchanged; the qualification is retained in the readiness ledger. No Java, production, or runtime state was changed during this source audit.

Local inspection evidence is retained under `audit/monstrosity-wake-source`, including the installed entity source, native pool JSON and decoded structure entity NBT. The full old template NBT should not be copied into a new fixture merely to reproduce its wake flag, because it also carries unrelated historical attributes.
