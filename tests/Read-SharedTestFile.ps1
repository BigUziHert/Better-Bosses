# Live test logs/metadata must never deny the game's concurrent append or replace.
function Read-SharedTestFile {
    param([Parameter(Mandatory=$true)][string]$LiteralPath,[int]$Tail=0,[int]$TotalCount=0)
    $share=[IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete
    $stream=[IO.File]::Open([IO.Path]::GetFullPath($LiteralPath),[IO.FileMode]::Open,[IO.FileAccess]::Read,$share)
    $reader=$null
    try {
        if($Tail -gt 0){
            $bytes=[long][Math]::Max(65536,$Tail*4096)
            while($true){
                $start=[Math]::Max(0,$stream.Length-$bytes)
                $null=$stream.Seek($start,[IO.SeekOrigin]::Begin)
                $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,4096,$true)
                $text=$reader.ReadToEnd();$reader.Dispose();$reader=$null
                $lines=$text.Split("`n")
                # Ignore an incomplete first/last JSONL record while a writer is active.
                $first=if($start -gt 0){1}else{0}
                $last=$lines.Length-2
                $count=$last-$first+1
                if($count -ge $Tail -or $start -eq 0){
                    if($count -gt 0){$begin=[Math]::Max($first,$last-$Tail+1);for($i=$begin;$i -le $last;$i++){$lines[$i].TrimEnd("`r")}}
                    break
                }
                $bytes*=2
            }
        }else{
            $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,4096,$true)
            if($TotalCount -gt 0){for($i=0;$i -lt $TotalCount;$i++){$line=$reader.ReadLine();if($null -eq $line){break};$line}}
            else{$reader.ReadToEnd()}
        }
    }finally{if($reader){$reader.Dispose()};$stream.Dispose()}
}
