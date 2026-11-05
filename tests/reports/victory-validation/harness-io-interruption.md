# Ignis test-logging interruption

The Ignis attempt in queue `2026-11-05T13-55-05.227990100Z` ended at the logging failure on 14:09:32 UTC. Its last ordinary record is a sample at tick 16640. A completed encounter record is absent, so final apple accounting and a native victory are unavailable. The [preserved incident record](harness-io-interruption.json) identifies the archived crash report and server log by SHA256.

The crash report records a Windows sharing exception while `FullFightCases.emit` appended the primary encounter log. That exception propagated into the server's entity tick. A concurrent live audit used the default `File.ReadLines` share mode on that primary log. Its access mode can deny a writer and is the likely trigger. The exception itself does not identify which reader held the file handle.

Native shield durability 1 and phase 0 were observed before the interruption. Recorded native hits retain their original evidence scope. The 534 pilot and B340 helper remain assigned to this unfinished attempt.

The report readers now open live files with `FileShare.ReadWrite | FileShare.Delete`. Client-log audits first copy a bounded complete-line prefix through that shared reader and then read and hash the closed snapshot. The coverage exporter hashes completed server evidence only. A task-owned fixture verified that a default append succeeds while the shared reader remains open.

The separately prepared 6D53 test helper adds queued log writes with explicit committed-row handling. Its future encounters carry their own executed provenance. Production remains identified by the 1.2.1 artifact hash in each encounter record.
