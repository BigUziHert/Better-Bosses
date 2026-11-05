param(
    [Parameter(Mandatory=$true)][string]$InputJson,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$report=Get-Content -LiteralPath $InputJson -Raw | ConvertFrom-Json
if($report.errors -ne 0){throw "Armor effects contain $($report.errors) errors"}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$rows=@($report.measurements)
$flat=foreach($m in $rows){[pscustomobject]@{
    armor=$m.armor;variant=$m.variant;protection=$m.protection;attack=$m.attack;method=$m.method
    hearts_lost=$m.heartsLost;armor_points=$m.armorPoints;toughness=$m.toughness;hit_number=$m.hitNumber
    effects=($m.effectsBefore -join ';');equipped_after=($m.equipped -join ';')
    item_damage_before=($m.itemDamageBefore -join ';');item_damage_after=($m.itemDamageAfter -join ';')
    item_max_durability_after=($m.itemMaxDurabilityAfter -join ';')
}}
$flat | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'armor-effects-and-endurance.csv') -NoTypeInformation -Encoding utf8
$lines=[Collections.Generic.List[string]]::new();$lines.Add('# Armor set effects and endurance');$lines.Add('')
$lines.Add("$($report.measurementCount) supplemental runtime measurements; $($report.errors) errors. All damage is hearts lost on Hard.");$lines.Add('');$lines.Add($report.scope);$lines.Add('')
$lines.Add('| Set | Condition | Protection | Naga head | Hydra fire | Lich magic | Ignis heavy probe | Warden melee | Sonic boom |')
$lines.Add('|---|---|---:|---:|---:|---:|---:|---:|---:|')
$normal=@($rows|Where-Object {$null-eq $_.hitNumber})
foreach($group in ($normal|Group-Object armor,variant|Sort-Object Name)){
    $one=$group.Group[0];$cells=@($one.armor,$one.variant,$one.protection)
    foreach($attack in 'naga_head','hydra_flame','lich_bolt','ignis_heavy_cap','warden_melee','warden_sonic_boom'){
        $m=@($group.Group|Where-Object attack -EQ $attack)
        if($m.Count-ne 1){throw "Missing/duplicate effect measurement $($group.Name)/$attack"}
        $cells+=([double]$m[0].heartsLost).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)
    }
    $lines.Add('| '+($cells -join ' | ')+' |')
}
$lines.Add('');$lines.Add('## Resonarium durability');$lines.Add('')
$lines.Add('The installed DeeperDarker 1.4.1 handler subtracts one quarter of post-mitigation damage for each Resonarium piece when the source does not bypass armor, and damages those items. Its armor cannot accept Protection enchantments. Full intact armor can therefore prevent a hit from costing health while the gear wears out. Armor-bypassing magic and Warden sonic damage remain dangerous. The following native Warden melee probes keep the same armor across 12 hits, resetting only hit cooldown. The measuring player has extra health to retain later outcomes after gear breaks.');$lines.Add('')
$lines.Add('| Starting condition | Hits measured | First hit costing health | Total hearts lost | Final equipped pieces |')
$lines.Add('|---|---:|---:|---:|---:|')
foreach($group in ($rows|Where-Object {$null-ne $_.hitNumber}|Group-Object variant|Sort-Object Name)){
    $ordered=@($group.Group|Sort-Object hitNumber);$first=@($ordered|Where-Object heartsLost -GT 0|Select-Object -First 1)
    $firstLabel=if($first.Count){$first[0].hitNumber}else{'none in sample'}
    $total=($ordered|Measure-Object heartsLost -Sum).Sum
    $remaining=@($ordered[-1].equipped|Where-Object {$_-ne 'minecraft:air'}).Count
    $lines.Add("| $($group.Name) | $($ordered.Count) | $firstLabel | $(([double]$total).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)) | $remaining |")
}
$lines.Add('');$lines.Add('[Every measured set-effect hit, remaining item durability, and endurance hit](armor-effects-and-endurance.csv) is available as CSV. The Aether matching-gloves variant uses its actual accessory slot; this matters because the copied server requires gloves for set abilities. These stationary dry-ground tests do not cover every water/lava/flight condition or arbitrary accessory combination.')
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'armor-effects-results.md'),($lines-join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{Measurements=$report.measurementCount;Errors=$report.errors;Output=$OutputDirectory}|Format-List
