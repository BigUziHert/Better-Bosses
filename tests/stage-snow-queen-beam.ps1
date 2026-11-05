param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [ValidateRange(10,3600)][int]$TimeoutSeconds=600,
    [ValidateRange(10,180)][int]$BeamTimeoutSeconds=45,
    [ValidateRange(50,1000)][int]$PollMilliseconds=100
)
# Test-client tactics only: observe native telemetry and change ordinary input choices.
# Does not send server commands or edit entity state, terrain, health or cooldowns.
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$runPath=[IO.Path]::GetFullPath($EncounterDirectory)
$resultsRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'results/full-fights'))+[IO.Path]::DirectorySeparatorChar
if(-not $runPath.StartsWith($resultsRoot,[StringComparison]::OrdinalIgnoreCase)){
    throw 'EncounterDirectory must be a run inside this repository tests/results/full-fights.'
}
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$journal=Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl'
$deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$boss='twilightforest:snow_queen'
$identity=$null
$bossIds=@()
$reader=$null
$pending=''
$lastPhase=''
$paused=$false
$beamDeadline=$null
$finished=$false
$pausedControlText=$null

function Read-Metadata {
    try { Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json -DateKind String }
    catch { $null } # The harness may be in the middle of an ordinary metadata write.
}
function Identity-Of($state) { (@($state.bossUuids | Sort-Object) -join ',') }
function Write-Journal([string]$action,$details) {
    [ordered]@{
        utc=[DateTime]::UtcNow.ToString('o'); run=Split-Path $runPath -Leaf
        boss=$boss; action=$action; details=$details
    } | ConvertTo-Json -Depth 8 -Compress | Add-Content -LiteralPath $journal
}
function Set-Tactic([bool]$attacking,[string]$reason,[bool]$allowOwnedExit=$false) {
    $now=Read-Metadata
    $currentControlText=if(Test-Path -LiteralPath $controls){[IO.File]::ReadAllText($controls)}else{''}
    $ownsPause=$null -ne $script:pausedControlText -and $currentControlText -ceq $script:pausedControlText
    if($attacking -and $paused -and -not $ownsPause){
        Write-Journal 'snow_beam_external_tactic_preserved' @{reason=$reason}
        return $false
    }
    $sameEncounter=$null -ne $now -and $now.running -and $now.boss -eq $boss -and (Identity-Of $now) -eq $identity
    if(-not $sameEncounter -and -not ($allowOwnedExit -and $attacking -and $ownsPause)){return $false}
    $choice=@{}
    if($currentControlText){
        try { $choice=$currentControlText | ConvertFrom-Json -AsHashtable -DateKind String }
        catch { throw 'Cannot safely merge the current client control file.' }
    }
    $choice.enabled=$true
    $choice.attackEnabled=$attacking
    $choice.guardEnabled=$false
    $choice.weaponMode=if($attacking){'auto'}else{'ranged'}
    $choice.preferredRange=if($attacking){0}else{8}
    $newControlText=$choice | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($controls,$newControlText,[Text.UTF8Encoding]::new($false))
    if(-not $attacking){$script:pausedControlText=$newControlText}
    Write-Journal $reason @{tick=$now.tick; attackEnabled=$attacking; weaponMode=$choice.weaponMode; preferredRange=$choice.preferredRange}
    return $true
}

try {
    while([DateTime]::UtcNow -lt $deadline -and -not $finished){
        $state=Read-Metadata
        if($null -eq $state){Start-Sleep -Milliseconds $PollMilliseconds;continue}
        if($null -eq $identity){
            $files=@(Get-ChildItem -LiteralPath $runPath -Filter '*-twilightforest_snow_queen-netherite_max.telemetry.jsonl' -ErrorAction SilentlyContinue)
            if($files.Count -gt 1){throw 'Select a standalone Snow Queen run with one matching telemetry file.'}
            if(-not $state.running -or $state.boss -ne $boss -or $files.Count -ne 1){Start-Sleep -Milliseconds $PollMilliseconds;continue}
            $identity=Identity-Of $state
            if([string]::IsNullOrWhiteSpace($identity)){throw 'Active Snow Queen has no encounter identity.'}
            $bossIds=@($state.bossUuids)
            $stream=[IO.File]::Open($files[0].FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
            $reader=[IO.StreamReader]::new($stream)
            Write-Journal 'snow_beam_watcher_started' @{timeoutSeconds=$TimeoutSeconds; beamTimeoutSeconds=$BeamTimeoutSeconds; pollMilliseconds=$PollMilliseconds}
        } elseif(-not $state.running -or $state.boss -ne $boss -or (Identity-Of $state) -ne $identity){
            Write-Journal 'snow_beam_watcher_encounter_ended' @{lastPhase=$lastPhase; paused=$paused}
            break
        }
        $pending+=$reader.ReadToEnd()
        $end=$pending.LastIndexOf("`n")
        if($end -ge 0){
            $complete=$pending.Substring(0,$end)
            $pending=$pending.Substring($end+1)
            foreach($line in ($complete -split "`n")){
                if([string]::IsNullOrWhiteSpace($line)){continue}
                try {$row=$line | ConvertFrom-Json -DateKind String} catch {throw 'Invalid completed telemetry row; stopping rather than guessing a phase.'}
                if($row.event -eq 'state_transition' -and $bossIds -contains $row.entity.uuid -and $null -ne $row.state.getCurrentPhase){
                    $lastPhase=[string]$row.state.getCurrentPhase
                    if($lastPhase -eq 'BEAM' -and -not $paused){
                        if(Set-Tactic $false 'snow_beam_pause_native_offense'){
                            $paused=$true
                            $beamDeadline=[DateTime]::UtcNow.AddSeconds($BeamTimeoutSeconds)
                        }
                    }
                }
                $nativeBreath=$row.event -eq 'damage_post' -and $row.victim.role -eq 'fixture_player' -and
                    $row.damageType -eq 'twilightforest:chilling_breath' -and $bossIds -contains $row.owner.uuid
                $accepted=$nativeBreath -and ([double]$row.damageHp -gt 0 -or [double]$row.reductionsHp.ABSORPTION -gt 0)
                if($accepted){
                    Write-Journal 'snow_chilling_breath_accepted' @{tick=$row.tick; attemptId=$row.attemptId; damageHp=$row.damageHp; absorptionHp=$row.reductionsHp.ABSORPTION; nativeCallers=$row.nativeCallers}
                    if($paused){[void](Set-Tactic $true 'snow_beam_resume_after_native_hit')}
                    $paused=$false;$finished=$true
                    break
                }
            }
        }
        if($paused -and [DateTime]::UtcNow -ge $beamDeadline){
            Write-Journal 'snow_beam_bounded_wait_expired_gap_retained' @{lastPhase=$lastPhase; seconds=$BeamTimeoutSeconds}
            [void](Set-Tactic $true 'snow_beam_resume_after_bounded_wait')
            $paused=$false;$finished=$true
        }
        if(-not $finished){Start-Sleep -Milliseconds $PollMilliseconds}
    }
    if(-not $finished){Write-Journal 'snow_beam_watcher_stopped_without_verified_hit' @{lastPhase=$lastPhase}}
} finally {
    if($null -ne $reader){$reader.Dispose()}
    # A later encounter must not inherit this watcher's pause. Restore only if the
    # control text is still exactly ours; preserve any subsequent external tactic.
    if($paused){[void](Set-Tactic $true 'snow_beam_resume_on_watcher_exit' $true)}
}
