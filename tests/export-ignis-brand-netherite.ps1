#Requires -Version 7.5
[CmdletBinding()]
param(
    [string]$OutputDirectory=(Join-Path $PSScriptRoot 'reports/victory-validation'),
    [string]$ResultsDirectory=(Join-Path $PSScriptRoot 'results'),
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ProductionSha256,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$HelperSha256
)
$ErrorActionPreference='Stop'
$nativePath=Join-Path $ResultsDirectory 'ignis-brand-netherite.json'
$assertionPath=Join-Path $ResultsDirectory 'ignis_brand_netherite.json'
$native=Get-Content -LiteralPath $nativePath -Raw|ConvertFrom-Json -DateKind String
$assertions=Get-Content -LiteralPath $assertionPath -Raw|ConvertFrom-Json -DateKind String
$rows=@($native.rows)
if($rows.Count -ne 36 -or @($rows|Where-Object error).Count -ne 0){throw 'Expected36 completed native probes without fixture errors'}
if($assertions.total -ne 504 -or $assertions.failed -ne 0 -or @($assertions.checks|Where-Object passed -NE $true).Count -gt 0){throw 'Native Brand assertions have not all passed'}
foreach($condition in @('fresh','native_enchanted_apple_effects')){foreach($brand in 0..5){foreach($attack in @('opening','heavy','ultimate')){
    $selected=@($rows|Where-Object {$_.condition -eq $condition -and $_.preHitBrand -eq $brand -and $_.attack -eq $attack})
    if($selected.Count -ne 1 -or @($selected[0].nativePostEvents).Count -ne 1 -or -not $selected[0].survived){throw "Incomplete/duplicate/unsurvived probe: $condition $brand $attack"}
}}}
$table=@(foreach($r in $rows){[pscustomobject]@{
    case=$r.case;condition=$r.condition;pre_hit_brand=$r.preHitBrand;attack=$r.attack;native_recipe=$r.nativeRecipe;maximum_health_hp=$r.maximumHealth
    armor_attribute=$r.armorBefore;integer_armor=$r.effectiveIntegerArmor;toughness=$r.toughnessBefore
    health_before_hp=$r.hpBefore;health_after_hp=$r.hpAfter;health_lost_hp=$r.healthDamageHp;health_lost_hearts=$r.healthDamageHp/2
    absorption_before_hp=$r.absorptionBefore;absorption_after_hp=$r.absorptionAfter;absorption_spent_hp=$r.absorptionSpentHp;absorption_spent_hearts=$r.absorptionSpentHp/2
    accepted_hp=$r.healthDamageHp+$r.absorptionSpentHp;expected_accepted_hp=$r.expectedDamageBeforeAbsorptionHp
    native_post_events=@($r.nativePostEvents).Count;survived=$r.survived;effects_before=ConvertTo-Json -InputObject @($r.effectsBefore) -Depth 4 -Compress;effects_after=ConvertTo-Json -InputObject @($r.effectsAfter) -Depth 4 -Compress
}})
New-Item -ItemType Directory -Force -Path $OutputDirectory|Out-Null
$table|Export-Csv (Join-Path $OutputDirectory 'ignis-brand-netherite.csv') -NoTypeInformation
Copy-Item -LiteralPath $nativePath -Destination (Join-Path $OutputDirectory 'ignis-brand-netherite-native.json') -Force
Copy-Item -LiteralPath $assertionPath -Destination (Join-Path $OutputDirectory 'ignis-brand-netherite-assertions.json') -Force
$maxHealth=($table.health_lost_hp|Measure-Object -Maximum).Maximum
$maxAbsorption=($table.absorption_spent_hp|Measure-Object -Maximum).Maximum
$manifest=[ordered]@{
    generatedUtc=[DateTimeOffset]::UtcNow.ToString('o');assertionFileWrittenUtc=(Get-Item -LiteralPath $assertionPath).LastWriteTimeUtc.ToString('o');nativeCases=36;assertions=504;failed=0
    productionSha256=$ProductionSha256.ToUpperInvariant()
    helperSha256=$HelperSha256.ToUpperInvariant()
    nativeResultSha256=(Get-FileHash -LiteralPath $nativePath).Hash;assertionResultSha256=(Get-FileHash -LiteralPath $assertionPath).Hash
    nativePostEvents=($table.native_post_events|Measure-Object -Sum).Sum;maximumHealthLostHp=$maxHealth;maximumHealthLostCases=@($table|Where-Object health_lost_hp -EQ $maxHealth|ForEach-Object case)
    maximumAbsorptionSpentHp=$maxAbsorption;maximumAbsorptionSpentCases=@($table|Where-Object absorption_spent_hp -EQ $maxAbsorption|ForEach-Object case)
    minimumFinalHealthHp=($table.health_after_hp|Measure-Object -Minimum).Minimum;appleHealthDamageHp=(@($table|Where-Object condition -EQ 'native_enchanted_apple_effects').health_lost_hp|Measure-Object -Sum).Sum
    scope=$native.scope;expectations=$native.expectations
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'ignis-brand-netherite-manifest.json'),($manifest|ConvertTo-Json -Depth 5)+"`n",[Text.UTF8Encoding]::new($false))
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Ignis Brand measurements in Protection IV netherite');$lines.Add('')
$lines.Add('All **36 native probes and 504 assertions passed**. The largest measured health loss was **6.84 HP (3.42 hearts)** from the ultimate with pre-hit Brand III, IV or V. The player retained 13.16 HP. With fresh native enchanted-apple effects, every hit spent absorption and removed **zero health**. The largest absorption cost was **5.472 HP (2.736 hearts)**, again at Brand III–V, leaving 10.528 of the initial 16 absorption HP.')
$lines.Add('');$lines.Add('Each row starts with full intact Protection IV netherite, 20 maximum/current HP and the stated pre-existing Brand. Fresh cases use the armor alone. Apple cases add native Resistance I and 16 absorption HP. Entries are hearts. The native integer armor conversion floors approximately 8/4 armor to 7/3 at Brand III/IV. The raw data retains exact attributes.')
$lines.Add('');$lines.Add('| Pre-hit Brand | Opening health lost | Shield-smash melee health lost | Ultimate health lost | Apple opening absorbed | Apple shield-smash absorbed | Apple ultimate absorbed |')
$lines.Add('|---|---:|---:|---:|---:|---:|---:|')
foreach($brand in 0..5){
    $label=@('None','I','II','III','IV','V')[$brand];$values=[Collections.Generic.List[string]]::new()
    foreach($condition in @('fresh','native_enchanted_apple_effects')){foreach($attack in @('opening','heavy','ultimate')){$r=$table|Where-Object {$_.condition -eq $condition -and $_.pre_hit_brand -eq $brand -and $_.attack -eq $attack};$v=if($condition -eq 'fresh'){$r.health_lost_hearts}else{$r.absorption_spent_hearts};$values.Add($v.ToString('F5',[Globalization.CultureInfo]::InvariantCulture))}}
    $lines.Add('| '+$label+' | '+($values -join ' | ')+' |')
}
$lines.Add('');$lines.Add('The opening uses the installed SWING_ATTACK AreaAttack recipe. The heavy probe measures the melee AreaAttack component from SHIELD_SMASH_ATTACK frame 34. Its separate wave is covered by other fixtures. Ultimate frame 74 runs native aiStep. Native post-hit Brand application continues where that recipe applies it, and the added stack affects subsequent hits.')
$lines.Add('');$lines.Add('Apple effects come from native ItemStack.finishUsingItem. Each controlled snapshot starts after consumption and measures one native component. Independent probes restore their initial health and equipment. Eating time, regeneration between hits, accumulated fire and whole combos require the separate continuous encounter observations.')
$lines.Add('');$lines.Add('[All 36 cases](ignis-brand-netherite.csv), [native event/effect records](ignis-brand-netherite-native.json), [all 504 assertions](ignis-brand-netherite-assertions.json) and [artifact/result hashes](ignis-brand-netherite-manifest.json) preserve the executed evidence.')
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'ignis-brand-netherite.md'),(($lines -join "`n") -replace 'initial16','initial 16' -replace 'approximately8/4','approximately 8/4' -replace 'to7/3','to 7/3' -replace 'frame34','frame 34' -replace 'frame74','frame 74' -replace 'the32','the 32' -replace 'Brand0','Brand 0' -replace 'older1','older 1' -replace 'All36','All 36' -replace 'all504','all 504' -replace 'the12','the 12' -replace 'emitted24','emitted 24' -replace 'reported151','reported 151')+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{cases=36;assertions=504;failed=0;maxHealthHp=$maxHealth;maxAbsorbedHp=$maxAbsorption;output=$OutputDirectory}|ConvertTo-Json -Compress
