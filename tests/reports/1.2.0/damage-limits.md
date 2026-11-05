# Damage limits and native debuffs

The approved damage values use **Hard difficulty, full intact unenchanted diamond armor, 20 armor and 8 toughness** as their reference. The [armor matrix](armor-all-attacks.csv) measures other equipment separately. These are damage calibration results, not a universal guarantee that every player survives a hit in every status or equipment condition. The production mod changes incoming damage before the native defenses run; it does not impose a universal post-armor health-loss cap.

Ignis's native **Blazing Brand** reduces both armor and toughness by 20% per stack. The effect is preserved. The existing production damage handler accounts specifically for the victim's pre-hit Brand stacks when enforcing the ordinary 8-heart and ultimate 9.5-heart reference ceilings.

The following are **executed native attack probes**, not calculations: the test player began each hit at 20 HP with full plain diamond equipment, received the stated native effect, and was hit through Ignis's `aiStep` at opening-sword frame 24 or ultimate frame 74. Each row is an independent hit, with state reset between probes.

| Pre-hit Brand | Armor attribute | Toughness | Opening sword, hearts | Ultimate, hearts |
|---|---:|---:|---:|---:|
| None | 20 | 8 | 3.500000 | 9.499999 |
| I | 16 | 6.4 | 5.148472 | 9.500000 |
| II | 12 | 4.8 | 6.843377 | 9.500001 |
| III | approximately 8 | 3.2 | 8.000000 | 9.500000 |
| IV | approximately 4 | 1.6 | 8.000000 | 9.500000 |
| V | 0 | 0 | 8.000000 | 9.500000 |

Every ultimate probe left approximately **1 HP** from 20. The approximately one-millionth-heart differences are floating-point rounding. Armor attribute values are rounded here; the native integer armor conversion still applies. A separate ordinary-heavy Brand V fixture measured 8 hearts. [Native scenario records](all-native-scenarios.csv) contain the `brand_0` through `brand_5` probes; [assertions](all-assertions.csv) contain their checks. The [probe implementation](../../../tests/src/main/java/survival/balance/tests/ExhaustiveCataclysm.java) records how the native frames were invoked.

For **Protection IV netherite**, the executed armor matrix measured the native ultimate at **2.98599 hearts with no pre-existing Brand**. A corresponding controlled Brand I–V netherite sweep was **not measured**; this report does not substitute calculated values for those missing measurements.

Blazing Brand alone therefore did not defeat the intended single-hit ceilings on intact reference diamond in these tests. Other vulnerability effects, equipment breaking, reduced maximum health, prior damage, or an additional attack can change survival. Native burning and other follow-up damage can kill a player left at 1 HP. The [set-effect and endurance tests](armor-effects-results.md) cover their stated modded armor conditions, not every possible debuff, accessory combination or sustained encounter. Single-hit caps do not cap a whole combo, multiple attackers, or damage over time.

Production artifact: `survival-boss-balance-1.2.0.jar`, SHA-256 `871F3C0ECF84236ADFDC5457C09C9D2C8EE34BC9582CE148C899109FDA822F70`. No production or boss behavior changes were made for this follow-up review.
