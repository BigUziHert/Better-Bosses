param(
    [string]$ResultsDirectory=(Join-Path $PSScriptRoot 'results'),
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$ProductionJar,
    [string]$Version,
    [string[]]$RequiredSuites=@('health','fixed_health','damage','actual_attacks','vanilla','boss_bars','exhaustive_twilight','exhaustive_bmd','exhaustive_cataclysm','armor_matrix','armor_effects','multiplayer_targets','config_migration','combined_projectiles','preinstall_verify_keep','installed_restart_verify'),
    [string]$FightSummary,
    [string]$HelperProvenance
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
if(-not $Version){$Version=([regex]::Match((Get-Content -LiteralPath (Join-Path $repo 'gradle.properties') -Raw),'(?m)^mod_version=(.+)$')).Groups[1].Value.Trim()}
if(-not $ProductionJar){$ProductionJar=Join-Path $repo "build/libs/survival-boss-balance-$Version.jar"}
$suites=@(Get-ChildItem -LiteralPath $ResultsDirectory -File -Filter '*.json' | Sort-Object Name | ForEach-Object{
    $data=Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    if($data -is [pscustomobject] -and $null -ne $data.phase -and $data.PSObject.Properties.Name -contains 'checks'){$data}
})
$missing=@($RequiredSuites | Where-Object {$_ -notin $suites.phase})
if($missing.Count){throw "Missing required suites: $($missing -join ', ')"}
$checks=@($suites | ForEach-Object {$_.checks})
if(@($checks | Where-Object passed -NE $true).Count){throw 'Failed assertions remain; do not publish a passing report'}
$armor=Get-Content -LiteralPath (Join-Path $ResultsDirectory 'armor-matrix.json') -Raw | ConvertFrom-Json
$effects=Get-Content -LiteralPath (Join-Path $ResultsDirectory 'armor-effects.json') -Raw | ConvertFrom-Json
if($armor.errors -or $effects.errors){throw 'Armor measurement errors remain'}
$comparison=@(Import-Csv -LiteralPath (Join-Path $OutputDirectory 'approved-damage-comparison.csv'))
if(@($comparison | Where-Object {$_.diamond_check -ne 'True' -or $_.netherite_check -ne 'True'}).Count){throw 'Approved damage comparisons have not all passed'}
$productionHash=(Get-FileHash -LiteralPath $ProductionJar -Algorithm SHA256).Hash
$helperHash=(Get-FileHash -LiteralPath (Join-Path $repo 'run-regression/mods/survival-boss-balance-tests-1.0.0.jar') -Algorithm SHA256).Hash
$lines=[Collections.Generic.List[string]]::new()
$lines.Add("# Better Bosses $Version regression results");$lines.Add('')
$lines.Add("**$($checks.Count) assertions passed, zero failures.** The isolated dedicated-server run includes $($armor.outfitCount) armor outfits, $($armor.measurementCount) attack/outfit/enchantment measurements and $($effects.measurementCount) additional set-effect/endurance measurements. The approved damage comparison covers $($comparison.Count) profiles.")
$lines.Add('');$lines.Add('Damage is calibrated to **Hard difficulty and full unenchanted diamond**. The marked Ignis ultimate has a 9.5-heart ceiling; its other heavies cap at 8. Maledictio Anima retains its prior raw cap, measuring about 6.795 hearts in diamond and 6 in Protection IV netherite. Yeti falling ice keeps its approved 5.25-heart diamond ceiling. Ur-Ghast fireballs and Harbinger missiles have an 8-heart combined budget per projectile and victim. Native weaker hits can remain below these ceilings.')
$lines.Add('');$lines.Add('The [production behavior-scope audit](behavior-scope.md) verifies that this release changes damage configuration and accounting without adding core AI, movement, phase, timing, projectile-trajectory or arena-rule changes. Existing health and bar support remains in place. Automated client strategies and isolated test-environment adjustments are test-only and excluded from the production mod.')
$lines.Add('');$lines.Add('S:\Survival was used as a read-only source of mods/configs. Testing and builds used isolated copies; this process did not install the new mod into that folder. The [source and artifact audit](source-and-artifact-audit.md) records the fresh comparison of 871 files, independent source-server restart activity, and the exact production JAR provenance. Gameplay configuration values and mod JARs remain unchanged; the source audit distinguishes one generated timestamp-comment rewrite and 111 modification-time-only changes from task actions.')
$lines.Add('');$lines.Add('## Automated checks');$lines.Add('')
$lines.Add('| Suite | Assertions | Failures |');$lines.Add('|---|---:|---:|')
foreach($suite in $suites){$lines.Add("| $($suite.phase) | $($suite.total) | $($suite.failed) |")}
$lines.Add('');$lines.Add('The required suites cover fixed health and multiplayer conditions, saved-world installation/restart, native server boss-bar state, approved damage fixtures, installed native attacks and phases, vanilla bosses, armor variants and effects, legacy configuration migration, and combined native projectile callbacks. Config migration checks use isolated temporary files and restore active runtime settings; no source configuration file is rewritten.')
$lines.Add('');$lines.Add('## Damage and armor');$lines.Add('')
$lines.Add('[Approved profile comparison](approved-damage-comparison.md) gives measured plain-diamond and Protection IV netherite outcomes for every approved profile. Its combined rows are real joint impact/splash measurements with native invulnerability frames. [All armor outfits](armor-results.md), [every measured attack](armor-all-attacks.csv), and [set effects/endurance](armor-effects-results.md) retain the complete individual results.')
$lines.Add('');$lines.Add('[Damage limits and defense conditions](damage-limits.md) distinguish the measured plain-diamond Brand sweep, fresh Protection IV netherite measurements and untested combinations. The single-hit reference ceilings are not a universal guarantee against lethal combinations, damage over time, weaker/broken equipment or arbitrary debuffs.')
$exhaustionChecks=@($checks | Where-Object {$_.test -match '/adversarial_.+_exhausted_budget$'})
if($exhaustionChecks.Count){$lines.Add('');$lines.Add("The $($exhaustionChecks.Count) separately labeled [budget-exhaustion stress cases](compound-budget-exhaustion.csv) retain health, equipment and per-projectile accounting across repeated native callbacks. They reach the eight-heart reference budget, verify final-hit truncation, and reject further damage. Half additionally simulate a compatibility mod removing impact/splash cooldown. In that condition, a stronger native Ur-Ghast power4 projectile accepts 4.5 + 3.5 = 8 diamond hearts, measuring 2.591331 hearts in Protection IV netherite. Repeated callbacks after projectile discard and the explicit cooldown-removal listener are adversarial conditions, not claims about ordinary gameplay.")}
$lines.Add('');$lines.Add('| Full set | Warden melee, plain | Warden melee, Protection IV | Sonic boom |');$lines.Add('|---|---:|---:|---:|')
foreach($id in 'minecraft:leather','minecraft:chainmail','minecraft:golden','minecraft:iron','minecraft:diamond','minecraft:netherite','alloyed:steel','deep_aether:stratus','deeperdarker:warden','cataclysm:ignitium','cataclysm:cursium','deeperdarker:resonarium'){
    $own=@($armor.measurements | Where-Object armor -EQ $id);if(-not $own.Count){continue}
    $values=foreach($prot in 0,4){$match=@($own | Where-Object {$_.attack -eq 'warden_melee' -and $_.protection -eq $prot});if($match.Count){([double]$match[0].heartsLost).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)}else{'unsupported'}}
    $sonic=@($own | Where-Object attack -EQ 'warden_sonic_boom')[0].heartsLost
    $lines.Add("| $id | $($values[0]) | $($values[1]) | $(([double]$sonic).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)) |")
}
$lines.Add('');$lines.Add('Values are hearts lost; one heart is 2 HP. A result at or above 10 hearts can exhaust a normal 20-HP health bar. Protection applies only where the native damage type permits it. The matrix retains actual registered equipment and supported enchantments. Extra measurement health avoids truncation on death; native percent-health attacks are either supplied captured 20-HP inputs or explicitly identified capped native probes. Full Resonarium can spend item durability instead of health, so repeated-hit results are reported separately. Aether matching-glove variants use the actual accessory slot.')
$lines.Add('');$lines.Add('## Evidence and limits');$lines.Add('')
$lines.Add('[Attack and phase evidence](boss-coverage-results.md), [native scenario records](all-native-scenarios.csv), [all assertions](all-assertions.csv), and the raw JSON retain individual outcomes. Controlled attack-frame probes reset between hits and do not measure whole-combo DPS. Joint-projectile cases preserve immediate impact/splash ordering. Native minions, burning, poison, Wither, falls and overlapping later attacks can add damage outside a single-hit ceiling. Server boss-bar checks establish state/progress/tracking; rendered client appearance requires the separate client evidence.')
$fightCount=0
if($FightSummary){
    $fights=@(Get-Content -LiteralPath $FightSummary -Raw | ConvertFrom-Json);$fightCount=$fights.Count
    $limited=@();$classificationPath=Join-Path $OutputDirectory 'client-exposure-classifications.json'
    if(Test-Path -LiteralPath $classificationPath){$limited=@((Get-Content -LiteralPath $classificationPath -Raw | ConvertFrom-Json).limitedCases)}
    function Format-FightResult($rows){
        if(-not $rows.Count){return 'not sampled'}
        return ($rows | ForEach-Object {$fight=$_;$flag=if(@($limited | Where-Object {$_.boss -eq $fight.boss -and $_.gear -eq $fight.gear}).Count){'; limited'}else{''};"$($fight.outcome) ($([Math]::Round($fight.tick/20.0,2)) s$flag)"}) -join '; '
    }
    $lines.Add('');$lines.Add('## Continuous encounter sample');$lines.Add('')
    $lines.Add("$fightCount bounded encounters are recorded separately from the passing assertions. Their outcomes are observations, not an assertion that every boss was defeated or every attack appeared. Automated client control is identified in each encounter record and is not presented as human play.")
    $lines.Add('');$lines.Add('| Outcome | Encounters |');$lines.Add('|---|---:|')
    foreach($group in $fights | Group-Object outcome | Sort-Object Name){$lines.Add("| $($group.Name) | $($group.Count) |")}
    $lines.Add('');$lines.Add('| Boss | Plain diamond loadout | Protection IV netherite loadout |');$lines.Add('|---|---|---|')
    foreach($group in $fights | Group-Object boss | Sort-Object Name){
        $diamond=@($group.Group | Where-Object gear -EQ 'diamond');$netherite=@($group.Group | Where-Object gear -EQ 'netherite_p4')
        $a=Format-FightResult $diamond;$b=Format-FightResult $netherite
        $lines.Add("| $($group.Name) | $a | $b |")
    }
    $lines.Add('');$lines.Add('[Every encounter](client-encounters/fight-summary.md), [outcomes and equipment use](client-encounters/encounters.csv), [server event timeline](client-encounters/server-timeline.csv), [client inputs](client-encounters/client-timeline.csv) and [received boss-bar observations](client-encounters/client-bars.csv) retain the individual evidence. The two encounter loadouts also use different weapons; the separate armor matrix isolates armor effects. The machine outcome lost_downed means a recorded lethal knockout and remains a defeat. A nonlethal ragdoll/down state that accepts ordinary crouch get-up input is a different condition. The [action audit](client-encounters/action-audit.json), [between-case cleanup log](client-encounters/between-case-cleanup.jsonl) and [ordinary native input log](client-encounters/native-recovery-inputs.jsonl) separate recorded lethal defeats from nonlethal get-ups. Logged cleanup commands follow a recorded knockout and precede the next encounter; they do not turn a loss into a win. Native phase or attack selection is not forced during these encounters; attack and phase evidence comes from the separately documented controlled fixtures, not from assuming every natural encounter reaches every phase.')
    $lines.Add('');$lines.Add('[Reviewed native observations](client-observation-notes.md) explain protected phases, native group bars, shield evidence and environmental damage. [Client environment](client-environment.json) discloses test-only adjustments, invalidated earlier trials and native corpse/ragdoll remains in the reused arena. [Export provenance](client-encounters/export-provenance.json) records the final input and exporter hashes. These bounded automated outcomes are not estimates of human win rates.')
    if($limited.Count){$lines.Add('');$lines.Add('Rows marked limited have the specific fixture/control restrictions retained in the [exposure classifications](client-exposure-classifications.json). They are not claimed as complete native fights or full attack/phase observations. The Dragon Hover/zero-crystal and zero-damage Warden samples are especially limited; controlled native numerical probes supply their attack/phase evidence.')}
}
$lines.Add('');$lines.Add("Production JAR SHA-256: ``$productionHash``.");$lines.Add("Latest installed test helper SHA-256: ``$helperHash``.")
$executedHelpers=@()
if($HelperProvenance){
    $provenance=Get-Content -LiteralPath $HelperProvenance -Raw | ConvertFrom-Json
    $executedHelpers=@($provenance.controlledHelpers)
    $lines.Add('');$lines.Add('The controlled suites were executed with the helper builds listed below against the same production JAR. Later helper builds extend the test fixtures and continuous encounter workflow; the latest installed helper is not claimed to have executed every earlier suite.')
    $lines.Add('');$lines.Add('| Executed helper SHA-256 | Controlled suites |');$lines.Add('|---|---|')
    foreach($helper in $executedHelpers){$lines.Add("| $($helper.sha256) | $($helper.suites -join ', ') |")}
}
$lines.Add('');$lines.Add('The test helper is test-only and must not be installed on the survival server. The release package contains the production mod, optional example config and reports; copied third-party mods remain in the isolated test environment.')
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $OutputDirectory "REGRESSION-$Version.md"),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
$manifest=[ordered]@{version=$Version;productionSha256=$productionHash;latestHelperSha256=$helperHash;executedControlledHelpers=$executedHelpers;assertions=$checks.Count;failed=0;approvedProfiles=$comparison.Count;armorOutfits=$armor.outfitCount;armorMeasurements=$armor.measurementCount;setEffectMeasurements=$effects.measurementCount;budgetExhaustionScenarios=$exhaustionChecks.Count;continuousEncounters=$fightCount;sourceReadOnly=$true;installedIntoSurvival=$false;damageReference='hard_plain_diamond'}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-run-manifest.json'),($manifest | ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
