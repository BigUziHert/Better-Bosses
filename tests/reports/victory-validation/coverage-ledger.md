# Native encounter coverage ledger

**22 of 23 bosses have a recorded native defeat** across 65 completed telemetry attempts. 17 declared move/phase/profile candidates remain unobserved in completed attempts. 0 bosses lack the expanded schema-2 inventory, so their missing-candidate count is incomplete. Observation errors: 0. Encounter controller errors: 1. Unclosed telemetry logs: 2.

This is a readiness ledger, not a passing certificate. A win does not establish every move, and a selected animation does not prove its damage connected. [Every candidate](moves-and-phases.csv), [attempt outcomes](attempts.csv), [move/phase timeline](native-move-phase-timeline.csv), [native damage callers](native-damage-callers.csv) and [every player damage/absorption event](native-player-damage.csv) preserve the distinction. The timeline labels sampled states separately from exact native phase assignments and action-entry/event observations. The raw compact telemetry also contains incoming attempts without post events, shield resolution, effect changes, projectiles and their owners. Fully absorbed hits retain their actual absorption cost even when the player loses no HP.

Completed attempts record 49 native knockdowns and 47 native self-revivals. The attempt CSV preserves those outcomes alongside final armor and remaining pieces.

The [apple accounting table](apple-accounting.csv) separates ordinary food-Finish events from the measured decrease between starting and ending inventory. Native self-revival uses an additional apple outside the food-Finish counter. Each row reconciles those events with the inventory decrease and flags any remaining difference for review. The existing enchanted_apples_eaten field retains its original food-event meaning.

The [independent movement audit](client-flight-audit.md) flags 4 winning attempts with recorded non-passenger flight flags; 0 winning attempts are not yet audited. Native death credit remains separate from normal-movement validity. The attempt CSV preserves each qualification and the audit sample count.

| Boss | Native wins | Unobserved candidates | State |
|---|---:|---:|---|
| bosses_of_mass_destruction:gauntlet | 2 | 0 | defeated_declared_candidates_seen_review_required |
| bosses_of_mass_destruction:lich | 1 | 0 | defeated_declared_candidates_seen_review_required |
| bosses_of_mass_destruction:obsidilith | 3 | 0 | defeated_declared_candidates_seen_review_required |
| bosses_of_mass_destruction:void_blossom | 1 | 0 | defeated_declared_candidates_seen_review_required |
| cataclysm:ancient_remnant | 2 | 0 | defeated_declared_candidates_seen_review_required |
| cataclysm:ender_guardian | 2 | 0 | defeated_declared_candidates_seen_review_required |
| cataclysm:ignis | 0 | 9 | native_defeat_pending |
| cataclysm:maledictus | 2 | 2 | defeated_natural_move_gaps_remain |
| cataclysm:netherite_monstrosity | 2 | 0 | defeated_declared_candidates_seen_review_required |
| cataclysm:scylla | 2 | 0 | defeated_declared_candidates_seen_review_required |
| cataclysm:the_harbinger | 2 | 0 | defeated_declared_candidates_seen_review_required |
| cataclysm:the_leviathan | 1 | 1 | defeated_natural_move_gaps_remain |
| minecraft:ender_dragon | 1 | 1 | defeated_natural_move_gaps_remain |
| minecraft:warden | 1 | 0 | defeated_declared_candidates_seen_review_required |
| minecraft:wither | 1 | 0 | defeated_declared_candidates_seen_review_required |
| twilightforest:alpha_yeti | 1 | 0 | defeated_declared_candidates_seen_review_required |
| twilightforest:hydra | 1 | 2 | defeated_natural_move_gaps_remain |
| twilightforest:knight_phantom | 1 | 1 | defeated_natural_move_gaps_remain |
| twilightforest:lich | 2 | 0 | defeated_declared_candidates_seen_review_required |
| twilightforest:minoshroom | 2 | 0 | defeated_declared_candidates_seen_review_required |
| twilightforest:naga | 1 | 0 | defeated_declared_candidates_seen_review_required |
| twilightforest:snow_queen | 1 | 0 | defeated_declared_candidates_seen_review_required |
| twilightforest:ur_ghast | 1 | 1 | defeated_natural_move_gaps_remain |

“Not inventoried” means the expanded natural-encounter catalogue is unavailable, not that there are zero missing moves. The CSV retains its explicit expanded_catalogue_observed flag; a numeric zero without that flag must not be interpreted as complete coverage. Earlier-schema attempts remain labeled separately even when a later winning attempt supplies the expanded catalogue.

The prior controlled matrix contains 8364 passing assertions and 13120 armor measurements against production SHA-256 53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77. It covers staged native callbacks/phases and actual armor. Its examples are not proof that those moves occurred in these encounters, nor measurements of every enchanted-golden-apple/status combination. The earlier client samples remain historical and are not imported as new victories.

Conditional branches, non-damaging windups, dormant source entries and lifecycle states need source-based adjudication. Do not remove an unobserved damaging move merely because another animation uses the same helper. Do not classify an incoming event without a post event as a miss: native cancellation, shield handling and other exits differ, and some early native rejects emit no event at all.

[Explicit source adjudications](source-adjudications.json) distinguish dormant fresh-spawn candidates from required moves and separate native components that share one damage profile. They also explain native-immunity gaps without removing those requirements: Hydra flame/mortar processing remains unobserved in the apple-equipped win because the installed damage types are fire-tagged. The CSV preserves raw requirement flags and marks source-added requirements, source reasoning and actual observations; raw telemetry is never rewritten. A dormant classification does not count as observing that move. Any contrary natural observation remains visible in the manifest for review. In particular, an Obsidilith death explosion cannot establish a falling-anvil impact merely because both use the same configured cap.

[Ordinary input retries](native-input-retries.csv) retain logged operator releases and automatic apple-use retries within the selected encounter intervals. They are input actions, not health/cooldown resets or proof of item consumption. Each attempt ID includes its queue directory so a retry cannot be confused with an earlier attempt of the same boss.

[Encounter observations and limits](observation-notes.md) explain native wins, input-limited attempts and unresolved coverage without treating earlier controlled probes as new victories.

[Unfinished Leviathan evidence](unfinished-leviathan/README.md) preserves the server-stall endpoint, accepted RUSH counter into native STUN and corrected received bar transition. The completed-case CSVs exclude that unfinished case. Its observed STUN branch therefore has separate natural evidence while a STUN observation within a completed win remains outstanding.

[Independent receipt adjudication](receipt-review-audit/README.md) explains the native helmet step omitted from the named reduction sum, retains the historical bar defect and separately counts valid received values for production 1.2.1. Its source hashes and sampled scope accompany every conclusion.

[Ignis logging interruption](harness-io-interruption.md) records the unfinished endpoint and Windows file-sharing exception. Its native observations retain their scope. The [reader access audit](reader-access-audit.json) documents shared access and closed snapshots used by the report tools.

[Supplemental Ignis Brand netherite probes](ignis-brand-netherite.md) add 36 controlled native cases and 504 passing assertions. They remain separate from natural move observations and native defeat counts.
