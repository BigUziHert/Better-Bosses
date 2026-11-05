# Native victory and coverage checks

The new victory run uses a separate evidence directory. The published 1.2.0 report and its 46 earlier client observations stay historical. Winning all 23 encounters, observing each move/phase, and validating damage are three separate requirements.

`EncounterTelemetry` is test-only and read-only. `FullFightCases` starts it after precombat setup, samples it every server tick before terminal checks, and finishes it before cleanup. It writes compact `.telemetry.jsonl` events and a `.telemetry.coverage.json` ledger beside each ordinary encounter log. The ordinary summary exporter excludes these compact telemetry files.

- Static native animation objects are resolved to their declared names, avoiding Java object-address strings. Cataclysm numbered states retain the installed-source names from the controlled inventory. Known dormant/lifecycle entries are labeled separately.
- Native phase/formation getters, Hydra head states, Naga movement states, active goals, Warden activity/pose/anger and relevant synchronized flags are observed. Derived BMD health bands are explicitly identified as derived; they do not replace native selection evidence.
- A separate Dragon observer runs immediately after the native phase manager assigns `currentPhase`, before `begin()` can cause a second transition in the same tick. It records the actual assigned instance, without changing it. Unchanged-phase requests return before assignment and are not counted. The ledger capability flag distinguishes this observer from earlier per-tick-only captures.
- Test-only mixins observe actual BMD `perform()` entries and server entity-event publication. They do not cancel calls, alter arguments/results, select moves or send replacement events. Native selected actions and their later projectiles/damage are distinct observations.
- Every participant incoming/post-damage event retains source, native call stack, armor/toughness, health and absorption. Native reductions—including absorption and invulnerability—are recorded separately. Shield resolution records blocked damage and durability cost. A zero-HP-damage post event is retained rather than discarded.
- Production profile-counter deltas identify which damage family actually processed the incoming call. Cap suffixes normalize to the setting name; Maledictus's two mace endpoints form one native rage-interpolated family. Combined projectile budgets remain separate controlled accounting checks, not attacks that must deal their maximum during a fight.
- Projectile/entity emission, owner changes (including reflection), collision type and removal are logged. Player UUIDs are omitted; boss/projectile UUIDs distinguish actual test entities. A projectile that never reaches an observable collision is not automatically labeled a miss.

The observers cannot identify a native immunity rejection that returns before any event is posted. An incoming event without a matching post event therefore remains `no_matching_post_event`, with observed cancellation/shield flags and no invented final cause. Goal/state observations show selection or activity, not necessarily completed damage execution. No gameplay field is rewritten by telemetry.

Goal and Hydra-state observation counts include reads made for damage context, so they are not duration-in-ticks measurements. First/last timestamps and actual transition records provide timing evidence. A BMD action whose owning actor cannot be resolved is not credited; native entity-event observations provide an independent partial cross-check.

Catalogue revision `2026-11-05-r2` adds explicit conditional candidates to existing capture: Warden activities/poses/anger, vibration and actual darkness; ordinary/dangerous Wither skull variants; Minoshroom ground-charge windup; Maledictus rage and Ignis shield-durability values. Native passenger mount/release transitions distinguish successful Yeti/Cataclysm grabs from selected animations, including the Leviathan tongue and Scylla anchor vehicles. Leviathan's completed `getMeltDown` mode is distinct from its half-health threshold. Effect observation counts are per-sample presence counts, not numbers of independent effect applications.

These conditional candidates may require a separate observation interval. Warden idle/sensing/low-anger states can be skipped by precombat target setup; digging is a despawn lifecycle and is inapplicable to persistent victory fixtures. Dangerous Wither skulls are rare or depend on side-head idle behavior. Ignis final-phase auto-break can set shield durability3 without the reflected-fireball shield-break animation, so the latter remains separately required. Remnant's missed-attack charge meter is logged as a conditional mechanic rather than inventing six independent attack phases. Unattempted bosses lack a runtime inventory file; their static animations and native enum candidates are already discovered automatically when an encounter begins.

## Readiness criteria

1. Each of the 23 managed boss IDs needs at least one actual native victory. For a group encounter, every managed member must reach native death; removal, timeout, lethal knockout, controller error and cancellation are not wins. A subsequent retry does not erase a previous loss.
2. The intended equipment/consumables and ordinary inputs must be present in the encounter evidence. Health, armor breakage, native effects and absorption remain real throughout combat. The production JAR must match the artifact under review.
3. Telemetry must start before combat, finish before cleanup, show no observation errors, and retain its complete file. Native BMD hooks need a runtime smoke check; successful compilation alone is not evidence they executed.
4. Every active move/phase candidate must be either observed, or explicitly retained as missing. Source-based adjudication may explain dormant entries, lifecycle states and conditional windups; sharing a damage helper does not establish that every branch occurred.
5. The earlier controlled matrix provides independent numerical/native-callback evidence: 7,785 passing assertions, 73 settings, 90 outfits, 13,120 armor measurements, 258 set-effect measurements and explicit attack/phase fixtures. It does not substitute for a new natural victory or prove every attack under enchanted-golden-apple effects. Additional staged probes must identify their changed defense conditions and stay separate from native-fight results.

Generate the ledger from one or more explicitly selected new-run directories:

```powershell
./tests/export-encounter-coverage.ps1 -InputDirectories './tests/results/full-fights/QUEUE' -OutputDirectory './tests/reports/victory-validation'
```

The exporter lists all 23 bosses even before any attempt exists, combines observed candidates across supplied attempts, links exact-name prior controlled examples where available, and preserves missing candidates. It never turns earlier controlled records into natural coverage. `allMovesAndPhasesCertified` intentionally remains false pending review of the native inventory and outstanding conditional cases; the separate `all23NativeDefeats` field reflects actual recorded wins.
