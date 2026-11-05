# Armor test results

90 outfits; 13120 measured attack/outfit/enchantment combinations. All damage below is **hearts lost on Hard**. Each cell shows **plain / Protection IV**; full Protection IV means level IV on each equipped piece. One heart is 2 HP.

Actual server Player.hurt with registered equipment, Hard difficulty; event-based item defenses apply. Damage fixture inputs reproduce attacks at a 20-HP target; 200-HP measurement victim avoids death/downing truncation. Native probes are separately labeled. No shields, accessories/gloves, active potion effects, set-effect warmup, or environmental bonuses. Partial outfits leave absent slots empty. Protection IV is tested only when every equipped item reports support through NeoForge supportsEnchantment; unsupported variants are omitted. Each hit starts with fresh armor, so durability-absorbing sets require the separate endurance probes.

An em dash means that enchantment variant was not tested because it is unsupported (or the outfit has no items). The Protection IV column never forces an enchantment rejected by the item. Fresh Resonarium can show zero health loss because its native mod transfers armor-sensitive damage to item durability; this is not unlimited invulnerability. See [set effects and endurance](armor-effects-results.md) for repeated-hit measurements.

The table gives representative attacks for every tested outfit. [All measured attacks](armor-all-attacks.csv) contains every fixture/native attack result for every outfit and enchantment state. [Item inventory](armor-item-inventory.csv) gives registry IDs, equipment slots, materials, and class names. A partial outfit leaves its other armor slots empty; it is not comparable to a complete set as an equal-slot loadout.

