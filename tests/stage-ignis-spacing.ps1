param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [Parameter(Mandatory=$true)][guid]$ExpectedUuid,
    [ValidateRange(10,600)][int]$MaxSeconds=120
)
# Ordinary client choices only. Native healing, counters, collision and damage stay authoritative.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$control=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$client=Join-Path $repo 'run-client-playtest/client-fight-events.jsonl'
$owned=$null;$deadline=[DateTimeOffset]::UtcNow.AddSeconds($MaxSeconds)
function Set-Tactic([bool]$Attack,[string]$Reason){
    & (Join-Path $PSScriptRoot 'set-native-player-tactic.ps1') -EncounterDirectory $EncounterDirectory `
        -Boss 'cataclysm:ignis' -ExpectedUuid $ExpectedUuid -WeaponMode ranged -PreferredRange 13.5 `
        -AttackEnabled $Attack -Reason $Reason | Out-Null
    $script:owned=(Read-SharedTestFile -LiteralPath $control)
}
try{
    while([DateTimeOffset]::UtcNow -lt $deadline){
        $state=Read-SharedTestFile -LiteralPath $metadata|ConvertFrom-Json -DateKind String
        if(-not $state.running -or $state.boss -ne 'cataclysm:ignis' -or $state.bossUuids[0] -ne $ExpectedUuid.ToString()){break}
        if(([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($state.timestamp)).TotalSeconds -gt 5){break}
        $raw=(Read-SharedTestFile -LiteralPath $control)
        if($null -ne $owned -and $raw -cne $owned){Write-Output 'Stopped: external tactic change.';break}
        $choice=$raw|ConvertFrom-Json -DateKind String
        $sample=@(Read-SharedTestFile -LiteralPath $client -Tail 100|ForEach-Object{try{$_|ConvertFrom-Json -DateKind String}catch{}}|
            Where-Object {$_.event -eq 'sample' -and $_.boss -eq 'cataclysm:ignis'})[-1]
        if($sample -and ([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($sample.utc)).TotalSeconds -le 4){
            $gap=[double]$sample.distance
            if($gap -lt 9 -and $choice.attackEnabled){Set-Tactic $false "Bounded ordinary kiting: gap=$gap below9; release offense and sprint outward using native inputs."}
            elseif($gap -ge 13 -and -not $choice.attackEnabled){Set-Tactic $true "Bounded ordinary kiting: gap=$gap at least13; allow native bow draw/release until gap below9."}
        }
        Start-Sleep -Milliseconds 500
    }
}finally{
    $state=Read-SharedTestFile -LiteralPath $metadata|ConvertFrom-Json -DateKind String
    if($owned -and (Read-SharedTestFile -LiteralPath $control) -ceq $owned -and $state.running -and
        $state.boss -eq 'cataclysm:ignis' -and $state.bossUuids[0] -eq $ExpectedUuid.ToString()){
        Set-Tactic $true 'Bounded kiting watcher ended; restore ordinary ranged13.5 offense, preserving native state.'
    }
}
