# Completed-attempt receipt review

The 65 completed attempts contain 20,248 native damage Post events, including 11,015 against the player. Every original-damage identity and actual health and absorption delta reconciles within 0.001 HP. The [raw receipt audit](receipt-checks.md), [individual receipts](damage-receipts.csv) and [source hashes](receipt-manifest.json) preserve the measurements.

The 32 raw review items have specific explanations. They are identical to the earlier reviewed set and remain in [review-items.json](review-items.json).

| Review | Count | Finding |
| --- | ---: | --- |
| Ur-Ghast tear damage | 24 | The native helmet step removes 25% before the named reduction fields. Including that step leaves a maximum residual of approximately 0.00000001 HP. |
| Historical Leviathan bar values | 8 | Production 1.2.0 transmitted negative values during the native pre-meltdown transition. These observations led to the 1.2.1 correction. |
| Remaining unexplained damage arithmetic | 0 | Original, health and absorption checks pass for the completed observations. |

The independent check verifies each affected tear hit against its equipped helmet and the installed damage-type tag. Its [results](adjudication/independent-audit.json) and [native source evidence](adjudication/native-source-evidence.json) retain the calculations and source identity.

Across the completed intervals, the client received 29,784 bar values in 27,569 samples. The only eight out-of-range values are the historical Leviathan observations above. The 25 completed production 1.2.1 cases contain 13,779 received bar values with zero invalid values. A separate scan of the unfinished Leviathan attempt contains 274 values with zero invalid values. Its [transition evidence](../unfinished-leviathan/README.md) verifies the clamp and native phase formula before the external-pack stall.

The client parser found zero malformed JSON rows and zero invalid UTC fields. It excludes 110 early samples that have no UTC field and precede the first dated sample. Gear and effects are observed context. These encounter measurements include enchanted-apple absorption, resistance and fire immunity.

The final Ignis attempt is included as a closed time-limit outcome with no native victory. The unfinished Leviathan stall and earlier Ignis logging interruption are excluded from completed-attempt arithmetic and outcome counts. The partial Leviathan bar scan remains separately labeled. Finite values establish the sampled range. Native phase formulas and visibility have their own controlled and encounter evidence.

The reader copied a complete client-log prefix with shared access, closed the copy and hashed that copy. The [snapshot manifest](adjudication/client-snapshot-manifest.json) records this access and identity. No active runtime file was hashed. All completed primary and telemetry hashes were independently checked against the receipt manifest.
