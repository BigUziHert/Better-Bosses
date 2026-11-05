param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [Parameter(Mandatory=$true)][guid]$ExpectedUuid,
    [ValidateRange(1,2400)][int]$MaxNativeTicks=2400,
    [ValidateRange(10,300)][int]$TimeoutSeconds=180,
    [ValidateRange(50,1000)][int]$PollMilliseconds=200
)
# Ordinary client spacing/weapon choices only. Never changes a boss/game field.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$runPath=[IO.Path]::GetFullPath($EncounterDirectory)
$resultsRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'results/full-fights'))+[IO.Path]::DirectorySeparatorChar
if(-not $runPath.StartsWith($resultsRoot,[StringComparison]::OrdinalIgnoreCase)){
    throw 'EncounterDirectory must be inside this repository tests/results/full-fights.'
}
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$journal=Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl'
$setter=Join-Path $PSScriptRoot 'set-native-player-tactic.ps1'
$boss='cataclysm:scylla'
$identity=$ExpectedUuid.ToString()
$ownedText=$null
$reader=$null
$pending=''
$startTick=0
$lastTick=0
$observed=$false
$reason='wall_clock_deadline'
$deadline=[DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)

function Write-Journal([string]$Action,$Details){
    [ordered]@{
        utc=[DateTimeOffset]::UtcNow.ToString('o');run=Split-Path $runPath -Leaf
        boss=$boss;bossUuid=$identity;action=$Action;details=$Details
    } | ConvertTo-Json -Depth 6 -Compress | Add-Content -LiteralPath $journal
}
function Read-Metadata { Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String }
function Same-Encounter($State){
    $State.running -and $State.boss -eq $boss -and $State.gear -eq 'netherite_max' -and
        @($State.bossUuids).Count -eq 1 -and $State.bossUuids[0] -eq $identity
}
function Resume-Owned([string]$Reason){
    $text=Read-SharedTestFile -LiteralPath $controls
    if($null -eq $script:ownedText -or $text -cne $script:ownedText){return}
    try{
        & $setter -EncounterDirectory $runPath -Boss $boss -ExpectedUuid $ExpectedUuid `
            -WeaponMode ranged -PreferredRange 8 -AttackEnabled $true -GuardEnabled $false `
            -Reason "Bounded Scylla DoubleSwing watcher resumes normal offense: $Reason" | Out-Null
        return
    }catch{
        # The guarded setter correctly rejects ended/stale metadata. A later encounter
        # must still never inherit our pause. Only replace our exact owned control text.
        if((Read-SharedTestFile -LiteralPath $controls) -cne $script:ownedText){return}
        $choice=$text | ConvertFrom-Json -AsHashtable -DateKind String
        $choice.enabled=$true;$choice.attackEnabled=$true;$choice.guardEnabled=$false
        $choice.weaponMode='ranged';$choice.preferredRange=8;$choice.activateEmp=$false
        $temporary=$controls+'.scylla-resume.tmp'
        [IO.File]::WriteAllText($temporary,($choice | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
        for($attempt=0;;$attempt++){
            if((Read-SharedTestFile -LiteralPath $controls) -cne $script:ownedText){return}
            try{[IO.File]::Move($temporary,$controls,$true);break}
            catch [IO.IOException]{if($attempt -ge 4){throw};Start-Sleep -Milliseconds 50}
        }
        Write-Journal 'scylla_owned_exit_offense_restored' @{reason=$Reason;scope='Restored only this watcher-owned pause; no encounter or entity mutation.'}
    }
}

try{
    $state=Read-Metadata
    if(-not (Same-Encounter $state)){throw 'Active Scylla, netherite_max and exact UUID must match.'}
    $files=@(Get-ChildItem -LiteralPath $runPath -Filter '*-cataclysm_scylla-netherite_max.telemetry.jsonl')
    if($files.Count -ne 1){throw 'Expected one Scylla telemetry file in this run.'}
    $share=[IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete
    $reader=[IO.StreamReader]::new([IO.File]::Open($files[0].FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,$share))
    $startTick=[int]$state.tick;$lastTick=$startTick
    # Precompute ownership before the setter so a post-write journal error still
    # enters finally with the exact pause text that needs to be restored.
    $choice=Read-SharedTestFile -LiteralPath $controls | ConvertFrom-Json -AsHashtable -DateKind String
    $choice.enabled=$true;$choice.weaponMode='ranged';$choice.preferredRange=0.5
    $choice.attackEnabled=$false;$choice.guardEnabled=$false;$choice.activateEmp=$false
    $ownedText=$choice | ConvertTo-Json -Compress
    $published=@(& $setter -EncounterDirectory $runPath -Boss $boss -ExpectedUuid $ExpectedUuid `
        -WeaponMode ranged -PreferredRange 0.5 -AttackEnabled $false -GuardEnabled $false `
        -Reason 'Bounded native Scylla DoubleSwing selection: ordinary close walking, deliberate attacks paused; healing/revival remain active.')
    if($published.Count -gt 0){$ownedText=[string]$published[-1]}
    Write-Journal 'scylla_double_swing_watcher_started' @{startTick=$startTick;maxNativeTicks=$MaxNativeTicks;timeoutSeconds=$TimeoutSeconds}
    while([DateTimeOffset]::UtcNow -lt $deadline){
        $state=Read-Metadata
        if(-not (Same-Encounter $state)){$reason='encounter_ended_or_changed';break}
        $age=([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($state.timestamp)).TotalSeconds
        if($age -lt -2 -or $age -gt 10){$reason='metadata_stale';break}
        if((Read-SharedTestFile -LiteralPath $controls) -cne $ownedText){$reason='external_tactic_change';break}
        $lastTick=[int]$state.tick
        $pending+=$reader.ReadToEnd()
        $lines=$pending.Split("`n");$pending=$lines[-1]
        for($index=0;$index -lt $lines.Length-1;$index++){
            if([string]::IsNullOrWhiteSpace($lines[$index])){continue}
            $row=$lines[$index] | ConvertFrom-Json -DateKind String
            if($row.event -eq 'state_transition' -and $row.entity.uuid -eq $identity -and
                $null -ne $row.state.getAttackState -and [int]$row.state.getAttackState -eq 3){
                $observed=$true;$reason='native_double_swing_selected'
                Write-Journal 'scylla_native_double_swing_observed' @{tick=$row.tick;sequence=$row.sequence;animationFrame=$row.animationFrame;scope='Native state selection; not proof of an accepted damage hit.'}
                break
            }
        }
        if($observed){break}
        if($lastTick-$startTick -ge $MaxNativeTicks){$reason='native_tick_deadline_gap_retained';break}
        Start-Sleep -Milliseconds $PollMilliseconds
    }
}catch{
    $reason='watcher_error: '+$_.Exception.Message
    throw
}finally{
    if($null -ne $reader){$reader.Dispose()}
    # Restore before journaling, so an evidence-output error cannot retain a pause.
    Resume-Owned $reason
    Write-Journal 'scylla_double_swing_watcher_finished' @{reason=$reason;observed=$observed;startTick=$startTick;lastTick=$lastTick;elapsedNativeTicks=$lastTick-$startTick}
    Write-Output "Scylla watcher ended: $reason; state3Observed=$observed; nativeTicks=$($lastTick-$startTick)"
}
