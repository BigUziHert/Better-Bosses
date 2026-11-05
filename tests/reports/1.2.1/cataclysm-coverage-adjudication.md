# Cataclysm coverage adjudication

The current capture contains 550 scenarios with accepted native player damage. All 33 active outgoing boss damage helpers in the installed-source inventory were checked against the actual captured native call stacks; 0 helpers lack a capture. Of 176 raw uncovered pose records, 0 remain unresolved after matching emitted effects, dedicated launch probes, hitbox alternatives, and non-damaging stages.

| Raw pose classification | Records |
|---|---:|
| alternate_hitbox_captured | 3 |
| damaging_child_captured | 86 |
| dedicated_native_launch_captured | 8 |
| non_damaging_stage | 79 |

The [pose-by-pose adjudication](cataclysm-adjudicated-pose-misses.csv) preserves every raw miss and names the accepted related capture. The [native helper inventory](cataclysm-native-damage-helpers.csv) provides a damage-stack example for every active helper. Defensive incoming-damage methods, Scylla's zero-damage self-reaction, and its dormant private Whip helper are excluded from the outgoing attack inventory. Active electric-whip moves use AreaAttack and emitted lightning, which are checked independently.

Health-phase checks cover Guardian 50%, Monstrosity 40%, Harbinger 50%, Remnant 50%, Leviathan 50%, Maledictus 50%/25%, and Scylla/Ignis two-thirds/one-third thresholds. Each configured phase context has staged native attack-frame probes; Scylla and Ignis transitions run through completion and entity serialization. Maledictus rage 0–5 and Ignis Brand 0–V have separate native measurements. The detailed phase assertions and per-move contexts are in the suite JSON and general native-scenario report.

This establishes native attack/damage paths and the enumerated health phases in controlled fixtures. It does not establish complete autonomous fights, every random attack choice, simultaneous projectile/DOT combinations, natural target switching throughout a fight, every environmental trajectory, or visual client animation correctness. Non-damaging stage classification does not claim that every parry, movement, lava-drain, or defensive gameplay condition was exercised. Existing helper invocations in aiStep may contain several branches; per-animation/phase captures supply the finer context rather than treating a single helper hit as universal branch coverage.
