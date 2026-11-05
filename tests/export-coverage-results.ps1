param(
    [Parameter(Mandatory=$true)][string]$ResultsDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$ConfigPath=(Join-Path (Split-Path $PSScriptRoot -Parent) 'config\survival-boss-balance.json')
)
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$checks=[System.Collections.Generic.List[object]]::new()
$scenarios=[System.Collections.Generic.List[object]]::new()
$suites=[System.Collections.Generic.List[object]]::new()
foreach($file in (Get-ChildItem -LiteralPath $ResultsDirectory -File -Filter '*.json' | Sort-Object Name)){
    $data=Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json
    if($data -isnot [array] -and $data.PSObject.Properties.Name -contains 'checks'){
        $suites.Add([pscustomobject]@{Suite=$data.phase;Checks=$data.total;Failed=$data.failed;Source=$file.Name})
        foreach($row in $data.checks){$checks.Add($row)}
    }
    if($file.Name -like '*-attacks.json'){
        foreach($row in $data){
            $hits=@($row.hits | Where-Object {$null -ne $_})
            $max=if($hits.Count -gt 0){($hits | Measure-Object -Property damageHp -Maximum).Maximum/2}else{$null}
            $scenarios.Add([pscustomobject]@{Boss=$row.boss;Move=$row.move;Status=$row.status;HitCount=$hits.Count;MaxSingleHitHearts=$max;Detail=$row.detail;Source=$file.Name})
        }
    }
}
$checks | Select-Object phase,test,entity,expected,actual,passed,error | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'all-assertions.csv') -NoTypeInformation -Encoding utf8
$scenarios | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'all-native-scenarios.csv') -NoTypeInformation -Encoding utf8
$gaps=@($scenarios | Where-Object Status -eq 'uncovered')
$gaps | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'coverage-gaps.csv') -NoTypeInformation -Encoding utf8
$failures=@($checks | Where-Object passed -NE $true)
$failures | Select-Object phase,test,entity,expected,actual,passed,error | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'failed-assertions.csv') -NoTypeInformation -Encoding utf8
$config=Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$bosses=@($config.healthTargets.PSObject.Properties.Name)
$lines=[System.Collections.Generic.List[string]]::new()
$lines.Add('# Boss regression evidence')
$lines.Add('')
$lines.Add("Current result files contain $($checks.Count) assertions, $($failures.Count) failed assertions, and $($scenarios.Count) native scenario records. There are $($gaps.Count) raw uncovered scenario records; these are preserved for review, never counted as passing attacks. Another dedicated scenario can cover the same damage path, so each raw gap must be read alongside its boss's measured native probes.")
$lines.Add('')
$lines.Add('These are controlled dedicated-server tests with real registered entities, items, native attack methods, and server damage processing. They do not establish a continuous fight with human players, every random attack pattern, arbitrary terrain trajectories, or client rendering. Health-bar records establish native server events and their tracking/progress, not a screenshot of every client HUD.')
$lines.Add('')
$lines.Add('| Suite | Assertions | Failed |')
$lines.Add('|---|---:|---:|')
foreach($suite in $suites){$lines.Add("| $($suite.Suite) | $($suite.Checks) | $($suite.Failed) |")}
$lines.Add('')
$lines.Add('| Boss | Fixed max HP | Assertions | Failed | Native damage scenarios | Mechanic/transition records | Raw uncovered records |')
$lines.Add('|---|---:|---:|---:|---:|---:|---:|')
foreach($id in $bosses){
    $ownChecks=@($checks | Where-Object entity -EQ $id)
    $ownScenarios=@($scenarios | Where-Object Boss -EQ $id)
    $native=@($ownScenarios | Where-Object Status -EQ 'damage_observed').Count+@($ownChecks | Where-Object {$null -ne $_.attackMethod}).Count
    $mechanics=@($ownScenarios | Where-Object Status -In @('mechanic','transition')).Count
    $uncovered=@($ownScenarios | Where-Object Status -EQ 'uncovered').Count
    $failed=@($ownChecks | Where-Object passed -NE $true).Count
    $lines.Add("| $id | $($config.healthTargets.$id) | $($ownChecks.Count) | $failed | $native | $mechanics | $uncovered |")
}
$lines.Add('')
$lines.Add('[All assertions](all-assertions.csv), [native move/phase scenarios](all-native-scenarios.csv), [failed assertions](failed-assertions.csv), and [raw coverage gaps](coverage-gaps.csv) retain individual evidence. Re-running one suite replaces that suite result file; historical failed runs must be retained separately by the test runner.')
$lines.Add('')
foreach($id in $bosses){
    $own=@($scenarios | Where-Object Boss -EQ $id)
    $nativeChecks=@($checks | Where-Object {$_.entity -eq $id -and $null -ne $_.attackMethod})
    $lines.Add("## $id")
    $lines.Add('')
    if($own.Count -eq 0 -and $nativeChecks.Count -eq 0){$lines.Add('No native scenario measurements are present in the selected results directory.');$lines.Add('');continue}
    $lines.Add('| Move / phase probe | Status | Hits | Largest single hit (hearts) |')
    $lines.Add('|---|---|---:|---:|')
    foreach($s in $own){
        $damage=if($null -eq $s.MaxSingleHitHearts){'—'}else{([double]$s.MaxSingleHitHearts).ToString('0.###',[Globalization.CultureInfo]::InvariantCulture)}
        $move=$s.Move.Replace('|','\|')
        $lines.Add("| $move | $($s.Status) | $($s.HitCount) | $damage |")
    }
    foreach($c in $nativeChecks){$status=if($c.passed){'native assertion passed'}else{'FAILED'};$lines.Add("| $($c.test) | $status | 1 | $($c.actual) |")}
    $lines.Add('')
}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'boss-coverage-results.md'),($lines -join "`n").TrimEnd()+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]@{Assertions=$checks.Count;Failed=$failures.Count;NativeRecords=$scenarios.Count;RawGaps=$gaps.Count;Output=$OutputDirectory} | Format-List
