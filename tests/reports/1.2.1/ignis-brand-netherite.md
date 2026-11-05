# Ignis Brand measurements in Protection IV netherite

All **36 native probes and 504 assertions passed**. The largest measured health loss was **6.84 HP (3.42 hearts)** from the ultimate with pre-hit Brand III, IV or V. The player retained 13.16 HP. With fresh native enchanted-apple effects, every hit spent absorption and removed **zero health**. The largest absorption cost was **5.472 HP (2.736 hearts)**, again at Brand III–V, leaving 10.528 of the initial 16 absorption HP.

Each row starts with full intact Protection IV netherite, 20 maximum/current HP and the stated pre-existing Brand. Fresh cases use the armor alone. Apple cases add native Resistance I and 16 absorption HP. Entries are hearts. The native integer armor conversion floors approximately 8/4 armor to 7/3 at Brand III/IV. The raw data retains exact attributes.

| Pre-hit Brand | Opening health lost | Shield-smash melee health lost | Ultimate health lost | Apple opening absorbed | Apple shield-smash absorbed | Apple ultimate absorbed |
|---|---:|---:|---:|---:|---:|---:|
| None | 1.13965 | 2.28559 | 2.98599 | 0.91172 | 1.82847 | 2.38879 |
| I | 1.73188 | 2.65162 | 3.13057 | 1.38550 | 2.12130 | 2.50446 |
| II | 2.34485 | 2.72947 | 3.22566 | 1.87588 | 2.18358 | 2.58053 |
| III | 2.84298 | 2.84298 | 3.42000 | 2.27438 | 2.27438 | 2.73600 |
| IV | 2.88000 | 2.88000 | 3.42000 | 2.30400 | 2.30400 | 2.73600 |
| V | 2.88000 | 2.88000 | 3.42000 | 2.30400 | 2.30400 | 2.73600 |

The opening uses the installed SWING_ATTACK AreaAttack recipe. The heavy probe measures the melee AreaAttack component from SHIELD_SMASH_ATTACK frame 34. Its separate wave is covered by other fixtures. Ultimate frame 74 runs native aiStep. Native post-hit Brand application continues where that recipe applies it, and the added stack affects subsequent hits.

Apple effects come from native ItemStack.finishUsingItem. Each controlled snapshot starts after consumption and measures one native component. Independent probes restore their initial health and equipment. Eating time, regeneration between hits, accumulated fire and whole combos require the separate continuous encounter observations.

[All 36 cases](ignis-brand-netherite.csv), [native event/effect records](ignis-brand-netherite-native.json), [all 504 assertions](ignis-brand-netherite-assertions.json) and [artifact/result hashes](ignis-brand-netherite-manifest.json) preserve the executed evidence.
