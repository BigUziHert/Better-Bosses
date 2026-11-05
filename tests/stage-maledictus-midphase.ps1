param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [Parameter(Mandatory=$true)][guid]$ExpectedUuid
)
# Ordinary weapon and movement choices. Never writes an entity or boss state.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$primary=Join-Path $EncounterDirectory '01-cataclysm_maledictus-netherite_max.jsonl'
$telemetry=Join-Path $EncounterDirectory '01-cataclysm_maledictus-netherite_max.telemetry.jsonl'
$owned=$null
$stage='damage_to_middle'
$pauseTick=0
$seen=[Collections.Generic.HashSet[int]]::new()
$deadline=[DateTimeOffset]::UtcNow.AddMinutes(21)
$reader=$null
$pending=''
function Set-Tactic([bool]$Attack,[double]$Range,[string]$Reason){
    & (Join-Path $PSScriptRoot 'set-native-player-tactic.ps1') -EncounterDirectory $EncounterDirectory `
        -Boss 'cataclysm:maledictus' -ExpectedUuid $ExpectedUuid -WeaponMode ranged `
        -PreferredRange $Range -AttackEnabled $Attack -Reason $Reason | Out-Null
    $script:owned=Read-SharedTestFile -LiteralPath $controls
    Write-Output $Reason
}
try{
    $stream=[IO.File]::Open($telemetry,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $reader=[IO.StreamReader]::new($stream)
    Set-Tactic $true 6 'Native Maledictus follow-up starts with ordinary ranged offense.'
    while([DateTimeOffset]::UtcNow -lt $deadline){
        $state=Read-SharedTestFile -LiteralPath $metadata|ConvertFrom-Json -DateKind String
        if(-not $state.running -or $state.boss -ne 'cataclysm:maledictus' -or $state.bossUuids[0] -ne $ExpectedUuid.ToString()){break}
        if(([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($state.timestamp)).TotalSeconds -gt 5){break}
        if((Read-SharedTestFile -LiteralPath $controls) -cne $owned){Write-Output 'External tactic change ended watcher ownership.';break}
        $pending+=$reader.ReadToEnd()
        $lines=$pending.Split("`n");$pending=$lines[-1]
        for($index=0;$index -lt $lines.Length-1;$index++){
            if($lines[$index] -notlike '*"event":"state_transition"*'){continue}
            $row=$lines[$index]|ConvertFrom-Json -DateKind String
            if($row.entity.uuid -eq $ExpectedUuid.ToString() -and $null -ne $row.state.getAttackState){$null=$seen.Add([int]$row.state.getAttackState)}
        }
        $sample=Read-SharedTestFile -LiteralPath $primary -Tail 1|ConvertFrom-Json -DateKind String
        $hp=[double]$sample.bosses[0].hp
        if($stage -eq 'damage_to_middle' -and $hp -le 520){
            $stage='observe_middle';$pauseTick=[int]$state.tick
            Set-Tactic $false 5.5 "Pause deliberate offense at native HP=$hp. Backpedal below 3.5 blocks to favor native backstep selection within 4.5 blocks."
        }
        if($stage -eq 'observe_middle' -and (($seen.Contains(12) -and $seen.Contains(15)) -or $state.tick-$pauseTick -ge 2400 -or $hp -le 300)){
            $stage='finish'
            Set-Tactic $true 6 "Resume ordinary offense. Observed12=$($seen.Contains(12)), observed15=$($seen.Contains(15)), pauseTicks=$($state.tick-$pauseTick)."
        }
        Start-Sleep -Milliseconds 250
    }
}finally{
    if($reader){$reader.Dispose()}
    $state=Read-SharedTestFile -LiteralPath $metadata|ConvertFrom-Json -DateKind String
    if($owned -and (Read-SharedTestFile -LiteralPath $controls) -ceq $owned -and $state.running -and $state.bossUuids[0] -eq $ExpectedUuid.ToString()){
        Set-Tactic $true 6 'Watcher ended. Ordinary ranged offense resumes automatically.'
    }
}
