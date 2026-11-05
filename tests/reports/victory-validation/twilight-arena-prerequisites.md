# Twilight arena prerequisite review

Read-only review of the installed `twilightforest-1.21.1-4.8.3345-universal.jar`, SHA-256 `493DA16D10210F9F53A3C33B3D8FCB4AE9B14016EE420D753C894562BD9ABA35`, on 2026-11-05. This source review does not add a native victory or substitute for observing an attack.

No absolute Y=0 combat cutoff like Obsidilith's anvil fallback was found in the eight boss classes, their relevant AI goals, projectiles, Hydra head/mortar helpers and movement controls reviewed. The concrete requirements below still matter at any arena elevation.

| Boss | Relevant native prerequisite |
|---|---|
| Naga | `NagaMovementPattern.crumbleBelowTarget` requires the target above the boss's own floor and destroyable blocks. Its floor is relative, not Y=0. Home recovery also compares against the restriction point. |
| Twilight Lich | Teleport destinations use target/home elevation, collision and line of sight. A walkable destination and adequate tracking range remain necessary. |
| Minoshroom | `GroundAttackGoal` requires the boss on ground; its slam damages grounded players in a boss-relative volume. Jumping can avoid the slam independently of armor. |
| Knight Phantoms | Formation destinations use the restriction-point Y. Native Hard spawning equips the sixth Knight with a shield; the source-reviewed large-circle enums are dormant for a fresh native spawn. |
| Alpha Yeti | `makeBlockFallAbove` scans 1–24 blocks upward for an ICE-tag block with air beneath it. Native griefing permission is required to release falling ice. `FallingIce` checks actual world build bounds, not an absolute zero floor. |
| Hydra | Head positions are relative to the boss. Mortar detonation uses block/entity collision and on-ground fuse behavior; no absolute zero-height landing shortcut was found. |
| Ur-Ghast | Native minion waves and flight points use discovered sky-visible ghast traps. Tantrum damage requires sky exposure at the victim. These are separate from a numerical Y threshold. |
| Snow Queen | Hover/drop/beam heights derive from the target. Summons require usable teleport space; beam and drop progression remain native. |

The test fixture source already includes the Yeti ice ceiling, Ur-Ghast traps and native home positions. The pending helper at the time of this review also contains the Naga entry pillar and sixth-Knight shield. That source statement is not an assertion that the pending helper was loaded in earlier encounters; [per-run provenance](runtime-provenance.json) records the executed binary separately.

Decompiled inspection files are retained locally under `audit/twilight-y-prerequisites` and `audit/bar-source/twilightforest/entity/boss`. This was a targeted terrain-prerequisite audit, not a proof that every random path or every possible native environmental interaction is covered.
