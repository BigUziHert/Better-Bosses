#Requires -Version 7.5
[CmdletBinding()]
param(
    [string]$InputDirectory,
    [string]$OutputDirectory,
    [string]$ClientLog,
    [switch]$IncludeUntimedClientRecords
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
if(-not $InputDirectory){
    $latest=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'results/full-fights') -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if(-not $latest){throw 'No full-fight results exist'}
    $InputDirectory=$latest.FullName
}
$InputDirectory=(Resolve-Path -LiteralPath $InputDirectory).Path
if(-not $OutputDirectory){$OutputDirectory=Join-Path $InputDirectory 'export'}
if(-not $ClientLog){$ClientLog=Join-Path $repo 'run-client-playtest/client-fight-events.jsonl'}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$encounters=[Collections.Generic.List[object]]::new()
$timeline=[Collections.Generic.List[object]]::new()
$parseWarnings=0
function ItemCount($inventory,[string]$item){
    $total=0
    foreach($stack in $inventory){if($stack.item -eq $item){$total+=[int]$stack.count}}
    return $total
}
function JsonCompact($value){return ConvertTo-Json -InputObject $value -Depth 12 -Compress}
function ParseUtc([string]$value){return [DateTimeOffset]::Parse(($value -replace '(\.\d{7})\d+Z$','$1Z'))}
foreach($file in (Get-ChildItem -LiteralPath $InputDirectory -File -Filter '*.jsonl' | Sort-Object Name)){
    $state=@{boss='';gear='';mode='';outcome='preparing';tick=0;start='';finish='';hp=$null;food=$null;bossHp=$null;bossMax=$null;maxPlayerHp=0;initialBossHp=$null;damageTaken=0d;damageDealt=0d;maxHit=0d;hitsTaken=0;hitsDealt=0;heals=0;lethal=$false;phases=0;errors=0;foodMin=64;arrowsMin=256;lastInventory=@();setup=@();damageTypes=@{};samples=0;blockingSamples=0}
    Get-Content -LiteralPath $file.FullName | ForEach-Object {
        try{$row=ConvertFrom-Json -InputObject $_ -AsHashtable -DateKind String}catch{$parseWarnings++;return}
        $kind=[string]$row.record
        $state.boss=$row.boss;$state.gear=$row.gear;$state.mode=$row.controllerMode;$state.tick=[int]$row.tick
        if($row.ContainsKey('playerHP')){$state.hp=$row.playerHP;$state.food=$row.food;$state.maxPlayerHp=[Math]::Max($state.maxPlayerHp,[double]$row.playerMaxHP)}
        if($row.bosses.Count -gt 0){$state.bossHp=($row.bosses | Measure-Object -Property hp -Sum).Sum;$state.bossMax=($row.bosses | Measure-Object -Property maxHP -Sum).Sum}
        if($kind -eq 'started'){$state.start=$row.timestamp;$state.outcome='in_progress';$state.initialBossHp=$state.bossHp;$state.setup=$row.setup}
        if($kind -eq 'player_lethal_event'){$state.lethal=$true}
        if($kind -eq 'player_damage'){
            $damage=[double]$row.damage;$state.damageTaken+=$damage;$state.hitsTaken++;$state.maxHit=[Math]::Max($state.maxHit,$damage)
            $type=[string]$row.damageType;if(-not $state.damageTypes.ContainsKey($type)){$state.damageTypes[$type]=0d};$state.damageTypes[$type]+=$damage
        }
        if($kind -eq 'boss_damage'){$state.damageDealt+=[double]$row.damage;$state.hitsDealt++}
        if($kind -eq 'native_heal' -and $row.entity -eq 'minecraft:player' -and -not $row.cancelled){$state.heals++}
        if($kind -eq 'native_phase_change'){$state.phases++}
        if($kind -eq 'controller_error'){$state.errors++}
        if($kind -eq 'sample'){$state.samples++;if($row.blocking){$state.blockingSamples++}}
        if(-not $state.lethal -and $row.ContainsKey('playerHP') -and [double]$row.playerHP -gt 0 -and $row.inventory.Count -gt 0){
            $state.foodMin=[Math]::Min($state.foodMin,(ItemCount $row.inventory 'minecraft:cooked_beef'))
            $state.arrowsMin=[Math]::Min($state.arrowsMin,(ItemCount $row.inventory 'minecraft:arrow'))
            $state.lastInventory=$row.inventory
        }
        if($kind -eq 'finished'){$state.outcome=$row.outcome;$state.finish=$row.timestamp}
        if($kind -in @('started','native_phase_change','player_damage','player_lethal_event','boss_death_event','controller_error','finished') -or ($kind -eq 'sample' -and [int]$row.tick%100 -eq 0)){
            $detail=switch($kind){
                'native_phase_change' {JsonCompact $row.states}
                'player_damage' {"$($row.damageType): $($row.damage) HP event amount"}
                'player_lethal_event' {"cancelled_by_mod=$($row.cancelledByMod); source=$($row.source)"}
                'boss_death_event' {"$($row.entity); cancelled=$($row.cancelled)"}
                'controller_error' {$row.error}
                'finished' {"$($row.outcome): $($row.reason)"}
                'started' {($row.loadout -join ', ')}
                default {''}
            }
            $timeline.Add([pscustomobject][ordered]@{case=$file.BaseName;boss=$row.boss;loadout=$row.gear;control_mode=$row.controllerMode;tick=$row.tick;game_seconds=$row.gameSeconds;utc=$row.timestamp;event=$kind;player_hp=$state.hp;food=$state.food;boss_hp_total=$state.bossHp;detail=$detail})
        }
    }
    $durability=@{}
    foreach($stack in $state.lastInventory){if([int]$stack.maxDamage -gt 0){$durability[[string]$stack.item]=[int]$stack.damage}}
    $encounters.Add([pscustomobject][ordered]@{
        case=$file.BaseName;boss=$state.boss;loadout=$state.gear;control_mode=$state.mode;outcome=$state.outcome
        ticks=$state.tick;game_seconds=$state.tick/20d;started_utc=$state.start;finished_utc=$state.finish
        player_hp_final=$state.hp;player_max_hp_observed=$state.maxPlayerHp;food_final=$state.food
        boss_hp_initial=$state.initialBossHp;boss_hp_final=$state.bossHp;boss_max_hp_total=$state.bossMax
        incoming_damage_event_total_hp=[Math]::Round($state.damageTaken,4);largest_damage_event_hp=[Math]::Round($state.maxHit,4);incoming_damage_events=$state.hitsTaken
        boss_damage_event_total_hp=[Math]::Round($state.damageDealt,4);boss_damage_events=$state.hitsDealt;native_player_heal_events=$state.heals
        lethal_event_observed=$state.lethal;phase_state_changes=$state.phases;controller_errors=$state.errors
        native_server_samples=$state.samples;native_blocking_samples=$state.blockingSamples
        steak_net_used_before_lethal=64-$state.foodMin;arrows_net_used_before_lethal=256-$state.arrowsMin
        last_live_durability_damage=(JsonCompact $durability);incoming_damage_types=(JsonCompact $state.damageTypes)
    })
}
$timeline | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'server-timeline.csv') -NoTypeInformation -Encoding utf8

