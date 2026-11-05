# Native dodge variation across repeated armor measurements

The two executed matrices match all 13,120 attack/outfit/enchantment keys. There are 42 damage differences larger than 0.0001 hearts. All occur on equipment with native random cancellation, and every differing pair contains one zero-damage outcome.

| Equipment | Different samples | Installed native behavior |
|---|---:|---|
| Alex's Mobs Emu leggings | 23 | Projectile damage has a configured 45% dodge chance |
| Cataclysm Cursium leggings | 19 | Projectile damage has a 15% dodge chance. Other eligible damage has an 8% chance |

The installed Alex's Mobs `ServerEvents` handler compares a native random value with `emuPantsDodgeChance` and cancels eligible projectile damage. The copied `amc.json` sets that value to 0.45. Cataclysm's installed `ServerEventHandler` checks projectile damage against 0.15 and other damage against 0.08, with the native invulnerability-bypass exclusion on the latter branch. The comparison is consistent with those native random defenses. A sampled dodge describes that particular hit. The table reports the configured probabilities rather than estimating them from these two runs.

The other 13,078 measurements agree within 0.0001 hearts. [Every differing sample](armor-repeat-variability.csv) retains both measured values and native acceptance results. The [comparison manifest](armor-repeat-variability.json) records the two CSV hashes and matching criteria.

The installed source identities are Alex's Mobs `21436BC21FB3E616007E422B931C2D0CC1587A9E6335D661D45F53D5F50D759D` and Cataclysm `371956D0C2E3B56729EE93B955E936875D210E372FE734CDB74326D89EF79875`. These measurements preserve the native random checks during each probe.
