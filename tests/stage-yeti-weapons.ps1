param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [ValidateRange(1,900)][int]$TimeoutSeconds=900
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$run=Split-Path $EncounterDirectory -Leaf
$telemetry=Join-Path $EncounterDirectory '01-twilightforest_alpha_yeti-netherite_max.telemetry.jsonl'
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$journal=Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl'
$encounter=Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String
if(-not $encounter.running -or $encounter.boss -ne 'twilightforest:alpha_yeti' -or $encounter.bossUuids.Count -ne 1){throw 'A single active native Yeti encounter is required.'}
$bossUuid=[string]$encounter.bossUuids[0]
$deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$latestState=$null
$pending=''
$reason='timeout'
$reader=$null

function Set-NativeTactic([string]$Mode,[int]$Range,[string]$Reason){
    $control=Read-SharedTestFile -LiteralPath $controls | ConvertFrom-Json -AsHashtable -DateKind String
    if($control.enabled -and $control.attackEnabled -and $control.weaponMode -eq $Mode -and [int]$control.preferredRange -eq $Range){return}
    $control.enabled=$true
    $control.attackEnabled=$true
    $control.weaponMode=$Mode
    $control.preferredRange=$Range
    $json=$control | ConvertTo-Json -Compress
    $temporary=$controls+'.yeti-watcher.tmp'
    [IO.File]::WriteAllText($temporary,$json)
    for($attempt=0;;$attempt++){
        try{[IO.File]::Move($temporary,$controls,$true);break}
        catch [IO.IOException]{if($attempt -ge 4){throw};Start-Sleep -Milliseconds 50}
    }
    [ordered]@{
        utc=[DateTime]::UtcNow.ToString('o');run=$run;boss='twilightforest:alpha_yeti';bossUuid=$bossUuid
        tactic='Bounded native-phase weapon and spacing choice';reason=$Reason
        observedTick=if($null -ne $latestState){$latestState.tick}else{$null}
        isRampaging=if($null -ne $latestState){$latestState.state.isRampaging}else{$null}
        isTired=if($null -ne $latestState){$latestState.state.isTired}else{$null}
        control=$control
    } | ConvertTo-Json -Compress -Depth 5 | Add-Content -LiteralPath $journal
    "Yeti normal inputs: $Mode range=$Range ($Reason)"
}

try{
    $stream=[IO.File]::Open($telemetry,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $reader=[IO.StreamReader]::new($stream)
    while([DateTime]::UtcNow -lt $deadline){
        $encounter=Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String
        if(-not $encounter.running){$reason='encounter_finished';break}
        if($encounter.boss -ne 'twilightforest:alpha_yeti' -or @($encounter.bossUuids).Count -ne 1 -or $encounter.bossUuids[0] -ne $bossUuid){$reason='encounter_changed';break}
        $pending+=$reader.ReadToEnd()
        $lines=$pending.Split("`n")
        $pending=$lines[-1]
        for($index=0;$index -lt $lines.Length-1;$index++){
            $line=$lines[$index]
            if($line -notlike '*"event":"state_transition"*'){continue}
            $observed=$line | ConvertFrom-Json -DateKind String
            if($observed.entity.uuid -eq $bossUuid){$latestState=$observed}
        }
        if($null -ne $latestState){
            if($latestState.state.isRampaging -or $latestState.state.isTired){Set-NativeTactic 'ranged' 16 'native rampage or fatigue observed'}
            else{Set-NativeTactic 'auto' 0 'native normal phase observed'}
        }
        Start-Sleep -Milliseconds 250
    }
}
finally{
    if($null -ne $reader){$reader.Dispose()}
    Set-NativeTactic 'auto' 0 ('watcher stopped: '+$reason)
}
