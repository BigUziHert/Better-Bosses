#Requires -Version 7.5
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ResultsDirectory,
    [Parameter(Mandatory=$true)][DateTimeOffset]$RunStartedUtc,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ExecutedHelperSha256,
    [string]$OutputDirectory=(Join-Path $PSScriptRoot 'reports/1.2.1'),
    [string]$ProductionJar=(Join-Path (Split-Path $PSScriptRoot -Parent) 'build/libs/survival-boss-balance-1.2.1.jar'),
    [string]$EntryDiff=(Join-Path $PSScriptRoot 'reports/victory-validation/production-1.2.1-entry-diff.json'),
    [string]$SnapshotManifest=(Join-Path $PSScriptRoot 'evidence/1.2.1-controlled-snapshot-manifest.json'),
    [string]$AttackInventory=(Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'boss-balance-tests/cataclysm-attack-inventory.json'),
    [switch]$ValidateOnly
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$expectedProduction='53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77'
$outputFull=[IO.Path]::GetFullPath($OutputDirectory)
$historical=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'reports/1.2.0'))
if($outputFull.Equals($historical,[StringComparison]::OrdinalIgnoreCase) -or $outputFull.StartsWith($historical+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Historical 1.2.0 output directory is protected.'}
$productionHash=(Get-FileHash -LiteralPath $ProductionJar -Algorithm SHA256).Hash
if($productionHash -ne $expectedProduction){throw 'Production artifact differs from the reviewed 1.2.1 build.'}
$diff=Get-Content -LiteralPath $EntryDiff -Raw|ConvertFrom-Json -DateKind String
if($diff.afterSha256 -ne $productionHash -or $diff.beforeSha256 -ne '871F3C0ECF84236ADFDC5457C09C9D2C8EE34BC9582CE148C899109FDA822F70'){throw 'Binary comparison is not bound to the reviewed artifacts.'}
$expectedChanges=@('survival/balance/SurvivalBossBalance.class','survival/balance/mixin/LeviathanBossBarMixin.class','survival_boss_balance.mixins.json','META-INF/neoforge.mods.toml')
$actualChanges=@($diff.entries|Where-Object status -NE 'identical'|ForEach-Object entry)
if(@(Compare-Object $expectedChanges $actualChanges).Count -or @($diff.removedEntries).Count){throw 'Unexpected production entry differences.'}
$snapshot=Get-Content -LiteralPath $SnapshotManifest -Raw|ConvertFrom-Json -DateKind String
if($snapshot.productionSha256-ne$productionHash -or $snapshot.helperSha256-ne$ExecutedHelperSha256){throw 'Snapshot artifact identities differ from the executed run.'}
foreach($entry in $snapshot.files){
    $file=Join-Path $ResultsDirectory $entry.path
    if((Get-Item -LiteralPath $file).Length-ne$entry.bytes -or (Get-FileHash -LiteralPath $file).Hash-ne$entry.sha256){throw "Frozen snapshot mismatch $($entry.path)"}
}
$required=@('preinstall_prepare','preinstall_verify_keep','installed_restart_verify','health','fixed_health','damage','actual_attacks','vanilla','boss_bars','exhaustive_twilight','exhaustive_bmd','exhaustive_cataclysm','armor_matrix','armor_effects','multiplayer_targets','config_migration','combined_projectiles','ignis_brand_netherite')
$requiredAux=@('armor-matrix.json','armor-effects.json','combined-projectiles.json','exhaustive_twilight-attacks.json','exhaustive_bmd-attacks.json','exhaustive_cataclysm-attacks.json','ignis-brand-netherite.json','preinstall-manifest.json','preinstall-load-evidence.json','installed-restart-manifest.json','installed-restart-load-evidence.json','multiplayer-target-selection.json')
$files=@(Get-ChildItem -LiteralPath $ResultsDirectory -File -Filter '*.json'|Sort-Object Name)
$missingAux=@($requiredAux|Where-Object{$_ -notin $files.Name})
if($missingAux.Count){throw "Missing result files $($missingAux -join ', ')"}
$suites=[Collections.Generic.List[object]]::new()
$inputs=[Collections.Generic.List[object]]::new()
foreach($file in $files){
    if($file.LastWriteTimeUtc -lt $RunStartedUtc.UtcDateTime){throw "Stale input $($file.Name) predates the new run."}
    $data=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json -DateKind String
    $inputs.Add([pscustomobject]@{file=$file.Name;bytes=$file.Length;lastWriteUtc=$file.LastWriteTimeUtc.ToString('o');sha256=(Get-FileHash -LiteralPath $file.FullName).Hash})
    if($data -is [pscustomobject] -and $null-ne$data.phase -and $data.PSObject.Properties.Name-contains'checks'){
        if($data.total -ne @($data.checks).Count -or $data.failed -ne 0 -or @($data.checks|Where-Object passed -NE $true).Count){throw "Incomplete or failed assertions in $($file.Name)"}
        $suites.Add($data)
    }
}
$missing=@($required|Where-Object{$_ -notin $suites.phase})
if($missing.Count){throw "Missing required suites $($missing -join ', ')"}
if(@($suites|Group-Object phase|Where-Object Count -NE 1).Count){throw 'Duplicate suite phases would double-count assertions.'}
$bars=@($suites|Where-Object phase -EQ 'boss_bars')[0]
$transition=@($bars.checks|Where-Object{$_.entity-eq'cataclysm:the_leviathan' -and $_.test-match'^bossbar_(phase_one_|pending_phase_|native_phase_)'})
foreach($name in 'bossbar_phase_one_segmented_bar_0.501','bossbar_phase_one_segmented_bar_0.5','bossbar_phase_one_segmented_bar_0.499','bossbar_phase_one_segmented_bar_0.1','bossbar_pending_phase_before90_bar','bossbar_native_phase_frame90_flag','bossbar_native_phase_after90_bar','bossbar_native_phase_after90_style'){
    if(@($transition|Where-Object test -EQ $name).Count-ne1){throw "Missing executed transition assertion $name"}
}
$armor=Get-Content -LiteralPath (Join-Path $ResultsDirectory 'armor-matrix.json') -Raw|ConvertFrom-Json -DateKind String
$effects=Get-Content -LiteralPath (Join-Path $ResultsDirectory 'armor-effects.json') -Raw|ConvertFrom-Json -DateKind String
if($armor.errors-ne0 -or $effects.errors-ne0){throw 'Armor measurements contain errors.'}
if($ValidateOnly){[pscustomobject]@{validatedSuites=$suites.Count;assertions=($suites|Measure-Object total -Sum).Sum;transitionAssertions=$transition.Count;inputFiles=$files.Count;productionSha256=$productionHash};return}
New-Item -ItemType Directory -Path $OutputDirectory -Force|Out-Null
& (Join-Path $PSScriptRoot 'export-coverage-results.ps1') -ResultsDirectory $ResultsDirectory -OutputDirectory $OutputDirectory
& (Join-Path $PSScriptRoot 'export-armor-results.ps1') -InputJson (Join-Path $ResultsDirectory 'armor-matrix.json') -OutputDirectory $OutputDirectory
& (Join-Path $PSScriptRoot 'export-armor-effects.ps1') -InputJson (Join-Path $ResultsDirectory 'armor-effects.json') -OutputDirectory $OutputDirectory
& (Join-Path $PSScriptRoot 'export-approved-comparison.ps1') -ArmorJson (Join-Path $ResultsDirectory 'armor-matrix.json') -CombinedJson (Join-Path $ResultsDirectory 'combined-projectiles.json') -OutputDirectory $OutputDirectory
& (Join-Path $PSScriptRoot 'audit-cataclysm-coverage.ps1') -CapturePath (Join-Path $ResultsDirectory 'exhaustive_cataclysm-attacks.json') -InventoryPath $AttackInventory -OutputDirectory $OutputDirectory
& (Join-Path $PSScriptRoot 'export-ignis-brand-netherite.ps1') -ResultsDirectory $ResultsDirectory -OutputDirectory $OutputDirectory -ProductionSha256 $productionHash -HelperSha256 $ExecutedHelperSha256
& (Join-Path $PSScriptRoot 'compare-armor-repeats.ps1') -PreviousCsv (Join-Path $PSScriptRoot 'reports/1.2.0/armor-all-attacks.csv') -CurrentCsv (Join-Path $OutputDirectory 'armor-all-attacks.csv') -OutputDirectory $OutputDirectory
$comparison=@(Import-Csv -LiteralPath (Join-Path $OutputDirectory 'approved-damage-comparison.csv'))
if($comparison.Count-ne73 -or @($comparison|Where-Object{$_.diamond_check-ne'True' -or $_.netherite_check-ne'True'}).Count){throw 'Approved profile comparison is incomplete or failed.'}
$transition|Export-Csv -LiteralPath (Join-Path $OutputDirectory 'leviathan-transition-bars.csv') -NoTypeInformation -Encoding utf8
Copy-Item -LiteralPath $EntryDiff -Destination (Join-Path $OutputDirectory 'production-entry-diff.json') -Force
Copy-Item -LiteralPath $SnapshotManifest -Destination (Join-Path $OutputDirectory 'result-snapshot-manifest.json') -Force
$counts=@($suites|Sort-Object phase|ForEach-Object{[pscustomobject]@{phase=$_.phase;assertions=$_.total;failed=$_.failed;productionPresent=($_.phase-ne'preinstall_prepare');productionSha256=if($_.phase-ne'preinstall_prepare'){$productionHash}else{$null};executedHelperSha256=$ExecutedHelperSha256.ToUpperInvariant()}})
$manifest=[ordered]@{version='1.2.1';generatedUtc=[DateTime]::UtcNow.ToString('o');runStartedUtc=$RunStartedUtc.ToUniversalTime().ToString('o');status='controlled_checks_passed';productionSha256=$productionHash;executedHelperSha256=$ExecutedHelperSha256.ToUpperInvariant();suiteProvenanceBasis='Explicit executed helper identity supplied with the completed snapshot. Each source file is hashed and its timestamp checked against the new run start.';suites=$counts;assertions=($suites|Measure-Object total -Sum).Sum;failed=0;leviathanTransitionAssertions=$transition.Count;approvedProfiles=$comparison.Count;armorOutfits=$armor.outfitCount;armorMeasurements=$armor.measurementCount;setEffectMeasurements=$effects.measurementCount;inputs=$inputs.ToArray();naturalVictoryLedger='../victory-validation/coverage-ledger.md';allMovesAndPhasesCertified=$false}
$manifest|ConvertTo-Json -Depth 10|Set-Content -LiteralPath (Join-Path $OutputDirectory 'test-run-manifest.json') -Encoding utf8
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Better Bosses 1.2.1 controlled validation');$lines.Add('')
$lines.Add("**$($manifest.assertions) assertions passed with zero failures.** Fresh measurements cover $($armor.outfitCount) armor outfits, $($armor.measurementCount) attack/outfit/enchantment combinations and $($effects.measurementCount) set-effect and endurance cases. All 73 approved damage profiles passed their plain-diamond and Protection IV netherite comparisons.")
$lines.Add('');$lines.Add('Version 1.2.1 normalizes the Leviathan health-bar progress while its native phase change is pending. The first segment stays empty after health crosses half until the boss completes its mode switch. The [binary comparison](production-entry-diff.json) binds the release to the reviewed build, and the [artifact continuity record](artifact-continuity.md) explains how earlier encounter evidence applies.')
$lines.Add('');$lines.Add("The expanded Leviathan regression contains $($transition.Count) [executed boundary and transition assertions](leviathan-transition-bars.csv). It checks values immediately around half health, low health during the pending phase, the native frame-90 mode switch, render style and preserved health.")
$lines.Add('');$lines.Add('| Suite | Assertions | Failures |');$lines.Add('|---|---:|---:|')
foreach($suite in $counts){$lines.Add("| $($suite.phase) | $($suite.assertions) | $($suite.failed) |")}
$lines.Add('');$lines.Add('The preinstall preparation boot saved 46 existing bosses before the balance mod was added. The installation and second-restart suites then checked their health profiles and persisted state. The preparation row records an absent production mod. Every later row records the 1.2.1 artifact.')
$lines.Add('');$lines.Add('[Every armor outfit](armor-results.md), [all attack measurements](armor-all-attacks.csv), [set effects and endurance](armor-effects-results.md) and the [approved damage comparison](approved-damage-comparison.md) retain individual outcomes. The reference loadout is full plain diamond on Hard. Native armor bypass, Protection eligibility, item durability and accessory effects remain visible in the results.')
$lines.Add('');$lines.Add('The [Ignis Brand sweep](ignis-brand-netherite.md) measures 36 native opening, shield-smash melee and ultimate cases across Brand 0–V in full Protection IV netherite. Fresh armor and native enchanted-apple effects have separate measured rows.')
$lines.Add('');$lines.Add('The [repeat-matrix comparison](armor-repeat-variability.md) identifies native random-dodge variation in Emu and Cursium equipment. Individual zero-damage samples record the observed dodge outcome. Their installed native probabilities and the matching damage measurements give the relevant context.')
$lines.Add('');$lines.Add('[All assertions](all-assertions.csv), [native scenarios](all-native-scenarios.csv) and [boss coverage](boss-coverage-results.md) preserve the controlled evidence. These probes execute installed attack methods and stage specific phase conditions. The [natural victory ledger](../victory-validation/coverage-ledger.md) separately records continuous encounters, native defeats, observed moves and remaining qualifications.')
$lines.Add('');$lines.Add('The test environment uses copied mods and configuration. The [source audit](../1.2.0/source-and-artifact-audit.md) records the read-only Survival source checks. Each run retains its own artifact identity and equipment conditions.')
$lines.Add('');$lines.Add("Production SHA256 ``$productionHash``");$lines.Add('');$lines.Add("Executed helper SHA256 ``$($ExecutedHelperSha256.ToUpperInvariant())``")
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'REGRESSION-1.2.1.md'),($lines-join"`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{suites=$suites.Count;assertions=$manifest.assertions;failed=0;output=$OutputDirectory;productionSha256=$productionHash}
