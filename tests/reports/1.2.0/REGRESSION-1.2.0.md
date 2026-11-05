# Better Bosses 1.2.0 regression results

**7785 assertions passed, zero failures.** The isolated dedicated-server run includes 90 armor outfits, 13120 attack/outfit/enchantment measurements and 258 additional set-effect/endurance measurements. The approved damage comparison covers 73 profiles.

Damage is calibrated to **Hard difficulty and full unenchanted diamond**. The marked Ignis ultimate has a 9.5-heart ceiling; its other heavies cap at 8. Maledictio Anima retains its prior raw cap, measuring about 6.795 hearts in diamond and 6 in Protection IV netherite. Yeti falling ice keeps its approved 5.25-heart diamond ceiling. Ur-Ghast fireballs and Harbinger missiles have an 8-heart combined budget per projectile and victim. Native weaker hits can remain below these ceilings.

The [production behavior-scope audit](behavior-scope.md) verifies that this release changes damage configuration and accounting without adding core AI, movement, phase, timing, projectile-trajectory or arena-rule changes. Existing health and bar support remains in place. Automated client strategies and isolated test-environment adjustments are test-only and excluded from the production mod.

S:\Survival was used as a read-only source of mods/configs. Testing and builds used isolated copies; this process did not install the new mod into that folder. The [source and artifact audit](source-and-artifact-audit.md) records the fresh comparison of 871 files, independent source-server restart activity, and the exact production JAR provenance. Gameplay configuration values and mod JARs remain unchanged; the source audit distinguishes one generated timestamp-comment rewrite and 111 modification-time-only changes from task actions.

## Automated checks

| Suite | Assertions | Failures |
|---|---:|---:|
| actual_attacks | 76 | 0 |
| armor_effects | 3 | 0 |
| armor_matrix | 95 | 0 |
| boss_bars | 367 | 0 |
| combined_projectiles | 448 | 0 |
| config_migration | 186 | 0 |
| damage | 221 | 0 |
| exhaustive_bmd | 1111 | 0 |
| exhaustive_cataclysm | 585 | 0 |
| exhaustive_twilight | 247 | 0 |
| fixed_health | 3031 | 0 |
| health | 639 | 0 |
| installed_restart_verify | 322 | 0 |
| multiplayer_targets | 155 | 0 |
| preinstall_verify_keep | 230 | 0 |
| vanilla | 69 | 0 |

The required suites cover fixed health and multiplayer conditions, saved-world installation/restart, native server boss-bar state, approved damage fixtures, installed native attacks and phases, vanilla bosses, armor variants and effects, legacy configuration migration, and combined native projectile callbacks. Config migration checks use isolated temporary files and restore active runtime settings; no source configuration file is rewritten.

## Damage and armor

[Approved profile comparison](approved-damage-comparison.md) gives measured plain-diamond and Protection IV netherite outcomes for every approved profile. Its combined rows are real joint impact/splash measurements with native invulnerability frames. [All armor outfits](armor-results.md), [every measured attack](armor-all-attacks.csv), and [set effects/endurance](armor-effects-results.md) retain the complete individual results.

[Damage limits and defense conditions](damage-limits.md) distinguish the measured plain-diamond Brand sweep, fresh Protection IV netherite measurements and untested combinations. The single-hit reference ceilings are not a universal guarantee against lethal combinations, damage over time, weaker/broken equipment or arbitrary debuffs.

The 12 separately labeled [budget-exhaustion stress cases](compound-budget-exhaustion.csv) retain health, equipment and per-projectile accounting across repeated native callbacks. They reach the eight-heart reference budget, verify final-hit truncation, and reject further damage. Half additionally simulate a compatibility mod removing impact/splash cooldown. In that condition, a stronger native Ur-Ghast power4 projectile accepts 4.5 + 3.5 = 8 diamond hearts, measuring 2.591331 hearts in Protection IV netherite. Repeated callbacks after projectile discard and the explicit cooldown-removal listener are adversarial conditions, not claims about ordinary gameplay.

