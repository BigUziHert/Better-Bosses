param(
    [Parameter(Mandatory=$true)][string]$InputJson,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$report=Get-Content -LiteralPath $InputJson -Raw | ConvertFrom-Json
if($report.errors -ne 0){throw "Armor measurements contain $($report.errors) errors; inspect the JSON before publishing."}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$measurements=@($report.measurements)
$flat=foreach($m in $measurements){
    [pscustomobject][ordered]@{
        armor=$m.armor; pieces=$m.pieces; protection=$m.protection; boss=$m.boss; attack=$m.attack
        method=$m.method; armor_points=$m.armorPoints; toughness=$m.toughness
        hearts_lost=[Math]::Round($m.heartsLost,5); accepted=$m.accepted
        equipped=($m.equipped -join ';')
        item_damage_after=($m.itemDamageAfter -join ';'); item_max_durability_after=($m.itemMaxDurabilityAfter -join ';')
    }
}
$flat | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'armor-all-attacks.csv') -NoTypeInformation -Encoding utf8
$report.inventory | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'armor-item-inventory.csv') -NoTypeInformation -Encoding utf8
$lines=[System.Collections.Generic.List[string]]::new()
$lines.Add('# Armor test results')
$lines.Add('')
$lines.Add("$($report.outfitCount) outfits; $($report.measurementCount) measured attack/outfit/enchantment combinations. All damage below is **hearts lost on Hard**. Each cell shows **plain / Protection IV**; full Protection IV means level IV on each equipped piece. One heart is 2 HP.")
$lines.Add('')
$lines.Add($report.scope)
$lines.Add('')
$lines.Add('An em dash means that enchantment variant was not tested because it is unsupported (or the outfit has no items). The Protection IV column never forces an enchantment rejected by the item. Fresh Resonarium can show zero health loss because its native mod transfers armor-sensitive damage to item durability; this is not unlimited invulnerability. See [set effects and endurance](armor-effects-results.md) for repeated-hit measurements.')
$lines.Add('')
$lines.Add('The table gives representative attacks for every tested outfit. [All measured attacks](armor-all-attacks.csv) contains every fixture/native attack result for every outfit and enchantment state. [Item inventory](armor-item-inventory.csv) gives registry IDs, equipment slots, materials, and class names. A partial outfit leaves its other armor slots empty; it is not comparable to a complete set as an equal-slot loadout.')
$lines.Add('')
$lines.Add('| Armor outfit | Pieces | Armor / toughness | Naga head | Hydra bite | Leviathan bite | Ignis heavy cap probe | Warden melee | Sonic boom |')
$lines.Add('|---|---:|---:|---:|---:|---:|---:|---:|---:|')
function Cell($rows,[string]$attack){
    $values=foreach($protection in 0,4){
        $match=@($rows | Where-Object {$_.attack -eq $attack -and $_.protection -eq $protection})
        if($match.Count -eq 0){'—'}elseif($match.Count -ne 1){throw "Duplicate measurement $attack/$protection"}else{([double]$match[0].heartsLost).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)}
    }
    $values -join ' / '
}
foreach($group in ($measurements | Group-Object armor | Sort-Object Name)){
    $rows=@($group.Group);$one=$rows[0]
    $cells=@($group.Name,$one.pieces,"$($one.armorPoints) / $($one.toughness)")
    foreach($attack in 'naga_head','hydra_bite','leviathan_bite','ignis_heavy_cap','warden_melee','warden_sonic_boom'){$cells+=Cell $rows $attack}
    $lines.Add('| '+($cells -join ' | ')+' |')
}
$lines.Add('')
$lines.Add('The configured boss damage numbers are calibrated to Protection IV netherite. They are not universal health-loss caps: weaker armor can take considerably more. The 200-HP instrumented player avoids a death/downing cutoff, so a value above 10 hearts denotes a potentially lethal single hit for an ordinary 20-HP player. Repeated attacks, status effects, falls, shield use, and accessory/set abilities are separate from these single-hit baseline measurements.')
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'armor-results.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{Outfits=$report.outfitCount;Measurements=$report.measurementCount;CsvRows=$flat.Count;Output=$OutputDirectory} | Format-List
