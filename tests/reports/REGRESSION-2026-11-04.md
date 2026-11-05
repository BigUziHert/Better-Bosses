# Better Bosses 1.1.0 regression results — 4 November 2026

**6790 assertions passed, zero failures.** The run includes all 23 managed bosses, 90 armor outfits, 12464 per-attack armor measurements, and 258 additional set-effect/endurance measurements. These are controlled dedicated-server tests using the installed mod code, not complete fights played by people.

The new build includes fixed **Wither 900 HP**, **Ender Dragon 1,100 HP**, and **Warden 1,300 HP**, with the attack values from the earlier discussion. It also fixes Wither spawn healing at increased HP and the native solo/late-arriving Knight Phantom health-bar counter. The existing 20 boss profiles and attack tuning remain in place.

**This work made no changes to S:\Survival and the new build was not installed there.** Mods/configs were copied into an isolated test directory. Original mod JARs and boss/combat configs matched the read-only baseline; an independently running production server regenerated one unrelated zfastnoise config file during testing. [Source audit](survival-read-only-audit.txt).

## What passed

- Fixed HP at 0, 1, 2, 3, 4, 5 and 10 nearby simulated players; target changes, distance changes, spectators, joining/leaving, wounded reloads and old party-multiplier migration. There is no online/nearby player multiplier in the balance code. Twilight native multiplayer adjustment is NONE. Cataclysm has a separate displayed lives counter; its installed Retry implementation does not change HP or attacks.
- A real world saved without the balance mod was stopped, then started with this build: all 46 existing full/wounded bosses received their targets. Before/after entity-load snapshots prove native max HP and no modifier before installation, followed by the new maximum and preserved health ratio. Native idle regeneration after loading is measured separately, not mistaken for migration healing.
- Native server boss bars: full/half/low HP, visibility, viewer tracking/rejoining, save/load, Lich shields and clones, six-Knight shared totals after deaths, solo/late Knights, Leviathan segmented phase bar, Cataclysm rage/dormancy, Wither spawn charge, and YUNG Dragon pre-ritual/spawned behavior. Warden retains its native absence of a bar.
- Controlled native attacks and phase gates for all 23 bosses, including emitted projectiles/effects, Cataclysm rage/Brand levels, vanilla Wither charge/arrow immunity/Wither II, Dragon breath cycles/perch threshold/crystal healing, and Warden emerging/sniffing/roaring/digging behavior. All 33 active Cataclysm outgoing damage helpers have accepted native captures; 176 preliminary pose misses are reconciled with child effects, alternate hitboxes, dedicated probes or non-damaging stages.

| Suite | Assertions | Failed |
|---|---:|---:|
| actual_attacks | 76 | 0 |
| armor_effects | 3 | 0 |
| armor_matrix | 90 | 0 |
| boss_bars | 367 | 0 |
| damage | 141 | 0 |
| exhaustive_bmd | 1111 | 0 |
| exhaustive_cataclysm | 585 | 0 |
| exhaustive_twilight | 247 | 0 |
| fixed_health | 3031 | 0 |
| health | 639 | 0 |
| multiplayer_targets | 155 | 0 |
| preinstall_prepare | 46 | 0 |
| preinstall_verify | 230 | 0 |
| vanilla | 69 | 0 |

## Armor results

[Every armor outfit](armor-results.md) lists plain/Protection IV results. [Every measured attack × outfit](armor-all-attacks.csv) contains the full matrix. [Set effects and durability](armor-effects-results.md) includes actual player ticking, Aether gloves, worn Zanite and repeated Resonarium hits. Unsupported Protection enchantments are omitted; partial outfits leave missing slots empty.

| Full set | Warden melee, plain | Warden melee, Protection IV | Sonic boom |
|---|---:|---:|---:|
| minecraft:leather | 21.137 | 7.609 | 5.5 |
| minecraft:chainmail | 20.242 | 7.287 | 5.5 |
| minecraft:golden | 20.421 | 7.351 | 5.5 |
| minecraft:iron | 19.704 | 7.093 | 5.5 |
| minecraft:diamond | 14.505 | 5.222 | 5.5 |
| minecraft:netherite | 12.5 | 4.5 | 5.5 |
| alloyed:steel | 19.167 | 6.9 | 5.5 |
| deep_aether:stratus | 15.938 | 5.738 | 5.5 |
| deeperdarker:warden | 7.58 | 2.729 | 5.5 |
| cataclysm:ignitium | 4.478 | 1.612 | 5.5 |
| cataclysm:cursium | 4.478 | 1.612 | 5.5 |
| deeperdarker:resonarium | 0 | not supported | 5.5 |

