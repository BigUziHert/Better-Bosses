# Armor test results

90 outfits; 12464 measured attack/outfit/enchantment combinations. All damage below is **hearts lost on Hard**. Each cell shows **plain / Protection IV**; full Protection IV means level IV on each equipped piece. One heart is 2 HP.

Actual server Player.hurt with registered equipment, Hard difficulty; event-based item defenses apply. Damage fixture inputs reproduce attacks at a 20-HP target; 200-HP measurement victim avoids death/downing truncation. Native probes are separately labeled. No shields, accessories/gloves, active potion effects, set-effect warmup, or environmental bonuses. Partial outfits leave absent slots empty. Protection IV is tested only when every equipped item reports support through NeoForge supportsEnchantment; unsupported variants are omitted. Each hit starts with fresh armor, so durability-absorbing sets require the separate endurance probes.

An em dash means that enchantment variant was not tested because it is unsupported (or the outfit has no items). The Protection IV column never forces an enchantment rejected by the item. Fresh Resonarium can show zero health loss because its native mod transfers armor-sensitive damage to item durability; this is not unlimited invulnerability. See [set effects and endurance](armor-effects-results.md) for repeated-hit measurements.

The table gives representative attacks for every tested outfit. [All measured attacks](armor-all-attacks.csv) contains every fixture/native attack result for every outfit and enchantment state. [Item inventory](armor-item-inventory.csv) gives registry IDs, equipment slots, materials, and class names. A partial outfit leaves its other armor slots empty; it is not comparable to a complete set as an equal-slot loadout.