$clientTimeline=[Collections.Generic.List[object]]::new()
$barStats=@{};$clientCounts=@{};$lastBarSet='';$clientRows=0;$barObservationErrors=[Collections.Generic.List[string]]::new()
$untimedExcluded=0;$windowExcluded=0;$clientCases=@{};$windows=@()
foreach($encounter in $encounters){
    if($encounter.started_utc){$windows+=@{case=$encounter.case;start=(ParseUtc $encounter.started_utc);end=$(if($encounter.finished_utc){ParseUtc $encounter.finished_utc}else{[DateTimeOffset]::UtcNow})}}
    $clientCases[$encounter.case]=@{melee=0;bow=0;eat=0;counter=0;shield=0;defenseBowRelease=0;combo=0;barNames=[Collections.Generic.HashSet[string]]::new();badBars=0;chunkMissing=0;physicsSamples=0;returns=0}
}
$windowStart=if($windows.Count){($windows.start | Sort-Object | Select-Object -First 1).AddSeconds(-2)}else{$null}
$windowEnd=if($windows.Count){($windows.end | Sort-Object | Select-Object -Last 1).AddSeconds(3)}else{$null}
if(Test-Path -LiteralPath $ClientLog){
    Get-Content -LiteralPath $ClientLog | ForEach-Object {
        try{$row=ConvertFrom-Json -InputObject $_ -AsHashtable -DateKind String}catch{$parseWarnings++;return}
        $case='';$clientUtc=$null
        if($row.utc){
            $clientUtc=ParseUtc $row.utc
            if($windowStart -and ($clientUtc -lt $windowStart -or $clientUtc -gt $windowEnd)){$windowExcluded++;return}
            foreach($window in $windows){if($clientUtc -ge $window.start -and $clientUtc -le $window.end){$case=$window.case;break}}
        }elseif(-not $IncludeUntimedClientRecords){$untimedExcluded++;return}
        $clientRows++;$kind=[string]$row.event
        if(-not $clientCounts.ContainsKey($kind)){$clientCounts[$kind]=0};$clientCounts[$kind]++
        if($case){
            $caseStats=$clientCases[$case]
            switch($kind){'melee_input'{$caseStats.melee++};'bow_release_input'{$caseStats.bow++};'eat_input'{$caseStats.eat++};'projectile_counter_input'{$caseStats.counter++};'defense_shield_input'{$caseStats.shield++};'defense_bow_release_input'{$caseStats.defenseBowRelease++};'arena_return_start'{$caseStats.returns++}}
            if($kind -eq 'sample'){$caseStats.combo=[Math]::Max($caseStats.combo,[int]$row.nativeCombatInputState.getComboCount);if($row.physics){$caseStats.physicsSamples++;if(-not $row.physics.chunkLoaded){$caseStats.chunkMissing++}}}
        }
        $bars=@();if($kind -eq 'received_boss_overlay'){$bars=$row.bars}elseif($kind -eq 'sample'){$bars=$row.receivedBossBars}
        foreach($bar in $bars){
            if($bar.observationError){$barObservationErrors.Add([string]$bar.observationError);continue}
            if($case){$null=$clientCases[$case].barNames.Add([string]$bar.name);if(-not $bar.finiteAndInRange){$clientCases[$case].badBars++}}
            $key=[string]$bar.uuid
            if(-not $barStats.ContainsKey($key)){$barStats[$key]=[ordered]@{uuid=$key;name=$bar.name;first_client_tick=$row.clientTick;last_client_tick=$row.clientTick;samples=0;min_progress=1d;max_progress=0d;last_progress=0d;invalid_progress_samples=0;color=$bar.color;overlay=$bar.overlay}}
            $b=$barStats[$key];$b.samples++;$b.last_client_tick=$row.clientTick;$b.last_progress=$bar.progress
            if($bar.finiteAndInRange){$b.min_progress=[Math]::Min($b.min_progress,[double]$bar.progress);$b.max_progress=[Math]::Max($b.max_progress,[double]$bar.progress)}else{$b.invalid_progress_samples++}
        }
        $barSet=(@($bars | ForEach-Object {$_.uuid} | Sort-Object) -join ',')
        $barChanged=$kind -eq 'received_boss_overlay' -and $barSet -ne $lastBarSet
        if($kind -eq 'received_boss_overlay'){$lastBarSet=$barSet}
        if($kind -in @('new_target','native_respawn_request','native_rune_mining_input','client_rune_removed','weakpoint_observation_error','arena_anchor','arena_return_start','arena_return_end') -or $barChanged -or ($kind -eq 'sample' -and [long]$row.clientTick%100 -eq 0)){
            $detail=if($barChanged){JsonCompact $bars}elseif($kind -eq 'sample'){JsonCompact $row.nativeCombatInputState}else{JsonCompact $row}
            $clientTimeline.Add([pscustomobject][ordered]@{case=$case;utc=$row.utc;client_tick=$row.clientTick;client_seconds=[long]$row.clientTick/20d;event=$kind;boss=$row.boss;health=$row.health;food=$row.food;detail=$detail})
        }
    }
    $clientTimeline | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'client-timeline.csv') -NoTypeInformation -Encoding utf8
    @($barStats.Values | ForEach-Object {[pscustomobject]$_} | Sort-Object first_client_tick) | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'client-bars.csv') -NoTypeInformation -Encoding utf8
}
foreach($encounter in $encounters){
    $stats=$clientCases[$encounter.case]
    $encounter | Add-Member -NotePropertyMembers ([ordered]@{client_melee_inputs=$stats.melee;client_bow_release_inputs=$stats.bow;client_eat_inputs=$stats.eat;client_counter_inputs=$stats.counter;client_shield_raise_inputs=$stats.shield;client_defense_bow_release_inputs=$stats.defenseBowRelease;client_max_observed_combo=$stats.combo;client_bar_names=(@($stats.barNames) -join '; ');client_invalid_bar_observations=$stats.badBars;client_missing_chunk_samples=$stats.chunkMissing;client_physics_samples=$stats.physicsSamples;client_arena_return_events=$stats.returns})
}
$encounters | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'encounters.csv') -NoTypeInformation -Encoding utf8
$metadata=[ordered]@{
    generated_utc=[DateTimeOffset]::UtcNow.ToString('o');encounter_files=$encounters.Count;client_log_rows=$clientRows;skipped_incomplete_or_invalid_json_lines=$parseWarnings
    outcomes=@($encounters | Group-Object outcome | ForEach-Object {[ordered]@{outcome=$_.Name;count=$_.Count}})
    client_event_counts=$clientCounts
    client_overlay_observation_error_count=$barObservationErrors.Count
    client_overlay_observation_errors=@($barObservationErrors | Select-Object -Unique)
    excluded_untimed_client_rows=$untimedExcluded;excluded_client_rows_outside_encounter_window=$windowExcluded
    scope=@(
        'Automated inputs, not a human playthrough. Loss, downing, stall, time limit and controller error are not passing encounters.'
        'Client records carrying UTC are matched to server encounter intervals. Untimed client records are excluded unless explicitly requested, and are never assigned to an encounter by guessing.'
        'Incoming damage event totals are native event amounts, not net HP loss; healing and death replacement can change net HP.'
        'Diamond: plain diamond armor, diamond sword, plain bow. Netherite_p4: Protection IV netherite armor, netherite sword, Power V bow. These are loadout comparisons, not isolated armor comparisons.'
        'Food/ammunition counts are observed net inventory use before a lethal event; recovered arrows can reduce net use. Durability is the last live observation before death inventory drops.'
        'Native blocking samples report server isBlocking at one-second intervals, not blocked-hit counts. Shield input counts are requests, not proof of successful blocks; inspect native blocking and shield durability together.'
        'Client bars are real received/registered events. Screenshots are still required to assess custom rendering, HUD placement and clipping. The client log can span multiple encounter queues.'
        'Natural attack selection in these runs does not establish every move or phase was reached. Separate controlled move/phase regressions provide that coverage.'
    )
}
$metadata | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary-metadata.json') -Encoding utf8
$report=[Collections.Generic.List[string]]::new()
$report.Add('Automated fight results. Each row is a recorded encounter, including unsuccessful and unfinished outcomes. Timings are game time.')
$report.Add('')
$report.Add('| Boss | Loadout | Outcome | Seconds | Player HP | Boss HP remaining | Largest incoming event |')
$report.Add('|---|---|---|---:|---:|---:|---:|')
foreach($entry in $encounters){$report.Add("| $($entry.boss) | $($entry.loadout) | $($entry.outcome) | $($entry.game_seconds) | $($entry.player_hp_final) | $($entry.boss_hp_final) / $($entry.boss_max_hp_total) | $($entry.largest_damage_event_hp) |")}
$report.Add('')
foreach($note in $metadata.scope){$report.Add($note);$report.Add('')}
$report | Set-Content -LiteralPath (Join-Path $OutputDirectory 'fight-summary.md') -Encoding utf8
Write-Output ([pscustomobject]@{encounters=$encounters.Count;client_rows=$clientRows;skipped_json_lines=$parseWarnings;output=$OutputDirectory}|ConvertTo-Json -Compress)
