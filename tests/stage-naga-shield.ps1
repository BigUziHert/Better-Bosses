param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [int]$TimeoutSeconds=45
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$telemetry=Join-Path $EncounterDirectory '01-twilightforest_naga-netherite_max.telemetry.jsonl'
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$dazed=$false
$stunless=$false
while([DateTime]::UtcNow -lt $deadline){
    $encounter=Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json
    if(-not $encounter.running -or $encounter.boss -ne 'twilightforest:naga'){break}
    $dazed=[bool](Select-String -LiteralPath $telemetry -Pattern '"movementPattern":"DAZE(?:D)?"|"isDazed":true' -Quiet)
    $stunless=[bool](Select-String -LiteralPath $telemetry -Pattern '"movementPattern":"STUNLESS[^" ]*"|"isStunlessCharging":true' -Quiet)
    if($dazed -and $stunless){break}
    Start-Sleep -Milliseconds 250
}
[IO.File]::WriteAllText($controls,'{"enabled":true,"weaponMode":"auto","attackEnabled":true}')
[ordered]@{
    utc=[DateTime]::UtcNow.ToString('o')
    run=Split-Path $EncounterDirectory -Leaf
    boss='twilightforest:naga'
    tactic='Bounded ordinary shield observation ends and native offense resumes'
    dazed=$dazed
    stunless=$stunless
} | ConvertTo-Json -Compress | Add-Content (Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl')
"Native Naga offense resumed. DAZE=$dazed STUNLESS=$stunless"
