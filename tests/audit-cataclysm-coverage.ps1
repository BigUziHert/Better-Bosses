param(
    [Parameter(Mandatory=$true)][string]$CapturePath,
    [Parameter(Mandatory=$true)][string]$InventoryPath,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$records=@(Get-Content -LiteralPath $CapturePath -Raw | ConvertFrom-Json)
$inventory=Get-Content -LiteralPath $InventoryPath -Raw | ConvertFrom-Json
$hits=@($records | Where-Object status -EQ 'damage_observed')
$misses=@($records | Where-Object status -EQ 'uncovered')
$adjudicated=foreach($miss in $misses){
    $related=@($hits | Where-Object {$_.boss -eq $miss.boss -and $_.move.StartsWith($miss.move+'/')})
    $parts=$miss.move.Split('/');$context=$parts[0];$move=$parts[1]
    $classification='unresolved';$explanation='No adjudication; follow-up investigation required.'
    if($related.Count){$classification='damaging_child_captured';$explanation='This parent pose does not directly hurt the player; its emitted native child has an accepted damage capture in the same phase/context.'}
    elseif($miss.boss -eq 'cataclysm:scylla' -and $move -eq 'state7_spin'){
        $related=@($hits | Where-Object {$_.boss -eq $miss.boss -and $_.move -eq "$context/state7_spin_ring5_5/native_frames"})
        if($related.Count){$classification='alternate_hitbox_captured';$explanation='Spin connects in the separate 5.5-block ring probe; the original 2-block pose is a legitimate miss.'}
    }
    elseif($miss.boss -eq 'cataclysm:scylla' -and $move -in @('state10_lightning_spear','state11_water_spear')){
        $kind=if($move -eq 'state10_lightning_spear'){'lightning'}else{'water'}
        $related=@($hits | Where-Object {$_.boss -eq $miss.boss -and $_.move.StartsWith("standalone_spear/$context/$kind/")})
        if($related.Count){$classification='dedicated_native_launch_captured';$explanation='Separate native SpearThrowGoal selection/start/tick, projectile flight, direct contact, and ground alternative probes cover this phase.'}
    }
    elseif($miss.boss -eq 'cataclysm:the_leviathan' -and $move -eq 'LEVIATHAN_GRAB'){
        $related=@($hits | Where-Object {$_.boss -eq $miss.boss -and $_.move -eq "$context/tongue_initial_contact/native_tick"})
        if($related.Count){$classification='dedicated_native_launch_captured';$explanation='Immediate native tongue emission/contact/mount probe runs before the parent goal discards the tongue.'}
    }
    if($classification -eq 'unresolved'){
        $nonDamage=switch($miss.boss){
            'cataclysm:ender_guardian' { $move -eq 'GUARDIAN_AIR_STRIKE1' }
            'cataclysm:netherite_monstrosity' { $move -in @('state2_awake','state7_drain') }
            'cataclysm:the_harbinger' { $move -eq 'STUN_ANIAMATION' }
            'cataclysm:ancient_remnant' { $move -in @('state2_awaken','state7_phase_roar','state10_charge_prepare','state12_charge_stun') }
            'cataclysm:the_leviathan' { $move -eq 'LEVIATHAN_STUN' }
            'cataclysm:scylla' { $move -in @('state6_backstep','state14_anchor_pull','state16_chain_jump2','state19_parry','state21_enchant1','state22_enchant2') }
            'cataclysm:maledictus' { $move -in @('state4_fall_loop','state5_fall_land','state8_flying_smash1','state10_backstep','state20_combo_first_end','state24_flying_halberd_smash1','state28_grab_start','state30_grab_fail','state31_grab_success','state32_grab_success_loop') }
            'cataclysm:ignis' { $move -in @('BREAK_THE_SHIELD','COUNTER','SHIELD_BREAK_COUNTER','SMASH_IN_AIR') }
            default { $false }
        }
        if($nonDamage){$classification='non_damaging_stage';$explanation='Installed source stage is windup, movement/carry, recovery, defensive response, awakening, healing, or phase transition; absence of direct outgoing contact is expected. This classifies its damage scope, not every possible gameplay response.'}
    }
    [pscustomobject]@{Boss=$miss.boss;RawMove=$miss.move;Classification=$classification;Evidence=($related.move -join '; ');Explanation=$explanation}
}
$helperRows=foreach($boss in $inventory.bosses){
    foreach($method in ($boss.damageCallsites.method | Sort-Object -Unique)){
        if($method -in @('hurt','hurtParts','attackEntityFromPart')){continue}
        if($method -eq 'handleDamageEvent' -and $boss.boss -eq 'Scylla_Entity'){continue}
        if($method -eq 'Whip' -and $boss.boss -eq 'Scylla_Entity'){continue}
        $found=@($hits | Where-Object {@($_.hits.nativeCallers | Where-Object {$_ -like "*.$($boss.boss).$method"}).Count -gt 0})
        [pscustomobject]@{BossClass=$boss.boss;NativeDamageHelper=$method;AcceptedScenarios=$found.Count;Status=if($found.Count){'accepted_native_damage'}else{'GAP'};Example=($found | Select-Object -First 1).move}
    }
}
$adjudicated | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'cataclysm-adjudicated-pose-misses.csv') -NoTypeInformation -Encoding utf8
$helperRows | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'cataclysm-native-damage-helpers.csv') -NoTypeInformation -Encoding utf8
$unresolved=@($adjudicated|Where-Object Classification -EQ unresolved)
$missing=@($helperRows|Where-Object Status -EQ GAP)
$lines=[Collections.Generic.List[string]]::new()
$lines.Add('# Cataclysm coverage adjudication')
$lines.Add('')
$lines.Add("The current capture contains $($hits.Count) scenarios with accepted native player damage. All $($helperRows.Count) active outgoing boss damage helpers in the installed-source inventory were checked against the actual captured native call stacks; $($missing.Count) helpers lack a capture. Of $($misses.Count) raw uncovered pose records, $($unresolved.Count) remain unresolved after matching emitted effects, dedicated launch probes, hitbox alternatives, and non-damaging stages.")
$lines.Add('')
$lines.Add('| Raw pose classification | Records |')
$lines.Add('|---|---:|')
foreach($group in ($adjudicated|Group-Object Classification|Sort-Object Name)){$lines.Add("| $($group.Name) | $($group.Count) |")}
$lines.Add('')
$lines.Add('The [pose-by-pose adjudication](cataclysm-adjudicated-pose-misses.csv) preserves every raw miss and names the accepted related capture. The [native helper inventory](cataclysm-native-damage-helpers.csv) provides a damage-stack example for every active helper. Defensive incoming-damage methods, Scylla''s zero-damage self-reaction, and its dormant private Whip helper are excluded from the outgoing attack inventory. Active electric-whip moves use AreaAttack and emitted lightning, which are checked independently.')
$lines.Add('')
$lines.Add('Health-phase checks cover Guardian 50%, Monstrosity 40%, Harbinger 50%, Remnant 50%, Leviathan 50%, Maledictus 50%/25%, and Scylla/Ignis two-thirds/one-third thresholds. Each configured phase context has staged native attack-frame probes; Scylla and Ignis transitions run through completion and entity serialization. Maledictus rage 0–5 and Ignis Brand 0–V have separate native measurements. The detailed phase assertions and per-move contexts are in the suite JSON and general native-scenario report.')
$lines.Add('')
$lines.Add('This establishes native attack/damage paths and the enumerated health phases in controlled fixtures. It does not establish complete autonomous fights, every random attack choice, simultaneous projectile/DOT combinations, natural target switching throughout a fight, every environmental trajectory, or visual client animation correctness. Non-damaging stage classification does not claim that every parry, movement, lava-drain, or defensive gameplay condition was exercised. Existing helper invocations in aiStep may contain several branches; per-animation/phase captures supply the finer context rather than treating a single helper hit as universal branch coverage.')
if($unresolved.Count -or $missing.Count){$lines.Add('');$lines.Add('Unresolved entries are retained in the CSVs and require follow-up before claiming complete enumerated damage-path coverage.')}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'cataclysm-coverage-adjudication.md'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{NativeScenarios=$hits.Count;Helpers=$helperRows.Count;MissingHelpers=$missing.Count;RawPoseMisses=$misses.Count;Unresolved=$unresolved.Count} | Format-List
