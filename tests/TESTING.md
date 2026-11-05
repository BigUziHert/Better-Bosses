# Boss regression and client playtest harness

These helpers are for a disposable Minecraft 1.21.1 / NeoForge 21.1.264 server only. They spawn and discard entities, alter gamerules, generate an arena, and save test results. Never put the helper JAR on a real server. Every entrypoint requires `-Dsurvival.bossBalance.testMode=true`.

## Setup

Use JDK 21. Create `run-regression/` under the repository using **copies** of the server's `mods`, `config`, `defaultconfigs`, `libraries`, `kubejs`, and `world/serverconfig`. Create a new disposable world; do not copy or launch the original world. Retain the original combat settings. Set the test game server and voice-chat server to unused loopback ports. **Never change anything in `S:\Survival`, including mods, configs, logs, or worlds.** All launches, generated files, configuration changes, and world restores belong inside the isolated repository test directories.

Build the production mod with `gradlew.bat build --offline` when the required Gradle dependencies are already cached. Build the helper with `tests/build-tests.ps1`; it uses the copied patched NeoForge server JAR first on its compilation classpath. Stop the test server before replacing either JAR in its `mods` directory. Record the production/helper SHA-256 hashes with each run.

Start from `run-regression/` using Java 21, the test-mode JVM flag, and `@libraries/net/neoforged/neoforge/21.1.264/win_args.txt nogui`. Runtime results go to `tests/results/`. The **1.2 reference is Hard difficulty, 20 HP, plain unenchanted diamond armor: 20 armor and 8 toughness**. Actual equipment, source bypass flags, effects, shields, cooldowns, and absorption are handled by the native damage pipeline. Protection IV netherite and modded outfits are separate measured configurations. Approved expectations live in `approved-damage-targets-1.2.json` and the independent test oracle.

Schema 2 damage targets use `damageReference: "hard_plain_diamond"`. Loading an unversioned/schema-1 config upgrades unchanged published 1.1 defaults to 1.2 defaults in memory. Custom old entries retain their old netherite/Protection IV calibration in explicit `legacyDamageTargets`, including each native source's bypass flags. The loader does not rewrite the input file. New combined projectile budgets always use plain-diamond reference hearts and also constrain custom individual attacks. `bbtest config_migration` tests defaults, custom and inherited caps, repeated migration, invalid settings, and unchanged file contents using disposable fixture files; it restores the active runtime settings afterward.

## Commands

| Command | Scope |
|---|---|
| `bbtest health` | All 23 profiles, health ratios, serialization, legacy modifiers, controls |
| `bbtest fixed_health` | 0/1/2/3/4/5/10 nearby simulated players, distance/spectator changes, joining/leaving, old party-profile migration |
| `bbtest damage` | Independent attack inputs/expectations, caps, effects, attribution and control victims |
| `bbtest config_migration` | Legacy defaults/custom overrides, schema validation, raw calibration, file preservation |
| `bbtest combined_projectiles` | Native Ur-Ghast/missile impact plus blast, shields, reflection, cooldown rejection, separate victims/projectiles; diamond and Protection IV netherite; separately labeled power-4, repeated-callback and no-invulnerability-frame budget stress cases |
| `bbtest actual_attacks` | Representative real modded attack methods |
| `bbtest vanilla` | Native Wither/Dragon/Warden attacks, phase transitions, lifecycle behaviors, health persistence |
| `bbtest boss_bars` | Native server bar progress, shields, group totals, rage, visibility and tracking |
| `bbtest multiplayer_targets` | Native target-goal integration with simulated players |
| `bbtest exhaustive twilight` | Staged native Twilight attack and phase probes |
| `bbtest exhaustive bmd` | Staged native Bosses of Mass Destruction attack and phase probes |
| `bbtest exhaustive cataclysm` | Staged native Cataclysm attack, emission, impact and phase probes |
| `bbtest armor_matrix` | Registered armor outfits, supported Protection IV variants, per-attack runtime measurements |
| `bbtest armor_effects` | Full modded sets after actual player ticks, Aether gloves, worn Zanite and Resonarium endurance |
| `bbtest preinstall_prepare` | Save 46 native full/wounded bosses without the production add-on |
| `bbtest preinstall_verify_keep` | Verify first installation on all 23 saved types, retain all 46, and save for another restart |
| `bbtest installed_restart_verify` | Verify all 46 already-migrated UUIDs after another complete JVM restart |
| `bbtest preinstall_verify` | First-install verification with immediate fixture cleanup; alternative to the keep/restart sequence |
| `bbtest prepare_restart` / `bbtest verify_restart` | Older two-boss wounded persistence check; the all-23 sequence above is broader |

