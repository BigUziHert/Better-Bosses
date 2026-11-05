# Artifact continuity

Version 1.2.1 changes the value sent to Leviathan's boss bar while its native phase transition is pending. The first segment now has a lower bound of zero. The native frame-90 mode switch still selects the second segment and its original artwork.

The [entry comparison](production-entry-diff.json) records these four differences from 1.2.0.

| Entry | Purpose |
|---|---|
| `LeviathanBossBarMixin.class` | Clamp both native progress arguments before broadcast |
| `survival_boss_balance.mixins.json` | Register the optional Leviathan mixin |
| `META-INF/neoforge.mods.toml` | Identify version 1.2.1 |
| `SurvivalBossBalance.class` | Update the startup version message |

The binary comparison verifies identical health, damage, configuration migration and projectile-budget classes in both builds. Earlier measurements therefore remain evidence for that shared implementation. Each earlier fight retains its 1.2.0 artifact identity. The new bar clamp has separate 1.2.1 validation, including the interval below half health before the native transition completes.

| Artifact | SHA256 |
|---|---|
| Production 1.2.0 | `871F3C0ECF84236ADFDC5457C09C9D2C8EE34BC9582CE148C899109FDA822F70` |
| Production 1.2.1 | `53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77` |
| Executed controlled helper | `C1C5ACEC1E30CA930457CB2B3B05448AA06F2C81849A4F8E00BAC4C184E3F598` |

The production build contains 45,981 bytes. Its build and all 8,364 controlled assertions passed. The [result manifest](test-run-manifest.json) identifies the executed helper for every suite. The preinstallation preparation boot is recorded with the production mod absent.

The [historical controlled report](../1.2.0/REGRESSION-1.2.0.md), [continuous victory ledger](../victory-validation/coverage-ledger.md) and [source audit](../1.2.0/source-and-artifact-audit.md) preserve the earlier execution conditions and findings.
