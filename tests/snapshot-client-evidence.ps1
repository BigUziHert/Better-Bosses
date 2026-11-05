#Requires -Version 7.5
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$source = [IO.Path]::GetFullPath((Join-Path $repo 'run-client-playtest/client-fight-events.jsonl'))
$folder = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence/shared-reader-snapshots'))
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$destination = Join-Path $folder 'client-fight-events-armor.jsonl'
$temporary = Join-Path $folder ('client-fight-events-armor.pending-' + [Guid]::NewGuid().ToString('N'))
$reader = [IO.FileStream]::new($source,[IO.FileMode]::Open,[IO.FileAccess]::Read,
    ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
$lengthAtOpen = $reader.Length
$writer = $null
try {
    $writer = [IO.FileStream]::new($temporary,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $buffer = [byte[]]::new(1048576)
    $remaining = $lengthAtOpen
    while ($remaining -gt 0) {
        $read = $reader.Read($buffer,0,[int][Math]::Min([long]$buffer.Length,$remaining))
        if ($read -eq 0) { throw 'Append log was truncated during shared snapshot. Retry after source review.' }
        $writer.Write($buffer,0,$read)
        $remaining -= $read
    }
    $writer.Flush()
    # A writer may have been midway through a final JSON line at open time.
    # Keep only the complete newline-terminated prefix in the copied file.
    $searchEnd = $writer.Length
    $completeLength = 0L
    while ($searchEnd -gt 0 -and $completeLength -eq 0) {
        $start = [Math]::Max(0L,$searchEnd - 65536L)
        $count = [int]($searchEnd - $start)
        $writer.Position = $start
        $read = $writer.Read($buffer,0,$count)
        for ($i=$read-1; $i-ge0; $i--) { if ($buffer[$i]-eq10) { $completeLength=$start+$i+1; break } }
        $searchEnd = $start
    }
    $writer.SetLength($completeLength)
} finally {
    if ($writer) { $writer.Dispose() }
    $reader.Dispose()
}
foreach($target in @($temporary,$destination)) {
    if(-not[IO.Path]::GetFullPath($target).StartsWith($folder+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Snapshot path escaped the intended evidence directory.'
    }
}
if((Test-Path -LiteralPath $destination) -and ((Get-Item -LiteralPath $destination).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw 'Snapshot destination is a reparse point.'
}
[IO.File]::Move($temporary,$destination,$true)
$manifest = [ordered]@{
    generatedUtc=[DateTime]::UtcNow.ToString('o')
    source='run-client-playtest/client-fight-events.jsonl'
    access='FileAccess.Read with FileShare.ReadWrite | FileShare.Delete'
    sourceLengthAtOpen=$lengthAtOpen
    completeSnapshotBytes=(Get-Item -LiteralPath $destination).Length
    omittedTrailingIncompleteBytes=$lengthAtOpen-(Get-Item -LiteralPath $destination).Length
    snapshot='tests/evidence/shared-reader-snapshots/client-fight-events-armor.jsonl'
    snapshotSha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
    sourceWasHashed=$false
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $folder 'client-fight-events-armor.manifest.json') -Encoding utf8
$manifest | ConvertTo-Json -Compress
