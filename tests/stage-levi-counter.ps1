param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [ValidateRange(30,240)][int]$TimeoutSeconds=180
)
# Observe native RUSH windup, then choose ordinary sword pursuit. No state setters.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$journal=Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl'
$state=Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String
if(-not $state.running -or $state.boss -ne 'cataclysm:the_leviathan' -or @($state.bossUuids).Count -ne 1){throw 'An active single Leviathan encounter is required.'}
$identity=[string]$state.bossUuids[0]
$telemetry=@(Get-ChildItem -LiteralPath $EncounterDirectory -Filter '*-cataclysm_the_leviathan-netherite_max.telemetry.jsonl')
if($telemetry.Count -ne 1){throw 'Expected one Leviathan telemetry file.'}
$deadline=[DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
$reader=$null;$pending='';$latest=$null;$stunned=$false;$ownedText=$null;$reason='bounded_wait_expired'
function Choose([bool]$attack,[string]$why){
    $prior=(Read-SharedTestFile -LiteralPath $controls)
    if($null -ne $script:ownedText -and $prior -cne $script:ownedText){throw 'External tactic change detected; preserve it and stop this watcher.'}
    $mode=if($attack){'auto'}else{'ranged'}
    $range=if($attack){0}else{10}
    & (Join-Path $PSScriptRoot 'set-native-player-tactic.ps1') -EncounterDirectory $EncounterDirectory -Boss 'cataclysm:the_leviathan' -ExpectedUuid $identity -WeaponMode $mode -PreferredRange $range -AttackEnabled $attack -Reason $why
    $script:ownedText=(Read-SharedTestFile -LiteralPath $controls)
}
try{
    $reader=[IO.StreamReader]::new([IO.File]::Open($telemetry[0].FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)))
    while([DateTimeOffset]::UtcNow -lt $deadline){
        $now=Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String
        if(-not $now.running -or $now.boss -ne 'cataclysm:the_leviathan' -or $now.bossUuids[0] -ne $identity){$reason='encounter_ended_or_changed';break}
        $pending+=$reader.ReadToEnd();$end=$pending.LastIndexOf("`n")
        if($end -ge 0){
            $complete=$pending.Substring(0,$end);$pending=$pending.Substring($end+1)
            foreach($line in ($complete -split "`n")){
                if($line -notlike '*"event":"state_transition"*'){continue}
                $row=$line | ConvertFrom-Json -DateKind String
                if($row.entity.uuid -ne $identity){continue}
                $latest=$row
                if($row.state.animation -eq 'LEVIATHAN_STUN'){$stunned=$true}
            }
        }
        if($stunned){$reason='native_stun_observed';Choose $true 'Native STUN observed; resume continuous ordinary offense';break}
        if($null -ne $latest){
            $rush=$latest.state.animation -eq 'LEVIATHAN_RUSH'
            Choose $rush $(if($rush){'Native RUSH observed; normal sword pursuit and existing windup anchor exemption'}else{'Bounded native counter attempt: ordinary ranged spacing10 without deliberate attacks until RUSH'})
        }
        Start-Sleep -Milliseconds 250
    }
} finally {
    if($null -ne $reader){$reader.Dispose()}
    # Restore only our own exact choice; never overwrite later manual steering.
    $current=(Read-SharedTestFile -LiteralPath $controls)
    if($null -ne $ownedText -and $current -ceq $ownedText){
        $choice=$current | ConvertFrom-Json -AsHashtable -DateKind String
        $choice.enabled=$true;$choice.weaponMode='auto';$choice.preferredRange=0;$choice.attackEnabled=$true;$choice.guardEnabled=$false;$choice.activateEmp=$false
        $text=$choice | ConvertTo-Json -Compress
        if($text -cne $current){
            $temporary=$controls+'.levi-exit.tmp';[IO.File]::WriteAllText($temporary,$text,[Text.UTF8Encoding]::new($false));[IO.File]::Move($temporary,$controls,$true)
        }
    }
    [ordered]@{utc=[DateTimeOffset]::UtcNow.ToString('o');run=Split-Path $EncounterDirectory -Leaf;boss='cataclysm:the_leviathan';bossUuid=$identity;action='bounded_native_counter_watcher_exit';reason=$reason;nativeStunObserved=$stunned;lastObservedTick=$latest.tick;controlPreservedOrRestored=(Read-SharedTestFile -LiteralPath $controls)} | ConvertTo-Json -Depth 5 -Compress | Add-Content -LiteralPath $journal
}
