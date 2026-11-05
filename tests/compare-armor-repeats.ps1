#Requires -Version 7.5
param(
    [Parameter(Mandatory=$true)][string]$PreviousCsv,
    [Parameter(Mandatory=$true)][string]$CurrentCsv,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$previous=@{}
foreach($row in Import-Csv -LiteralPath $PreviousCsv){
    $key=$row.armor,$row.protection,$row.boss,$row.attack -join '|'
    if($previous.ContainsKey($key)){throw "Duplicate previous measurement $key"}
    $previous[$key]=$row
}
$current=@(Import-Csv -LiteralPath $CurrentCsv)
if($current.Count-ne$previous.Count){throw 'Measurement count differs between compared matrices.'}
$seen=@{}
$differences=@(foreach($row in $current){
    $key=$row.armor,$row.protection,$row.boss,$row.attack -join '|'
    if($seen.ContainsKey($key) -or -not$previous.ContainsKey($key)){throw "Duplicate or unmatched current measurement $key"}
    $seen[$key]=$true
    $old=$previous[$key]
    $delta=[double]$row.hearts_lost-[double]$old.hearts_lost
    if([Math]::Abs($delta)-gt0.0001){
        [pscustomobject]@{armor=$row.armor;protection=$row.protection;boss=$row.boss;attack=$row.attack;previous_hearts=[double]$old.hearts_lost;current_hearts=[double]$row.hearts_lost;delta_hearts=$delta;previous_accepted=$old.accepted;current_accepted=$row.accepted;one_zero_outcome=([double]$old.hearts_lost-eq0 -or [double]$row.hearts_lost-eq0)}
    }
})
$differences|Export-Csv -LiteralPath (Join-Path $OutputDirectory 'armor-repeat-variability.csv') -NoTypeInformation -Encoding utf8
[ordered]@{generatedUtc=[DateTime]::UtcNow.ToString('o');previousSha256=(Get-FileHash -LiteralPath $PreviousCsv).Hash;currentSha256=(Get-FileHash -LiteralPath $CurrentCsv).Hash;matchedMeasurements=$current.Count;comparisonToleranceHearts=0.0001;differentMeasurements=$differences.Count;groups=@($differences|Group-Object armor|ForEach-Object{[pscustomobject]@{armor=$_.Name;differences=$_.Count}});allDifferencesHaveAZeroOutcome=@($differences|Where-Object one_zero_outcome -NE $true).Count-eq0;scope='Offline comparison of two executed single-probe matrices. Source-based native dodge interpretation is documented separately. No dodge probability is estimated from this sample.'}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $OutputDirectory 'armor-repeat-variability.json') -Encoding utf8
[pscustomobject]@{matched=$current.Count;differences=$differences.Count;groups=@($differences.armor|Sort-Object -Unique)}
