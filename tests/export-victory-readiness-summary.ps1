#Requires -Version 7.5
param([string]$IgnisPrimaryQueue='', [string]$FinalIgnisAttemptQueue='')
$ErrorActionPreference='Stop'
if($IgnisPrimaryQueue -and $FinalIgnisAttemptQueue){throw 'Choose a verified Ignis victory or a closed final unsuccessful attempt, not both.'}
$ignisQueue=if($IgnisPrimaryQueue){$IgnisPrimaryQueue}else{$FinalIgnisAttemptQueue}
. (Join-Path $PSScriptRoot 'shared-evidence-io.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$output=Join-Path $PSScriptRoot 'reports/victory-validation'
$twilight='2026-11-05T09-48-02.144915600Z'
$cat='2026-11-05T13-04-36.346328200Z'
$selected=@(
    @('Naga','twilightforest:naga',$twilight,1000),
    @('Twilight Lich','twilightforest:lich',$twilight,650),
    @('Minoshroom','twilightforest:minoshroom',$twilight,1000),
    @('Knight Phantoms','twilightforest:knight_phantom',$twilight,180),
    @('Alpha Yeti','twilightforest:alpha_yeti','2026-11-05T10-39-59.147500100Z',1100),
    @('Hydra','twilightforest:hydra','2026-11-05T10-45-02.652861400Z',1500),
    @('Ur-Ghast','twilightforest:ur_ghast','2026-11-05T10-50-20.914724500Z',1100),
    @('Snow Queen','twilightforest:snow_queen','2026-11-05T10-59-18.377888100Z',1200),
    @('Night Lich','bosses_of_mass_destruction:lich','2026-11-05T05-15-32.793840700Z',1300),
    @('Void Blossom','bosses_of_mass_destruction:void_blossom','2026-11-05T06-07-42.519596400Z',1400),
    @('Nether Gauntlet','bosses_of_mass_destruction:gauntlet','2026-11-05T20-55-55.930980500Z',1500),
    @('Obsidilith','bosses_of_mass_destruction:obsidilith','2026-11-05T21-31-58.436218300Z',1700),
    @('Netherite Monstrosity','cataclysm:netherite_monstrosity',$cat,1200),
    @('Ender Guardian','cataclysm:ender_guardian',$cat,850),
    @('Harbinger','cataclysm:the_harbinger',$cat,950),
    @('Ancient Remnant','cataclysm:ancient_remnant',$cat,1000),
    @('Leviathan','cataclysm:the_leviathan','2026-11-05T08-21-27.453392900Z',1100),
    @('Scylla','cataclysm:scylla','2026-11-05T20-09-07.454139900Z',1100),
    @('Maledictus','cataclysm:maledictus','2026-11-05T13-41-48.460192600Z',1100),
    @('Ignis','cataclysm:ignis',$ignisQueue,1100),
    @('Wither','minecraft:wither','2026-11-05T11-23-19.891522100Z',900),
    @('Ender Dragon','minecraft:ender_dragon','2026-11-05T12-52-50.722593800Z',1100),
    @('Warden','minecraft:warden','2026-11-05T11-29-10.093878200Z',1300)
)
$flight=Get-Content (Join-Path $output 'client-flight-audit.json') -Raw|ConvertFrom-Json -DateKind String
$rows=@()
foreach($selection in $selected){
    $name,$id,$queue,$maxHp=$selection
    if(-not $queue){
        $rows+=,[ordered]@{name=$name;boss=$id;maxHpEach=$maxHp;status='native_defeat_pending';queue=$null;controlledEvidence='ignis-controlled-candidate-audit.md'}
        continue
    }
    $slug=$id.Replace(':','_')
    $files=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "results/full-fights/$queue") -Filter "*-$slug-netherite_max.jsonl")
    if($files.Count -ne 1){throw "Expected one primary encounter for $name"}
    $file=$files[0]
    $records=@(Get-SharedEvidenceLines $file.FullName|Where-Object {$_ -match '"record":"(started|finished|boss_death_event)"'}|ForEach-Object {$_|ConvertFrom-Json -DateKind String})
    $start=$records|Where-Object record -EQ 'started'|Select-Object -First 1
    $end=$records|Where-Object record -EQ 'finished'|Select-Object -Last 1
    $deaths=@($records|Where-Object {$_.record -eq 'boss_death_event' -and $_.cancelled -eq $false})
    $finalUnsuccessfulAttempt=($id -eq 'cataclysm:ignis' -and $FinalIgnisAttemptQueue)
    if(-not $start -or -not $end){throw "Primary lacks a completed native outcome for $name"}
    if($finalUnsuccessfulAttempt){
        if($end.outcome -eq 'won'){throw 'Ignis won. Review its native-death evidence and use -IgnisPrimaryQueue.'}
    }elseif($end.outcome -ne 'won' -or $deaths.Count -lt @($start.bosses).Count -or @($end.bosses|Where-Object hp -GT 0).Count){throw "Primary does not have a completed native victory for $name"}
    if(@($start.bosses|Where-Object {[math]::Abs($_.maxHP-$maxHp)-gt.01}).Count){throw "Observed maximum HP mismatch for $name"}
    $appleStart=[int](($start.inventory|Where-Object item -EQ 'minecraft:enchanted_golden_apple'|Measure-Object count -Sum).Sum)
    $appleEnd=[int](($end.inventory|Where-Object item -EQ 'minecraft:enchanted_golden_apple'|Measure-Object count -Sum).Sum)
    $spent=$appleStart-$appleEnd
    if($spent -ne ($end.enchantedApplesEaten+$end.nativeRevivals)){throw "Primary apple accounting needs review for $name"}
    $armorPieces=@($end.gearRemaining|Where-Object {$_ -match '^(FEET|LEGS|CHEST|HEAD):minecraft:netherite_'}).Count
    $attempt="$queue/$($file.BaseName)"
    $movement=$flight.summaries|Where-Object attempt -EQ $attempt
    if(-not $finalUnsuccessfulAttempt -and (-not $movement -or $movement.flyingSamples -ne 0 -or $movement.missingPhysicsSamples -ne 0)){throw "Primary movement evidence needs review for $name"}
    $rows+=,[ordered]@{name=$name;boss=$id;maxHpEach=$maxHp;initialBossCount=@($start.bosses).Count;initialCombinedHp=($start.bosses.maxHP|Measure-Object -Sum).Sum;status=if($finalUnsuccessfulAttempt){'native_defeat_not_observed_final_attempt'}else{'native_victory_observed'};observedOutcome=$end.outcome;finalBossHp=($end.bosses.hp|Measure-Object -Sum).Sum;queue=$queue;attempt=$attempt;ordinaryLog=$file.FullName.Substring($repo.Length+1).Replace('\','/');startedUtc=$start.timestamp;finishedUtc=$end.timestamp;gameTicks=$end.tick;gameSeconds=$end.tick/20.0;finalPlayerHp=$end.playerHP;normalFoodEvents=$end.enchantedApplesEaten;applesInventoryStart=$appleStart;applesInventoryEnd=$appleEnd;applesSpent=$spent;nativeKnockdowns=$end.nativeKnockdowns;nativeSelfRevivals=$end.nativeRevivals;netheriteArmorPiecesRemaining=$armorPieces;finalArmorAttribute=$end.armor;clientSamples=$movement.samples;clientFlyingSamples=$movement.flyingSamples;clientLargestSampleGapSeconds=$movement.largestSampleGapSeconds}
}
$nativeWins=@($rows|Where-Object status -EQ 'native_victory_observed').Count
$receiptFolder=if(Test-Path (Join-Path $output 'receipts-final-complete/adjudication/independent-audit.json')){'receipts-final-complete'}else{'receipts-final-closed'}
$receiptStats=Get-Content (Join-Path $output "$receiptFolder/receipt-manifest.json") -Raw|ConvertFrom-Json -DateKind String
$receiptAudit=Get-Content (Join-Path $output "$receiptFolder/adjudication/independent-audit.json") -Raw|ConvertFrom-Json -DateKind String
$report=[ordered]@{generatedUtc=[DateTime]::UtcNow.ToString('o');status=if($nativeWins -eq 23){'native_defeats_observed_pending_final_review'}elseif($FinalIgnisAttemptQueue){'validation_concluded_native_defeat_requirement_unmet'}else{'in_progress_ignis_native_defeat_pending'};finalReleaseVerdictIssued=$false;productionVersion='1.2.1';productionSha256='53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77';selectedNativeVictories=$nativeWins;managedBosses=23;controlledAssertions=8383;controlledFailures=0;baselineAssertions=8364;separateDragonSupplementAssertions=19;rows=$rows;scope='Selected completed native victories with legal maximum netherite kit and enchanted golden apples. Victory rows require independent native-death witnesses, reconciled apples and zero sampled flight flags. A final unsuccessful attempt is explicitly labeled and receives no victory credit. Gear wear and native self-revival remain visible. The readiness status separately records whether the requested native-victory requirement was met.'}
$report['requestedReadiness']=if($FinalIgnisAttemptQueue){'not_established_all_boss_victory_unmet_and_external_stall_unresolved'}else{'awaiting_final_review'}
$report['nativeVictoryRequirementPassed']=($nativeWins -eq 23)
$report['finalAttemptClosed']=[bool]($IgnisPrimaryQueue -or $FinalIgnisAttemptQueue)
if($FinalIgnisAttemptQueue){$report.finalReleaseVerdictIssued=$true}
$report|ConvertTo-Json -Depth 12|Set-Content (Join-Path $output 'readiness-summary.json') -Encoding utf8
$rows|ForEach-Object {[pscustomobject]$_}|Select-Object name,boss,status,observedOutcome,maxHpEach,initialBossCount,initialCombinedHp,finalBossHp,gameTicks,gameSeconds,applesSpent,normalFoodEvents,nativeKnockdowns,nativeSelfRevivals,netheriteArmorPiecesRemaining,finalPlayerHp,finalArmorAttribute,clientSamples,clientFlyingSamples,clientLargestSampleGapSeconds,queue,startedUtc,finishedUtc,ordinaryLog|Export-Csv (Join-Path $output 'readiness-bosses.csv') -NoTypeInformation
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Boss test readiness report')
$lines.Add('')
if($FinalIgnisAttemptQueue){
    $lines.Add("**Requested readiness is not established. Controlled validation passed, and native victories were verified for $nativeWins of 23 bosses. The final Ignis attempt ended without a defeat, so the all-boss victory requirement remains unmet.** The external-pack Leviathan stall also remains unresolved.")
}elseif($nativeWins -eq 23){
    $lines.Add('**Controlled validation passed and all 23 bosses have verified native victories.** The recorded external-pack Leviathan stall remains a runtime limitation for final review.')
}else{
    $lines.Add("**Work remains in progress. $nativeWins of 23 bosses have a selected native victory. Ignis is pending unless its row explicitly records a completed defeat.** No final release verdict is issued by this report.")
}
$lines.Add('')
$lines.Add('The controlled runs passed **8,383 assertions with zero failures**, comprising the frozen 8,364-assertion baseline and the separate 19-assertion Dragon supplement. See the [controlled report](../1.2.1/REGRESSION-1.2.1.md) and [supplement](../1.2.1/supplements/dragon-death-transition/README.md).')
$lines.Add('')
$lines.Add('Selected fights used legal maximum netherite gear and native enchanted golden apples. Time is game time, not elapsed wall time. Apples include ordinary eating and native self-revival consumption. KO and revival counts preserve lethal events. Armor means pieces still present at completion. The CSV preserves final player and boss health for each recorded outcome.')
$lines.Add('')
$lines.Add('| Boss | Maximum HP | Native result / game time | Apples spent | KO / revivals | Armor remaining |')
$lines.Add('| --- | ---: | --- | ---: | ---: | --- |')
foreach($r in $rows){
    if($r.status -eq 'native_defeat_pending'){$lines.Add("| $($r.name) | $($r.maxHpEach) | **Pending** | — | — | — |");continue}
    $hp=if($r.initialBossCount -gt 1){"$($r.maxHpEach) each / $($r.initialCombinedHp) group"}else{"$($r.maxHpEach)"}
    $elapsed=[TimeSpan]::FromSeconds($r.gameSeconds)
    $time='{0}:{1:00.00}' -f [math]::Floor($elapsed.TotalMinutes),($elapsed.TotalSeconds%60)
    $outcomeLabel=if($r.status -eq 'native_victory_observed'){'Won'}else{"**Defeat unverified** ($($r.observedOutcome))"}
    $lines.Add("| $($r.name) | $hp | $outcomeLabel / $time | $($r.applesSpent) | $($r.nativeKnockdowns) / $($r.nativeSelfRevivals) | $($r.netheriteArmorPiecesRemaining)/4 netherite |")
}
$lines.Add('')
$lines.Add('The Warden victory includes seven native knockdowns and self-revivals, and all four armor pieces wore out. Its 48 apples comprise 41 food events plus seven self-feeds. Each other selected victory has all four armor pieces present and zero native knockdowns. The [23-boss CSV](readiness-bosses.csv) and [primary records](readiness-summary.json) identify the exact queues and final inventory counts.')
$lines.Add('')
if($FinalIgnisAttemptQueue){
    $lines.Add('The [final Ignis attempt](final-ignis-attempt.json) ended at the 20-minute game-time limit. Ignis had 1100/1100 HP, the player had 19.3408/20 HP, all four armor pieces had worn out and 79 enchanted golden apples had been consumed. Its native shield broke, but a defeat was not achieved. The recorded damage and native healing supply evidence of this outcome without establishing a numerical defect or an impossible fight. No further gameplay retries are planned.')
    $lines.Add('')
}
$lines.Add('The [Gauntlet](gauntlet-clean-replay.json) and [Obsidilith](obsidilith-clean-replay.json) clean replays replace the earlier startup-flight-qualified runs as primary movement evidence. All selected victories have zero recorded flight flags with complete matched physics fields. Sampling cannot exclude brief transitions between samples. Earlier attempts remain in the [complete attempt table](attempts.csv).')
$lines.Add('')
$lines.Add('Attack and phase evidence is available in the [coverage ledger](coverage-ledger.md), [every candidate](moves-and-phases.csv), [native timeline](native-move-phase-timeline.csv) and [damage receipts](native-player-damage.csv). The [nine Ignis candidates](ignis-controlled-candidate-audit.md) and [other eight candidates](other-controlled-candidate-audit.md) have evidence-specific controlled qualifications. Natural random selection and movement-path gaps remain visible.')
$lines.Add('')
$lines.Add("The [completed-attempt receipt review]($receiptFolder/README.md) checks $($receiptStats.damagePosts) native damage events from $($receiptStats.completedAttempts) completed attempts. Health and absorption arithmetic reconcile within 0.001 HP. All $($receiptAudit.currentProductionCompletedBarValues) received bar values from the $($receiptAudit.currentProductionCompletedCases) completed production 1.2.1 attempts are valid. The review retains the earlier eight Leviathan bar defects and explains 24 native helmet-reduction residuals. Its source manifest identifies every included closed attempt.")
$lines.Add('')
$lines.Add('The [armor report](../1.2.1/armor-results.md), [every armor/attack measurement](../1.2.1/armor-all-attacks.csv), [armor effects](../1.2.1/armor-effects-results.md) and [approved damage comparison](../1.2.1/approved-damage-comparison.md) preserve individual gear results. The [Ignis Brand sweep](../1.2.1/ignis-brand-netherite.md) separates fresh and enchanted-apple effects across Brand levels.')
$lines.Add('')
$lines.Add('The controlled [regression report](../1.2.1/REGRESSION-1.2.1.md) includes fixed HP, multiple-player scenarios, native health bars and installation into previously saved chunks followed by another restart. [Artifact continuity](../1.2.1/artifact-continuity.md) connects the older native wins to the current JAR through identical health/damage classes and the isolated Leviathan bar correction. [Runtime provenance](runtime-provenance.json) identifies each executed helper and client version.')
$lines.Add('')
$lines.Add('A later Leviathan attempt suffered a recorded server stall after Sable portal-ray errors, with the sampled server thread in Lithium collision traversal through Sable movement hooks. Its [unfinished evidence](unfinished-leviathan/README.md) preserves the real RUSH-to-STUN counter and corrected bar transition before the stall. It supplies no new victory. The earlier completed Leviathan win remains the selected result. This external-pack interaction remains a known runtime limitation.')
$lines.Add('')
$lines.Add('The [prepared artifacts](FINAL-ARTIFACTS.md) identify the exact production JAR and report files. The [final source-folder audit](source-read-only-final.json) preserves the read-only comparison of S:\Survival and the separate datapack-copy check.')
[IO.File]::WriteAllText((Join-Path $output 'READINESS-SUMMARY.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{rows=$rows.Count;selectedNativeVictories=$nativeWins;status=$report.status;finalReleaseVerdictIssued=$report.finalReleaseVerdictIssued}|ConvertTo-Json -Compress
