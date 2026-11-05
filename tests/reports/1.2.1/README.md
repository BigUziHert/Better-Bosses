# Better Bosses 1.2.1 validation

The 1.2.1 build passed **8,364 assertions across 18 fresh controlled suites**. It clamps Leviathan boss-bar progress to the valid 0–1 range during the native phase transition. The [controlled results](REGRESSION-1.2.1.md) contain the full outcome table and detailed evidence links.

Preparation passed 46 checks before installation, installation passed 230 checks and the second restart passed 322 checks. All 396 boss-bar assertions passed, including the 29 added boundary and transition assertions. Fresh measurements cover 90 armor outfits, 13,120 attack/outfit/enchantment combinations, 258 set-effect cases and all 73 approved damage profiles. The native Ignis Brand sweep also passed all 504 assertions.

The [natural encounter ledger](../victory-validation/coverage-ledger.md) records each completed fight with its executed artifact and qualifications. The [transition investigation](../victory-validation/leviathan-bar-transition-review.md) preserves the eight negative values that prompted this release, the passing controlled correction and the corrected client values during a naturally reached transition. That natural transition occurred before a later server stall, documented as an unfinished encounter.

The report was generated from the [verified frozen result snapshot](result-snapshot-manifest.json) using `tests/export-release-1.2.1.ps1`. Its input checks required fresh suite timestamps, complete assertions, all 73 approved comparisons and the new Leviathan boundary and phase-transition cases. The [run manifest](test-run-manifest.json) records each input hash and its executed helper identity. The [artifact continuity review](artifact-continuity.md) relates the earlier measurements to this build.

A later [Dragon death-transition supplement](supplements/dragon-death-transition/README.md) passed 19 controlled native-method assertions with the separately recorded B340 helper. The baseline plus supplement contains 8,383 passing assertions across the two executions. Natural Dragon phase coverage and victory are tracked in the encounter ledger.
