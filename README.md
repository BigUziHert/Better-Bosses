# Better Bosses

Survival Boss Balance 1.2.1 is a NeoForge add-on for Minecraft Java 1.21.1. It gives 23 bosses fixed maximum health and tunes their attacks against players: the Wither, Ender Dragon, Warden, and 20 bosses from Twilight Forest, Bosses of Mass Destruction, and Cataclysm.

## Behavior

- Every covered boss uses its configured maximum HP at any party size. Existing bosses saved with the previous party multiplier migrate when their entities load, preserving their remaining health percentage.
- Schema-2 damage settings use hearts lost on Hard with full **unenchanted diamond** armor. Rules can set a target, cap a stronger hit, or scale a native attack proportionally. Minecraft still processes the player's actual armor, effects, shielding, and hit cooldowns.
- Ur-Ghast fireball impact and explosion share an 8-heart reference budget per projectile and victim. Harbinger missiles use the same combined limit. Rejected or shield-blocked hits do not spend the budget. Native smaller blasts can deal less than the limit.
- Ignis's marked ultimate is capped at 9.5 reference hearts; its other heavy hits cap at 8. Alpha Yeti falling ice keeps its 5.25-heart ceiling and native enchantment bypass. Maledictio Anima preserves its prior raw cap, approximately 6.795 diamond hearts or 6 hearts in Protection IV netherite.
- The Naga uses a segment threshold of one-tenth of its maximum health. The server handles gameplay. Clients can install the same JAR for the matching segment visuals.
- The Wither's spawn-charge healing scales with its maximum health so its charge bar can finish with full health. Passive combat regeneration and Wither damage-over-time retain their native values.
- Existing native boss bars retain their encounter-specific behavior, including health fractions, Lich shields, Leviathan phases, Knight group totals, and Wither spawn-charge progress. The Warden retains its native behavior without a boss bar.
- Leviathan's displayed progress is clamped to the valid 0–1 range during its half-health transition. Its native phase formula and transition timing are preserved.
- A solo Knight Phantom's uninitialized group count is repaired before native AI updates its shared health bar. Established group totals remain intact when Knights die or unload.

The [example configuration](config/survival-boss-balance.json) lists 23 health values and 73 attack/budget settings. `healthTargets` takes maximum HP. `damageTargets` takes plain-diamond reference hearts. The mod reads the config at startup and uses built-in defaults for missing entries. Legacy party-scaling fields are ignored.

Unversioned configurations from earlier releases are migrated in memory without rewriting the file. Unchanged legacy defaults adopt the new balance. Custom legacy attack values retain their old Protection IV netherite reference through `legacyDamageTargets`. When creating a new schema-2 config, use plain-diamond units for `damageTargets`. The newly introduced combined budgets use plain-diamond units even when individual legacy attacks have custom overrides.

## Vanilla encounters

Reference hearts mean Hard difficulty and full unenchanted diamond. Weaker equipment can take more damage. Separate hits, damage over time and falls can still add up; a single-hit ceiling does not cap an entire combo.

| Encounter | Fixed HP | Attack tuning in reference hearts |
|---|---:|---|
| Wither | 900 | Direct skull 2.5; skull explosion capped at 2; spawn explosion capped at 7. Native Wither damage over time is unchanged. |
| Ender Dragon | 1,100 | Head/neck 6; wing 2; perched breath 0.75; fireball cloud 1.25 per application. |
| Warden | 1,300 | Melee 8; sonic boom 5.5, retaining its armor and enchantment bypass. |

Dragon head and wing collisions are identified from native attack methods. Cloud damage is identified from the cloud's saved harm effect, so a fireball cloud retains its damage when the dragon lands or the cloud reloads. The original encounters still control shields, targets, phases, timings, crystal healing, drops, and player attacks against the bosses. A vanilla or modded boss already saved before installation receives its configured maximum when loaded, preserving its remaining health percentage.

## Build

