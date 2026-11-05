param(
    [Parameter(Mandatory=$true)][string]$EncounterDirectory,
    [Parameter(Mandatory=$true)]
    [ValidateSet('cataclysm:the_leviathan','cataclysm:scylla','cataclysm:maledictus','cataclysm:ignis')]
    [string]$Boss,
    [Parameter(Mandatory=$true)][guid]$ExpectedUuid,
    [ValidateSet('auto','melee','ranged')][string]$WeaponMode='auto',
    [ValidateRange(0,64)][double]$PreferredRange=0,
    [bool]$AttackEnabled=$true,
    [bool]$GuardEnabled=$false,
    [Parameter(Mandatory=$true)][string]$Reason,
    [switch]$PreviewOnly
)
# One ordinary client input-policy choice, not a server/entity-state operation.
# The four permitted bosses are the explicitly assigned latter half of the queue.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Read-SharedTestFile.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$runPath=[IO.Path]::GetFullPath($EncounterDirectory)
$resultsRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'results/full-fights'))+[IO.Path]::DirectorySeparatorChar
if(-not $runPath.StartsWith($resultsRoot,[StringComparison]::OrdinalIgnoreCase)){
    throw 'EncounterDirectory must be under this repository tests/results/full-fights.'
}
$controls=Join-Path $repo 'run-client-playtest/bossfight-control.json'
$metadata=Join-Path $repo 'run-client-playtest/bossfight-encounter.json'
$journal=Join-Path $PSScriptRoot 'evidence/victory-tactics.jsonl'
$state=Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String
if(-not $state.running -or $state.boss -ne $Boss -or $state.gear -ne 'netherite_max' -or
    @($state.bossUuids).Count -ne 1 -or $state.bossUuids[0] -ne $ExpectedUuid.ToString()){
    throw 'The requested active boss, equipment, and encounter UUID must match current metadata.'
}
$age=([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($state.timestamp)).TotalSeconds
if($age -lt -2 -or $age -gt 10){throw 'Encounter metadata is not fresh.'}
$pattern='*-'+$Boss.Replace(':','_')+'-netherite_max.jsonl'
$files=@(Get-ChildItem -LiteralPath $runPath -Filter $pattern)
if($files.Count -ne 1){throw 'Expected exactly one matching encounter log in the requested run.'}
$latest=@(Read-SharedTestFile -LiteralPath $files[0].FullName -Tail 1 | ConvertFrom-Json -DateKind String)[-1]
if($latest.record -eq 'finished' -or $latest.boss -ne $Boss){throw 'Requested encounter is already closed or mismatched.'}
$telemetry=$files[0].FullName.Replace('.jsonl','.telemetry.jsonl')
$identity=@(Read-SharedTestFile -LiteralPath $telemetry -TotalCount 20 | ConvertFrom-Json -DateKind String |
    Where-Object {$_.event -eq 'state_transition' -and $_.entity.uuid -eq $ExpectedUuid.ToString()})
if($identity.Count -eq 0){throw 'The requested run telemetry does not establish this boss UUID.'}
$choice=Read-SharedTestFile -LiteralPath $controls | ConvertFrom-Json -AsHashtable -DateKind String
$same=$choice.enabled -and $choice.weaponMode -eq $WeaponMode -and
    [double]$choice.preferredRange -eq $PreferredRange -and $choice.attackEnabled -eq $AttackEnabled -and
    $choice.guardEnabled -eq $GuardEnabled -and -not $choice.activateEmp
$choice.enabled=$true
$choice.weaponMode=$WeaponMode
$choice.preferredRange=$PreferredRange
$choice.attackEnabled=$AttackEnabled
$choice.guardEnabled=$GuardEnabled
$choice.activateEmp=$false
$text=$choice | ConvertTo-Json -Compress
if($PreviewOnly -or $same){$text;return}
# Recheck identity immediately before replacing only the isolated client's choices.
$now=Read-SharedTestFile -LiteralPath $metadata | ConvertFrom-Json -DateKind String
if(-not $now.running -or $now.boss -ne $Boss -or @($now.bossUuids).Count -ne 1 -or
    $now.bossUuids[0] -ne $ExpectedUuid.ToString()){throw 'Encounter changed before tactic publication.'}
$temporary=$controls+'.native-tactic.tmp'
[IO.File]::WriteAllText($temporary,$text,[Text.UTF8Encoding]::new($false))
for($attempt=0;;$attempt++){
    try{[IO.File]::Move($temporary,$controls,$true);break}
    catch [IO.IOException]{if($attempt -ge 4){throw};Start-Sleep -Milliseconds 50}
}
[ordered]@{
    utc=[DateTimeOffset]::UtcNow.ToString('o');run=Split-Path $runPath -Leaf
    boss=$Boss;bossUuid=$ExpectedUuid.ToString();tick=$now.tick
    action='ordinary_player_tactic';reason=$Reason;control=$choice
    scope='Normal client movement, weapon and held-key choices only; native healing and self-revival priorities remain active.'
} | ConvertTo-Json -Depth 6 -Compress | Add-Content -LiteralPath $journal
$text
