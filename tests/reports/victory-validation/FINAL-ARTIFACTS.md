# Production artifact and final test results

The requested readiness standard remains unmet. Controlled validation passed, but native victories were verified for 22 of 23 bosses. The [final Ignis attempt](final-ignis-attempt.json) ended at its time limit with the boss alive. The external-pack Leviathan server stall remains unresolved. No further gameplay retries are planned.

| Artifact | Result or identity |
| --- | --- |
| [Production JAR](../../../build/libs/survival-boss-balance-1.2.1.jar) | Version 1.2.1, 45,981 bytes |
| [Readiness report](READINESS-SUMMARY.md) | 23 boss rows with HP, time, apples, knockdowns, revivals and remaining armor |
| [23-boss CSV](readiness-bosses.csv) | Exact selected queues and measured outcomes, including the unsuccessful final Ignis attempt |
| [Primary evidence records](readiness-summary.json) | Machine-readable evidence and unmet native-victory requirement |
| [Controlled regression](../1.2.1/REGRESSION-1.2.1.md) | 8,364 passing baseline assertions |
| [Dragon supplement](../1.2.1/supplements/dragon-death-transition/README.md) | 19 further passing assertions, for 8,383 total and zero failures |
| [Every armor measurement](../1.2.1/armor-all-attacks.csv) | Individual armor and attack results |
| [Native coverage ledger](coverage-ledger.md) | Completed attempts, moves, phases and retained qualifications |
| [Final receipt audit](receipts-final-complete/README.md) | 20,248 damage receipts from 65 closed attempts, with all raw review items adjudicated |
| [Ignis controlled audit](ignis-controlled-candidate-audit.md) | Exact native evidence for its remaining natural-catalogue gaps |
| [Other controlled audit](other-controlled-candidate-audit.md) | Exact evidence and limits for the other eight catalogue gaps |
| [Runtime provenance](runtime-provenance.json) | Production, helper, pilot and defense-module identities by execution |
| [Source-folder audit](source-read-only-final.json) | Fresh read-only comparison and separate matching datapack-copy check |

The production JAR SHA-256 is

```text
53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77
```

The [artifact continuity review](../1.2.1/artifact-continuity.md) records the health/damage class identity across the earlier evidence and the isolated Leviathan boss-bar normalization correction. Test controllers and fixture helpers have separate provenance.

The fresh source comparison contains 871 files, one previously established generated timestamp-comment difference and 111 modification-time-only differences. No files were added or removed and all reads were stable. The task made no changes to `S:\Survival`. Two source datapack files match the staged test-world copies. That separate datapack comparison does not extend the older 871-file baseline retrospectively.

The [unfinished Leviathan evidence](unfinished-leviathan/README.md) preserves the recorded Sable portal-ray and Lithium/Sable collision stall, the native counter that preceded it and the corrected received bar samples. The final report retains this runtime limitation and distinguishes controlled callback coverage from naturally selected moves and completed victories.
