# Better Bosses

Survival Boss Balance 1.1.0 is a NeoForge add-on for Minecraft Java 1.21.1. It gives 23 bosses fixed maximum health and tunes their attacks against players: the Wither, Ender Dragon, Warden, and 20 bosses from Twilight Forest, Bosses of Mass Destruction, and Cataclysm.

## Behavior

- Every covered boss uses its configured maximum HP at any party size. Existing bosses saved with the previous party multiplier migrate when their entities load, preserving their remaining health percentage.
- Damage settings use hearts lost on Hard with full netherite and Protection IV. Rules can set a target, cap a stronger hit, or scale a native attack proportionally. They apply per hit. Minecraft still processes the player's actual armor, effects, shielding, and hit cooldowns.
- The Naga uses a segment threshold of one-tenth of its maximum health. The server handles gameplay. Clients can install the same JAR for the matching segment visuals.
- The Wither's spawn-charge healing scales with its maximum health so its charge bar can finish with full health. Passive combat regeneration and Wither damage-over-time retain their native values.
- Existing native boss bars retain their encounter-specific behavior, including health fractions, Lich shields, Leviathan phases, Knight group totals, and Wither spawn-charge progress. The Warden retains its native behavior without a boss bar.
- A solo Knight Phantom's uninitialized group count is repaired before native AI updates its shared health bar. Established group totals remain intact when Knights die or unload.

The [example configuration](config/survival-boss-balance.json) lists the 23 health values and 69 attack settings. `healthTargets` takes maximum HP. `damageTargets` takes reference hearts. The mod reads the config at startup and uses the built-in values for missing entries, including the vanilla profiles when upgrading an old config. Legacy party-scaling fields are ignored.

## Vanilla encounters

Reference hearts mean Hard difficulty, full netherite, and Protection IV on all four pieces. Weaker equipment can take more damage. Caps apply per damage event, and overlapping impacts, effects, and falls can add up.

| Encounter | Fixed HP | Attack tuning in reference hearts |
|---|---:|---|
| Wither | 900 | Direct skull 1.5; skull explosion capped at 2; spawn explosion capped at 5. Native Wither II remains 0.18 per damage tick against the reference gear. |
| Ender Dragon | 1,100 | Head/neck 3.5; wing 1.25; perched breath 1.25; fireball cloud 2 per application. |
| Warden | 1,300 | Melee 4.5; sonic boom 5.5, retaining its armor and enchantment bypass. |

Dragon head and wing collisions are identified from native attack methods. Cloud damage is identified from the cloud's saved harm effect, so a fireball cloud retains its damage when the dragon lands or the cloud reloads. The original encounters still control shields, targets, phases, timings, crystal healing, drops, and player attacks against the bosses. A vanilla or modded boss already saved before installation receives its configured maximum when loaded, preserving its remaining health percentage.

## Build

Use JDK 21. From the project root, run `./gradlew build` on macOS or Linux, or `./gradlew.bat build` on Windows. The JAR is written to `build/libs/survival-boss-balance-1.1.0.jar`.

The build pins NeoForge 21.1.264 and resolves its development dependencies through Gradle.

## Install

1. Stop the NeoForge server and put the built JAR in `mods/`, replacing any earlier version of Survival Boss Balance.
2. An existing `config/survival-boss-balance.json` can stay in place: missing vanilla entries use the built-in defaults. For a new installation or custom numbers, use the example configuration and preserve any existing custom values.
3. Start the server and look for `Survival Boss Balance 1.1.0 loaded` in the log.

## Verification

The test-only mod under `tests/` runs on an isolated NeoForge 21.1.264 server copied from the survival pack. It requires `-Dsurvival.bossBalance.testMode=true` and must never be installed on a production server. Its reports distinguish controlled native attack/phase probes from continuous player-driven fights. The 1.1.0 regression report records actual coverage, armor measurements, saved-world migration, and any remaining limitations.

The [4 November regression report](tests/reports/REGRESSION-2026-11-04.md) records 6,790 passing assertions, 12,464 armor attack measurements across 90 outfits, and 258 set-effect/endurance measurements. See [all armor results](tests/reports/armor-results.md) and [harness instructions](tests/TESTING.md).
