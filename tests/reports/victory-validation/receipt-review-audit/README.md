# Independent receipt review

All 32 review items in the [53-attempt receipt audit](inputs/receipt-manifest.json) have an identified explanation. The [independent results](independent-audit.json) preserve the original review-input hashes, each corrected calculation and sampled bar counts. The [original review items](inputs/review-items.json) remain available in the frozen input copy.

The 24 Ur-Ghast tantrum residuals arise from the native helmet step. The installed Twilight Forest tag marks `ghast_tear` as `damages_helmet`. With a helmet equipped, patched `LivingEntity.hurt` multiplies incoming damage by 0.75 and updates the damage container directly. Its named reductions therefore omit this separate step. [Exact native source evidence](native-source-evidence.json) includes the installed JAR hash, damage tag and patched method excerpt.

Every reviewed tear receipt had a netherite helmet equipped and the same arithmetic.

| Step | HP |
| --- | ---: |
| Processed incoming amount | 2 |
| Native helmet reduction | 0.5 |
| Resistance reduction | 0.29999995 |
| Enchantment reduction | 0.768 |
| Absorption spent | 0.43200004 |
| Health damage | 0 |

Accounting for the helmet step leaves a maximum residual of approximately 0.00000001 HP across the 24 receipts. The actual health and absorption deltas agree with the recorded receipts. This establishes the arithmetic for those observed hits and their equipment and effects.

The eight bar review items all belong to the earlier production 1.2.0 Leviathan victory. They preserve the negative native transition values that led to the [1.2.1 bar correction](../leviathan-bar-transition-review.md).

The independent client scan matched 5,699 received bar values to 13 completed production 1.2.1 cases and found zero invalid values. It separately checked 274 received values before the unfinished Leviathan stall and found zero invalid values. These are finite-range observations over the recorded intervals. Native phase-formula verification is documented separately in the transition investigation.

The receipt audit's 110 skipped client samples lack UTC fields. All appear before the first dated sample in the append log. Independent JSON parsing found zero malformed JSON rows and zero invalid UTC values in dated samples. The skipped legacy samples remain excluded from timed encounter matching. The later closed-case telemetry reports retain zero observer errors.

Reproduce this read-only audit by running `tests/snapshot-client-evidence.ps1` and then `node tests/audit-receipt-review.mjs` from the repository root. The shared reader copies a complete client-log prefix and the Node audit reads that closed snapshot. It verifies the frozen 53-attempt source hashes, checks the equipped helmet for each affected hit and audits received bars by executed production identity. The unfinished Leviathan interval remains separate from completed attempts and victory counts.
