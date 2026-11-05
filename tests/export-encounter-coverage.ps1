#Requires -Version 7.5
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string[]]$InputDirectories,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$PriorReportDirectory=(Join-Path $PSScriptRoot 'reports/1.2.1'),
    [string]$NativeInputRetryLog=(Join-Path $PSScriptRoot 'evidence/victory-native-input-retries.jsonl'),
    [string]$SourceAdjudications=(Join-Path $PSScriptRoot 'victory-coverage-adjudications.json'),
    [string]$ClientLog
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'shared-evidence-io.ps1')
$repo=Split-Path $PSScriptRoot -Parent
if(-not $ClientLog){$ClientLog=Join-Path $repo 'run-client-playtest/client-fight-events.jsonl'}
$health=(Get-Content (Join-Path $repo 'config/survival-boss-balance.json') -Raw | ConvertFrom-Json -DateKind String).healthTargets
$bosses=@($health.PSObject.Properties.Name | Sort-Object)
$prior=@(Import-Csv (Join-Path $PriorReportDirectory 'all-native-scenarios.csv'))
$priorProfiles=@(Import-Csv (Join-Path $PriorReportDirectory 'approved-damage-comparison.csv'))
$priorManifest=Get-Content (Join-Path $PriorReportDirectory 'test-run-manifest.json') -Raw|ConvertFrom-Json -DateKind String
$runtimeByQueue=@{}
$runtimePath=Join-Path $OutputDirectory 'runtime-provenance.json'
if(Test-Path -LiteralPath $runtimePath){
    $runtime=Get-Content -LiteralPath $runtimePath -Raw|ConvertFrom-Json -DateKind String
    foreach($record in @($runtime.qualifyingNativeWins)+@($runtime.supersededExecutions)+@($runtime.currentExecution)){if($record.queue){$runtimeByQueue[$record.queue]=$record}}
}
$flightByAttempt=@{}
$flightAuditPath=Join-Path $OutputDirectory 'client-flight-audit.json'
if(Test-Path -LiteralPath $flightAuditPath){
    $flightAudit=Get-Content -LiteralPath $flightAuditPath -Raw|ConvertFrom-Json -DateKind String
    foreach($row in $flightAudit.summaries){$flightByAttempt[$row.attempt]=$row}
}
$adjudications=@{}
$additionalCandidates=@()
if(Test-Path -LiteralPath $SourceAdjudications){
    $sourceReview=Get-Content -LiteralPath $SourceAdjudications -Raw|ConvertFrom-Json -DateKind String
    foreach($entry in $sourceReview.entries){$key="$($entry.boss)|$($entry.key)";if($adjudications.ContainsKey($key)){throw "Duplicate source adjudication: $key"};$adjudications[$key]=$entry}
    $additionalCandidates=@($sourceReview.additionalCandidates|Where-Object {$_})
}
$files=@($InputDirectories|ForEach-Object{Get-ChildItem -LiteralPath $_ -File -Filter '*.telemetry.coverage.json'}|Sort-Object FullName -Unique)
$attempts=[Collections.Generic.List[object]]::new()
$entries=@{}
$damageCallers=[Collections.Generic.List[object]]::new()
$damageRows=[Collections.Generic.List[object]]::new()
$phaseRows=[Collections.Generic.List[object]]::new()
$intervals=[Collections.Generic.List[object]]::new()
$openLogs=[Collections.Generic.List[string]]::new()
$openBosses=@{}
foreach($directory in $InputDirectories){foreach($file in Get-ChildItem -LiteralPath $directory -File -Filter '*.telemetry.jsonl'){
    if(-not(Test-Path -LiteralPath ($file.FullName -replace '\.jsonl$','.coverage.json'))){
        $openLogs.Add($file.Directory.Name+'/'+$file.Name)
        $first=Get-SharedEvidenceFirstLine $file.FullName
        if($first){$started=$first|ConvertFrom-Json -DateKind String;if($started.boss){$openBosses[$started.boss]=$true}}
    }
}}
foreach($file in $files){
    $data=Get-SharedEvidenceText $file.FullName|ConvertFrom-Json -DateKind String
    if($data.boss -notin $bosses){throw "Unexpected managed boss in $($file.Name): $($data.boss)"}
    $attempt=$file.Directory.Name+'/'+($file.Name -replace '\.telemetry\.coverage\.json$','')
    $ordinaryLog=$file.FullName -replace '\.telemetry\.coverage\.json$','.jsonl'
    $begun=Get-SharedEvidenceLines $ordinaryLog|Where-Object {$_ -match '"record"\s*:\s*"started"'}|Select-Object -First 1|ConvertFrom-Json -DateKind String
    $ended=Get-SharedEvidenceLines $ordinaryLog|Select-Object -Last 1|ConvertFrom-Json -DateKind String
    if($ended.record -ne 'finished'){
        $unclosed=$file.Directory.Name+'/'+($file.Name -replace '\.coverage\.json$','.jsonl')
        if(-not $openLogs.Contains($unclosed)){$openLogs.Add($unclosed)}
        $openBosses[$data.boss]=$true
        continue
    }
    $deaths=@(Get-SharedEvidenceLines $ordinaryLog|Where-Object {$_ -match '"record"\s*:\s*"boss_death_event"'}|ConvertFrom-Json -DateKind String|Where-Object cancelled -EQ $false)
    $expectedBosses=@($begun.bosses).Count
    $nativeDeathWitness=($expectedBosses -gt 0 -and $deaths.Count -ge $expectedBosses -and $ended.playerHP -gt 0 -and @($ended.bosses|Where-Object hp -GT 0).Count -eq 0)
    if($begun.timestamp -and $ended.record -eq 'finished'){$intervals.Add([pscustomobject]@{attempt=$attempt;queue=$file.Directory.Name;boss=$data.boss;start=[DateTimeOffset]::Parse($begun.timestamp);finish=[DateTimeOffset]::Parse($ended.timestamp)})}
    $attempts.Add([pscustomobject]@{attempt=$attempt;queue=$file.Directory.Name;boss=$data.boss;outcome=$data.outcome;ticks=$data.ticks;telemetry_schema=if($data.schemaVersion){$data.schemaVersion}else{1};exact_dragon_phase_observer=($data.exactDragonPhaseObserver -eq $true);native_defeat=($data.naturalDefeatObserved -eq $true -and $data.outcome -eq 'won' -and $nativeDeathWitness);expected_boss_count=$expectedBosses;uncancelled_native_death_events=$deaths.Count;native_death_witness=$nativeDeathWitness;final_player_hp=$ended.playerHP;final_boss_hp=(@($ended.bosses.hp) -join '; ');observation_errors=@($data.observationErrors).Count;missing_candidates=@($data.missingRequiredCandidates).Count;event_count=($data.counts.PSObject.Properties.Value|Measure-Object -Sum).Sum})
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName catalogue_version -NotePropertyValue $(if($data.catalogueVersion){$data.catalogueVersion}else{'earlier_schema'})
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName final_armor -NotePropertyValue $ended.armor
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName final_armor_pieces -NotePropertyValue @($ended.inventory|Where-Object {$_.slot -ge 36 -and $_.slot -le 39 -and $_.count -gt 0}).Count
    foreach($field in @{native_knockdowns='nativeKnockdowns';native_self_revivals='nativeRevivals';player_lethal_events='playerLethalEvents';enchanted_apples_eaten='enchantedApplesEaten'}.GetEnumerator()){$attempts[$attempts.Count-1]|Add-Member -NotePropertyName $field.Key -NotePropertyValue $ended.($field.Value)}
    $inventoryRecorded=$begun.PSObject.Properties.Name-contains'inventory' -and $ended.PSObject.Properties.Name-contains'inventory'
    $countersRecorded=$ended.PSObject.Properties.Name-contains'enchantedApplesEaten' -and $ended.PSObject.Properties.Name-contains'nativeRevivals'
    $appleStart=if($inventoryRecorded){[int](($begun.inventory|Where-Object item -EQ 'minecraft:enchanted_golden_apple'|Measure-Object count -Sum).Sum)}else{$null}
    $appleEnd=if($inventoryRecorded){[int](($ended.inventory|Where-Object item -EQ 'minecraft:enchanted_golden_apple'|Measure-Object count -Sum).Sum)}else{$null}
    $appleSpent=if($inventoryRecorded){$appleStart-$appleEnd}else{$null}
    $otherChange=if($inventoryRecorded-and$countersRecorded){$appleSpent-[int]$ended.enchantedApplesEaten-[int]$ended.nativeRevivals}else{$null}
    $accounting=if(-not$inventoryRecorded){'missing_inventory_snapshot'}elseif(-not$countersRecorded){'missing_event_counters'}elseif($otherChange-eq0){'reconciled_food_and_self_revival'}else{'additional_inventory_change_requires_review'}
    foreach($field in @{enchanted_apples_inventory_start=$appleStart;enchanted_apples_inventory_end=$appleEnd;enchanted_apples_inventory_spent=$appleSpent;enchanted_apples_food_finish_events=$ended.enchantedApplesEaten;enchanted_apples_other_inventory_change=$otherChange;apple_accounting_status=$accounting}.GetEnumerator()){$attempts[$attempts.Count-1]|Add-Member -NotePropertyName $field.Key -NotePropertyValue $field.Value}
    $execution=$runtimeByQueue[$file.Directory.Name]
    foreach($field in @{production_sha256=$execution.productionSha256;helper_sha256=$execution.helperSha256;pilot_source_sha256=$execution.pilotSourceSha256}.GetEnumerator()){$attempts[$attempts.Count-1]|Add-Member -NotePropertyName $field.Key -NotePropertyValue $field.Value}
    $flight=$flightByAttempt[$attempt]
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName client_movement_audit -NotePropertyValue $(if($flight){$flight.assessment}elseif($attempts[$attempts.Count-1].native_defeat){'not_audited'}else{'not_applicable_no_native_win'})
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName client_nonpassenger_flying_samples -NotePropertyValue $flight.nonPassengerFlyingSamples
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName client_movement_audit_samples -NotePropertyValue $flight.samples
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName raw_missing_candidates -NotePropertyValue @($data.missingRequiredCandidates).Count
    $attempts[$attempts.Count-1].missing_candidates=@($data.missingRequiredCandidates|Where-Object {$a=$adjudications["$($data.boss)|$_"];-not $a -or $a.requiredCandidate}).Count
    foreach($entry in $data.entries){
        $key="$($data.boss)|$($entry.key)"
        if(-not $entries.ContainsKey($key)){$entries[$key]=[ordered]@{boss=$data.boss;key=$entry.key;category=$entry.category;required_candidate=[bool]$entry.requiredCandidate;origin=$entry.origin;observations=0;observed_in_winning_attempt=$false;attempts=[Collections.Generic.List[string]]::new()}}
        $row=$entries[$key];$row.observations+=[long]$entry.observations
        if($entry.observations -gt 0){$row.attempts.Add($attempt);if($data.outcome -eq 'won' -and $data.naturalDefeatObserved -and $nativeDeathWitness){$row.observed_in_winning_attempt=$true}}
    }
    $extraCandidates=@($additionalCandidates|Where-Object boss -EQ $data.boss)
    $extraSeen=@{}
    foreach($candidate in $extraCandidates){
        $key="$($data.boss)|$($candidate.key)"
        if(-not $entries.ContainsKey($key)){$entries[$key]=[ordered]@{boss=$data.boss;key=$candidate.key;category='source_verified_damage_component';required_candidate=[bool]$candidate.requiredCandidate;source_requirement=$true;origin=$candidate.origin;observations=0;observed_in_winning_attempt=$false;attempts=[Collections.Generic.List[string]]::new()}}
        $extraSeen[$candidate.key]=0
    }
    foreach($caller in $data.nativeDamageCallers.PSObject.Properties){$damageCallers.Add([pscustomobject]@{attempt=$attempt;boss=$data.boss;native_caller=$caller.Name;post_event_observations=$caller.Value})}
    $logPath=$file.FullName -replace '\.coverage\.json$','.jsonl'
    $profileByAttempt=@{}
    foreach($line in Get-Content -LiteralPath $logPath){
        $event=$line|ConvertFrom-Json -DateKind String
        if($event.event -in @('state_transition','native_phase_assignment','native_action_entry','native_entity_event','player_effects_changed','player_vehicle_changed')){
            $eventState=if($event.state){$event.state|ConvertTo-Json -Depth 10 -Compress}elseif($event.event -eq 'player_effects_changed'){ConvertTo-Json -InputObject @($event.effects) -Depth 5 -Compress}elseif($event.event -eq 'player_vehicle_changed'){@{previousVehicle=$event.previousVehicle;vehicle=$event.vehicle}|ConvertTo-Json -Depth 5 -Compress}else{''}
            $phaseRows.Add([pscustomobject]@{attempt=$attempt;boss=$data.boss;tick=$event.tick;utc=$event.utc;evidence=$event.event;subject=$event.subject;native_class=$event.class;native_name=$event.nativeName;event_byte=$event.nativeEventByte;phase_id=$event.phaseId;phase_name=$event.phaseName;animation_frame=$event.animationFrame;state=$eventState})
        }
        if($event.event -eq 'damage_incoming_processed'){$profileByAttempt[[string]$event.attemptId]=@($event.processedBalanceProfiles.PSObject.Properties.Name)}
        if($event.event -ne 'damage_post' -or $event.victim.role -ne 'fixture_player'){continue}
        foreach($candidate in $extraCandidates){
            $connected=[double]$event.damageHp+[double]$event.reductionsHp.ABSORPTION+[double]$event.blockedHp
            if($connected -le 0 -or -not @($event.nativeCallers|Where-Object {$_.Contains($candidate.nativeCallerContains)}).Count){continue}
            if($candidate.damageType -and $event.damageType -ne $candidate.damageType){continue}
            if($null -ne $candidate.originalIncomingHp -and [Math]::Abs([double]$event.originalHp-[double]$candidate.originalIncomingHp) -gt 0.0001){continue}
            if($candidate.bossStateField -and -not @($event.bossContext|Where-Object {$_.state.($candidate.bossStateField) -eq $candidate.bossStateValue}).Count){continue}
            $extraSeen[$candidate.key]++
            $row=$entries["$($data.boss)|$($candidate.key)"];$row.observations++;$row.attempts.Add($attempt)
            if($data.outcome -eq 'won' -and $data.naturalDefeatObserved -and $nativeDeathWitness){$row.observed_in_winning_attempt=$true}
        }
        $context=if($event.bossContext){$event.bossContext|ConvertTo-Json -Depth 12 -Compress}else{'not_recorded_by_earlier_telemetry_schema'}
        $damageRows.Add([pscustomobject]@{
            attempt=$attempt;boss=$data.boss;tick=$event.tick;utc=$event.utc;attempt_id=$event.attemptId;result=$event.result
            damage_type=$event.damageType;direct_type=$event.direct.type;owner_type=$event.owner.type;processed_profiles=($profileByAttempt[[string]$event.attemptId] -join '; ')
            original_incoming_hp=$event.originalHp;health_damage_hp=$event.damageHp;absorption_spent_hp=$event.reductionsHp.ABSORPTION;blocked_hp=$event.blockedHp
            armor_reduction_hp=$event.reductionsHp.ARMOR;enchantment_reduction_hp=$event.reductionsHp.ENCHANTMENTS;effect_reduction_hp=$event.reductionsHp.MOB_EFFECTS
            hp_before=$event.hpBefore;hp_after=$event.hpAfter;absorption_before=$event.absorptionBefore;absorption_after=$event.absorptionAfter;armor=$event.armor;toughness=$event.toughness
            native_callers=$event.nativeCallers -join '; ';boss_context=$context
        })
    }
    $attempts[$attempts.Count-1]|Add-Member -NotePropertyName source_component_gaps -NotePropertyValue @($extraCandidates|Where-Object {$_.requiredCandidate -and $extraSeen[$_.key] -eq 0}).Count
    $attempts[$attempts.Count-1].missing_candidates+=$attempts[$attempts.Count-1].source_component_gaps
}
$moveRows=@(foreach($row in $entries.Values){
    $probe=''
    if($row.category -eq 'animation'){$probe=($row.key -replace '^state/animation=','')}
    elseif($row.category -eq 'attack_state'){$probe='state'+($row.key -replace '^state/getAttackState=','')+'_'+$row.origin}
    elseif($row.category -eq 'native_action_entry'){$probe=$row.key -replace '^bmd_action/',''}
    $examples=@()
    $profileExamples=@()
    if($probe){$rx='(^|/)'+[regex]::Escape($probe)+'($|/|_)';$examples=@($prior|Where-Object {$_.Boss -eq $row.boss -and $_.Move -match $rx -and $_.Status -in @('damage_observed','native_attack','mechanic','transition')}|Select-Object -First 4|ForEach-Object{"$($_.Move) [$($_.Status)]"})}
    if($row.category -eq 'processed_damage_profile'){$profile=$row.key -replace '^balance_profile/','';$profileExamples=@($priorProfiles|Where-Object {$_.profile -eq $profile -or $profile -eq 'maledictus.mace_rage' -and $_.profile -in @('maledictus.mace_min','maledictus.mace_max')})}
    $adjudication=$adjudications["$($row.boss)|$($row.key)"]
    $required=if($adjudication){[bool]$adjudication.requiredCandidate}else{$row.required_candidate}
    [pscustomobject]@{boss=$row.boss;key=$row.key;category=$row.category;required_candidate=$required;raw_required_candidate=if($row.source_requirement){$null}else{$row.required_candidate};source_added_requirement=($row.source_requirement -eq $true);source_adjudication=$adjudication.classification;adjudication_reason=$adjudication.reason;adjudication_source=$adjudication.source;origin=$row.origin;natural_observations=$row.observations;observed_in_winning_attempt=$row.observed_in_winning_attempt;natural_status=if($row.observations -gt 0){'observed'}elseif($required){'not_observed'}elseif($adjudication){'source_adjudicated_not_required'}else{'optional_or_lifecycle_not_observed'};observed_attempts=($row.attempts|Sort-Object -Unique)-join '; ';prior_controlled_examples=$examples -join '; ';prior_damage_profiles=$profileExamples.profile -join '; ';prior_measured_diamond_hearts=$profileExamples.measured_diamond_hearts -join '; ';prior_measured_netherite_p4_hearts=$profileExamples.measured_netherite_protection4_hearts -join '; ';prior_examples_are_natural_coverage=$false;prior_measurements_include_enchanted_apple_effects=$false}
})
$moveRows=@($moveRows|Sort-Object boss,category,key)
$bossRows=@(foreach($boss in $bosses){
    $own=@($attempts|Where-Object boss -EQ $boss);$moves=@($moveRows|Where-Object boss -EQ $boss);$required=@($moves|Where-Object required_candidate -EQ $true);$missing=@($required|Where-Object natural_status -EQ 'not_observed');$outsideWins=@($required|Where-Object {$_.natural_observations -gt 0 -and -not $_.observed_in_winning_attempt});$wins=@($own|Where-Object native_defeat -EQ $true)
    $expanded=@($own|Where-Object telemetry_schema -GE 2).Count -gt 0
    [pscustomobject]@{boss=$boss;attempts=$own.Count;native_wins=$wins.Count;defeated=($wins.Count -gt 0);open_attempt=$openBosses.ContainsKey($boss);expanded_catalogue_observed=$expanded;declared_candidates=$required.Count;candidates_not_observed=$missing.Count;candidates_only_seen_outside_winning_attempt=$outsideWins.Count;telemetry_errors=[int]($own|Measure-Object observation_errors -Sum).Sum;prior_controlled_scenarios=@($prior|Where-Object Boss -EQ $boss).Count;readiness=if(-not $own.Count -and $openBosses.ContainsKey($boss)){'in_progress'}elseif(-not $own.Count){'not_attempted'}elseif(-not $wins.Count){'native_defeat_pending'}elseif(-not $expanded){'defeated_earlier_catalogue_needs_review'}elseif($missing.Count){'defeated_natural_move_gaps_remain'}elseif($outsideWins.Count){'defeated_some_candidates_only_in_other_attempts'}else{'defeated_declared_candidates_seen_review_required'}}
})
$wins=@($bossRows|Where-Object defeated -EQ $true).Count
$errors=[int]($attempts|Measure-Object observation_errors -Sum).Sum
$missing=@($moveRows|Where-Object {$_.required_candidate -and $_.natural_status -eq 'not_observed'}).Count
$selectedQueues=@($InputDirectories|ForEach-Object{Split-Path (Resolve-Path -LiteralPath $_).Path -Leaf})
$retryRows=[Collections.Generic.List[object]]::new()
if(Test-Path -LiteralPath $NativeInputRetryLog){foreach($line in Get-SharedEvidenceLines $NativeInputRetryLog){
    $event=$line|ConvertFrom-Json -DateKind String
    if($event.queue -notin $selectedQueues){continue}
    $when=[DateTimeOffset]::Parse($event.utc)
    $match=@($intervals|Where-Object {$_.queue -eq $event.queue -and $when -ge $_.start -and $when -le $_.finish})
    $retryRows.Add([pscustomobject]@{source='logged ordinary operator input';utc=$event.utc;queue=$event.queue;attempt=($match.attempt -join '; ');boss=($match.boss -join '; ');within_completed_encounter=($match.Count -eq 1);action=$event.action;detail=$line})
}}
if(Test-Path -LiteralPath $ClientLog){$clientReader=Open-SharedEvidenceReader $ClientLog;try{while(($line=$clientReader.ReadLine()) -ne $null){
    if($line -notmatch '"event"\s*:\s*"native_apple_use_retry"'){continue}
    try{$event=$line|ConvertFrom-Json -DateKind String}catch{continue}
    if(-not $event.utc){continue};$when=[DateTimeOffset]::Parse($event.utc)
    $match=@($intervals|Where-Object {$when -ge $_.start -and $when -le $_.finish})
    if($match.Count -ne 1){continue}
    $retryRows.Add([pscustomobject]@{source='automatic native-input retry guard';utc=$event.utc;queue=$match[0].queue;attempt=$match[0].attempt;boss=$match[0].boss;within_completed_encounter=$true;action=$event.event;detail=$line})
}}finally{$clientReader.Dispose()}}
$outcomeCounts=[ordered]@{};foreach($group in $attempts|Group-Object outcome){$outcomeCounts[$group.Name]=$group.Count}
$manifest=[ordered]@{
    generatedUtc=[DateTimeOffset]::UtcNow.ToString('o');managedBosses=$bosses.Count;bossesWithNativeDefeat=$wins;completedTelemetryAttempts=$attempts.Count;unclosedTelemetryLogs=@($openLogs);observationErrors=$errors;declaredCandidatesNotObserved=$missing
    naturalObservationScope='Completed telemetry attempts. Unfinished native evidence remains in separately identified sidecars.'
    unfinishedNativeEvidenceSources=@(
        if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'unfinished-leviathan/stall-manifest.json')){'unfinished-leviathan/stall-manifest.json'}
        if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'harness-io-interruption.json')){'harness-io-interruption.json'}
    )
    all23NativeDefeats=($wins -eq 23);allMovesAndPhasesCertified=$false
    bossesWithoutExpandedCatalogue=@($bossRows|Where-Object expanded_catalogue_observed -NE $true|ForEach-Object boss)
    includedQueues=$selectedQueues;associatedNativeInputRetries=$retryRows.Count;encounterOutcomes=$outcomeCounts;controllerErrors=[int]$outcomeCounts['controller_error']
    recordedNativeKnockdowns=[int]($attempts|Measure-Object native_knockdowns -Sum).Sum;recordedNativeSelfRevivals=[int]($attempts|Measure-Object native_self_revivals -Sum).Sum
    appleAccountingReconciledAttempts=@($attempts|Where-Object apple_accounting_status -EQ 'reconciled_food_and_self_revival').Count
    appleAccountingReviewAttempts=@($attempts|Where-Object apple_accounting_status -NE 'reconciled_food_and_self_revival'|ForEach-Object attempt)
    nativeWinningAttemptsWithFlightFlagSamples=@($attempts|Where-Object {$_.native_defeat -and $_.client_nonpassenger_flying_samples -gt 0}).Count
    nativeWinningAttemptsWithoutMovementAudit=@($attempts|Where-Object {$_.native_defeat -and $_.client_movement_audit -in @('not_audited','no_client_samples','incomplete_physics_fields')}).Count
    registeredSourceAdjudications=$adjudications.Count;appliedSourceAdjudications=@($moveRows|Where-Object source_adjudication).Count;observedCandidatesWithDormantAdjudication=@($moveRows|Where-Object {$_.source_adjudication -like 'dormant*' -and $_.natural_observations -gt 0}|ForEach-Object {"$($_.boss)|$($_.key)"})
    registeredAdditionalSourceComponents=$additionalCandidates.Count;appliedAdditionalSourceComponents=@($moveRows|Where-Object source_added_requirement).Count
    priorControlledProductionSha256=$priorManifest.productionSha256;priorControlledAssertions=$priorManifest.assertions;priorControlledFailures=$priorManifest.failed
    scope='Natural deaths, selected moves, processed profiles and accepted/absorbed damage are distinct evidence. Prior controlled examples are labeled references, never substituted for natural observations. Missing candidates require another observation or explicit source adjudication; this generated ledger cannot certify every random trajectory or phase combination.'
}
New-Item -ItemType Directory -Path $OutputDirectory -Force|Out-Null
if(Test-Path -LiteralPath $SourceAdjudications){Copy-Item -LiteralPath $SourceAdjudications -Destination (Join-Path $OutputDirectory 'source-adjudications.json') -Force}
$attempts|Export-Csv (Join-Path $OutputDirectory 'attempts.csv') -NoTypeInformation
$attempts|Select-Object attempt,boss,outcome,enchanted_apples_inventory_start,enchanted_apples_inventory_end,enchanted_apples_inventory_spent,enchanted_apples_food_finish_events,native_self_revivals,enchanted_apples_other_inventory_change,apple_accounting_status|Export-Csv (Join-Path $OutputDirectory 'apple-accounting.csv') -NoTypeInformation
$moveRows|Export-Csv (Join-Path $OutputDirectory 'moves-and-phases.csv') -NoTypeInformation
$bossRows|Export-Csv (Join-Path $OutputDirectory 'boss-readiness.csv') -NoTypeInformation
$damageCallers|Export-Csv (Join-Path $OutputDirectory 'native-damage-callers.csv') -NoTypeInformation
$damageRows|Export-Csv (Join-Path $OutputDirectory 'native-player-damage.csv') -NoTypeInformation
$phaseRows|Export-Csv (Join-Path $OutputDirectory 'native-move-phase-timeline.csv') -NoTypeInformation
$retryRows|Export-Csv (Join-Path $OutputDirectory 'native-input-retries.csv') -NoTypeInformation
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'native-input-retries.json'),(ConvertTo-Json -InputObject @($retryRows) -Depth 7)+"`n",[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'coverage-manifest.json'),($manifest|ConvertTo-Json -Depth 7)+"`n",[Text.UTF8Encoding]::new($false))
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Native encounter coverage ledger');$lines.Add('');$lines.Add("**$wins of 23 bosses have a recorded native defeat** across $($attempts.Count) completed telemetry attempts. $missing declared move/phase/profile candidates remain unobserved in completed attempts. $($manifest.bossesWithoutExpandedCatalogue.Count) bosses lack the expanded schema-2 inventory, so their missing-candidate count is incomplete. Observation errors: $errors. Encounter controller errors: $($manifest.controllerErrors). Unclosed telemetry logs: $($openLogs.Count).")
$lines.Add('');$lines.Add('This is a readiness ledger, not a passing certificate. A win does not establish every move, and a selected animation does not prove its damage connected. [Every candidate](moves-and-phases.csv), [attempt outcomes](attempts.csv), [move/phase timeline](native-move-phase-timeline.csv), [native damage callers](native-damage-callers.csv) and [every player damage/absorption event](native-player-damage.csv) preserve the distinction. The timeline labels sampled states separately from exact native phase assignments and action-entry/event observations. The raw compact telemetry also contains incoming attempts without post events, shield resolution, effect changes, projectiles and their owners. Fully absorbed hits retain their actual absorption cost even when the player loses no HP.')
$lines.Add('');$lines.Add("Completed attempts record $($manifest.recordedNativeKnockdowns) native knockdowns and $($manifest.recordedNativeSelfRevivals) native self-revivals. The attempt CSV preserves those outcomes alongside final armor and remaining pieces.")
$lines.Add('');$lines.Add('The [apple accounting table](apple-accounting.csv) separates ordinary food-Finish events from the measured decrease between starting and ending inventory. Native self-revival uses an additional apple outside the food-Finish counter. Each row reconciles those events with the inventory decrease and flags any remaining difference for review. The existing enchanted_apples_eaten field retains its original food-event meaning.')
if($flightByAttempt.Count){$lines.Add('');$lines.Add("The [independent movement audit](client-flight-audit.md) flags $($manifest.nativeWinningAttemptsWithFlightFlagSamples) winning attempts with recorded non-passenger flight flags; $($manifest.nativeWinningAttemptsWithoutMovementAudit) winning attempts are not yet audited. Native death credit remains separate from normal-movement validity. The attempt CSV preserves each qualification and the audit sample count.")}
$lines.Add('');$lines.Add('| Boss | Native wins | Unobserved candidates | State |');$lines.Add('|---|---:|---:|---|')
foreach($row in $bossRows){$gapLabel=if($row.expanded_catalogue_observed){[string]$row.candidates_not_observed}else{'not inventoried'};$lines.Add("| $($row.boss) | $($row.native_wins) | $gapLabel | $($row.readiness) |")}
$lines.Add('');$lines.Add('“Not inventoried” means the expanded natural-encounter catalogue is unavailable, not that there are zero missing moves. The CSV retains its explicit expanded_catalogue_observed flag; a numeric zero without that flag must not be interpreted as complete coverage. Earlier-schema attempts remain labeled separately even when a later winning attempt supplies the expanded catalogue.')
$lines.Add('');$lines.Add("The prior controlled matrix contains $($priorManifest.assertions) passing assertions and $($priorManifest.armorMeasurements) armor measurements against production SHA-256 $($priorManifest.productionSha256). It covers staged native callbacks/phases and actual armor. Its examples are not proof that those moves occurred in these encounters, nor measurements of every enchanted-golden-apple/status combination. The earlier client samples remain historical and are not imported as new victories.")
$lines.Add('');$lines.Add('Conditional branches, non-damaging windups, dormant source entries and lifecycle states need source-based adjudication. Do not remove an unobserved damaging move merely because another animation uses the same helper. Do not classify an incoming event without a post event as a miss: native cancellation, shield handling and other exits differ, and some early native rejects emit no event at all.')
if($adjudications.Count -or $additionalCandidates.Count){$lines.Add('');$lines.Add('[Explicit source adjudications](source-adjudications.json) distinguish dormant fresh-spawn candidates from required moves and separate native components that share one damage profile. They also explain native-immunity gaps without removing those requirements: Hydra flame/mortar processing remains unobserved in the apple-equipped win because the installed damage types are fire-tagged. The CSV preserves raw requirement flags and marks source-added requirements, source reasoning and actual observations; raw telemetry is never rewritten. A dormant classification does not count as observing that move. Any contrary natural observation remains visible in the manifest for review. In particular, an Obsidilith death explosion cannot establish a falling-anvil impact merely because both use the same configured cap.')}
$lines.Add('');$lines.Add('[Ordinary input retries](native-input-retries.csv) retain logged operator releases and automatic apple-use retries within the selected encounter intervals. They are input actions, not health/cooldown resets or proof of item consumption. Each attempt ID includes its queue directory so a retry cannot be confused with an earlier attempt of the same boss.')
if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'observation-notes.md')){$lines.Add('');$lines.Add('[Encounter observations and limits](observation-notes.md) explain native wins, input-limited attempts and unresolved coverage without treating earlier controlled probes as new victories.')}
if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'unfinished-leviathan/stall-manifest.json')){$lines.Add('');$lines.Add('[Unfinished Leviathan evidence](unfinished-leviathan/README.md) preserves the server-stall endpoint, accepted RUSH counter into native STUN and corrected received bar transition. The completed-case CSVs exclude that unfinished case. Its observed STUN branch therefore has separate natural evidence while a STUN observation within a completed win remains outstanding.')}
if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'receipt-review-audit/independent-audit.json')){$lines.Add('');$lines.Add('[Independent receipt adjudication](receipt-review-audit/README.md) explains the native helmet step omitted from the named reduction sum, retains the historical bar defect and separately counts valid received values for production 1.2.1. Its source hashes and sampled scope accompany every conclusion.')}
if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'harness-io-interruption.json')){$lines.Add('');$lines.Add('[Ignis logging interruption](harness-io-interruption.md) records the unfinished endpoint and Windows file-sharing exception. Its native observations retain their scope. The [reader access audit](reader-access-audit.json) documents shared access and closed snapshots used by the report tools.')}
if(Test-Path -LiteralPath (Join-Path $OutputDirectory 'ignis-brand-netherite.md')){$lines.Add('');$lines.Add('[Supplemental Ignis Brand netherite probes](ignis-brand-netherite.md) add 36 controlled native cases and 504 passing assertions. They remain separate from natural move observations and native defeat counts.')}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'coverage-ledger.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{nativeDefeats=$wins;managedBosses=$bosses.Count;attempts=$attempts.Count;missingCandidates=$missing;errors=$errors;output=$OutputDirectory}|ConvertTo-Json -Compress
