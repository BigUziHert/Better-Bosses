# Better Bosses

Survival Boss Balance 1.0.2 is a NeoForge add-on for Minecraft Java 1.21.1. It gives 20 bosses from Twilight Forest, Bosses of Mass Destruction, and Cataclysm fixed maximum health and tunes their attacks against players.

## Behavior

- Every covered boss uses its configured maximum HP at any party size. Existing bosses saved with the previous party multiplier migrate when their entities load, preserving their remaining health percentage.
- Damage settings use hearts lost on Hard with full netherite and Protection IV. Rules can set a target, cap a stronger hit, or scale a native attack proportionally. They apply per hit. Minecraft still processes the player's actual armor, effects, shielding, and hit cooldowns.
- The Naga uses a segment threshold of one-tenth of its maximum health. The server handles gameplay. Clients can install the same JAR for the matching segment visuals.

The [example configuration](config/survival-boss-balance.json) lists the 20 health values and 60 attack settings. `healthTargets` takes maximum HP. `damageTargets` takes reference hearts. The mod reads the config at startup and uses the built-in values for missing entries.

## Build

Use JDK 21. From the project root, run `./gradlew build` on macOS or Linux, or `./gradlew.bat build` on Windows. The JAR is written to `build/libs/survival-boss-balance-1.0.2.jar`.

The build pins NeoForge 21.1.264 and resolves its development dependencies through Gradle.

## Install

1. Stop the NeoForge server and put the built JAR in `mods/`, replacing any earlier version of Survival Boss Balance.
2. To customize the numbers, copy the example to `config/survival-boss-balance.json` in the server directory and edit the values.
3. Start the server and look for `Survival Boss Balance 1.0.2 loaded` in the log.

## Verification

The 1.0.2 build passed 2,542 checks on an isolated NeoForge 21.1.264 server with Twilight Forest 4.8.3345, Bosses of Mass Destruction 1.3.3, and Cataclysm 3.33. The checks covered fixed HP, saved-boss migration, simulated party changes and target selection, damage rules, and representative native attacks.
