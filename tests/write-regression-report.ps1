param([string]$ResultsDirectory=(Join-Path $PSScriptRoot 'results'),[string]$OutputDirectory=(Join-Path $PSScriptRoot 'reports'))
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$suites=@(Get-ChildItem -LiteralPath $ResultsDirectory -File -Filter '*.json' | Sort-Object Name | ForEach-Object {
    $data=Get-Content -LiteralPath $_.FullName -Raw|ConvertFrom-Json
    if($data -is [pscustomobject] -and $data.PSObject.Properties.Name -contains 'checks'){$data}
})
$checks=@($suites|ForEach-Object{$_.checks})
if(@($checks|Where-Object passed -NE $true).Count){throw 'Failed assertions remain; do not produce a passing report'}
$armor=Get-Content -LiteralPath (Join-Path $ResultsDirectory 'armor-matrix.json') -Raw|ConvertFrom-Json
$effects=Get-Content -LiteralPath (Join-Path $ResultsDirectory 'armor-effects.json') -Raw|ConvertFrom-Json
if($armor.errors -or $effects.errors){throw 'Armor measurement errors remain'}
$config=Get-Content -LiteralPath (Join-Path $repo 'config\survival-boss-balance.json') -Raw|ConvertFrom-Json
$jarHash=(Get-FileHash -LiteralPath (Join-Path $repo 'build\libs\survival-boss-balance-1.1.0.jar')).Hash
$helperHash=(Get-FileHash -LiteralPath (Join-Path $repo 'run-regression\mods\survival-boss-balance-tests-1.0.0.jar')).Hash
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Better Bosses 1.1.0 regression results — 4 November 2026')
$lines.Add('')
$lines.Add("**$($checks.Count) assertions passed, zero failures.** The run includes all 23 managed bosses, $($armor.outfitCount) armor outfits, $($armor.measurementCount) per-attack armor measurements, and $($effects.measurementCount) additional set-effect/endurance measurements. These are controlled dedicated-server tests using the installed mod code, not complete fights played by people.")
$lines.Add('')
$lines.Add('The new build includes fixed **Wither 900 HP**, **Ender Dragon 1,100 HP**, and **Warden 1,300 HP**, with the attack values from the earlier discussion. It also fixes Wither spawn healing at increased HP and the native solo/late-arriving Knight Phantom health-bar counter. The existing 20 boss profiles and attack tuning remain in place.')
$lines.Add('')
$lines.Add('**This work made no changes to S:\Survival and the new build was not installed there.** Mods/configs were copied into an isolated test directory. Original mod JARs and boss/combat configs matched the read-only baseline; an independently running production server regenerated one unrelated zfastnoise config file during testing. [Source audit](survival-read-only-audit.txt).')
$lines.Add('')
$lines.Add('## What passed')
$lines.Add('')
$lines.Add('- Fixed HP at 0, 1, 2, 3, 4, 5 and 10 nearby simulated players; target changes, distance changes, spectators, joining/leaving, wounded reloads and old party-multiplier migration. There is no online/nearby player multiplier in the balance code. Twilight native multiplayer adjustment is NONE. Cataclysm has a separate displayed lives counter; its installed Retry implementation does not change HP or attacks.')
$lines.Add('- A real world saved without the balance mod was stopped, then started with this build: all 46 existing full/wounded bosses received their targets. Before/after entity-load snapshots prove native max HP and no modifier before installation, followed by the new maximum and preserved health ratio. Native idle regeneration after loading is measured separately, not mistaken for migration healing.')
$lines.Add('- Native server boss bars: full/half/low HP, visibility, viewer tracking/rejoining, save/load, Lich shields and clones, six-Knight shared totals after deaths, solo/late Knights, Leviathan segmented phase bar, Cataclysm rage/dormancy, Wither spawn charge, and YUNG Dragon pre-ritual/spawned behavior. Warden retains its native absence of a bar.')
$lines.Add('- Controlled native attacks and phase gates for all 23 bosses, including emitted projectiles/effects, Cataclysm rage/Brand levels, vanilla Wither charge/arrow immunity/Wither II, Dragon breath cycles/perch threshold/crystal healing, and Warden emerging/sniffing/roaring/digging behavior. All 33 active Cataclysm outgoing damage helpers have accepted native captures; 176 preliminary pose misses are reconciled with child effects, alternate hitboxes, dedicated probes or non-damaging stages.')
$lines.Add('')
$lines.Add('| Suite | Assertions | Failed |')
$lines.Add('|---|---:|---:|')
foreach($suite in $suites){$lines.Add("| $($suite.phase) | $($suite.total) | $($suite.failed) |")}
$lines.Add('')
$lines.Add('## Armor results')
$lines.Add('')
$lines.Add('[Every armor outfit](armor-results.md) lists plain/Protection IV results. [Every measured attack × outfit](armor-all-attacks.csv) contains the full matrix. [Set effects and durability](armor-effects-results.md) includes actual player ticking, Aether gloves, worn Zanite and repeated Resonarium hits. Unsupported Protection enchantments are omitted; partial outfits leave missing slots empty.')
$lines.Add('')
$lines.Add('| Full set | Warden melee, plain | Warden melee, Protection IV | Sonic boom |')
$lines.Add('|---|---:|---:|---:|')
foreach($id in 'minecraft:leather','minecraft:chainmail','minecraft:golden','minecraft:iron','minecraft:diamond','minecraft:netherite','alloyed:steel','deep_aether:stratus','deeperdarker:warden','cataclysm:ignitium','cataclysm:cursium','deeperdarker:resonarium'){
    $own=@($armor.measurements|Where-Object armor -EQ $id);if(-not $own.Count){continue}
    $values=foreach($prot in 0,4){$m=@($own|Where-Object {$_.attack -eq 'warden_melee' -and $_.protection -eq $prot});if($m.Count){([double]$m[0].heartsLost).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)}else{'not supported'}}
    $sonic=@($own|Where-Object attack -EQ 'warden_sonic_boom')[0].heartsLost
    $lines.Add("| $id | $($values[0]) | $($values[1]) | $(([double]$sonic).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)) |")
}
$lines.Add('')
$lines.Add('All values are hearts lost on Hard. One heart is 2 HP. More than 10 hearts can exhaust the entire health bar of a normal player; RagRevival can turn a lethal outcome into downing. Extra health on the measurement victim prevents death/downing from truncating the recorded damage.')
$lines.Add('')
$lines.Add('Phoenix armor with matching gloves cancels the tested Hydra fire damage; four armor pieces without gloves do not receive that set immunity under these server settings. Fresh Resonarium transfers armor-sensitive damage to durability and blocked all 12 sampled Warden melee hits. At 75% worn, health damage began on hit 5; at 99% worn, the first hit broke the set and the second cost 22.391 hearts. Magic and sonic boom still hurt. These are native armor mechanics, not universal damage caps.')
$lines.Add('')
$lines.Add('## Fixed HP and vanilla attack values')
$lines.Add('')
$lines.Add('| Boss | Fixed max HP |')
$lines.Add('|---|---:|')
foreach($entry in $config.healthTargets.PSObject.Properties){$lines.Add("| $($entry.Name) | $($entry.Value) |")}
$lines.Add('')
$lines.Add('Against full Protection IV netherite on Hard: Wither direct black/blue skulls measured 1.5 hearts; skull splash measured 1.539 in the full-exposure probe, below its 2-heart cap; spawn explosion capped at 5; Wither II remains 0.18 per damage tick. Dragon head/neck measured 3.5, wing 1.25, perched breath 1.25 and fireball cloud 2 per application. Warden melee measured 4.5 and sonic boom 5.5, retaining armor/enchantment bypass.')
$lines.Add('')
$lines.Add('## Evidence and limits')
$lines.Add('')
$lines.Add('[Boss-by-boss attack/phase evidence](boss-coverage-results.md), [Cataclysm coverage reconciliation](cataclysm-coverage-adjudication.md), [all assertions](all-assertions.csv), and [native scenario records](all-native-scenarios.csv) retain the individual outcomes. Raw JSON is included in the evidence package. Earlier failed fixtures and candidate runs remain archived in the isolated test checkout; the report uses final passing outputs.')
$lines.Add('')
$lines.Add('The tests use simulated server players and staged native attack/phase methods. They do not establish continuous human multiplayer fights, every random trajectory or environmental combination, accumulated overlapping hazards, every accessory combination, or visually rendered client HUDs/animations. Boss-bar results validate server events/progress/visibility/tracking, not screenshots. Armor comparisons use the specified gear and conditions; the separate set-effect tests cover 30 complete modded combinations on dry ground, with documented glove/durability variants.')
$lines.Add('')
$lines.Add('Runtime: Minecraft 1.21.1; NeoForge 21.1.264; Java 21.0.11+10; Twilight Forest 4.8.3345; Bosses of Mass Destruction 1.3.3; Cataclysm 3.33; the copied full survival mod/config pack. The copied legacy 20-boss config was retained during execution, so the new vanilla defaults were also tested without requiring a config replacement.')
$lines.Add('')
$lines.Add("Production JAR SHA-256: ``$jarHash``.")
$lines.Add("Test helper SHA-256: ``$helperHash``.")
$lines.Add('')
$lines.Add('The deliverable contains the production JAR, optional example config and reports. The test helper and third-party mods are not part of the install package. Do not install the test helper on the survival server.')
New-Item -ItemType Directory -Path $OutputDirectory -Force|Out-Null
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'REGRESSION-2026-11-04.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
$manifest=[ordered]@{version='1.1.0';productionSha256=$jarHash;helperSha256=$helperHash;assertions=$checks.Count;failed=0;armorOutfits=$armor.outfitCount;armorMeasurements=$armor.measurementCount;setEffectMeasurements=$effects.measurementCount;sourceReadOnly=$true;installedIntoSurvival=$false}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'test-run-manifest.json'),($manifest|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
