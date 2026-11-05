# Read append logs without denying the game's writer or rotation operations.
function Open-SharedEvidenceReader([string]$Path) {
    $stream = [IO.FileStream]::new([IO.Path]::GetFullPath($Path), [IO.FileMode]::Open,
        [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    try { return [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true) }
    catch { $stream.Dispose(); throw }
}
function Get-SharedEvidenceFirstLine([string]$Path) {
    $reader = Open-SharedEvidenceReader $Path
    try { return $reader.ReadLine() } finally { $reader.Dispose() }
}
function Get-SharedEvidenceText([string]$Path) {
    $reader = Open-SharedEvidenceReader $Path
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Get-SharedEvidenceLines([string]$Path) {
    $reader = Open-SharedEvidenceReader $Path
    try { while (($line = $reader.ReadLine()) -ne $null) { $line } }
    finally { $reader.Dispose() }
}
function Get-SharedEvidenceHash([string]$Path) {
    $stream = [IO.FileStream]::new([IO.Path]::GetFullPath($Path), [IO.FileMode]::Open,
        [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToHexString($sha.ComputeHash($stream)) }
    finally { $sha.Dispose(); $stream.Dispose() }
}
