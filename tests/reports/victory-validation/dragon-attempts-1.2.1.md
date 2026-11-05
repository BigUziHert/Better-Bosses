# Dragon attempts with production 1.2.1

The fifth production 1.2.1 attempt defeated the Dragon through continuous native combat. Four earlier attempts ended with a living Dragon. All five preserved 20 player HP and all four initial netherite armor pieces. Their native hits and phase observations remain available in the [attempt ledger](attempts.csv) and [move timeline](native-move-phase-timeline.csv). The executed production artifact was `53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77` throughout.

| Queue on 5 November 2026 UTC | Helper / pilot source | Outcome | Ticks | Dragon HP | Apples spent | Native knockdowns / self-revivals |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 11-35-32.452851200Z | C1 / 3E3 | Cancelled | 12,918 | 1100 | 4 | 0 / 0 |
| 11-50-01.355596700Z | 61C / 3E3 | Cancelled | 8,377 | 877.92737 | 19 | 0 / 0 |
| 12-02-50.620811600Z | 61C / 191D | Time limit | 24,000 | 81.5809 | 16 | 0 / 0 |
| 12-26-52.308947800Z | 61C / 191D | Time limit | 24,000 | 269.52637 | 22 | 0 / 0 |
| 12-52-50.722593800Z | B340 / 3556 | Native victory | 11,117 | 0 | 16 | 0 / 0 |

Every listed attempt has zero telemetry observer errors. Apple spending comes from starting inventory minus ending inventory and equals ordinary food-finish events in these attempts. Full artifact hashes appear in [runtime provenance](runtime-provenance.json).

The 11:35 attempt exposed an access-fixture cavity around the inner ring. The player fell into that cavity and could not return through the available ordinary movement. Native CHARGING_PLAYER was observed. The subsequent precombat fixture filled air within ring radius 1–6 while preserving the native center and existing blocks.

The 11:50 attempt destroyed all ten crystals through distinct native arrow collisions and recorded all four processed Dragon damage profiles. Perched breath spent 0.432 HP of absorption at telemetry tick 1861. The pilot rejected normal sword clicks when the native crosshair selected the neck while its requested aim part was the head. That input limitation prompted a client correction accepting an in-reach part of the same Dragon.

The 12:02 attempt demonstrated that correction through native 13 HP sword hits at ticks 2498, 2514, 2530 and 2545. Client records also show ordinary stair recovery from exterior Y=59 to the ring at Y=72. Late conditional input choices sought the airborne lethal transition while the Dragon remained alive. Healing from surviving crystals and interrupted bow aim contributed to the unfinished attempt. DYING phase 9 remained unobserved in the continuous fight.

The 12:26 attempt used continuous automatic offense throughout. Native sword hits worked. A client route review identified opposed head-aim and waypoint-aim turns that cancelled movement, including a measured 122.53 degree opposition. The attempt reached its time limit with 269.52637 HP remaining. CHARGING_PLAYER phase 8 and DYING phase 9 were unobserved in this particular attempt. Earlier continuous attempts provide phase 8 evidence.

The fresh 12-52-50.722593800Z attempt used helper B340 and pilot 3556 with the route correction. Native death occurred at tick 10917 and 13:02:43.359 UTC. The native death interval finished at tick 11117 and 13:02:53.359 UTC with the Dragon removed and player at 20 HP. All armor pieces remained equipped, and all 16 apples spent matched ordinary food-finish events. The attempt recorded zero knockdowns, revivals and telemetry errors.

The corrected client reached sword contact during its first perch approach in 60 ticks, compared with 824 ticks in the reviewed 191D attempt. This comparison describes the test controller's ordinary approach inputs. The production artifact and native boss behavior are recorded independently.

All four Dragon damage profiles were processed naturally in that victory. Counts were 135 fireball-cloud events, eight wing events, 28 head events and 58 perched-breath events. All declared phase candidates except airborne DYING phase 9 were observed. The perched lethal outcome completed the native defeat while leaving that distinct phase unobserved in the continuous fight.

The received client bar showed progress 0.0 at 13:02:52.767 UTC and disappeared by the next observation at 13:02:53.767 UTC after the native death interval and encounter cleanup.

## Separate death-transition probe

The idle [controlled death-transition supplement](../1.2.1/supplements/dragon-death-transition/README.md) passed 19 assertions at 12:52:08 UTC using helper B340. A native lethal head hit selected DYING and retained 1 HP. Native death-phase method calls retained 1 HP away from the destination, then set zero after a position-only arrival fixture. Its manifest explicitly records `nativeVictory=false` and `continuousNaturalPhaseCoverage=false`. These checks add controlled phase-method evidence while the continuous encounter ledger keeps its own unresolved requirements.
