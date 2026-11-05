#Requires -Version 7.5
[CmdletBinding()]
param(
    [string]$ResultFile = "$PSScriptRoot/results/dragon_death_transition.json",
    [string]$SnapshotDirectory = "$PSScriptRoot/evidence/1.2.1-dragon-death-transition-2026-11-05T12-52-08Z",
    [string]$OutputDirectory = "$PSScriptRoot/reports/1.2.1/supplements/dragon-death-transition",
    [string]$ProductionSha256 = '53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77',
    [string]$ExecutedHelperSha256 = 'B34017BB173BA9126AD43AA6A7BB7EE5BF9594A2150A6C482200318C156AD3A7'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'shared-evidence-io.ps1')
$source = Get-Item -LiteralPath $ResultFile
$before = Get-SharedEvidenceHash $source.FullName
$data = Get-SharedEvidenceText $source.FullName | ConvertFrom-Json -DateKind String
if ($data.phase -ne 'dragon_death_transition' -or $data.total -ne 19 -or $data.failed -ne 0 -or
    @($data.checks).Count -ne 19 -or @($data.checks | Where-Object { -not $_.passed }).Count -ne 0 -or
    $data.nativeVictory -ne $false) { throw 'Dragon supplement scope or assertion validation failed.' }
$baselinePath = "$PSScriptRoot/reports/1.2.1/test-run-manifest.json"
$baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json -DateKind String
if ($baseline.assertions -ne 8364 -or $baseline.failed -ne 0 -or $baseline.productionSha256 -ne $ProductionSha256) {
    throw 'Baseline identity or assertion count differs from the frozen 1.2.1 run.'
}
New-Item -ItemType Directory -Force -Path $SnapshotDirectory,$OutputDirectory | Out-Null
$frozenPath = Join-Path $SnapshotDirectory $source.Name
if (Test-Path -LiteralPath $frozenPath) {
    if ((Get-FileHash -LiteralPath $frozenPath).Hash -ne $before) { throw 'Existing immutable supplement differs.' }
} else {
    Copy-Item -LiteralPath $source.FullName -Destination $frozenPath
}
$after = Get-SharedEvidenceHash $source.FullName
$copied = (Get-FileHash -LiteralPath $frozenPath).Hash
if ($before -ne $after -or $before -ne $copied) { throw 'Supplement copy verification failed.' }
(Get-Item -LiteralPath $frozenPath).IsReadOnly = $true
$manifest = [ordered]@{
    schemaVersion = 1
    generatedUtc = [DateTime]::UtcNow.ToString('o')
    phase = $data.phase
    sourceResultLastWriteUtc = $source.LastWriteTimeUtc.ToString('o')
    productionSha256 = $ProductionSha256
    executedHelperSha256 = $ExecutedHelperSha256
    runtimeIdentityBasis = 'Parent confirmed the installed B340 helper and 1.2.1 production for the idle command. Installed helper hash was independently read after execution.'
    sourceResult = 'tests/results/dragon_death_transition.json'
    resultBytes = $source.Length
    resultSha256 = $copied
    sourceBeforeAfterAndSnapshotHashesMatch = $true
    assertions = 19
    failed = 0
    nativeVictory = $false
    continuousNaturalPhaseCoverage = $false
    scope = $data.scope
    fixture = $data.fixture
    baseline = [ordered]@{
        manifest = '../../test-run-manifest.json'
        manifestSha256 = (Get-FileHash -LiteralPath $baselinePath).Hash
        assertions = 8364
        executedHelperSha256 = $baseline.executedHelperSha256
    }
    baselinePlusSeparateSupplementAssertions = 8383
}
$frozenManifest = Join-Path $SnapshotDirectory 'manifest.json'
if (-not (Test-Path -LiteralPath $frozenManifest)) {
    $manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $frozenManifest -Encoding utf8
    (Get-Item -LiteralPath $frozenManifest).IsReadOnly = $true
} else {
    $existing = Get-Content -LiteralPath $frozenManifest -Raw | ConvertFrom-Json -DateKind String
    if ($existing.resultSha256 -ne $copied -or $existing.executedHelperSha256 -ne $ExecutedHelperSha256 -or
        $existing.productionSha256 -ne $ProductionSha256) { throw 'Immutable supplement manifest identity differs.' }
}
Copy-Item -LiteralPath $frozenPath -Destination (Join-Path $OutputDirectory 'checks.json') -Force
(Get-Item -LiteralPath (Join-Path $OutputDirectory 'checks.json')).IsReadOnly = $false
Copy-Item -LiteralPath $frozenManifest -Destination (Join-Path $OutputDirectory 'manifest.json') -Force
(Get-Item -LiteralPath (Join-Path $OutputDirectory 'manifest.json')).IsReadOnly = $false
$data.checks | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'checks.csv') -NoTypeInformation -Encoding utf8
@'
# Dragon death-transition supplement

All **19 controlled assertions passed** at 12:52:08 UTC on 5 November 2026. The [checks](checks.csv), [raw result](checks.json) and [artifact manifest](manifest.json) preserve this separate execution.

The fixture used an unjoined Dragon in the isolated overworld. Its balanced maximum health was 1,100 HP. Before the hit, the fixture supplied Holding Pattern, 10 current HP and a position away from the native death destination. A native head hit selected DYING phase 9 and left 1 HP. Native death-phase ticks retained 1 HP while far from the destination. After a separate position-only arrival setup, the native death-phase tick set health to zero. The assertions also checked the native destination and flight-speed value.

This is controlled native-method evidence. Autonomous death flight, a continuous encounter and a natural victory remain separate coverage requirements. The fixture selected Holding Pattern before the hit. Native hurt selected DYING, and the subsequent health changes came from the native death-phase method.

The frozen baseline remains **8,364 passing assertions** with its recorded C1 helper. This supplement used the B340 helper and adds 19 distinct passing assertions, for **8,383 across the two executions**. Both used production 1.2.1 SHA256 `53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77`. The baseline snapshot and natural-victory ledger retain their own counts and scope.
'@ | Set-Content -LiteralPath (Join-Path $OutputDirectory 'README.md') -Encoding utf8
[pscustomobject]@{ phase=$data.phase; assertions=19; failed=0; baselineAssertions=8364; combinedAssertions=8383; nativeVictory=$false; resultSha256=$copied } | ConvertTo-Json -Compress
