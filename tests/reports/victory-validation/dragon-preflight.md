# Dragon retry preflight

This is a read-only review of the loaded F355 test controller and prepared-ring source, not a completed Dragon retry. Production behavior is unchanged. The two earlier Dragon attempts remain cancelled, and the new ring has not yet been tested in combat.

## Three crystals left in the earlier attempt

The isolated End's saved entity regions were last written at 04:58:47 UTC, immediately after cancellation of queue `2026-11-05T04-55-04.076189900Z`. They retain the same Dragon UUID as that encounter and three crystals. The contemporaneous native command counted three; earlier telemetry recorded seven distinct uncancelled crystal collisions from ten initial crystals.

| Crystal position | Saved surrounding blocks |
|---|---|
| `(-53.5, 92, -0.5)` | Iron-bar cage: 76 iron-bar blocks in the inspected nearby volume; bedrock base. |
| `(43.5, 87, 31.5)` | No iron bars in the inspected nearby volume; bedrock base. |
| `(16.5, 92, 51.5)` | No iron bars in the inspected nearby volume; bedrock base. |

These are historical saved-world observations, not a live query or a promise that a future native reset has identical geometry. The inspection box extends four blocks horizontally and from two below to five above the crystal's floored position. The read-only extraction and block evidence are retained in `tests/evidence/victory-dragon-saved-crystals-readonly.json`; the inspected code only reads the isolated world's region files. It never accesses `S:\Survival`.

## What the current controller does

- In landing approach/landing phases 2 and 3, it selects the sword and walks toward one fixed arrival anchor. While sitting, it targets the real `dragon.head`, aims at its nearest box surface and requires native crosshair selection plus normal player reach before an attack input.
- During flight, it chooses the nearest visible crystal within 80 blocks. If none is visible, it aims at the Dragon's head. It has no crystal-cage mining or tower-climbing branch.
- The new fixture supplies an End-stone ring from radius 6 to 24 at landing height minus two, plus radial stairs. It preserves the native central column and publishes ring waypoints/stair geometry. The current controller does not consume that geometry; it uses the single arrival anchor and local collision/stuck strafing.
- Ring recovery compares horizontal distance only. If knocked below the ring, the controller can approach the correct X/Z and stop underneath the anchor without returning through an outer staircase. The broader ring reduces unsupported walking but does not itself establish successful recovery.
- An explicit nonzero `preferredRange` takes precedence over the Dragon anchor branch in ranged movement. A future retry should start with the intended ordinary controls (`auto`, attacks enabled, preferred range zero) rather than inherit the Monstrosity range-16/no-attack bait policy.

The old attempt recorded 42 crystal-directed bow releases and 18 Dragon-directed releases, but no melee-input event. Its last client samples were on the native lower floor at Y=58, below the Y=72 entry. Thus the existing evidence establishes projectile/crystal activity and an access problem, not successful perched-head melee.

## Legitimate input fallback if the retry stalls

Use the prepared ring to approach the perched head from its current side, walking around the preserved central structure instead of trying to pass through it. After a fall, walk to a radial staircase and climb it using normal movement/jump inputs before resuming head attacks. During flight, change firing angle along the supported ring for crystals hidden from the fixed anchor. For the caged crystal, use an actual visible opening if one exists; otherwise ordinary climbing/building and pickaxe removal of cage bars are the relevant survival actions. Do not clear crystals, assign phases, teleport during combat, or alter boss health to bypass those mechanics.

These are concrete access risks and ordinary-input options. Whether the new ring and existing local obstacle handling suffice must be established in the actual retry. No controller or fixture change was made for this preflight review.

## Separately prepared, unexecuted recovery source

After the findings above, the client owner prepared a future `DragonAccessNavigator` that reads actual stair endpoints, retains a recovery route, checks support/body collision, and walks around the native central obstruction at ring height. Its integration cancels a drawn bow before below-ring recovery. Read-only review found no blocking defect in that narrow implementation, but it has not yet established runtime recovery or a Dragon victory. It adds no cage-mining or crystal-vantage tour.

Reviewed future source hashes: pilot `1FBDD16A770DB5074FBF36F62785CD91CD8817F05D8E7F3F09E0B0D6FF1F3484`; navigator `0EB771E4B30D3A131B5FB197B0C01660ABB4EAE4A9AB769D7BEAC40D26223363`. The ongoing Cataclysm encounters still use loaded F355; compiled future source is not presented as executed evidence.
