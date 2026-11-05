# Source folder and release artifact audit

The refreshed 1.2.0 source comparison checked **871 files: one generated timestamp-comment change and 111 modification-time-only changes**. No files were added or removed; all reads were stable and successful. Gameplay configuration values and mod JAR contents remained unchanged. The comparison finished at 2026-11-05 03:21:20 UTC. The earlier comparison at 01:45:03 UTC found zero differences.

The read-only scope was `S:\Survival` under `mods`, `config`, `defaultconfigs`, `world/serverconfig` and `kubejs`. Each file was compared against the 1.2.0 baseline by SHA-256, length and UTC modification time. This verifies those mod/config/script files; it is not a claim that a running server's world saves and logs remain static. The task used enumeration, metadata and hashing only in the source folder. Builds, test worlds and configuration adjustments used isolated copies.

The source server's own `logs/latest.log` records a restart at 03:01:06 UTC and readiness at 03:02:27 UTC, still loading Survival Boss Balance **1.0.2**. The changed file, `config/zfastnoise.mixin.properties`, contains the same settings; only its generated comment changed from `#Wed Nov 04 17:41:27 CST 2026` to `#Wed Nov 04 21:01:19 CST 2026`. Replacing that comment in memory reproduces its baseline SHA-256 exactly. The 111 other files retain identical hashes and lengths, with timestamps between 03:01:08 and 03:04:15 UTC. This pattern is consistent with native configuration rewrites during the independently started source server's startup; file comparisons alone do not identify the writing process. This task did not launch, stop, or write to that server and did not revert those external changes.

[Sanitized source audit](survival-read-only-audit.json) retains the scope, timestamps, baseline digest, exact difference paths, hashes and supporting restart observations without workstation paths or configuration values. It also retains the earlier zero-difference comparison as a separate observation. The earlier release's separate audit remains historical evidence and is not substituted for this 1.2.0 comparison.

The tested production artifact is `survival-boss-balance-1.2.0.jar`, 45,150 bytes, SHA-256:

`871F3C0ECF84236ADFDC5457C09C9D2C8EE34BC9582CE148C899109FDA822F70`

[Artifact provenance](production-artifact-provenance.json) records its version metadata, 14 compiled production classes, packaged resources and production-source digests. The JAR contains the production mod only. Test harnesses, the automated client controller and its development-only FlightLib compatibility adjustment are excluded. The artifact and production-source digests were rechecked when preparing this report.

[Executed test helper provenance](test-helper-provenance.json) separately identifies the helper version used for each controlled suite. [Client environment](client-environment.json) discloses isolated client dependencies and adjustments; it is not a description of changes installed on the survival server.
