# Unfinished Leviathan encounter

Case 05 of queue `2026-11-05T13-04-36.346328200Z` stopped advancing after tick 5668 at 13:32:09.561 UTC. Its ordinary log ends with a player-damage record. A final encounter record is absent. The last sampled boss health was 734.75934 HP, with player 20 HP and armor 20. Final apple accounting and a completed outcome remain unavailable.

The [stall manifest](stall-manifest.json) records verified copies and SHA256 values for the ordinary log, telemetry, bounded native evidence and thread dump. The original records are preserved with their existing endpoints. The first four cases in this queue completed before the stall and have independent native-death witnesses.

The last progress coincided with a Sable bounds error involving the native portal ray. At 13:39:05 UTC, the [server thread sample](server-thread-sample.txt) showed the thread running inside Lithium collision traversal through Sable movement hooks while processing a player-movement packet. This identifies the observed stall path. Root-cause analysis remains separate from the completed balance assertions.

## Native evidence before the stall

The [bounded observation record](native-observations.json) retains the actual client bar values and native counter sequence.

| UTC | Boss HP | Native mode | Received bar progress |
| --- | ---: | --- | ---: |
| 13:29:34.982 | 547.3194 | Before meltdown flag | 0 |
| 13:29:35.977 | 538.2994 | Before meltdown flag | 0 |
| 13:29:36.980 | 518.09937 | Before meltdown flag | 0 |
| 13:30:31.976 | 482.0194 | After meltdown flag | 0.43819946 |

The native flag changed at telemetry tick 2789 during phase-two animation. These received values reproduce the below-half interval that previously produced negative progress and verify the 1.2.1 clamp during natural combat. The later value follows native health divided by 1100.

An accepted player attack at tick 5426 hit the Leviathan during RUSH frame 45 for 9.0199995 HP. The next state observation at tick 5427 recorded LEVIATHAN_STUN. Ordinary client spacing and sword inputs produced that counter. This establishes the native STUN branch in an unfinished encounter. A STUN observation within a completed winning attempt remains a separate qualification.

These observations belong to production 1.2.1, helper B340 and pilot 3556, with full hashes in the manifest. The completed-case CSVs retain their original scope. This unfinished case contributes the explicitly linked native observations and receives zero victory credit.
