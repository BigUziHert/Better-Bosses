# Ignis Brand: measured Protection IV netherite results

All **36 native probes and 504 assertions passed**. The largest measured health loss was **6.84 HP (3.42 hearts)**, from the ultimate with pre-hit Brand III, IV or V; the player retained 13.16 HP. With fresh native enchanted-apple effects, every hit spent absorption and removed **zero health**. The largest absorption cost was **5.472 HP (2.736 hearts)**, again at Brand III–V, leaving 10.528 of the initial 16 absorption HP. These are executed measurements, not calculated substitutes.

Each row below starts with full intact Protection IV netherite, 20 maximum/current HP and the stated pre-existing Brand. Fresh cases have no apple effects; apple cases start with native Resistance I and 16 absorption HP. Entries are hearts. The native integer armor conversion floors approximately 8/4 armor to 7/3 at Brand III/IV; the raw data retains exact attributes.

| Pre-hit Brand | Opening: health lost | Shield-smash melee: health lost | Ultimate: health lost | Apple opening: absorbed | Apple shield-smash: absorbed | Apple ultimate: absorbed |
|---|---:|---:|---:|---:|---:|---:|
| None | 1.13965 | 2.28559 | 2.98599 | 0.91172 | 1.82847 | 2.38879 |
| I | 1.73188 | 2.65162 | 3.13057 | 1.38550 | 2.12130 | 2.50446 |
| II | 2.34485 | 2.72947 | 3.22566 | 1.87588 | 2.18358 | 2.58053 |
| III | 2.84298 | 2.84298 | 3.42000 | 2.27438 | 2.27438 | 2.73600 |
| IV | 2.88000 | 2.88000 | 3.42000 | 2.30400 | 2.30400 | 2.73600 |
| V | 2.88000 | 2.88000 | 3.42000 | 2.30400 | 2.30400 | 2.73600 |

The opening uses the installed SWING_ATTACK AreaAttack recipe. The ordinary-heavy probe isolates the actual melee AreaAttack component from SHIELD_SMASH_ATTACK frame 34; its separate wave is excluded and is not newly certified here. Ultimate frame 74 runs native aiStep. Native post-hit Brand application remains active where that recipe applies it. Its new stack affects subsequent hits, not the recorded pre-hit defenses.

Apple effects come from the native ItemStack.finishUsingItem implementation. This controlled snapshot excludes the 32-tick eating interval, intervening regeneration, fire damage and later combo hits. Health and equipment reset only between independent probes. The results close the specifically documented Brand 0–V netherite gap for these three native components; they do not establish survival under every status, broken armor, prior damage or a whole combo. The older 1.2.0 report remains an accurate historical record of what had been measured then.

[All 36 cases](ignis-brand-netherite.csv), [native event/effect records](ignis-brand-netherite-native.json), [all 504 assertions](ignis-brand-netherite-assertions.json), and [artifact/result hashes](ignis-brand-netherite-manifest.json) preserve the evidence. These controlled results do not add a native victory to the encounter ledger.

During the 12 ultimate probes, Leaky emitted 24 warnings: each reported 151 entities in one of two fixture sections. The logged call stack leads through native Ignis UltimateAttack entity spawning. Bytecode inspection of installed leaky-1.21-3.6.jar shows it creates an Exception solely as a Logger.warn stack trace and then returns; it does not throw that diagnostic. The suite source snapshots existing entities before each probe and discards newly introduced entities in finally, then discards its fake player. The constant warning count per section is consistent with repeated transient native spawns, not a rising count across probes. A post-cleanup live entity census was not recorded, so this report does not claim an independently measured zero-residual count. Suite completion and all assertions succeeded. [Diagnostic audit](ignis-brand-leaky-audit.json) preserves the compact provenance.

Production remained unchanged; the helper alone added this test. No writes were made to S:\Survival.
