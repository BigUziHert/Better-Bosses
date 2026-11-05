# Better Bosses 1.2.1 controlled validation

**8364 assertions passed with zero failures.** Fresh measurements cover 90 armor outfits, 13120 attack/outfit/enchantment combinations and 258 set-effect and endurance cases. All 73 approved damage profiles passed their plain-diamond and Protection IV netherite comparisons.

Version 1.2.1 normalizes the Leviathan health-bar progress while its native phase change is pending. The first segment stays empty after health crosses half until the boss completes its mode switch. The [binary comparison](production-entry-diff.json) binds the release to the reviewed build, and the [artifact continuity record](artifact-continuity.md) explains how earlier encounter evidence applies.

The expanded Leviathan regression contains 32 [executed boundary and transition assertions](leviathan-transition-bars.csv). It checks values immediately around half health, low health during the pending phase, the native frame-90 mode switch, render style and preserved health.

| Suite | Assertions | Failures |
|---|---:|---:|
| actual_attacks | 76 | 0 |
| armor_effects | 3 | 0 |
| armor_matrix | 95 | 0 |
| boss_bars | 396 | 0 |
| combined_projectiles | 448 | 0 |
| config_migration | 186 | 0 |
| damage | 221 | 0 |
| exhaustive_bmd | 1111 | 0 |
| exhaustive_cataclysm | 585 | 0 |
| exhaustive_twilight | 247 | 0 |
| fixed_health | 3031 | 0 |
| health | 639 | 0 |
| ignis_brand_netherite | 504 | 0 |
| installed_restart_verify | 322 | 0 |
| multiplayer_targets | 155 | 0 |
| preinstall_prepare | 46 | 0 |
| preinstall_verify_keep | 230 | 0 |
| vanilla | 69 | 0 |

The preinstall preparation boot saved 46 existing bosses before the balance mod was added. The installation and second-restart suites then checked their health profiles and persisted state. The preparation row records an absent production mod. Every later row records the 1.2.1 artifact.

[Every armor outfit](armor-results.md), [all attack measurements](armor-all-attacks.csv), [set effects and endurance](armor-effects-results.md) and the [approved damage comparison](approved-damage-comparison.md) retain individual outcomes. The reference loadout is full plain diamond on Hard. Native armor bypass, Protection eligibility, item durability and accessory effects remain visible in the results.

The [Ignis Brand sweep](ignis-brand-netherite.md) measures 36 native opening, shield-smash melee and ultimate cases across Brand 0–V in full Protection IV netherite. Fresh armor and native enchanted-apple effects have separate measured rows.

The [repeat-matrix comparison](armor-repeat-variability.md) identifies native random-dodge variation in Emu and Cursium equipment. Individual zero-damage samples record the observed dodge outcome. Their installed native probabilities and the matching damage measurements give the relevant context.

[All assertions](all-assertions.csv), [native scenarios](all-native-scenarios.csv) and [boss coverage](boss-coverage-results.md) preserve the controlled evidence. These probes execute installed attack methods and stage specific phase conditions. The [natural victory ledger](../victory-validation/coverage-ledger.md) separately records continuous encounters, native defeats, observed moves and remaining qualifications.

The test environment uses copied mods and configuration. The [source audit](../1.2.0/source-and-artifact-audit.md) records the read-only Survival source checks. Each run retains its own artifact identity and equipment conditions.

Production SHA256 `53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77`

Executed helper SHA256 `C1C5ACEC1E30CA930457CB2B3B05448AA06F2C81849A4F8E00BAC4C184E3F598`