Use JDK 21. From the project root, run `./gradlew build` on macOS or Linux, or `./gradlew.bat build` on Windows. The JAR is written to `build/libs/survival-boss-balance-1.2.1.jar`.

The build pins NeoForge 21.1.264 and resolves its development dependencies through Gradle.

## Install

1. Stop the NeoForge server and put the built JAR in `mods/`, replacing any earlier version of Survival Boss Balance.
2. An existing `config/survival-boss-balance.json` can stay in place: the in-memory migration preserves custom legacy values and upgrades unchanged defaults. For a new installation or custom numbers, use the example configuration and preserve intentional custom values in the appropriate reference units.
3. Start the server and look for `Survival Boss Balance 1.2.1 loaded` in the log.

## Verification

**The requested server-readiness standard remains unmet: native victories were verified for 22 of 23 bosses.** The final Ignis attempt reached its 20-minute game-time limit with the boss alive, and a later Leviathan attempt encountered an unresolved Sable/Lithium server stall. Controlled checks passed, but the build is not cleared against the requested all-boss victory requirement. Full native attack and phase coverage has qualifications recorded in the fight report.

The [1.2.1 controlled regression](tests/reports/1.2.1/REGRESSION-1.2.1.md) and [Dragon transition supplement](tests/reports/1.2.1/supplements/dragon-death-transition/README.md) record 8,383 passing assertions across their separate executions. The [current fight readiness report](tests/reports/victory-validation/READINESS-SUMMARY.md) lists each boss's verified result, armor wear, apple use and remaining verification limits. It also documents the observed Sable/Lithium runtime stall.

The [1.2.0 regression report](tests/reports/1.2.0/REGRESSION-1.2.0.md) records **7,785 passing assertions and zero failures** against the release JAR. Controlled checks cover all 23 bosses, fixed health across multiplayer conditions, existing saved entities before installation and after another restart, native boss-bar state, attacks/phases, and configuration migration. The [behavior audit](tests/reports/1.2.0/behavior-scope.md) confirms that this release adds no core AI, movement, phase or attack-timing changes.

The [73-profile damage comparison](tests/reports/1.2.0/approved-damage-comparison.md) shows measured plain-diamond and Protection IV netherite results. [All 90 armor outfits](tests/reports/1.2.0/armor-results.md), [13,120 individual attack measurements](tests/reports/1.2.0/armor-all-attacks.csv), and [258 set-effect/endurance measurements](tests/reports/1.2.0/armor-effects-results.md) include vanilla and installed modded equipment. [Damage limits](tests/reports/1.2.0/damage-limits.md) distinguish single-hit reference ceilings from combinations, status damage and untested defense conditions.

The separate [46 automated native-client observations](tests/reports/1.2.0/client-encounters/fight-summary.md) ended in one win, 28 recorded lethal knockouts and 17 time limits. They are supplemental observations, not 46 passing fights or proof that every natural encounter reached every phase. [Reviewed observations and limitations](tests/reports/1.2.0/client-observation-notes.md) disclose protected phases, controller limitations, the Dragon Hover/zero-crystal fixture, and the diamond Warden sample without combat exposure. The loadouts also use different weapons; the controlled armor matrix isolates armor effects.

Tests used isolated copies of the survival server's mods/configuration. This task made no changes to `S:\Survival`; the [read-only source and artifact audit](tests/reports/1.2.0/source-and-artifact-audit.md) separately records a source-server restart's generated timestamp comment and timestamp-only rewrites. The tested production JAR and executed helper versions have explicit hashes.

The test-only mod under `tests/` requires `-Dsurvival.bossBalance.testMode=true` and must never be installed on a production server. Controlled fixtures and client observations retain their separate scopes and provenance.

The historical [1.1.0 regression report](tests/reports/REGRESSION-2026-11-04.md) describes the previous release, not validation of 1.2.0. See [harness instructions](tests/TESTING.md) for the controlled suites. The optional `runClientPlaytest` Gradle task builds a separate development-client controller under `tests/client/`; its code and metadata are excluded from the production JAR.
