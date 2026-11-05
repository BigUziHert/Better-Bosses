# Flight-state audit of earlier native victories

The initial retrospective review after the cancelled Naga trial matched 4,862 client samples to all 13 earlier winning attempts representing 12 distinct bosses. Every matched sample contained flight and passenger fields; there were no JSON parse errors. Nine winning attempts had no flying=true samples. Four attempts had short startup intervals with flying=true, mayFly=false, no passenger and no native knockout:

| Attempt | Flagged samples and UTC interval | First subsequent flying=false sample |
|---|---|---|
| Gauntlet, 07:32 queue | 5 samples, 07:32:46.007–07:32:49.996 | 07:32:51.078 |
| Earlier negative-Y Obsidilith, 07:41 queue | 9 samples, 07:42:08.012–07:42:16.017 | 07:42:17.018 |
| Primary positive-Y Obsidilith, 08:09 queue | 10 samples, 08:10:32.225–08:10:41.215 | 08:10:42.224 |
| Monstrosity, Cat queue case 01 | 5 samples, 08:22:03.079–08:22:07.091 | 08:22:08.086 |

All later samples in those four attempts had flying=false. There is no sampled sustained Naga-like hover during these victories, but the startup anomalies remain a movement qualification. Obsidilith had already received some damage during its flagged windows, so those intervals are not described as harmless or outside combat. Gauntlet and Monstrosity still had full health during their flagged windows. The native death observations remain valid facts; this audit does not convert them into unqualified normal-movement trials.

Minoshroom, Twilight Lich, Night Lich, Void Blossom, Guardian, Harbinger, Remnant, Leviathan and Scylla had no flying=true samples in their matched winning intervals. Sampling was approximately once per second, with the largest observed gap 5.015 seconds. The audit cannot exclude a brief unsampled transition and does not claim continuous per-tick inspection.

The later cancelled Naga trial is separate: its client remained flying=true over air after the entry pillar disappeared, with no connected combat. It receives no victory credit. Future precombat abilities synchronization is test-fixture work; no mid-fight physics override or production AI change is part of this audit.

The fresh Naga victory in the 09:48 queue used the new precombat abilities synchronization. All 553 matched samples had flying=false, with no missing physics fields. The JSON/CSV audit now includes that later win as well as the original retrospective sample set.

After the Yeti and Hydra victories, the refreshed audit covers 19 winning attempts representing 16 bosses, with 6,429 matched samples and no missing physics fields or parse errors. The four earlier startup-qualified attempts remain the only flagged wins. The fresh Yeti and Hydra wins contribute 231 and 255 samples respectively, all with flying=false.

The subsequent Ur-Ghast and Snow Queen wins add 473 and 415 non-flying samples. The current export therefore covers 21 winning attempts representing 18 bosses and 7,317 matched samples, still with no missing physics fields, parse errors or additional flagged attempts.

The 1.2.1 Wither and Warden wins add 251 and 238 non-flying samples. The current audit contains 23 winning attempts representing 20 bosses and 7,806 matched samples. The four earlier startup-qualified attempts remain the only flagged wins, with zero missing physics fields and zero parse errors.

The production 1.2.1 Dragon victory adds 516 non-flying samples. The refreshed audit now covers 24 winning attempts representing 21 bosses and 8,322 matched samples. It found zero missing physics fields, zero parse errors and no additional flagged attempts.

The four completed Cataclysm follow-ups add 1,169 non-flying samples. The audit now covers 28 winning attempts representing 21 bosses and 9,491 samples, with zero missing physics fields and zero parse errors. Fresh Monstrosity, Guardian, Harbinger and Remnant wins each have a clear sampled movement record. The earlier four flagged attempts remain preserved. The unfinished Leviathan case is outside this victory-only movement audit.

The first Maledictus victory adds 151 non-flying samples, bringing the audit to 29 winning attempts, 22 distinct bosses and 9,642 samples. All physics fields were present and the scan found zero parse errors.

The 20:03 Maledictus and 20:09 Scylla follow-up victories add 289 and 191 non-flying samples. The current audit covers 31 winning attempts representing 22 bosses and 10,122 samples. The four historical flagged attempts remain preserved. The new wins have zero missing physics fields and the snapshot scan has zero parse errors.

[Per-attempt results](client-flight-audit.csv) and [exact flagged samples, time ranges and log hash](client-flight-audit.json) preserve the evidence. First run `tests/snapshot-client-evidence.ps1`, which copies a complete log prefix with explicit shared access. Then run `node tests/audit-client-flight.mjs` against the closed snapshot. The audit matches both the UTC encounter interval and boss identity, and independently requires an uncancelled native death witness for each winning attempt.