| Full set | Warden melee, plain | Warden melee, Protection IV | Sonic boom |
|---|---:|---:|---:|
| minecraft:leather | 14.741 | 5.307 | 5.5 |
| minecraft:chainmail | 14.116 | 5.082 | 5.5 |
| minecraft:golden | 14.241 | 5.127 | 5.5 |
| minecraft:iron | 13.742 | 4.947 | 5.5 |
| minecraft:diamond | 8 | 2.88 | 5.5 |
| minecraft:netherite | 7.025 | 2.529 | 5.5 |
| alloyed:steel | 10.875 | 3.915 | 5.5 |
| deep_aether:stratus | 8.697 | 3.131 | 5.5 |
| deeperdarker:warden | 3.876 | 1.395 | 5.5 |
| cataclysm:ignitium | 3.123 | 1.124 | 5.5 |
| cataclysm:cursium | 3.123 | 1.124 | 5.5 |
| deeperdarker:resonarium | 0 | unsupported | 5.5 |

Values are hearts lost; one heart is 2 HP. A result at or above 10 hearts can exhaust a normal 20-HP health bar. Protection applies only where the native damage type permits it. The matrix retains actual registered equipment and supported enchantments. Extra measurement health avoids truncation on death; native percent-health attacks are either supplied captured 20-HP inputs or explicitly identified capped native probes. Full Resonarium can spend item durability instead of health, so repeated-hit results are reported separately. Aether matching-glove variants use the actual accessory slot.

## Evidence and limits

[Attack and phase evidence](boss-coverage-results.md), [native scenario records](all-native-scenarios.csv), [all assertions](all-assertions.csv), and the raw JSON retain individual outcomes. Controlled attack-frame probes reset between hits and do not measure whole-combo DPS. Joint-projectile cases preserve immediate impact/splash ordering. Native minions, burning, poison, Wither, falls and overlapping later attacks can add damage outside a single-hit ceiling. Server boss-bar checks establish state/progress/tracking; rendered client appearance requires the separate client evidence.

## Continuous encounter sample

46 bounded encounters are recorded separately from the passing assertions. Their outcomes are observations, not an assertion that every boss was defeated or every attack appeared. Automated client control is identified in each encounter record and is not presented as human play.

| Outcome | Encounters |
|---|---:|
| lost_downed | 28 |
| time_limit | 17 |
| won | 1 |

| Boss | Plain diamond loadout | Protection IV netherite loadout |
|---|---|---|
| bosses_of_mass_destruction:gauntlet | lost_downed (9.45 s) | lost_downed (77.45 s) |
| bosses_of_mass_destruction:lich | time_limit (180 s; limited) | time_limit (180 s) |
| bosses_of_mass_destruction:obsidilith | lost_downed (17.55 s) | time_limit (180 s) |
| bosses_of_mass_destruction:void_blossom | lost_downed (14.05 s; limited) | time_limit (180 s) |
| cataclysm:ancient_remnant | lost_downed (22.75 s) | lost_downed (56.75 s) |
| cataclysm:ender_guardian | lost_downed (15 s) | lost_downed (135.3 s) |
| cataclysm:ignis | lost_downed (9.55 s) | lost_downed (19.1 s) |
| cataclysm:maledictus | lost_downed (8.15 s) | lost_downed (25.1 s) |
| cataclysm:netherite_monstrosity | lost_downed (163 s) | lost_downed (97.2 s) |
| cataclysm:scylla | lost_downed (10.5 s) | lost_downed (34.4 s) |
| cataclysm:the_harbinger | lost_downed (6.55 s) | lost_downed (28.55 s) |
| cataclysm:the_leviathan | lost_downed (47.55 s) | lost_downed (85.75 s) |
| minecraft:ender_dragon | time_limit (180 s; limited) | time_limit (180 s; limited) |
| minecraft:warden | time_limit (180 s; limited) | lost_downed (37.15 s) |
| minecraft:wither | lost_downed (50.05 s) | lost_downed (45.9 s) |
| twilightforest:alpha_yeti | time_limit (180 s) | time_limit (180 s) |
| twilightforest:hydra | lost_downed (22.45 s) | time_limit (180 s) |
| twilightforest:knight_phantom | time_limit (180 s) | time_limit (180 s) |
| twilightforest:lich | lost_downed (27.2 s) | lost_downed (107.75 s) |
| twilightforest:minoshroom | lost_downed (176.5 s) | won (101.85 s) |
| twilightforest:naga | time_limit (180 s) | time_limit (180 s) |
| twilightforest:snow_queen | lost_downed (74.05 s) | time_limit (180 s) |
| twilightforest:ur_ghast | time_limit (180 s; limited) | time_limit (180 s) |

