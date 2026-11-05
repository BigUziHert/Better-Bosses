$ErrorActionPreference='Stop'
$testRoot=$PSScriptRoot
$repo=Split-Path $testRoot -Parent
$libraryRoot=Join-Path $repo 'run-regression\libraries'
$patchedServer=Join-Path $libraryRoot 'net\neoforged\neoforge\21.1.264\neoforge-21.1.264-server.jar'
$minecraftServer=Join-Path $libraryRoot 'net\minecraft\server\1.21.1-20240808.144430\server-1.21.1-20240808.144430-srg.jar'
$allLibraryJars=Get-ChildItem -LiteralPath $libraryRoot -Recurse -Filter '*.jar' | Where-Object { $_.FullName -ne $patchedServer -and $_.FullName -ne $minecraftServer } | ForEach-Object {$_.FullName}
$classpath=(@($patchedServer,$minecraftServer)+$allLibraryJars)-join ';'
$classes=Join-Path $testRoot 'build\classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=Get-ChildItem -LiteralPath (Join-Path $testRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object {$_.FullName}
& javac -encoding UTF-8 -cp $classpath -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Test mod compilation failed'}
Copy-Item -LiteralPath (Join-Path $testRoot 'src\main\resources\META-INF') -Destination $classes -Recurse -Force
$jarPath=Join-Path $testRoot 'build\survival-boss-balance-tests-1.0.0.jar'
& jar --create --file $jarPath -C $classes .
if($LASTEXITCODE -ne 0){throw 'Test jar creation failed'}
Write-Output $jarPath
