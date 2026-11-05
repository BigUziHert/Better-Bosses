param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [Parameter(Mandatory=$true)][guid]$ExpectedUuid,
    [ValidateRange(10,300)][int]$CounterSeconds=300,
    [ValidateRange(30,1500)][int]$MaxSeconds=1300
)
# Run as a bounded background process: a failed agent turn must not leave passive inputs forever.
# Only ordinary client weapon/spacing/held-key choices are written. No game state is changed.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$control=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$journal=Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl'
$identity=$ExpectedUuid.ToString()
$state=Read-SharedTestFile -LiteralPath $metadata|ConvertFrom-Json -DateKind String
if(-not $state.running -or $state.boss -ne 'cataclysm:ignis' -or @($state.bossUuids).Count -ne 1 -or $state.bossUuids[0] -ne $identity){throw 'An exact matching active Ignis encounter is required.'}
$telemetry=@(Get-ChildItem -LiteralPath $EncounterDirectory -Filter '*-cataclysm_ignis-netherite_max.telemetry.jsonl')
if($telemetry.Count -ne 1){throw 'Expected one Ignis telemetry file.'}
$started=[DateTimeOffset]::UtcNow
$counterDeadline=$started.AddSeconds($CounterSeconds)
$deadline=$started.AddSeconds($MaxSeconds)
$stage='counter';$owned=$null;$reader=$null;$pending='';$latest=$null;$reason='overall_deadline'
$guardOrb=$null;$guardUntil=0;$guardActive=$false;$guardReflected=$false

