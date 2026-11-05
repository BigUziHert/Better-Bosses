# Ancient Remnant native wake prerequisite

Read-only inspection of installed `L_Ender's Cataclysm 1.21.1-3.33.jar`, SHA-256 `371956D0C2E3B56729EE93B955E936875D210E372FE734CDB74326D89EF79875`. The first maximum-kit victory observed all declared active combat candidates but not wake state 2. This source review identifies a follow-up setup; it adds no runtime coverage.

`Ancient_Remnant_Entity.defineSynchedData` initializes `NECKLACE=true`. Its `finalizeSpawn` only delegates to the superclass, so changing the reason from COMMAND to STRUCTURE alone does not remove the necklace. The existing precombat necklace interaction returned PASS because that native prerequisite was already satisfied.

The actual Cursed Pyramid spawn path is `Cursed_Pyramid_Structure.Piece.handleDataMarker("remnant", position, ServerLevelAccessor, random, boundingBox)`. The installed `data/cataclysm/structure/cursed_pyramid_lower2.nbt` contains that DATA marker at local position `[44,8,33]` in a `[47,48,47]` template. It has no embedded Remnant entity. The native marker callback creates a fresh entity, sets its necklace false, makes it persistent, moves it to the marker, finalizes it with STRUCTURE, adds it to the level and clears the marker block.

A test-only precombat adapter can invoke that native marker callback and retain the resulting entity. This reproduces the installed structure initialization without a harness assignment to an attack state or a copied historical attribute set. It should record the absent necklace before activation and use the existing native necklace interaction. While the necklace is absent, `RemnantDoNothingGoal` prevents ordinary movement; native `isSleep` also reflects this state.

Native `mobInteract` accepts Necklace of the Desert only when the necklace is absent. It consumes one item for a survival player, sets the home position, attaches the necklace and enters attack state 2, returning SUCCESS. `RemnantAwakenGoal` then runs its native 80-tick awakening and returns to state 0. Those transitions and item consumption should be observed before continuing ordinary combat. No direct harness phase assignment is required.

Local decompilation and decoded marker evidence are retained under `audit/remnant-wake-source`. The earlier D8AD/F355 victory remains valid combat evidence with its startup gap; no production code, active encounter or source-server file was changed by this review.
