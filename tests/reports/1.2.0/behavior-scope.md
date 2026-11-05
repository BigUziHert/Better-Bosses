# Production behavior scope

The production source diff against published 1.1.0 commit `1407791d514004a1bb1a14ac74023fbe9ee214d6` introduces **no changes to boss AI, targeting, movement, phase selection, attack timing or frequency, projectile motion, explosion radius, or block-griefing behavior**. The 1.2.0 changes concern the agreed damage values and caps, combined projectile damage budgets, and configuration migration. All 23 health targets and `BossHealthBalance.java` are unchanged from that baseline.

The [damage limits report](damage-limits.md) distinguishes executed native Brand measurements from the limits of fresh-armor reference values. The reviewed production artifact is `survival-boss-balance-1.2.0.jar`, SHA-256:

`871F3C0ECF84236ADFDC5457C09C9D2C8EE34BC9582CE148C899109FDA822F70`

| Production file | Scope of the 1.2.0 change |
|---|---|
| [BossDamageBalance.java](../../../src/main/java/survival/balance/BossDamageBalance.java) | Calibrates approved attack damage to Hard difficulty with plain diamond armor; distinguishes the native Ignis ultimate damage frame; retains Maledictio Anima's prior raw cap. It observes attack context without selecting attacks or changing animation state. |
| [ProjectileDamageBudget.java](../../../src/main/java/survival/balance/ProjectileDamageBudget.java) | Enforces the approved Ur-Ghast/Harbinger combined damage budgets separately for each projectile and victim. Accepted damage consumes the budget; native armor, enchantments, shields, damage cooldowns and bypass flags still apply. |
| [UrGhastExplosionMixin.java](../../../src/main/java/survival/balance/mixin/UrGhastExplosionMixin.java) | Tracks which projectile produced a native ownerless explosion. Calls the original explosion with every argument unchanged, including source, position, radius, fire flag and interaction mode. |
| [BalanceSettings.java](../../../src/main/java/survival/balance/BalanceSettings.java), [LegacyDamageDefaults.java](../../../src/main/java/survival/balance/LegacyDamageDefaults.java), [example config](../../../config/survival-boss-balance.json) | Migrates old default damage targets and preserves customized legacy calibration explicitly; does not rewrite the user's configuration file. |

The existing [health/bar support](../../../src/main/java/survival/balance/BossHealthBalance.java) remains unchanged from 1.1.0: fixed maximum health and saved-health-ratio migration; Naga segment thresholds proportional to maximum health; proportional Wither spawn-charge healing; and the Knight Phantom solo/late-companion group-count repair used by its shared health bar. These are existing health/bar adjustments, not new 1.2.0 AI changes. Native phase logic continues to use its own health conditions.

The automated controller, arena setup, encounter instrumentation and development-only FlightLib adjustment live under `tests/`. The [client test source set](../../../build.gradle) is separate from production. None of those helpers, the test server mod, or third-party mods are packaged in the production JAR; its 14 production classes and resources are recorded in the [artifact audit](source-and-artifact-audit.md). Test environment adjustments describe isolated test conditions, not changes installed on the survival server. This audit documents code scope and does not claim that every native move was observed during continuous fights.
