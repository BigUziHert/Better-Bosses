# Boss test readiness report

**Requested readiness is not established. Controlled validation passed, and native victories were verified for 22 of 23 bosses. The final Ignis attempt ended without a defeat, so the all-boss victory requirement remains unmet.** The external-pack Leviathan stall also remains unresolved.

The controlled runs passed **8,383 assertions with zero failures**, comprising the frozen 8,364-assertion baseline and the separate 19-assertion Dragon supplement. See the [controlled report](../1.2.1/REGRESSION-1.2.1.md) and [supplement](../1.2.1/supplements/dragon-death-transition/README.md).

Selected fights used legal maximum netherite gear and native enchanted golden apples. Time is game time, not elapsed wall time. Apples include ordinary eating and native self-revival consumption. KO and revival counts preserve lethal events. Armor means pieces still present at completion. The CSV preserves final player and boss health for each recorded outcome.

| Boss | Maximum HP | Native result / game time | Apples spent | KO / revivals | Armor remaining |
| --- | ---: | --- | ---: | ---: | --- |
| Naga | 1000 | Won / 10:06.25 | 11 | 0 / 0 | 4/4 netherite |
| Twilight Lich | 650 | Won / 3:57.00 | 10 | 0 / 0 | 4/4 netherite |
| Minoshroom | 1000 | Won / 2:06.15 | 3 | 0 / 0 | 4/4 netherite |
| Knight Phantoms | 180 each / 1080 group | Won / 3:12.30 | 5 | 0 / 0 | 4/4 netherite |
| Alpha Yeti | 1100 | Won / 3:56.30 | 19 | 0 / 0 | 4/4 netherite |
| Hydra | 1500 | Won / 4:29.65 | 4 | 0 / 0 | 4/4 netherite |
| Ur-Ghast | 1100 | Won / 8:06.65 | 4 | 0 / 0 | 4/4 netherite |
| Snow Queen | 1200 | Won / 6:56.80 | 4 | 0 / 0 | 4/4 netherite |
| Night Lich | 1300 | Won / 5:06.90 | 9 | 0 / 0 | 4/4 netherite |
| Void Blossom | 1400 | Won / 3:49.65 | 10 | 0 / 0 | 4/4 netherite |
| Nether Gauntlet | 1500 | Won / 6:04.50 | 9 | 0 / 0 | 4/4 netherite |
| Obsidilith | 1700 | Won / 6:52.25 | 20 | 0 / 0 | 4/4 netherite |
| Netherite Monstrosity | 1200 | Won / 4:40.95 | 6 | 0 / 0 | 4/4 netherite |
| Ender Guardian | 850 | Won / 4:50.10 | 17 | 0 / 0 | 4/4 netherite |
| Harbinger | 950 | Won / 5:15.95 | 21 | 0 / 0 | 4/4 netherite |
| Ancient Remnant | 1000 | Won / 5:29.05 | 13 | 0 / 0 | 4/4 netherite |
| Leviathan | 1100 | Won / 9:43.80 | 28 | 0 / 0 | 4/4 netherite |
| Scylla | 1100 | Won / 3:23.00 | 18 | 0 / 0 | 4/4 netherite |
| Maledictus | 1100 | Won / 2:47.85 | 16 | 0 / 0 | 4/4 netherite |
| Ignis | 1100 | **Defeat unverified** (time_limit) / 20:00.00 | 79 | 0 / 0 | 0/4 netherite |
| Wither | 900 | Won / 4:26.75 | 20 | 0 / 0 | 4/4 netherite |
| Ender Dragon | 1100 | Won / 9:15.85 | 16 | 0 / 0 | 4/4 netherite |
| Warden | 1300 | Won / 4:23.30 | 48 | 7 / 7 | 0/4 netherite |

The Warden victory includes seven native knockdowns and self-revivals, and all four armor pieces wore out. Its 48 apples comprise 41 food events plus seven self-feeds. Each other selected victory has all four armor pieces present and zero native knockdowns. The [23-boss CSV](readiness-bosses.csv) and [primary records](readiness-summary.json) identify the exact queues and final inventory counts.

The [final Ignis attempt](final-ignis-attempt.json) ended at the 20-minute game-time limit. Ignis had 1100/1100 HP, the player had 19.3408/20 HP, all four armor pieces had worn out and 79 enchanted golden apples had been consumed. Its native shield broke, but a defeat was not achieved. The recorded damage and native healing supply evidence of this outcome without establishing a numerical defect or an impossible fight. No further gameplay retries are planned.

The [Gauntlet](gauntlet-clean-replay.json) and [Obsidilith](obsidilith-clean-replay.json) clean replays replace the earlier startup-flight-qualified runs as primary movement evidence. All selected victories have zero recorded flight flags with complete matched physics fields. Sampling cannot exclude brief transitions between samples. Earlier attempts remain in the [complete attempt table](attempts.csv).

Attack and phase evidence is available in the [coverage ledger](coverage-ledger.md), [every candidate](moves-and-phases.csv), [native timeline](native-move-phase-timeline.csv) and [damage receipts](native-player-damage.csv). The [nine Ignis candidates](ignis-controlled-candidate-audit.md) and [other eight candidates](other-controlled-candidate-audit.md) have evidence-specific controlled qualifications. Natural random selection and movement-path gaps remain visible.

The [completed-attempt receipt review](receipts-final-complete/README.md) checks 20248 native damage events from 65 completed attempts. Health and absorption arithmetic reconcile within 0.001 HP. All 13779 received bar values from the 25 completed production 1.2.1 attempts are valid. The review retains the earlier eight Leviathan bar defects and explains 24 native helmet-reduction residuals. Its source manifest identifies every included closed attempt.

The [armor report](../1.2.1/armor-results.md), [every armor/attack measurement](../1.2.1/armor-all-attacks.csv), [armor effects](../1.2.1/armor-effects-results.md) and [approved damage comparison](../1.2.1/approved-damage-comparison.md) preserve individual gear results. The [Ignis Brand sweep](../1.2.1/ignis-brand-netherite.md) separates fresh and enchanted-apple effects across Brand levels.

The controlled [regression report](../1.2.1/REGRESSION-1.2.1.md) includes fixed HP, multiple-player scenarios, native health bars and installation into previously saved chunks followed by another restart. [Artifact continuity](../1.2.1/artifact-continuity.md) connects the older native wins to the current JAR through identical health/damage classes and the isolated Leviathan bar correction. [Runtime provenance](runtime-provenance.json) identifies each executed helper and client version.

A later Leviathan attempt suffered a recorded server stall after Sable portal-ray errors, with the sampled server thread in Lithium collision traversal through Sable movement hooks. Its [unfinished evidence](unfinished-leviathan/README.md) preserves the real RUSH-to-STUN counter and corrected bar transition before the stall. It supplies no new victory. The earlier completed Leviathan win remains the selected result. This external-pack interaction remains a known runtime limitation.

The [prepared artifacts](FINAL-ARTIFACTS.md) identify the exact production JAR and report files. The [final source-folder audit](source-read-only-final.json) preserves the read-only comparison of S:\Survival and the separate datapack-copy check.
