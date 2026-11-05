# Native damage receipt audit

65 completed attempts contain 20248 native damage Post events, including 11015 against the player. Damage arithmetic review items: 24, at a 0.001 HP tolerance. Received boss bars: 29784 values in 27569 client samples; 8 out-of-range values require native renderer review.

The audit independently checks original-damage identity, processed incoming damage minus shield/reductions versus final damage, and observed health/absorption changes. Health and absorption clamp at zero. Native cancellation, lethal interception, nested damage or later hooks can create a residual; those rows remain review items rather than being silently passed. Incoming attempts without a Post are not classified as misses.

[Every receipt](damage-receipts.csv), [player profile summaries](player-profile-summary.csv), [attempt armor/apple/revival context](attempt-context.csv), and [review items](review-items.json) preserve the measured values. Gear and effects are the most recent observed snapshots; exact armor/toughness at the damage observer are also retained. Enchanted apples add absorption and status effects, so these are not bare netherite P4 measurements.

Approved controlled reference values are linked for context, not used as unconditional encounter caps. In particular, Warden sonic boom is 5.5 hearts in the approved reference and 5.5 hearts in the prior netherite P4 measurement, without enchanted-apple absorption. Brand, source bypass flags, cooldowns, shields and other native conditions require their recorded context. The audit does not substitute a generic 9.5-heart figure.

Finite bar values do not prove each native phase formula, tracking visibility, or that a bar should exist. Those have separate staged and encounter evidence. This audit changes no gameplay, files in S:\Survival, or test controls.
