# Dragon death-transition supplement

All **19 controlled assertions passed** at 12:52:08 UTC on 5 November 2026. The [checks](checks.csv), [raw result](checks.json) and [artifact manifest](manifest.json) preserve this separate execution.

The fixture used an unjoined Dragon in the isolated overworld. Its balanced maximum health was 1,100 HP. Before the hit, the fixture supplied Holding Pattern, 10 current HP and a position away from the native death destination. A native head hit selected DYING phase 9 and left 1 HP. Native death-phase ticks retained 1 HP while far from the destination. After a separate position-only arrival setup, the native death-phase tick set health to zero. The assertions also checked the native destination and flight-speed value.

This is controlled native-method evidence. Autonomous death flight, a continuous encounter and a natural victory remain separate coverage requirements. The fixture selected Holding Pattern before the hit. Native hurt selected DYING, and the subsequent health changes came from the native death-phase method.

The frozen baseline remains **8,364 passing assertions** with its recorded C1 helper. This supplement used the B340 helper and adds 19 distinct passing assertions, for **8,383 across the two executions**. Both used production 1.2.1 SHA256 `53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77`. The baseline snapshot and natural-victory ledger retain their own counts and scope.
