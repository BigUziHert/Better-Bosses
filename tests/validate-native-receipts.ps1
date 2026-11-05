#Requires -Version 7.5
[CmdletBinding()]
param(
    [string[]]$InputDirectories,
    [string]$OutputDirectory=(Join-Path $PSScriptRoot 'reports/victory-validation/receipts'),
    [string]$ClientLog,
    [double]$ToleranceHp=0.001
)
# Offline observations only. No server commands, control-file writes or game mutations.
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
if(-not $ClientLog){$ClientLog=Join-Path $repo 'run-client-playtest/client-fight-events.jsonl'}
if(-not $InputDirectories){$InputDirectories=@(Get-ChildItem (Join-Path $PSScriptRoot 'results/full-fights') -Directory|Where-Object Name -GE '2026-11-05T04-36'|ForEach-Object FullName)}
$approvedPath=Join-Path $PSScriptRoot 'reports/1.2.0/approved-damage-comparison.csv'
$approved=@{};foreach($reference in Import-Csv -LiteralPath $approvedPath){$approved[$reference.profile]=$reference}
$details=[Collections.Generic.List[object]]::new()
$issues=[Collections.Generic.List[object]]::new()
$attempts=[Collections.Generic.List[object]]::new()
$intervals=[Collections.Generic.List[object]]::new()
$sources=[Collections.Generic.List[object]]::new()
$files=@($InputDirectories|ForEach-Object{Get-ChildItem -LiteralPath $_ -Filter '*.telemetry.coverage.json'}|Sort-Object FullName -Unique)
function Finite([double]$Value){return -not([double]::IsNaN($Value)-or[double]::IsInfinity($Value))}
function Add-Issue([string]$Attempt,[long]$Tick,[string]$Check,$Residual,[string]$Context){
    $issues.Add([pscustomobject]@{attempt=$Attempt;tick=$Tick;check=$Check;residual_hp=$Residual;context=$Context})
}
foreach($file in $files){
    $coverage=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json -DateKind String
    $primary=$file.FullName -replace '\.telemetry\.coverage\.json$','.jsonl'
    $telemetry=$file.FullName -replace '\.coverage\.json$','.jsonl'
    $ended=Get-Content -LiteralPath $primary -Tail 1|ConvertFrom-Json -DateKind String
    if($ended.record -ne 'finished'){continue}
    $attempt=$file.Directory.Name+'/'+($file.Name -replace '\.telemetry\.coverage\.json$','')
    $snapshots=[Collections.Generic.List[object]]::new();$start=$null
    foreach($line in [IO.File]::ReadLines($primary)){
        $sample=$line|ConvertFrom-Json -DateKind String
        if($sample.record -eq 'started'){$start=$sample.timestamp}
        if($null -eq $sample.inventory){continue}
        $pieces=@($sample.inventory|Where-Object {$_.slot -ge 36 -and $_.slot -le 39 -and $_.count -gt 0})
        $allNetherite=$pieces.Count -eq 4 -and @($pieces|Where-Object item -notlike 'minecraft:netherite_*').Count -eq 0
        $snapshots.Add([pscustomobject]@{tick=[int]$sample.tick;pieces=$pieces.Count;allNetherite=$allNetherite})
    }
    if($start){$intervals.Add([pscustomobject]@{attempt=$attempt;start=[DateTimeOffset]::Parse($start);end=[DateTimeOffset]::Parse($ended.timestamp)})}
    $incoming=@{};$effects=@();$effectsKnown=$false;$effectsTick=-1;$snapshotIndex=0;$firstDetail=$details.Count
    foreach($line in [IO.File]::ReadLines($telemetry)){
        if($line -notmatch '"event":"(damage_incoming_processed|damage_post|player_effects_changed)"'){continue}
        $event=$line|ConvertFrom-Json -DateKind String
        if($event.event -eq 'player_effects_changed'){$effects=@($event.effects);$effectsKnown=$true;$effectsTick=[int]$event.tick;continue}
        if($event.event -eq 'damage_incoming_processed'){$incoming[[string]$event.attemptId]=$event;continue}
        $processed=$incoming[[string]$event.attemptId]
        $profiles=if($processed){@($processed.processedBalanceProfiles.PSObject.Properties.Name)}else{@()}
        $profileLabel=if($profiles.Count){$profiles -join '; '}else{'unattributed_native_damage'}
        $context="attemptId=$($event.attemptId); victim=$($event.victim.type); profiles=$profileLabel"
        $numbers=@($event.originalHp,$event.amountAtObserver,$event.damageHp,$event.blockedHp,$event.hpAfter,$event.absorptionAfter)+@($event.reductionsHp.PSObject.Properties.Value)
        if($processed){$numbers+=@($processed.amountAtObserver,$processed.hpBefore,$processed.absorptionBefore)}
        $finite=@($numbers|Where-Object {-not(Finite ([double]$_))}).Count -eq 0
        if(-not $finite){Add-Issue $attempt $event.tick 'nonfinite_damage_value' $null $context}
        $sum=0.0;foreach($reduction in $event.reductionsHp.PSObject.Properties){$sum+=[double]$reduction.Value}
        $originalResidual=$null;$stageResidual=$null;$hpResidual=$null;$absResidual=$null
        if($processed -and $finite){
            $originalResidual=[double]$event.originalHp-[double]$processed.originalHp
            $stageResidual=[double]$processed.amountAtObserver-[double]$event.blockedHp-$sum-[double]$event.damageHp
            # Explicit double zero prevents PowerShell selecting Math.Max(int,int).
            $hpResidual=[double]$event.hpAfter-[Math]::Max([double]0,([double]$event.hpBefore-[double]$event.damageHp))
            $absResidual=[double]$event.absorptionAfter-[Math]::Max([double]0,([double]$event.absorptionBefore-[double]$event.reductionsHp.ABSORPTION))
            foreach($check in @(@('original_damage_identity',$originalResidual),@('processed_minus_reductions',$stageResidual),@('actual_health_delta',$hpResidual),@('actual_absorption_delta',$absResidual))){
                if([Math]::Abs([double]$check[1]) -gt $ToleranceHp){Add-Issue $attempt $event.tick $check[0] $check[1] $context}
            }
        }else{Add-Issue $attempt $event.tick 'missing_incoming_pair_or_nonfinite' $null $context}
        while($snapshotIndex+1 -lt $snapshots.Count -and $snapshots[$snapshotIndex+1].tick -le $event.tick){$snapshotIndex++}
        $gear=if($snapshots.Count -and $snapshots[$snapshotIndex].tick -le $event.tick){$snapshots[$snapshotIndex]}else{$null}
        $player=$event.victim.role -eq 'fixture_player'
        $effectLabel=if($player -and $effectsKnown){(@($effects|ForEach-Object {"$($_.id):$($_.amplifier)"}) -join '; ')}else{'not_observed'}
        $matches=@($profiles|ForEach-Object {$key=$_ -replace '\.cap$','';if($approved.ContainsKey($key)){$approved[$key]}})
        $details.Add([pscustomobject]@{
            attempt=$attempt;boss=$coverage.boss;outcome=$coverage.outcome;tick=$event.tick;utc=$event.utc;attempt_id=$event.attemptId;victim=$event.victim.type;fixture_player=$player
            processed_profiles=$profileLabel;damage_type=$event.damageType;original_incoming_hp=$event.originalHp
            processed_incoming_hp=if($processed){$processed.amountAtObserver}else{$null};health_damage_hp=$event.damageHp;absorption_spent_hp=$event.reductionsHp.ABSORPTION;blocked_hp=$event.blockedHp
            hp_before=$event.hpBefore;hp_after=$event.hpAfter;absorption_before=$event.absorptionBefore;absorption_after=$event.absorptionAfter
            armor=$event.armor;toughness=$event.toughness;armor_reduction_hp=$event.reductionsHp.ARMOR;enchantment_reduction_hp=$event.reductionsHp.ENCHANTMENTS;effect_reduction_hp=$event.reductionsHp.MOB_EFFECTS
            last_observed_full_netherite=($player -and $null -ne $gear -and $gear.allNetherite);equipment_snapshot_age_ticks=if($gear){[int]$event.tick-$gear.tick}else{$null}
            effects_last_observed=$effectLabel;effects_snapshot_tick=if($player){$effectsTick}else{$null}
            finite=$finite;original_residual_hp=$originalResidual;stage_residual_hp=$stageResidual;health_delta_residual_hp=$hpResidual;absorption_delta_residual_hp=$absResidual
            approved_reference_diamond_hearts=($matches.approved_diamond_hearts -join '; ');prior_measured_netherite_p4_hearts=($matches.measured_netherite_protection4_hearts -join '; ')
            reference_is_encounter_expectation=$false;native_callers=($event.nativeCallers -join '; ')
        })
    }
    $attempts.Add([pscustomobject]@{attempt=$attempt;boss=$coverage.boss;outcome=$coverage.outcome;posts=$details.Count-$firstDetail;apples_eaten=$ended.enchantedApplesEaten;knockdowns=$ended.nativeKnockdowns;revivals=$ended.nativeRevivals;final_hp=$ended.playerHP;final_armor=$ended.armor})
    $sources.Add([pscustomobject]@{attempt=$attempt;telemetry_sha256=(Get-FileHash -LiteralPath $telemetry -Algorithm SHA256).Hash;primary_sha256=(Get-FileHash -LiteralPath $primary -Algorithm SHA256).Hash})
}