## Installing into a pre-existing world

Remove only the production balance JAR from the **test copy**, retaining the optional test helper. Run `bbtest preinstall_prepare`, then stop the test server. This saves full and wounded instances of all 23 types with their native HP. Preserve that test-world snapshot and `preinstall-manifest.json`.

Install the production balance JAR into the test copy and start the same saved world. Allow asynchronous entity loading to complete before running `bbtest preinstall_verify_keep`. Test listeners capture health and max HP immediately before and after the real entity-join event. This checks the native pre-install maximum, absence of the balance modifier, new maximum, and preserved health ratio. The command writes `preinstall-load-evidence.json` and `installed-restart-manifest.json`, keeps the entities/chunks loaded, and saves the world.

After a clean stop, preserve the installed world and first-load evidence. Restart that same world with the same production JAR and helper, wait for entity loading, then run `bbtest installed_restart_verify`. This checks all 46 original UUIDs, persisted profile version/modifier/maximum, and unchanged health across the second join event. Native idle healing between saves is recorded separately and is not mistaken for migration healing. Successful completion should produce 230 first-install assertions and 322 second-restart assertions; these are expected counts, not a claim that a run passed. Read the actual result files.

For a rerun, stop the isolated server first, validate that the resolved current-world, backup, and snapshot paths stay under this repository, move the current world to a unique evidence directory, and copy the untouched native snapshot back to `run-regression/world`. Restore its paired `preinstall-manifest.json`. Never overwrite the only snapshot or reuse a manifest from another world. The existing `tests/evidence/world-before-install` snapshot is paired with `tests/evidence/1.1.0-final-before-1.2/results/preinstall-manifest.json`; `native-world-snapshot-audit-1.2.json` records a direct region/NBT UUID audit and hashes.

## Continuous encounters

| Command | Scope |
|---|---|
| `bbtest fight start <boss-id\|all> <diamond\|netherite_p4\|both> [ticks]` | Continuous native-AI encounter with an automated server player |
| `bbtest fightclient <boss-id\|all> <diamond\|netherite_p4\|both> [ticks] [player]` | Same encounter with a connected client; default player `BossPlaytest` |
| `bbtest fight status` | Current encounter, queue and completed outcomes |
| `bbtest fight cancel` | Stop the queue and record cancellation |

The default limit is 3,600 game ticks (180 game seconds); accepted limits are 200–24,000 ticks. Lag can make wall time longer. `all both` queues 46 trials. These are automated encounters, not human play or an assertion that every attack naturally occurred.

Each trial starts with 20 HP, fresh equipment, sword, bow, arrows, shield and ordinary food. The netherite trial includes Protection IV and a Power V bow, so its win/loss comparison also reflects the documented weapon loadout. Natural food regeneration and mob griefing are enabled. After combat begins there are no health, damage, phase, or cooldown resets, extra immunity, potions, or golden apples. Native damage, durability, summons, projectiles, shields and lethal/downed outcomes continue normally. A time limit is an unfinished encounter, not a pass. Any native respawn requested after a loss occurs after that outcome was recorded.

Most bosses use a constructed flat arena at the test world's ground level (-60); Leviathan uses a water arena. The arena edge does not introduce an artificial elevated fall. The Dragon uses the actual End dimension, fight manager, and generated towers/crystals. Initial boss spawning/activation and restored Dragon crystals are setup operations, with their details recorded in each log. Structure-specific geography, travel and summoning acquisition are not reproduced. Native attack/phase observations are recorded; an unobserved move requires separate evidence from the staged probes.

The saved source-world rules were inspected read-only for the 1.2 run. Hard difficulty, natural regeneration, mob griefing, inventory loss on death, fall/fire/drowning damage, and enforced Twilight progression match the continuous-fight conditions. Ambient mob spawning is disabled in the trials but enabled in the source world. The isolated world also retains advancement announcements and a 100% sleeping threshold, versus disabled announcements and 0% in the source; the trials do not sleep. Staged damage suites disable regeneration and griefing for measurement, then continuous fights explicitly enable them. See the local audit `audit/world-conditions-comparison-1.2.json` for the saved NBT values and timestamps; a saved file is not a live server query.