[Every encounter](client-encounters/fight-summary.md), [outcomes and equipment use](client-encounters/encounters.csv), [server event timeline](client-encounters/server-timeline.csv), [client inputs](client-encounters/client-timeline.csv) and [received boss-bar observations](client-encounters/client-bars.csv) retain the individual evidence. The two encounter loadouts also use different weapons; the separate armor matrix isolates armor effects. The machine outcome lost_downed means a recorded lethal knockout and remains a defeat. A nonlethal ragdoll/down state that accepts ordinary crouch get-up input is a different condition. The [action audit](client-encounters/action-audit.json), [between-case cleanup log](client-encounters/between-case-cleanup.jsonl) and [ordinary native input log](client-encounters/native-recovery-inputs.jsonl) separate recorded lethal defeats from nonlethal get-ups. Logged cleanup commands follow a recorded knockout and precede the next encounter; they do not turn a loss into a win. Native phase or attack selection is not forced during these encounters; attack and phase evidence comes from the separately documented controlled fixtures, not from assuming every natural encounter reaches every phase.

[Reviewed native observations](client-observation-notes.md) explain protected phases, native group bars, shield evidence and environmental damage. [Client environment](client-environment.json) discloses test-only adjustments, invalidated earlier trials and native corpse/ragdoll remains in the reused arena. [Export provenance](client-encounters/export-provenance.json) records the final input and exporter hashes. These bounded automated outcomes are not estimates of human win rates.

Rows marked limited have the specific fixture/control restrictions retained in the [exposure classifications](client-exposure-classifications.json). They are not claimed as complete native fights or full attack/phase observations. The Dragon Hover/zero-crystal and zero-damage Warden samples are especially limited; controlled native numerical probes supply their attack/phase evidence.

Production JAR SHA-256: `871F3C0ECF84236ADFDC5457C09C9D2C8EE34BC9582CE148C899109FDA822F70`.
Latest installed test helper SHA-256: `2CA3BA0D197C345B5CE1CFABD22833BB1EE2A22AD8BCC9EA39960D9662B78EF0`.

The controlled suites were executed with the helper builds listed below against the same production JAR. Later helper builds extend the test fixtures and continuous encounter workflow; the latest installed helper is not claimed to have executed every earlier suite.

| Executed helper SHA-256 | Controlled suites |
|---|---|
| 5F065867E3758A5ABB3F309F4681942570247E809F3D4654FA49B33C48172424 | config_migration, damage, health, boss_bars, multiplayer_targets, actual_attacks, vanilla, exhaustive_twilight, exhaustive_bmd, exhaustive_cataclysm |
| B28200733D87A607A63024F55A1AC96C322DC80D84DCA85E775E8142FDE6CF58 | fixed_health, armor_matrix, armor_effects, preinstall_verify_keep, installed_restart_verify |
| 5022EA4143297AD7256C980024495BC19F9D5E4642DB5C3132C10D01DB06886D | combined_projectiles |

The test helper is test-only and must not be installed on the survival server. The release package contains the production mod, optional example config and reports; copied third-party mods remain in the isolated test environment.
