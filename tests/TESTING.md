# Dedicated-server regression harness

These helpers are for a disposable Minecraft 1.21.1 / NeoForge 21.1.264 server only. They spawn and discard entities, alter gamerules, generate an arena, and save test results. Never put the helper JAR on a real server. Every entrypoint requires `-Dsurvival.bossBalance.testMode=true`.

## Setup

Use JDK 21. Create `run-regression/` under the repository using **copies** of the server's `mods`, `config`, `defaultconfigs`, `libraries`, `kubejs`, and `world/serverconfig`. Create a new disposable world; do not copy or launch the original world. Retain the original combat settings. Set the test game server and voice-chat server to unused loopback ports. Keep the source server read-only throughout.

Build the production mod with `gradlew.bat build`. Build the helper with `tests/build-tests.ps1`; it uses the copied patched NeoForge server JAR first on its compilation classpath. Copy the two built JARs into the test server's `mods` directory, replacing only the balance JAR in the test copy.

Start from `run-regression/` using Java 21, the test-mode JVM flag, and `@libraries/net/neoforged/neoforge/21.1.264/win_args.txt nogui`. Runtime results go to `tests/results/`. The standard damage reference is Hard difficulty and full Protection IV netherite; actual equipment is used by the server damage pipeline.

## Commands

| Command | Scope |
|---|---|
| `bbtest health` | All 23 profiles, health ratios, serialization, legacy modifiers, controls |
| `bbtest fixed_health` | 0/1/2/3/4/5/10 nearby simulated players, distance/spectator changes, joining/leaving, old party-profile migration |
| `bbtest damage` | Independent attack inputs/expectations, caps, effects, attribution and control victims |
| `bbtest actual_attacks` | Representative real modded attack methods |
| `bbtest vanilla` | Native Wither/Dragon/Warden attacks, phase transitions, lifecycle behaviors, health persistence |
| `bbtest boss_bars` | Native server bar progress, shields, group totals, rage, visibility and tracking |
| `bbtest multiplayer_targets` | Native target-goal integration with simulated players |
| `bbtest exhaustive twilight` | Staged native Twilight attack and phase probes |
| `bbtest exhaustive bmd` | Staged native Bosses of Mass Destruction attack and phase probes |
| `bbtest exhaustive cataclysm` | Staged native Cataclysm attack, emission, impact and phase probes |
| `bbtest armor_matrix` | Registered armor outfits, supported Protection IV variants, per-attack runtime measurements |
| `bbtest armor_effects` | Full modded sets after actual player ticks, Aether gloves, worn Zanite and Resonarium endurance |
| `bbtest prepare_restart` / `bbtest verify_restart` | Save/stop/start persistence check for wounded Minoshroom and Alpha Yeti |

## Installing into a pre-existing world

Remove only the production balance JAR from the **test copy**, retaining the optional test helper. Run `bbtest preinstall_prepare`, then stop the test server. This saves full and wounded instances of all 23 types with their native HP. Preserve that test-world snapshot and `preinstall-manifest.json`.

Install the production balance JAR into the test copy and start the same saved world. Allow asynchronous entity loading to complete before running `bbtest preinstall_verify`. Test listeners capture health and max HP immediately before and after the real entity-join event. This verifies ratio preservation at migration without mistaking subsequent native idle healing for a migration failure. Results also establish the original native maximum and absence of a balance modifier before the join.

## Evidence and limits

Preserve failed runs before rerunning; each command replaces its own result file. Report exporters produce Markdown and CSV from the recorded JSON. `audit-cataclysm-coverage.ps1` reconciles pose misses against actual emitted-effect captures and the installed-version native helper inventory. A passing assertion count alone is not a claim of every possible encounter outcome.

The harness uses real registered entities, installed mod code, equipment, events and Minecraft damage processing, with simulated server players. It stages attacks and phase gates. It does not substitute for continuous fights with human players or screenshots of client HUDs. Independent contact probes reset health/cooldowns, so combined hazards, arbitrary terrain, accessory combinations and player skill are outside their scope. The final report names these limits alongside measured outcomes.