Continuous logs and summaries are stored under `tests/results/full-fights/<timestamp>/`. They distinguish `simulated_server_player` from `real_client`. Preserve each trial's terminal outcome, damage events, phase observations, setup notes and controller errors.

## Isolated rendered client

The `clientPlaytest` source set in `build.gradle` is test-only and is excluded from the production JAR. Use `run-client-playtest/` with the same Minecraft 1.21.1 / NeoForge **21.1.264** as the copied dedicated server. Populate it from the isolated copies of the survival server's 219 mod JARs and configuration; use this development profile rather than launching or altering a CurseForge profile. Do not add the server helper or a second production balance JAR to the client mods folder: the development run loads production `main` sources and the pilot source set directly.

The present client copy adds exactly three client rendering dependencies absent from the server mod folder: `fusion-1.3.16-neoforge-mc1.21.1.jar`, `SimplyTooltips-neoforge-0.1.5.jar`, and `sodium-neoforge-0.8.13+mc1.21.1.jar`. Keep their inventory/hashes with the client evidence. These additions belong only to the isolated client.

The development client needs JVM property `-Dowo.handshake.enabled=false` (Gradle run setting `systemProperty 'owo.handshake.enabled', 'false'`) to match this installed oωo library's production-server default. Its development default otherwise registers a different required channel set and rejects the connection with missing `owo:handshake_off`. This is a client launch compatibility setting, not a change to the server or its source configs.

FlightLib 4.0.36 also treats diamond and gold chestplates as free jetpacks only when NeoForge reports a development environment. This check is independent of oωo's debug properties. The test-client `ProductionFlightSources` helper must run during client setup: it positively identifies and removes only that development caster, preserving the normal equipment/accessory flight providers. This restores the production-server behavior for plain armor. Keep its initialization log with the evidence and verify ordinary falling/movement before recording encounters; observations made while the development chestplate jetpack was active are not representative production fights. The helper is excluded from the production balance JAR.

Run `gradlew.bat runClientPlaytest --offline`. The configured username is `BossPlaytest`, window size 1280×720, and quick-connect endpoint `127.0.0.1:25576`. The pilot is guarded by the test-only JVM property and that exact loopback address. Set `run-client-playtest/bossfight-control.json` to `{"enabled":true}` to enable it, or `{"enabled":false}` to release its controls. Connect first, then issue the server's `bbtest fightclient` command.

The pilot uses native client movement, aim, item-use, crosshair/reach checks, attacks and mining inputs; the normal packets reach the dedicated server. It supplies automated choices, not human skill. It does not rewrite server health, phases or damage. Client actions are recorded in `run-client-playtest/client-fight-events.jsonl`. Save actual screenshots or recordings for rendered HUD/bar claims; server-side `boss_bars` assertions alone do not establish rendered appearance.

## Evidence and limits

Preserve failed runs before rerunning; each command replaces its own result file. Report exporters produce Markdown and CSV from the recorded JSON. `audit-cataclysm-coverage.ps1` reconciles pose misses against actual emitted-effect captures and the installed-version native helper inventory. A passing assertion count alone is not a claim of every possible encounter outcome.

The new native-victory workflow also writes compact encounter telemetry and a per-attempt coverage inventory. [Encounter coverage criteria](ENCOUNTER-COVERAGE.md) describe the read-only observers, exact Dragon phase-assignment hook, action/damage distinctions, and `export-encounter-coverage.ps1`. Its ledger keeps native wins separate from observed moves and prior controlled evidence, and retains fully absorbed hits even when no player HP is lost. Historical 1.2.0 reports are not rewritten or imported as new victories.

Keep three evidence categories distinct: staged attack/phase probes, continuous automated encounters, and observed rendered client behavior. Independent contact probes reset health/cooldowns. Ordinary compound scenarios retain native impact/blast order and cooldowns. Separately labeled adversarial cases repeat native callbacks on the same retained projectile object even after its discard, preserving the victim's health and equipment to exhaust the budget; half also clear only that fixture victim's cooldown at native explosion start. Those tests prove truncation and rejection after exhaustion under compatibility stress, not ordinary encounter outcomes. Continuous encounters retain normal combat state. A combined budget is a ceiling: shields, armor, distance, or native invulnerability frames can produce less damage. Arbitrary terrain, every accessory combination, network conditions and human strategies remain outside exhaustive coverage. Report actual wins, losses, time limits, errors, and observed mechanics alongside those limits.