All values are hearts lost on Hard. One heart is 2 HP. More than 10 hearts can exhaust the entire health bar of a normal player; RagRevival can turn a lethal outcome into downing. Extra health on the measurement victim prevents death/downing from truncating the recorded damage.

Phoenix armor with matching gloves cancels the tested Hydra fire damage; four armor pieces without gloves do not receive that set immunity under these server settings. Fresh Resonarium transfers armor-sensitive damage to durability and blocked all 12 sampled Warden melee hits. At 75% worn, health damage began on hit 5; at 99% worn, the first hit broke the set and the second cost 22.391 hearts. Magic and sonic boom still hurt. These are native armor mechanics, not universal damage caps.

## Fixed HP and vanilla attack values

| Boss | Fixed max HP |
|---|---:|
| twilightforest:naga | 1000 |
| twilightforest:lich | 650 |
| twilightforest:minoshroom | 1000 |
| twilightforest:knight_phantom | 180 |
| twilightforest:alpha_yeti | 1100 |
| twilightforest:hydra | 1500 |
| twilightforest:ur_ghast | 1100 |
| twilightforest:snow_queen | 1200 |
| bosses_of_mass_destruction:lich | 1300 |
| bosses_of_mass_destruction:void_blossom | 1400 |
| bosses_of_mass_destruction:gauntlet | 1500 |
| bosses_of_mass_destruction:obsidilith | 1700 |
| cataclysm:netherite_monstrosity | 1200 |
| cataclysm:ender_guardian | 850 |
| cataclysm:the_harbinger | 950 |
| cataclysm:ancient_remnant | 1000 |
| cataclysm:the_leviathan | 1100 |
| cataclysm:scylla | 1100 |
| cataclysm:maledictus | 1100 |
| cataclysm:ignis | 1100 |
| minecraft:wither | 900 |
| minecraft:ender_dragon | 1100 |
| minecraft:warden | 1300 |

Against full Protection IV netherite on Hard: Wither direct black/blue skulls measured 1.5 hearts; skull splash measured 1.539 in the full-exposure probe, below its 2-heart cap; spawn explosion capped at 5; Wither II remains 0.18 per damage tick. Dragon head/neck measured 3.5, wing 1.25, perched breath 1.25 and fireball cloud 2 per application. Warden melee measured 4.5 and sonic boom 5.5, retaining armor/enchantment bypass.

## Evidence and limits

[Boss-by-boss attack/phase evidence](boss-coverage-results.md), [Cataclysm coverage reconciliation](cataclysm-coverage-adjudication.md), [all assertions](all-assertions.csv), and [native scenario records](all-native-scenarios.csv) retain the individual outcomes. Raw JSON is included in the evidence package. Earlier failed fixtures and candidate runs remain archived in the isolated test checkout; the report uses final passing outputs.

The tests use simulated server players and staged native attack/phase methods. They do not establish continuous human multiplayer fights, every random trajectory or environmental combination, accumulated overlapping hazards, every accessory combination, or visually rendered client HUDs/animations. Boss-bar results validate server events/progress/visibility/tracking, not screenshots. Armor comparisons use the specified gear and conditions; the separate set-effect tests cover 30 complete modded combinations on dry ground, with documented glove/durability variants.

Runtime: Minecraft 1.21.1; NeoForge 21.1.264; Java 21.0.11+10; Twilight Forest 4.8.3345; Bosses of Mass Destruction 1.3.3; Cataclysm 3.33; the copied full survival mod/config pack. The copied legacy 20-boss config was retained during execution, so the new vanilla defaults were also tested without requiring a config replacement.

Production JAR SHA-256: `135D556668DC93906689E85E2697B8A18EA508FA4CFAACEFBEA8EEE879B86709`.
Test helper SHA-256: `0DA21B69BE3F9314CA3129EA2F37D645296BD0A158FB877305252357D71E250A`.

The deliverable contains the production JAR, optional example config and reports. The test helper and third-party mods are not part of the install package. Do not install the test helper on the survival server.