function Journal([string]$Action,[string]$Detail){
    $row=[ordered]@{utc=[DateTimeOffset]::UtcNow.ToString('o');run=Split-Path $EncounterDirectory -Leaf;boss='cataclysm:ignis';bossUuid=$identity;action=$Action;reason=$Detail;stage=$stage;observedTick=$state.tick;shieldDurability=if($latest){$latest.state.getShieldDurability}else{$null};nativePhase=if($latest){$latest.state.getBossPhase}else{$null}}
    $bytes=[Text.Encoding]::UTF8.GetBytes(($row|ConvertTo-Json -Compress)+"`n")
    $stream=[IO.File]::Open($journal,[IO.FileMode]::Append,[IO.FileAccess]::Write,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    try{$stream.Write($bytes)}finally{$stream.Dispose()}
}
function Choose([bool]$Attack,[string]$Why,[bool]$Guard=$false){
    $before=Read-SharedTestFile -LiteralPath $control
    if($null -ne $script:owned -and $before -cne $script:owned){throw 'External tactic change detected; preserve it.'}
    # Precompute ownership so a journal error after the atomic control write can
    # still restore exactly the choice this process published.
    $next=$before|ConvertFrom-Json -AsHashtable -DateKind String
    $mode=if($Guard -or $Attack){'auto'}else{'ranged'}
    [double]$range=if($Guard -or $Attack){0}else{13.5}
    $next.enabled=$true;$next.weaponMode=$mode;$next.preferredRange=$range
    $next.attackEnabled=$Attack;$next.guardEnabled=$Guard;$next.activateEmp=$false
    $script:owned=$next|ConvertTo-Json -Compress
    try{
        & (Join-Path $PSScriptRoot 'set-native-player-tactic.ps1') -EncounterDirectory $EncounterDirectory `
            -Boss 'cataclysm:ignis' -ExpectedUuid $ExpectedUuid -WeaponMode $mode -PreferredRange $range `
            -AttackEnabled $Attack -GuardEnabled $Guard -Reason $Why | Out-Null
        if((Read-SharedTestFile -LiteralPath $control) -cne $script:owned){$script:owned=$null;throw 'External tactic change after publication; preserve it.'}
    }catch{
        $after=Read-SharedTestFile -LiteralPath $control
        if($after -ceq $before){$script:owned=$before}
        elseif($after -cne ($next|ConvertTo-Json -Compress)){$script:owned=$null}
        throw
    }
}
try{
    Choose $false "Bounded Ignis counter stage: native passive13.5 for at most$($CounterSeconds)seconds, then automatic offense regardless of agent availability."
    Journal 'bounded_ignis_stage_start' "Counter deadline $($counterDeadline.ToString('o')); overall deadline $($deadline.ToString('o'))."
    $reader=[IO.StreamReader]::new([IO.File]::Open($telemetry[0].FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)))
    while([DateTimeOffset]::UtcNow -lt $deadline){
        $state=Read-SharedTestFile -LiteralPath $metadata|ConvertFrom-Json -DateKind String
        if(-not $state.running -or $state.boss -ne 'cataclysm:ignis' -or $state.bossUuids[0] -ne $identity){$reason='encounter_ended_or_changed';break}
        if(([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($state.timestamp)).TotalSeconds -gt 10){$reason='stale_metadata';break}
        if((Read-SharedTestFile -LiteralPath $control) -cne $owned){$reason='external_tactic_change';break}
        $pending+=$reader.ReadToEnd();$end=$pending.LastIndexOf("`n")
        if($end -ge 0){
            $complete=$pending.Substring(0,$end);$pending=$pending.Substring($end+1)
            foreach($line in ($complete -split "`n")){
                if($line -notmatch '"event":"(state_transition|entity_emitted|projectile_owner_changed)"'){continue}
                $row=$line|ConvertFrom-Json -DateKind String
                if($row.event -eq 'state_transition' -and $row.entity.uuid -eq $identity){$latest=$row}
                elseif($row.event -eq 'entity_emitted' -and $row.entity.type -eq 'cataclysm:ignis_abyss_fireball' -and $row.owner.uuid -eq $identity){
                    # Only a native new charge, not a previously fired projectile reloaded from a chunk.
                    $animation=$row.ownerStateAtEmission.animation
                    $delay=if($animation -eq 'MAGIC_ATTACK'){109}elseif($animation -in @('REINFORCED_SMASH','REINFORCED_SMASH_SOUL')){54}else{0}
                    $until=[int]$row.tick+[Math]::Min(150,$delay+40)
                    if($delay -gt 0 -and $until -gt [int]$state.tick -and @($row.nativeCallers -match 'shootAbyssFireball').Count -gt 0){
                        $guardOrb=[string]$row.entity.uuid;$guardUntil=$until;$guardReflected=$false
                    }
                }elseif($row.event -eq 'projectile_owner_changed' -and $row.projectile.uuid -eq $guardOrb -and $row.owner.role -eq 'fixture_player'){$guardReflected=$true}
            }
        }
        if($stage -eq 'counter' -and (($latest -and ($latest.state.getIsShieldBreak -or $latest.state.animation -eq 'BREAK_THE_SHIELD')) -or [DateTimeOffset]::UtcNow -ge $counterDeadline)){
            $stage='offense'
            if(-not $guardActive){Choose $true "Native shield break observed or$($CounterSeconds)second counter deadline reached; hold ordinary AUTO0 sword offense. Native client inputs handle movement and attacks every tick."}
            Journal 'bounded_ignis_stage_changed' 'Passive-only preparation has ended permanently for this watcher.'
        }
        if($guardOrb -and ($guardReflected -or [int]$state.tick -ge $guardUntil)){
            if($guardActive){Choose ($stage -eq 'offense') "Bounded native shield window ended: reflected=$guardReflected; nativeTick=$($state.tick), deadline=$guardUntil."}
            Journal 'bounded_ignis_guard_end' "Native orb=$guardOrb; reflected=$guardReflected; deadline=$guardUntil."
            $guardOrb=$null;$guardActive=$false;$guardReflected=$false
        }elseif($guardOrb -and -not $guardActive){
            Choose $false "Native abyss charge observed: ordinary AUTO shield facing boss until nativeTick=$guardUntil or player-owned reflection; no boss state changes." $true
            $guardActive=$true
            Journal 'bounded_ignis_guard_start' "Native orb=$guardOrb; guard deadline=$guardUntil."
        }
        # Offense stays enabled between bounded shield windows. The client
        # performs ordinary native sword movement and attacks every tick.
        Start-Sleep -Milliseconds 500
    }
}catch{
    $reason='watcher_error: '+$_.Exception.Message
    throw
}finally{
    if($reader){$reader.Dispose()}
    # Restore only the exact bytes owned by this process. This also handles a changed encounter.
    $current=Read-SharedTestFile -LiteralPath $control
    if($owned -and $current -ceq $owned){
        $choice=$current|ConvertFrom-Json -AsHashtable -DateKind String
        $choice.enabled=$true;$choice.weaponMode='auto';$choice.preferredRange=0;$choice.attackEnabled=$true;$choice.guardEnabled=$false;$choice.activateEmp=$false
        $temporary=$control+'.ignis-stage-exit.tmp'
        [IO.File]::WriteAllText($temporary,($choice|ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
        for($attempt=0;;$attempt++){
            if((Read-SharedTestFile -LiteralPath $control) -cne $owned){break}
            try{[IO.File]::Move($temporary,$control,$true);break}
            catch [IO.IOException]{if($attempt -ge 4){throw};Start-Sleep -Milliseconds 50}
        }
    }
    Journal 'bounded_ignis_stage_exit' $reason
}