| Armor outfit | Pieces | Armor / toughness | Naga head | Hydra bite | Leviathan bite | Ignis heavy cap probe | Ignis native ultimate | Warden melee | Sonic boom |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| aeronautics:aviators_goggles | 1 | 1 / 0 | 10.725 / 9.009 | 14.25 / 11.97 | 15.491 / 13.012 | 15.491 / 13.012 | 17.222 / 14.466 | 15.491 / 13.012 | 5.5 / 5.5 |
| aether:gravitite | 4 | 20 / 8 | 4.5 / 1.62 | 7 / 2.52 | 8 / 2.88 | 8 / 2.88 | 9.5 / 3.42 | 8 / 2.88 | 5.5 / 5.5 |
| aether:neptune | 4 | 15 / 4 | 7.442 / 2.679 | 11.249 / 4.05 | 12.749 / 4.59 | 12.749 / 4.59 | 14.981 / 5.393 | 12.749 / 4.59 | 5.5 / 5.5 |
| aether:obsidian | 4 | 20 / 12 | 4.032 / 1.452 | 6.175 / 2.223 | 7.025 / 2.529 | 7.025 / 2.529 | 8.294 / 2.986 | 7.025 / 2.529 | 5.5 / 5.5 |
| aether:phoenix | 4 | 20 / 8 | 4.5 / 1.62 | 7 / 2.52 | 8 / 2.88 | 8 / 2.88 | 9.5 / 3.42 | 8 / 2.88 | 5.5 / 5.5 |
| aether:sentry | 1 | 2 / 0 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| aether:valkyrie | 4 | 20 / 8 | 4.5 / 1.62 | 7 / 2.52 | 8 / 2.88 | 8 / 2.88 | 9.5 / 3.42 | 8 / 2.88 | 5.5 / 5.5 |
| aether:zanite | 4 | 15 / 0 | 9 / 3.24 | 12.641 / 4.551 | 13.742 / 4.947 | 13.742 / 4.947 | 15.277 / 5.5 | 13.742 / 4.947 | 5.5 / 5.5 |
| alexsmobs:centipede | 1 | 6 / 0.5 | 10.292 / 8.646 | 13.675 / 11.487 | 14.866 / 12.487 | 14.866 / 12.487 | 16.527 / 13.883 | 14.866 / 12.487 | 5.5 / 5.5 |
| alexsmobs:crocodile | 1 | 5 / 1 | 10.379 / 8.718 | 13.79 / 11.584 | 14.991 / 12.592 | 14.991 / 12.592 | 16.666 / 14 | 14.991 / 12.592 | 5.5 / 5.5 |
| alexsmobs:emu | 1 | 4 / 0.5 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| alexsmobs:fedora | 1 | 2 / 0.5 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| alexsmobs:flying_fish | 1 | 1 / 0 | 10.725 / 9.009 | 14.25 / 11.97 | 15.491 / 13.012 | 15.491 / 13.012 | 17.222 / 14.466 | 15.491 / 13.012 | 5.5 / 5.5 |
| alexsmobs:frontier_cap | 1 | 3 / 2.5 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alexsmobs:froststalker | 1 | 3 / 0.5 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alexsmobs:moose_headgear | 1 | 3 / 0.5 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alexsmobs:novelty_hat | 1 | 2 / 0 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| alexsmobs:roadrunner | 1 | 3 / 0 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alexsmobs:rocky | 1 | 5 / 0.5 | 10.379 / 8.718 | 13.79 / 11.584 | 14.991 / 12.592 | 14.991 / 12.592 | 16.666 / 14 | 14.991 / 12.592 | 5.5 / 5.5 |
| alexsmobs:sombrero | 1 | 2 / 0.5 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| alexsmobs:spiked_turtle_shell | 1 | 3 / 1 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alexsmobs:tarantula_hawk_elytra | 1 | 3 / 0 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alexsmobs:unsettling_kimono | 1 | 3 / 0 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| alloyed:steel | 4 | 18 / 4 | 6.144 / 2.212 | 9.525 / 3.429 | 10.875 / 3.915 | 10.875 / 3.915 | 12.898 / 4.643 | 10.875 / 3.915 | 5.5 / 5.5 |
| cataclysm:bloom_stone_pauldrons | 1 | 9 / 2 | 10.033 / 8.428 | 13.331 / 11.198 | 14.491 / 12.173 | 14.491 / 12.173 | 16.111 / 13.533 | 14.491 / 12.173 | 5.5 / 5.5 |
| cataclysm:bone_reptile | 2 | 17 / 5 | 6.337 / 4.309 | 9.676 / 6.58 | 10.999 / 7.48 | 10.999 / 7.48 | 12.974 / 8.823 | 10.999 / 7.48 | 5.5 / 5.5 |
| cataclysm:cursium | 4 | 28 / 16 | 2.162 / 0.778 | 2.873 / 1.034 | 3.123 / 1.124 | 3.123 / 1.124 | 3.472 / 1.25 | 3.123 / 1.124 | 5.5 / 5.5 |
| cataclysm:ignitium | 4 | 32 / 16 | 2.162 / 0.778 | 2.873 / 1.034 | 3.123 / 1.124 | 3.123 / 1.124 | 3.472 / 1.25 | 3.123 / 1.124 | 5.5 / 5.5 |
| cataclysm:ignitium_elytra | 1 | 11 / 4 | 9.171 / 7.704 | 13.101 / 11.005 | 14.241 / 11.963 | 14.241 / 11.963 | 15.833 / 13.3 | 14.241 / 11.963 | 5.5 / 5.5 |
| cataclysm:ignitium_with_elytra | 4 | 32 / 16 | 2.162 / 0.778 | 2.873 / 1.034 | 3.123 / 1.124 | 3.123 / 1.124 | 3.472 / 1.25 | 3.123 / 1.124 | 5.5 / 5.5 |
| cataclysm:monstrous_helm | 1 | 3 / 3 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| create_jetpack:jetpack | 1 | 4 / 0 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| create_jetpack:netherite_jetpack | 1 | 8 / 3 | 10.119 / 8.5 | 13.446 / 11.294 | 14.616 / 12.278 | 14.616 / 12.278 | 16.25 / 13.65 | 14.616 / 12.278 | 5.5 / 5.5 |
| create:cardboard | 4 | 4 / 0 | 10.465 / 3.768 | 13.905 / 5.006 | 15.116 / 5.442 | 15.116 / 5.442 | 16.805 / 6.05 | 15.116 / 5.442 | 5.5 / 5.5 |
| create:copper_backtank | 1 | 4 / 0 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| create:copper_diving | 2 | 3 / 0 | 10.552 / 7.175 | 14.02 / 9.534 | 15.241 / 10.364 | 15.241 / 10.364 | 16.944 / 11.522 | 15.241 / 10.364 | 5.5 / 5.5 |
| create:netherite_backtank | 1 | 8 / 3 | 10.119 / 8.5 | 13.446 / 11.294 | 14.616 / 12.278 | 14.616 / 12.278 | 16.25 / 13.65 | 14.616 / 12.278 | 5.5 / 5.5 |
| create:netherite_diving | 2 | 6 / 6 | 10.292 / 6.999 | 13.675 / 9.299 | 14.866 / 10.109 | 14.866 / 10.109 | 16.527 / 11.239 | 14.866 / 10.109 | 5.5 / 5.5 |
| deep_aether:skyjade | 4 | 20 / 0 | 6.838 / 2.462 | 11.127 / 4.006 | 12.877 / 4.636 | 12.877 / 4.636 | 14.583 / 5.25 | 12.877 / 4.636 | 5.5 / 5.5 |
| deep_aether:stormforged | 4 | 17 / 0 | 8.135 / 2.929 | 12.411 / 4.468 | 13.492 / 4.857 | 13.492 / 4.857 | 15 / 5.4 | 13.492 / 4.857 | 5.5 / 5.5 |
| deep_aether:stratus | 4 | 20 / 6 | 4.834 / 1.74 | 7.59 / 2.732 | 8.697 / 3.131 | 8.697 / 3.131 | 10.361 / 3.73 | 8.697 / 3.131 | 5.5 / 5.5 |
| deeperdarker:resonarium | 4 | 20 / 8 | 0 / — | 0 / — | 0 / — | 0 / — | 0 / — | 0 / — | 5.5 / — |
| deeperdarker:warden | 4 | 24 / 16 | 2.162 / 0.778 | 3.326 / 1.197 | 3.876 / 1.395 | 3.876 / 1.395 | 4.713 / 1.697 | 3.876 / 1.395 | 5.5 / 5.5 |
| friendsandfoes:wildfire_crown | 1 | 3 / 1 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| minecraft:chainmail | 4 | 12 / 0 | 9.773 / 3.518 | 12.986 / 4.675 | 14.116 / 5.082 | 14.116 / 5.082 | 15.694 / 5.65 | 14.116 / 5.082 | 5.5 / 5.5 |
| minecraft:copper | 4 | 10 / 0 | 9.946 / 3.581 | 13.216 / 4.758 | 14.366 / 5.172 | 14.366 / 5.172 | 15.972 / 5.75 | 14.366 / 5.172 | 5.5 / 5.5 |
| minecraft:diamond | 4 | 20 / 8 | 4.5 / 1.62 | 7 / 2.52 | 8 / 2.88 | 8 / 2.88 | 9.5 / 3.42 | 8 / 2.88 | 5.5 / 5.5 |
| minecraft:golden | 4 | 11 / 0 | 9.86 / 3.55 | 13.101 / 4.716 | 14.241 / 5.127 | 14.241 / 5.127 | 15.833 / 5.7 | 14.241 / 5.127 | 5.5 / 5.5 |
| minecraft:iron | 4 | 15 / 0 | 9 / 3.24 | 12.641 / 4.551 | 13.742 / 4.947 | 13.742 / 4.947 | 15.277 / 5.5 | 13.742 / 4.947 | 5.5 / 5.5 |
| minecraft:leather | 4 | 7 / 0 | 10.206 / 3.674 | 13.56 / 4.882 | 14.741 / 5.307 | 14.741 / 5.307 | 16.388 / 5.9 | 14.741 / 5.307 | 5.5 / 5.5 |
| minecraft:netherite | 4 | 20 / 12 | 4.032 / 1.452 | 6.175 / 2.223 | 7.025 / 2.529 | 7.025 / 2.529 | 8.294 / 2.986 | 7.025 / 2.529 | 5.5 / 5.5 |
| minecraft:shulker_shell | 1 | 0 / 1 | 10.811 / — | 14.365 / — | 15.616 / — | 15.616 / — | 17.361 / — | 15.616 / — | 5.5 / — |
| minecraft:turtle | 1 | 2 / 0 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| none | 0 | 0 / 0 | 10.811 / — | 14.365 / — | 15.616 / — | 15.616 / — | 17.361 / — | 15.616 / — | 5.5 / — |
| powergrid:portable_battery | 1 | 3 / 0 | 10.552 / — | 14.02 / — | 15.241 / — | 15.241 / — | 16.944 / — | 15.241 / — | 5.5 / — |
| quark:backpack | 1 | 0 / 0 | 10.811 / — | 14.365 / — | 15.616 / — | 15.616 / — | 17.361 / — | 15.616 / — | 5.5 / — |
| quark:forgotten_hat | 1 | 0 / 0 | 10.811 / — | 14.365 / — | 15.616 / — | 15.616 / — | 17.361 / — | 15.616 / — | 5.5 / — |
| the_bumblezone:bee_outfit_1 | 4 | 10 / 2 | 9.946 / 3.581 | 13.216 / 4.758 | 14.366 / 5.172 | 14.366 / 5.172 | 15.972 / 5.75 | 14.366 / 5.172 | 5.5 / 5.5 |
| the_bumblezone:bee_outfit_2 | 4 | 10 / 2 | 9.946 / 3.581 | 13.216 / 4.758 | 14.366 / 5.172 | 14.366 / 5.172 | 15.972 / 5.75 | 14.366 / 5.172 | 5.5 / 5.5 |
| the_bumblezone:bee_outfit_trans_1 | 4 | 10 / 2 | 9.946 / 3.581 | 13.216 / 4.758 | 14.366 / 5.172 | 14.366 / 5.172 | 15.972 / 5.75 | 14.366 / 5.172 | 5.5 / 5.5 |
| the_bumblezone:bee_outfit_trans_2 | 4 | 10 / 2 | 9.946 / 3.581 | 13.216 / 4.758 | 14.366 / 5.172 | 14.366 / 5.172 | 15.972 / 5.75 | 14.366 / 5.172 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_1 | 1 | 4 / 0.5 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_2 | 1 | 4 / 0.5 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_trans_1 | 1 | 4 / 0.5 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| the_bumblezone:bumble_bee_chestplate_trans_2 | 1 | 4 / 0.5 | 10.465 / 8.791 | 13.905 / 11.68 | 15.116 / 12.697 | 15.116 / 12.697 | 16.805 / 14.116 | 15.116 / 12.697 | 5.5 / 5.5 |
| the_bumblezone:carpenter_bee_boots_1 | 1 | 1 / 0.5 | 10.725 / 9.009 | 14.25 / 11.97 | 15.491 / 13.012 | 15.491 / 13.012 | 17.222 / 14.466 | 15.491 / 13.012 | 5.5 / 5.5 |
| the_bumblezone:carpenter_bee_boots_2 | 1 | 1 / 0.5 | 10.725 / 9.009 | 14.25 / 11.97 | 15.491 / 13.012 | 15.491 / 13.012 | 17.222 / 14.466 | 15.491 / 13.012 | 5.5 / 5.5 |
| the_bumblezone:flower_headwear | 1 | 1 / 0 | 10.725 / 9.009 | 14.25 / 11.97 | 15.491 / 13.012 | 15.491 / 13.012 | 17.222 / 14.466 | 15.491 / 13.012 | 5.5 / 5.5 |
| the_bumblezone:honey_bee_leggings_1 | 1 | 3 / 0.5 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| the_bumblezone:honey_bee_leggings_2 | 1 | 3 / 0.5 | 10.552 / 8.864 | 14.02 / 11.777 | 15.241 / 12.802 | 15.241 / 12.802 | 16.944 / 14.233 | 15.241 / 12.802 | 5.5 / 5.5 |
| the_bumblezone:stingless_bee_helmet_1 | 1 | 2 / 0.5 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| the_bumblezone:stingless_bee_helmet_2 | 1 | 2 / 0.5 | 10.638 / 8.936 | 14.135 / 11.873 | 15.366 / 12.907 | 15.366 / 12.907 | 17.083 / 14.35 | 15.366 / 12.907 | 5.5 / 5.5 |
| twilightforest:arctic | 4 | 16 / 8 | 6.23 / 2.243 | 9.298 / 3.347 | 10.498 / 3.779 | 10.498 / 3.779 | 12.278 / 4.42 | 10.498 / 3.779 | 5.5 / 5.5 |
| twilightforest:fiery | 4 | 24 / 6 | 3.104 / 1.117 | 5.291 / 1.905 | 6.198 / 2.231 | 6.198 / 2.231 | 7.583 / 2.73 | 6.198 / 2.231 | 5.5 / 5.5 |
| twilightforest:ironwood | 4 | 16 / 0 | 8.568 / 3.084 | 12.526 / 4.509 | 13.617 / 4.902 | 13.617 / 4.902 | 15.139 / 5.45 | 13.617 / 4.902 | 5.5 / 5.5 |
| twilightforest:knightmetal | 4 | 20 / 4 | 5.279 / 1.901 | 8.376 / 3.015 | 9.626 / 3.465 | 9.626 / 3.465 | 11.509 / 4.143 | 9.626 / 3.465 | 5.5 / 5.5 |
| twilightforest:naga | 2 | 13 / 1 | 9.345 / 6.355 | 12.871 / 8.752 | 13.992 / 9.514 | 13.992 / 9.514 | 15.555 / 10.578 | 13.992 / 9.514 | 5.5 / 5.5 |
| twilightforest:phantom | 2 | 11 / 5 | 8.932 / 6.073 | 13.101 / 8.909 | 14.241 / 9.684 | 14.241 / 9.684 | 15.833 / 10.766 | 14.241 / 9.684 | 5.5 / 5.5 |
| twilightforest:steeleaf | 4 | 20 / 0 | 6.838 / 2.462 | 11.127 / 4.006 | 12.877 / 4.636 | 12.877 / 4.636 | 14.583 / 5.25 | 12.877 / 4.636 | 5.5 / 5.5 |
| twilightforest:travellers | 1 | 2 / 0 | 10.638 / — | 14.135 / — | 15.366 / — | 15.366 / — | 17.083 / — | 15.366 / — | 5.5 / — |
| twilightforest:travellers_belt | 1 | 0 / 0 | 10.811 / — | 14.365 / — | 15.616 / — | 15.616 / — | 17.361 / — | 15.616 / — | 5.5 / — |
| twilightforest:travellers_gloves | 1 | 0 / 0 | 10.811 / — | 14.365 / — | 15.616 / — | 15.616 / — | 17.361 / — | 15.616 / — | 5.5 / — |
| twilightforest:travellers_gloves_belt | 4 | 4 / 0 | 10.465 / — | 13.905 / — | 15.116 / — | 15.116 / — | 16.805 / — | 15.116 / — | 5.5 / — |
| twilightforest:travellers_gloves_wings | 4 | 7 / 0 | 10.206 / — | 13.56 / — | 14.741 / — | 14.741 / — | 16.388 / — | 14.741 / — | 5.5 / — |
| twilightforest:travellers_goggles | 1 | 2 / 0 | 10.638 / — | 14.135 / — | 15.366 / — | 15.366 / — | 17.083 / — | 15.366 / — | 5.5 / — |
| twilightforest:travellers_vest | 1 | 4 / 0 | 10.465 / — | 13.905 / — | 15.116 / — | 15.116 / — | 16.805 / — | 15.116 / — | 5.5 / — |
| twilightforest:travellers_vest_belt | 4 | 8 / 0 | 10.119 / — | 13.446 / — | 14.616 / — | 14.616 / — | 16.25 / — | 14.616 / — | 5.5 / — |
| twilightforest:travellers_vest_wings | 4 | 11 / 0 | 9.86 / — | 13.101 / — | 14.241 / — | 14.241 / — | 15.833 / — | 14.241 / — | 5.5 / — |
| twilightforest:travellers_wings | 1 | 3 / 0 | 10.552 / — | 14.02 / — | 15.241 / — | 15.241 / — | 16.944 / — | 15.241 / — | 5.5 / — |
| twilightforest:yeti | 4 | 20 / 12 | 4.032 / 1.452 | 6.175 / 2.223 | 7.025 / 2.529 | 7.025 / 2.529 | 8.294 / 2.986 | 7.025 / 2.529 | 5.5 / 5.5 |

The configured boss damage numbers are calibrated to full unenchanted diamond on Hard. They are not universal health-loss caps: weaker armor can take considerably more. The 200-HP instrumented player avoids a death/downing cutoff, so a value at or above 10 hearts denotes a potentially lethal single hit for an ordinary 20-HP player. The native Ignis ultimate is capped independently of the measuring victim's extra health. Repeated attacks, status effects, falls, shield use, and accessory/set abilities are separate from these single-hit baseline measurements. Combined projectile ceilings are validated separately with shared native impact/explosion callbacks.
