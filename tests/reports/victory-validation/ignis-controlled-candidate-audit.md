# Ignis controlled evidence for nine natural-coverage gaps

All nine candidates have executed native callback evidence in the frozen 1.2.1 controlled results. Their continuous-fight selection gaps remain separate. This conclusion comes from named scenarios, captured native callers and passing phase assertions, rather than the overall assertion count.

The [machine-readable audit](ignis-controlled-candidate-audit.json) contains the exact result rows and verified source hashes. These results ran with production `53C2399B` and helper `C1C5ACEC`. The main source is `tests/evidence/1.2.1-controlled-final-results/exhaustive_cataclysm-attacks.json`, paired with `exhaustive_cataclysm.json`.

| Candidate | Executed evidence | Coverage limit |
| --- | --- | --- |
| BODY_CHECK_ATTACK_SOUL2 | `phase1/BODY_CHECK_ATTACK_SOUL2/native_frames` captured `BodyCheckAttack → aiStep`, one accepted hit of 5.806964 HP. Corresponding phase0 and phase2 contexts also hit. | The fixture set the animation and each frame directly. No matching goal was found. Its normal follow-up selector was bypassed. |
| BODY_CHECK_ATTACK_SOUL3 | `phase1/BODY_CHECK_ATTACK_SOUL3/native_frames` captured the same native caller chain and one 5.806964 HP hit, with hits in the other two contexts. | This verifies the attack callback. It does not verify selection from phase1 SHIELD_SMASH while the shield remains intact. |
| BODY_CHECK_ATTACK_SOUL4 | `phase1/BODY_CHECK_ATTACK_SOUL4/native_frames` captured the same native caller chain and one 5.806964 HP hit, with hits in the other two contexts. | The failed-poke follow-up, random choice and proximity requirements were bypassed by direct animation assignment. |
| SWING_ATTACK_BERSERK | `phase2_shield_broken/SWING_ATTACK_BERSERK/native_frames` executed matching `Swing_Attack_Goal` callbacks and `AreaAttack → aiStep`. One hit removed 7 HP and matched `ignis.sword`. | The animation and each frame were staged. This does not establish natural selection, chase or full-combo damage. |
| PHASE_3 | `phase2` records a real `boss.tick()` selecting PHASE_3 after health was staged below one-third in prior phase1. The `phase1/PHASE_3/native_frames` row separately captured `Phase_Transition → aiStep` and the native shield explosion. | The health threshold and prior phase were fixture inputs. The later animation frames were explicitly advanced, with native `aiStep` processing each frame. |
| getBossPhase=2 | `exhaustive_ignis_phase2_native_tick`, `native_completion`, `returns_idle` and `nbt_reload` all passed. Native completion produced phase2 and entity NBT reload retained it. | This proves a controlled native transition and serialization. It is not an autonomous combat progression or a separate server restart. |
| ULTIMATE_ATTACK | `phase2_shield_broken/ULTIMATE_ATTACK/native_frames` executed native matching-goal callbacks and `aiStep`, producing an accepted `AreaAttack` hit. Six separate outer-lane probes captured `UltimateAttack → aiStep`, each removing 14.409420 HP. | Animation, phase, frame and target pose were staged. These are independent contacts, not a naturally selected complete ultimate sequence against a moving player. |
| ignis.flame_pulse | `phase2_shield_broken/ULTIMATE_ATTACK/emitted_cataclysm:flame_strike/effect_ticks` captured eight native `Flame_Strike_Entity.damage → tick` hits, each 1.5 HP, matching `ignis.flame_pulse.cap`. Phase0 and phase1 each have the same eight accepted effect contacts. | Each ultimate context natively emitted seven flame strikes. One representative was retained and ticked in staged contact, with the player reset between ticks. This does not test every emitted trajectory or cumulative DOT. |
| ignis.ultimate | The ultimate native-frame row recorded raw 49.5 HP and accepted 18.999998 HP through `AreaAttack → aiStep`, matching `ignis.ultimate`. Six separate Brand0–V frame74 probes also matched the approved 9.5-heart limit. | Frame74, phase and defenses were fixture inputs. The separate armor and netherite Brand sweeps add gear/effect measurements, with their own scope. |

The animation sweep runs each declared animation in phase0, phase1 and phase2/shield-broken contexts. It explicitly restores animation and frame before native callbacks. Some combinations intentionally bypass normal availability. The meaningful body-check evidence therefore demonstrates native callback damage, while the missing natural follow-up branches stay open.

The three direct helpers in `ActualCataclysmAttacks.run` alone would not establish this coverage. The stronger evidence above comes from `ExhaustiveCataclysm` and `ExhaustiveCapture`. The generic `DamageCases` flame-cap probe manufactures an incoming damage request and is not used here as proof of native flame emission or animation execution.

An existing command can repeat the controlled evidence when the isolated server is idle.

```text
bbtest exhaustive cataclysm cataclysm:ignis
```

It writes filtered `exhaustive_cataclysm_ignis` assertion and attack files. `bbtest ignis_brand_netherite` separately repeats opening, shield-smash melee and ultimate frame74 across Brand0–V with fresh and enchanted-apple effect snapshots. Neither command supplies missing natural random-selection evidence. No command was executed for this read-only audit, and no test or production implementation changed.