$barSamples=0;$barValues=0;$barIssues=0;$badClientRows=0
$sorted=@($intervals|Sort-Object start);$intervalIndex=0
if($sorted.Count -and (Test-Path -LiteralPath $ClientLog)){
    foreach($line in [IO.File]::ReadLines([IO.Path]::GetFullPath($ClientLog))){
        if($line -notmatch '"event":"sample"' -or $line -notmatch '"receivedBossBars"'){continue}
        try{$sample=$line|ConvertFrom-Json -DateKind String;$when=[DateTimeOffset]::Parse($sample.utc)}catch{$badClientRows++;continue}
        while($intervalIndex -lt $sorted.Count -and $when -gt $sorted[$intervalIndex].end){$intervalIndex++}
        if($intervalIndex -ge $sorted.Count){break}
        $interval=$sorted[$intervalIndex]
        if($when -lt $interval.start){continue}
        $barSamples++
        foreach($bar in $sample.receivedBossBars){
            $barValues++;$value=[double]$bar.progress
            if(-not(Finite $value)-or$value -lt 0-or$value -gt 1){$barIssues++;Add-Issue $interval.attempt $sample.clientTick 'received_bar_not_finite_or_out_of_range' $value "clientUtc=$($sample.utc); name=$($bar.name); uuid=$($bar.uuid)"}
        }
    }
}
$profileRows=@(foreach($group in @($details|Where-Object fixture_player|Group-Object boss,processed_profiles)){
    $rows=@($group.Group);$first=$rows[0]
    [pscustomobject]@{
        boss=$first.boss;processed_profiles=$first.processed_profiles;posts=$rows.Count;attempts=@($rows.attempt|Sort-Object -Unique).Count
        last_observed_full_netherite_posts=@($rows|Where-Object last_observed_full_netherite).Count
        armor_min=($rows.armor|Measure-Object -Minimum).Minimum;armor_max=($rows.armor|Measure-Object -Maximum).Maximum
        hp_damage_total=($rows.health_damage_hp|Measure-Object -Sum).Sum;hp_damage_max=($rows.health_damage_hp|Measure-Object -Maximum).Maximum
        absorption_spent_total=($rows.absorption_spent_hp|Measure-Object -Sum).Sum;absorption_spent_max=($rows.absorption_spent_hp|Measure-Object -Maximum).Maximum
        blocked_hp_total=($rows.blocked_hp|Measure-Object -Sum).Sum
        last_observed_effect_sets=(@($rows.effects_last_observed|Sort-Object -Unique)-join ' | ')
        approved_reference_diamond_hearts=$first.approved_reference_diamond_hearts;prior_measured_netherite_p4_hearts=$first.prior_measured_netherite_p4_hearts
        reference_is_encounter_expectation=$false
    }
})
New-Item -ItemType Directory -Path $OutputDirectory -Force|Out-Null
$details|Export-Csv (Join-Path $OutputDirectory 'damage-receipts.csv') -NoTypeInformation
$profileRows|Export-Csv (Join-Path $OutputDirectory 'player-profile-summary.csv') -NoTypeInformation
$attempts|Export-Csv (Join-Path $OutputDirectory 'attempt-context.csv') -NoTypeInformation
ConvertTo-Json -InputObject @($issues) -Depth 6|Set-Content -LiteralPath (Join-Path $OutputDirectory 'review-items.json')
$arithmeticReviewItems=@($issues|Where-Object check -ne 'received_bar_not_finite_or_out_of_range').Count
$manifest=[ordered]@{
    generatedUtc=[DateTimeOffset]::UtcNow.ToString('o');completedAttempts=$attempts.Count;damagePosts=$details.Count;playerPosts=@($details|Where-Object fixture_player).Count
    toleranceHp=$ToleranceHp;reviewItems=$issues.Count;damageArithmeticReviewItems=$arithmeticReviewItems;receivedBarSamples=$barSamples;receivedBarValues=$barValues;invalidBarValues=$barIssues;unparsedClientSampleLines=$badClientRows
    approvedReferenceSha256=(Get-FileHash -LiteralPath $approvedPath -Algorithm SHA256).Hash;scriptSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash;sources=@($sources)
    scope='Offline arithmetic audit, not a proof of all intended attack amounts. Incoming amounts may be intentionally changed by the balance mod; post amounts plus recorded reductions are checked against processed incoming amounts. Unexplained residuals require native-hook review, not automatic blame on production. Effect/gear context is last observed, not a reconstruction of missing damage-source flags. Received-bar checks establish only finite 0..1 values, not every native phase formula or absence/tracking.'
}
$manifest|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $OutputDirectory 'receipt-manifest.json')
$warden=$approved['warden.sonic_boom']
$lines=@(
    '# Native damage receipt audit','',
    "$($attempts.Count) completed attempts contain $($details.Count) native damage Post events, including $($manifest.playerPosts) against the player. Damage arithmetic review items: $arithmeticReviewItems, at a $ToleranceHp HP tolerance. Received boss bars: $barValues values in $barSamples client samples; $barIssues out-of-range values require native renderer review.",'',
    'The audit independently checks original-damage identity, processed incoming damage minus shield/reductions versus final damage, and observed health/absorption changes. Health and absorption clamp at zero. Native cancellation, lethal interception, nested damage or later hooks can create a residual; those rows remain review items rather than being silently passed. Incoming attempts without a Post are not classified as misses.','',
    '[Every receipt](damage-receipts.csv), [player profile summaries](player-profile-summary.csv), [attempt armor/apple/revival context](attempt-context.csv), and [review items](review-items.json) preserve the measured values. Gear and effects are the most recent observed snapshots; exact armor/toughness at the damage observer are also retained. Enchanted apples add absorption and status effects, so these are not bare netherite P4 measurements.','',
    "Approved controlled reference values are linked for context, not used as unconditional encounter caps. In particular, Warden sonic boom is $($warden.approved_diamond_hearts) hearts in the approved reference and $($warden.measured_netherite_protection4_hearts) hearts in the prior netherite P4 measurement, without enchanted-apple absorption. Brand, source bypass flags, cooldowns, shields and other native conditions require their recorded context. The audit does not substitute a generic 9.5-heart figure.",'',
    'Finite bar values do not prove each native phase formula, tracking visibility, or that a bar should exist. Those have separate staged and encounter evidence. This audit changes no gameplay, files in S:\Survival, or test controls.'
)
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'receipt-checks.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{attempts=$attempts.Count;posts=$details.Count;reviewItems=$issues.Count;barValues=$barValues;invalidBars=$barIssues;output=$OutputDirectory}|ConvertTo-Json -Compress