| Armor outfit | Pieces | Armor / toughness | Naga head | Hydra bite | Leviathan bite | Ignis heavy cap probe | Warden melee | Sonic boom |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| aeronautics:aviators_goggles | 1 | 1 / 0 | 10.967 / 9.212 | 22.212 / 18.658 | 25.074 / 21.062 | 26.411 / 22.186 | 22.212 / 18.658 | 5.5 / 5.5 |
| aether:gravitite | 4 | 20 / 8 | 4.656 / 1.676 | 14.505 / 5.222 | 17.833 / 6.42 | 19.502 / 7.021 | 14.505 / 5.222 | 5.5 / 5.5 |
| aether:neptune | 4 | 15 / 4 | 7.681 / 2.765 | 19.704 / 7.093 | 22.243 / 8.008 | 23.43 / 8.435 | 19.704 / 7.093 | 5.5 / 5.5 |
| aether:obsidian | 4 | 20 / 12 | 4.167 / 1.5 | 12.5 / 4.5 | 15.278 / 5.5 | 16.667 / 6 | 12.5 / 4.5 | 5.5 / 5.5 |
| aether:phoenix | 4 | 20 / 8 | 4.656 / 1.676 | 14.505 / 5.222 | 17.833 / 6.42 | 19.502 / 7.021 | 14.505 / 5.222 | 5.5 / 5.5 |
| aether:sentry | 1 | 2 / 0 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| aether:valkyrie | 4 | 20 / 8 | 4.656 / 1.676 | 14.505 / 5.222 | 17.833 / 6.42 | 19.502 / 7.021 | 14.505 / 5.222 | 5.5 / 5.5 |
| aether:zanite | 4 | 15 / 0 | 9.311 / 3.352 | 19.704 / 7.093 | 22.243 / 8.008 | 23.43 / 8.435 | 19.704 / 7.093 | 5.5 / 5.5 |
| alexsmobs:centipede | 1 | 6 / 0.5 | 10.525 / 8.841 | 21.316 / 17.906 | 24.063 / 20.213 | 25.346 / 21.291 | 21.316 / 17.906 | 5.5 / 5.5 |
| alexsmobs:crocodile | 1 | 5 / 1 | 10.613 / 8.915 | 21.495 / 18.056 | 24.265 / 20.383 | 25.559 / 21.47 | 21.495 / 18.056 | 5.5 / 5.5 |
| alexsmobs:emu | 1 | 4 / 0.5 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| alexsmobs:fedora | 1 | 2 / 0.5 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| alexsmobs:flying_fish | 1 | 1 / 0 | 10.967 / 9.212 | 22.212 / 18.658 | 25.074 / 21.062 | 26.411 / 22.186 | 22.212 / 18.658 | 5.5 / 5.5 |
| alexsmobs:frontier_cap | 1 | 3 / 2.5 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alexsmobs:froststalker | 1 | 3 / 0.5 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alexsmobs:moose_headgear | 1 | 3 / 0.5 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alexsmobs:novelty_hat | 1 | 2 / 0 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| alexsmobs:roadrunner | 1 | 3 / 0 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alexsmobs:rocky | 1 | 5 / 0.5 | 10.613 / 8.915 | 21.495 / 18.056 | 24.265 / 20.383 | 25.559 / 21.47 | 21.495 / 18.056 | 5.5 / 5.5 |
| alexsmobs:sombrero | 1 | 2 / 0.5 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| alexsmobs:spiked_turtle_shell | 1 | 3 / 1 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alexsmobs:tarantula_hawk_elytra | 1 | 3 / 0 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alexsmobs:unsettling_kimono | 1 | 3 / 0 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| alloyed:steel | 4 | 18 / 4 | 6.355 / 2.288 | 19.167 / 6.9 | 21.637 / 7.789 | 22.791 / 8.205 | 19.167 / 6.9 | 5.5 / 5.5 |
| cataclysm:bloom_stone_pauldrons | 1 | 9 / 2 | 10.259 / 8.618 | 20.779 / 17.454 | 23.457 / 19.704 | 24.707 / 20.754 | 20.779 / 17.454 | 5.5 / 5.5 |
| cataclysm:bone_reptile | 2 | 17 / 5 | 6.546 / 4.451 | 19.346 / 13.155 | 21.839 / 14.85 | 23.004 / 15.642 | 19.346 / 13.155 | 5.5 / 5.5 |
| cataclysm:cursium | 4 | 28 / 16 | 2.211 / 0.796 | 4.478 / 1.612 | 5.486 / 1.975 | 6.257 / 2.252 | 4.478 / 1.612 | 5.5 / 5.5 |
| cataclysm:ignitium | 4 | 32 / 16 | 2.211 / 0.796 | 4.478 / 1.612 | 5.055 / 1.82 | 5.325 / 1.917 | 4.478 / 1.612 | 5.5 / 5.5 |
| cataclysm:ignitium_elytra | 1 | 11 / 4 | 9.45 / 7.938 | 20.421 / 17.153 | 23.052 / 19.364 | 24.281 / 20.396 | 20.421 / 17.153 | 5.5 / 5.5 |
| cataclysm:ignitium_with_elytra | 4 | 32 / 16 | 2.211 / 0.796 | 4.478 / 1.612 | 5.055 / 1.82 | 5.325 / 1.917 | 4.478 / 1.612 | 5.5 / 5.5 |
| cataclysm:monstrous_helm | 1 | 3 / 3 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| create_jetpack:jetpack | 1 | 4 / 0 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| create_jetpack:netherite_jetpack | 1 | 8 / 3 | 10.348 / 8.692 | 20.958 / 17.605 | 23.659 / 19.873 | 24.92 / 20.933 | 20.958 / 17.605 | 5.5 / 5.5 |
| create:cardboard | 4 | 4 / 0 | 10.702 / 3.853 | 21.675 / 7.803 | 24.468 / 8.808 | 25.772 / 9.278 | 21.675 / 7.803 | 5.5 / 5.5 |
| create:copper_backtank | 1 | 4 / 0 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| create:copper_diving | 2 | 3 / 0 | 10.79 / 7.337 | 21.854 / 14.861 | 24.67 / 16.776 | 25.985 / 17.67 | 21.854 / 14.861 | 5.5 / 5.5 |
| create:netherite_backtank | 1 | 8 / 3 | 10.348 / 8.692 | 20.958 / 17.605 | 23.659 / 19.873 | 24.92 / 20.933 | 20.958 / 17.605 | 5.5 / 5.5 |
| create:netherite_diving | 2 | 6 / 6 | 10.525 / 7.157 | 21.316 / 14.495 | 24.063 / 16.363 | 25.346 / 17.236 | 21.316 / 14.495 | 5.5 / 5.5 |
| deep_aether:skyjade | 4 | 20 / 0 | 7.1 / 2.556 | 18.809 / 6.771 | 21.232 / 7.644 | 22.365 / 8.051 | 18.809 / 6.771 | 5.5 / 5.5 |
| deep_aether:stormforged | 4 | 17 / 0 | 8.427 / 3.034 | 19.346 / 6.965 | 21.839 / 7.862 | 23.004 / 8.281 | 19.346 / 6.965 | 5.5 / 5.5 |
| deep_aether:stratus | 4 | 20 / 6 | 5.005 / 1.802 | 15.938 / 5.738 | 19.659 / 7.077 | 21.527 / 7.75 | 15.938 / 5.738 | 5.5 / 5.5 |
| deeperdarker:resonarium | 4 | 20 / 8 | 0 / — | 0 / — | 0 / — | 0 / — | 0 / — | 5.5 / — |
| deeperdarker:warden | 4 | 24 / 16 | 2.211 / 0.796 | 7.58 / 2.729 | 9.53 / 3.431 | 10.516 / 3.786 | 7.58 / 2.729 | 5.5 / 5.5 |
| friendsandfoes:wildfire_crown | 1 | 3 / 1 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| minecraft:chainmail | 4 | 12 / 0 | 9.994 / 3.598 | 20.242 / 7.287 | 22.85 / 8.226 | 24.068 / 8.665 | 20.242 / 7.287 | 5.5 / 5.5 |
| minecraft:copper | 4 | 10 / 0 | 10.171 / 3.662 | 20.6 / 7.416 | 23.254 / 8.372 | 24.494 / 8.818 | 20.6 / 7.416 | 5.5 / 5.5 |
| minecraft:diamond | 4 | 20 / 8 | 4.656 / 1.676 | 14.505 / 5.222 | 17.833 / 6.42 | 19.502 / 7.021 | 14.505 / 5.222 | 5.5 / 5.5 |
| minecraft:golden | 4 | 11 / 0 | 10.083 / 3.63 | 20.421 / 7.351 | 23.052 / 8.299 | 24.281 / 8.741 | 20.421 / 7.351 | 5.5 / 5.5 |
| minecraft:iron | 4 | 15 / 0 | 9.311 / 3.352 | 19.704 / 7.093 | 22.243 / 8.008 | 23.43 / 8.435 | 19.704 / 7.093 | 5.5 / 5.5 |
| minecraft:leather | 4 | 7 / 0 | 10.436 / 3.757 | 21.137 / 7.609 | 23.861 / 8.59 | 25.133 / 9.048 | 21.137 / 7.609 | 5.5 / 5.5 |
| minecraft:netherite | 4 | 20 / 12 | 4.167 / 1.5 | 12.5 / 4.5 | 15.278 / 5.5 | 16.667 / 6 | 12.5 / 4.5 | 5.5 / 5.5 |
| minecraft:shulker_shell | 1 | 0 / 1 | 11.055 / — | 22.391 / — | 25.277 / — | 26.624 / — | 22.391 / — | 5.5 / — |
| minecraft:turtle | 1 | 2 / 0 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| none | 0 | 0 / 0 | 11.055 / — | 22.391 / — | 25.277 / — | 26.624 / — | 22.391 / — | 5.5 / — |
| powergrid:portable_battery | 1 | 3 / 0 | 10.79 / — | 21.854 / — | 24.67 / — | 25.985 / — | 21.854 / — | 5.5 / — |
| quark:backpack | 1 | 0 / 0 | 11.055 / — | 22.391 / — | 25.277 / — | 26.624 / — | 22.391 / — | 5.5 / — |
| quark:forgotten_hat | 1 | 0 / 0 | 11.055 / — | 22.391 / — | 25.277 / — | 26.624 / — | 22.391 / — | 5.5 / — |
| the_bumblezone:bee_outfit_1 | 4 | 10 / 2 | 10.171 / 3.662 | 20.6 / 7.416 | 23.254 / 8.372 | 24.494 / 8.818 | 20.6 / 7.416 | 5.5 / 5.5 |
| the_bumblezone:bee_outfit_2 | 4 | 10 / 2 | 10.171 / 3.662 | 20.6 / 7.416 | 23.254 / 8.372 | 24.494 / 8.818 | 20.6 / 7.416 | 5.5 / 5.5 |
| the_bumblezone:bee_outfit_trans_1 | 4 | 10 / 2 | 10.171 / 3.662 | 20.6 / 7.416 | 23.254 / 8.372 | 24.494 / 8.818 | 20.6 / 7.416 | 5.5 / 5.5 |
| the_bumblezone:bee_outfit_trans_2 | 4 | 10 / 2 | 10.171 / 3.662 | 20.6 / 7.416 | 23.254 / 8.372 | 24.494 / 8.818 | 20.6 / 7.416 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_1 | 1 | 4 / 0.5 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_2 | 1 | 4 / 0.5 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_trans_1 | 1 | 4 / 0.5 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_trans_2 | 1 | 4 / 0.5 | 10.702 / 8.989 | 21.675 / 18.207 | 24.468 / 20.553 | 25.772 / 21.649 | 21.675 / 18.207 | 5.5 / 5.5 |
| the_bumblezone:carpenter_bee_boots_1 | 1 | 1 / 0.5 | 10.967 / 9.212 | 22.212 / 18.658 | 25.074 / 21.062 | 26.411 / 22.186 | 22.212 / 18.658 | 5.5 / 5.5 |
| the_bumblezone:carpenter_bee_boots_2 | 1 | 1 / 0.5 | 10.967 / 9.212 | 22.212 / 18.658 | 25.074 / 21.062 | 26.411 / 22.186 | 22.212 / 18.658 | 5.5 / 5.5 |
| the_bumblezone:flower_headwear | 1 | 1 / 0 | 10.967 / 9.212 | 22.212 / 18.658 | 25.074 / 21.062 | 26.411 / 22.186 | 22.212 / 18.658 | 5.5 / 5.5 |
| the_bumblezone:honey_bee_leggings_1 | 1 | 3 / 0.5 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| the_bumblezone:honey_bee_leggings_2 | 1 | 3 / 0.5 | 10.79 / 9.064 | 21.854 / 18.357 | 24.67 / 20.723 | 25.985 / 21.828 | 21.854 / 18.357 | 5.5 / 5.5 |
| the_bumblezone:stingless_bee_helmet_1 | 1 | 2 / 0.5 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| the_bumblezone:stingless_bee_helmet_2 | 1 | 2 / 0.5 | 10.879 / 9.138 | 22.033 / 18.508 | 24.872 / 20.893 | 26.198 / 22.007 | 22.033 / 18.508 | 5.5 / 5.5 |
| twilightforest:arctic | 4 | 16 / 8 | 6.424 / 2.313 | 18.088 / 6.512 | 21.878 / 7.876 | 23.217 / 8.358 | 18.088 / 6.512 | 5.5 / 5.5 |
| twilightforest:fiery | 4 | 24 / 6 | 3.236 / 1.165 | 12.355 / 4.448 | 15.615 / 5.621 | 17.268 / 6.216 | 12.355 / 4.448 | 5.5 / 5.5 |
| twilightforest:ironwood | 4 | 16 / 0 | 8.869 / 3.193 | 19.525 / 7.029 | 22.041 / 7.935 | 23.217 / 8.358 | 19.525 / 7.029 | 5.5 / 5.5 |
| twilightforest:knightmetal | 4 | 20 / 4 | 5.47 / 1.969 | 17.848 / 6.425 | 21.232 / 7.644 | 22.365 / 8.051 | 17.848 / 6.425 | 5.5 / 5.5 |
| twilightforest:naga | 2 | 13 / 1 | 9.652 / 6.564 | 20.062 / 13.642 | 22.648 / 15.4 | 23.855 / 16.222 | 20.062 / 13.642 | 5.5 / 5.5 |
| twilightforest:phantom | 2 | 11 / 5 | 9.2 / 6.256 | 20.421 / 13.886 | 23.052 / 15.676 | 24.281 / 16.511 | 20.421 / 13.886 | 5.5 / 5.5 |
| twilightforest:steeleaf | 4 | 20 / 0 | 7.1 / 2.556 | 18.809 / 6.771 | 21.232 / 7.644 | 22.365 / 8.051 | 18.809 / 6.771 | 5.5 / 5.5 |
| twilightforest:travellers | 1 | 2 / 0 | 10.879 / — | 22.033 / — | 24.872 / — | 26.198 / — | 22.033 / — | 5.5 / — |
| twilightforest:travellers_belt | 1 | 0 / 0 | 11.055 / — | 22.391 / — | 25.277 / — | 26.624 / — | 22.391 / — | 5.5 / — |
| twilightforest:travellers_gloves | 1 | 0 / 0 | 11.055 / — | 22.391 / — | 25.277 / — | 26.624 / — | 22.391 / — | 5.5 / — |
| twilightforest:travellers_gloves_belt | 4 | 4 / 0 | 10.702 / — | 21.675 / — | 24.468 / — | 25.772 / — | 21.675 / — | 5.5 / — |
| twilightforest:travellers_gloves_wings | 4 | 7 / 0 | 10.436 / — | 21.137 / — | 23.861 / — | 25.133 / — | 21.137 / — | 5.5 / — |
| twilightforest:travellers_goggles | 1 | 2 / 0 | 10.879 / — | 22.033 / — | 24.872 / — | 26.198 / — | 22.033 / — | 5.5 / — |
| twilightforest:travellers_vest | 1 | 4 / 0 | 10.702 / — | 21.675 / — | 24.468 / — | 25.772 / — | 21.675 / — | 5.5 / — |
| twilightforest:travellers_vest_belt | 4 | 8 / 0 | 10.348 / — | 20.958 / — | 23.659 / — | 24.92 / — | 20.958 / — | 5.5 / — |
| twilightforest:travellers_vest_wings | 4 | 11 / 0 | 10.083 / — | 20.421 / — | 23.052 / — | 24.281 / — | 20.421 / — | 5.5 / — |
| twilightforest:travellers_wings | 1 | 3 / 0 | 10.79 / — | 21.854 / — | 24.67 / — | 25.985 / — | 21.854 / — | 5.5 / — |
| twilightforest:yeti | 4 | 20 / 12 | 4.167 / 1.5 | 12.5 / 4.5 | 15.278 / 5.5 | 16.667 / 6 | 12.5 / 4.5 | 5.5 / 5.5 |

The configured boss damage numbers are calibrated to Protection IV netherite. They are not universal health-loss caps: weaker armor can take considerably more. The 200-HP instrumented player avoids a death/downing cutoff, so a value above 10 hearts denotes a potentially lethal single hit for an ordinary 20-HP player. Repeated attacks, status effects, falls, shield use, and accessory/set abilities are separate from these single-hit baseline measurements.
