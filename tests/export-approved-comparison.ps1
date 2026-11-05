param(
    [Parameter(Mandatory=$true)][string]$ArmorJson,
    [Parameter(Mandatory=$true)][string]$CombinedJson,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$ApprovedJson=(Join-Path $PSScriptRoot 'approved-damage-targets-1.2.json')
)
$ErrorActionPreference='Stop'
$approved=Get-Content -LiteralPath $ApprovedJson -Raw | ConvertFrom-Json
$armor=Get-Content -LiteralPath $ArmorJson -Raw | ConvertFrom-Json
if($armor.errors -ne 0){throw 'Armor measurement errors remain'}
$combined=@(Get-Content -LiteralPath $CombinedJson -Raw | ConvertFrom-Json)
$mapping=[ordered]@{}
foreach($sourceName in 'DamageCases.java','VanillaBossCases.java'){
    $source=Get-Content -LiteralPath (Join-Path $PSScriptRoot "src/main/java/survival/balance/tests/$sourceName") -Raw
    foreach($match in [regex]::Matches($source,'(?:add\(|new Case\(|ATTACKS\.put\()"(?<attack>[^"]+)"[^\r\n]*?BalanceExpectations\.target\("(?<profile>[^"]+)"\)')){
        $profile=$match.Groups['profile'].Value
        if(-not $mapping.Contains($profile)){$mapping[$profile]=$match.Groups['attack'].Value}
    }
}
$mapping['ignis.ultimate']='native_ignis_ultimate'
$mapping['ur_ghast.combined']='ur_fireball/direct_plus_splash'
$mapping['harbinger.combined']='harb_missile/direct_plus_splash'
$armorOnly=@('alpha_yeti.falling_ice','maledictus.anima')
$neither=@('ignis.flame_pulse','warden.sonic_boom')
$protectionOnly=@('twilight_lich.bolt','twilight_lich.bomb','minoshroom.slam','ur_ghast.tantrum','ender_guardian.rune','harbinger.laser','leviathan.beam','scylla.lightning','scylla.lightning_pulse','ender_dragon.perched_breath','ender_dragon.fireball_cloud')
function Convert-Normal([double]$hearts,[bool]$protection=$true){
    $raw=[Math]::Sqrt(100+200*$hearts)-10
    # Force floating-point Math overloads; integer overloads round effective armor.
    $damage=$raw*(1-[Math]::Max(4.0,[Math]::Min(20.0,20.0-$raw/5.0))/25.0)/2.0
    if($protection){$damage*=.36};return $damage
}
$rows=foreach($entry in $approved.damageTargets.PSObject.Properties){
    $key=$entry.Name;$target=[double]$entry.Value
    if(-not $mapping.Contains($key)){throw "No independent runtime case mapped to approved profile $key"}
    $attack=$mapping[$key]
    if($key.EndsWith('.combined')){
        $native=@($combined | Where-Object {$_.move -eq "diamond/$attack" -and $null -ne $_.hits})
        $nativeNetherite=@($combined | Where-Object {$_.move -eq "netherite_p4/$attack" -and $null -ne $_.hits})
        if($native.Count -ne 1 -or $nativeNetherite.Count -ne 1){throw "Missing/duplicate native combined result $attack"}
        $measured=($native[0].hits | Measure-Object damageHp -Sum).Sum/2
        $measuredNetherite=($nativeNetherite[0].hits | Measure-Object damageHp -Sum).Sum/2
        $direct=if($key.StartsWith('ur_')){4.5}else{2.25}
        $estimated=(Convert-Normal $direct)+(Convert-Normal ($target-$direct))
        [pscustomobject][ordered]@{profile=$key;condition='native combined ceiling';approved_diamond_hearts=$target;measured_diamond_hearts=$measured;measured_netherite_protection4_hearts=$measuredNetherite;calculated_netherite_protection4_limit=$estimated;defense_rule='two separately mitigated normal hits';diamond_check=($measured -le $target+.005);netherite_check=($measuredNetherite -le 2.592+.005);attack=$attack}
        continue
    }
    $diamond=@($armor.measurements | Where-Object {$_.armor -eq 'minecraft:diamond' -and $_.protection -eq 0 -and $_.attack -eq $attack})
    $netherite=@($armor.measurements | Where-Object {$_.armor -eq 'minecraft:netherite' -and $_.protection -eq 4 -and $_.attack -eq $attack})
    if($diamond.Count -ne 1 -or $netherite.Count -ne 1){throw "Missing/duplicate reference armor result $key/$attack"}
    $rule='armor and Protection'
    if($neither -contains $key){$estimate=$target;$rule='bypasses both'}
    elseif($armorOnly -contains $key){$estimate=Convert-Normal $target $false;$rule='bypasses Protection'}
    elseif($protectionOnly -contains $key){$estimate=$target*.36;$rule='bypasses armor'}
    else{$estimate=Convert-Normal $target}
    $kind=if($attack.EndsWith('_cap')){'saturated cap fixture'}elseif($attack -eq 'native_ignis_ultimate'){'native ultimate ceiling'}else{'target or reference-strength attack'}
    [pscustomobject][ordered]@{profile=$key;condition=$kind;approved_diamond_hearts=$target;measured_diamond_hearts=$diamond[0].heartsLost;measured_netherite_protection4_hearts=$netherite[0].heartsLost;calculated_netherite_protection4_limit=$estimate;defense_rule=$rule;diamond_check=([Math]::Abs($diamond[0].heartsLost-$target) -lt .005);netherite_check=([Math]::Abs($netherite[0].heartsLost-$estimate) -lt .005);attack=$attack}
}
$failed=@($rows | Where-Object {$_.diamond_check -ne $true -or $_.netherite_check -eq $false})
if($failed.Count){$failed | Format-Table;throw "$($failed.Count) approved damage comparisons differ; do not publish as passing"}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$rows | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'approved-damage-comparison.csv') -NoTypeInformation -Encoding utf8
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Approved damage and measured armor comparison');$lines.Add('')
$lines.Add("$($rows.Count) approved profiles checked. Values are hearts lost on Hard. Diamond means a complete unenchanted set; netherite has Protection IV on all four pieces. Cap fixtures intentionally use a strong incoming hit to reach the ceiling. Native weaker strikes are allowed to remain below a cap.")
$lines.Add('');$lines.Add('| Damage profile | Approved diamond target/cap | Measured diamond | Measured netherite Protection IV | Test condition |')
$lines.Add('|---|---:|---:|---:|---|')
foreach($row in $rows){
    $ni=if($null -eq $row.measured_netherite_protection4_hearts){'not sampled jointly'}else{([double]$row.measured_netherite_protection4_hearts).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)}
    $lines.Add("| $($row.profile) | $($row.approved_diamond_hearts) | $(([double]$row.measured_diamond_hearts).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)) | $ni | $($row.condition) |")
}
$lines.Add('');$lines.Add('The combined rows measure one native collision with its immediate splash and unchanged invulnerability frames in both actual armor profiles. The CSV separately labels its calculated full-budget estimate; actual native collisions may spend less. Additional joint scenarios cover homing missiles, shields, misses, existing invulnerability frames, native reflection, separate victims/projectiles and an explicitly labeled Ur-Ghast power4 stress variant. Separate projectiles, later damage ticks, status effects and falls are not one shared hit ceiling.')
$stress=@($combined | Where-Object {$_.move -match '/adversarial_' -and $null -ne $_.hits})
if($stress.Count){
    $assertions=Get-Content -LiteralPath (Join-Path (Split-Path $CombinedJson -Parent) 'combined_projectiles.json') -Raw | ConvertFrom-Json
    if($assertions.failed -ne 0){throw 'Cannot publish passing budget-exhaustion evidence while compound assertions fail'}
    $stressRows=foreach($group in ($stress | Group-Object {($_.move -split '/callback_')[0]})){
        $name=$group.Name
        $accepted=@($group.Group | Where-Object {$_.move -notmatch '/callback_after_exhaustion$'})
        $after=@($group.Group | Where-Object {$_.move -match '/callback_after_exhaustion$'})
        $ledger=@($assertions.checks | Where-Object {$_.test -eq "compound_${name}_exhausted_budget"})
        $truncated=@($assertions.checks | Where-Object {$_.test -eq "compound_${name}_remaining_budget_was_truncated"})
        $blocked=@($assertions.checks | Where-Object {$_.test -eq "compound_${name}_exhaustion_no_material_accepted_damage"})
        if($ledger.Count -ne 1 -or $truncated.Count -ne 1 -or $blocked.Count -ne 1 -or $after.Count -ne 1 -or -not $ledger[0].passed -or -not $truncated[0].passed -or -not $blocked[0].passed){throw "Incomplete budget-exhaustion evidence for $name"}
        [pscustomobject][ordered]@{scenario=$name;native_callbacks_to_exhaust=$accepted.Count;total_measured_hearts=($accepted.hits | Measure-Object damageHp -Sum).Sum/2;reference_budget_spent=$ledger[0].actual;remaining_budget_truncated=$truncated[0].passed;hearts_after_exhaustion=($after[0].hits | Measure-Object damageHp -Sum).Sum/2;exhaustion_check_passed=$blocked[0].passed}
    }
    $stressRows | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'compound-budget-exhaustion.csv') -NoTypeInformation -Encoding utf8
    $lines.Add('');$lines.Add('## Explicit budget-exhaustion stress')
    $lines.Add('');$lines.Add("$($stressRows.Count) additional scenarios retained player health, equipment/durability and each projectile ledger while repeating native collision callbacks after clearing only hurt cooldown. They independently checked accepted damage against the eight-heart reference budget, verified final-hit truncation, then checked that another callback caused no material health loss or budget spend. The same held projectile object was deliberately reused after native discard; these are adversarial compatibility checks, not ordinary gameplay collisions.")
    $lines.Add('');$lines.Add('Half also simulate a compatibility mod removing immediate impact/splash invulnerability: a scoped test listener clears only the fixture victim cooldown when the native explosion starts. The native Ur-Ghast power4 callback then records accepted 4.5 + 3.5 = 8 diamond hearts, or 1.451684 + 1.139647 = 2.591331 hearts in Protection IV netherite. Harbinger missile and homing-missile variants also exhaust their own retained budgets across repeated callbacks. No damage source, ownership, player health or explosion calculation is replaced.')
    $lines.Add('');$lines.Add('[All budget-exhaustion measurements](compound-budget-exhaustion.csv) preserve both armor profiles. Repeated callbacks can split the same reference budget across more than two accepted hits; their total netherite damage is therefore distinct from the two-component bound. The generic two-component bound is 2.592 hearts; Harbinger impact at most 2.25 gives the tighter bound 2.583413. Numeric-zero exhaustion allows at most 0.00001 HP/reference-heart rounding remainder.')
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'approved-damage-comparison.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{Profiles=$rows.Count;Failed=$failed.Count;Output=$OutputDirectory}
