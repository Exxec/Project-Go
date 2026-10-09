param([Parameter(Mandatory=$true)][string]$Launcher,
      [Parameter(Mandatory=$true)][string]$Fixture,
      [Parameter(Mandatory=$true)][string]$Response)
$ErrorActionPreference = 'Stop'
# pwsh CI parents can omit Windows PowerShell modules from the inherited path.
$env:PSModulePath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\Modules;$env:PSModulePath"
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('project-go-native-' + [Guid]::NewGuid().ToString('N'))
$previousData = $env:LOCALAPPDATA
$previousMemory = $env:SSMT_TRANSLATION_MEMORY
try {
    $env:LOCALAPPDATA = Join-Path $testRoot 'fresh-profile'
    $env:SSMT_TRANSLATION_MEMORY = Join-Path $env:LOCALAPPDATA 'catalog.db'
    $unicodeName = ([char]0x4e2d).ToString() + [char]0x6587 + ' mod & spaced'
    $source = Join-Path $testRoot $unicodeName
    New-Item -ItemType Directory -Path $source | Out-Null
    Get-ChildItem -LiteralPath $Fixture -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $source -Recurse
    }
    $before = @(Get-ChildItem -LiteralPath $source -Recurse -File | Get-FileHash | Sort-Object Path)
    & $Launcher $source
    if ($LASTEXITCODE -ne 0) { throw 'Native Unicode request pass failed' }
    $request = @(Get-ChildItem -LiteralPath $testRoot -File -Filter '*.json')
    if ($request.Count -ne 1) { throw 'Native Unicode request not produced' }
    $units = [IO.File]::ReadAllText($request[0].FullName) | ConvertFrom-Json
    if ($units.entries.Count -ne 18) { throw 'Native request lost Unicode units' }
    Copy-Item -LiteralPath $Response -Destination (Join-Path $testRoot 'arbitrary-response.json')
    & $Launcher (Join-Path $source 'mod_info.json')
    if ($LASTEXITCODE -ne 0) { throw 'Native Unicode metadata/build pass failed' }
    $outputs = @(Get-ChildItem -LiteralPath $testRoot -Directory | Where-Object {
        $_.Name -ne $unicodeName -and $_.Name -ne 'fresh-profile'
    })
    if ($outputs.Count -ne 1) { throw 'Native Auto did not publish exactly one copy' }
    $expectedRoot = Join-Path (Split-Path -Parent $Fixture) 'expected-output'
    foreach ($expected in Get-ChildItem -LiteralPath $expectedRoot -Recurse -File) {
        $relative = $expected.FullName.Substring($expectedRoot.Length + 1)
        $actual = Join-Path $outputs[0].FullName $relative
        if (-not (Test-Path -LiteralPath $actual) -or
            (Get-FileHash -LiteralPath $actual).Hash -ne (Get-FileHash -LiteralPath $expected.FullName).Hash) {
            throw "Native Unicode output differs: $relative"
        }
    }
    $after = @(Get-ChildItem -LiteralPath $source -Recurse -File | Get-FileHash | Sort-Object Path)
    if (($before | ConvertTo-Json -Compress) -cne ($after | ConvertTo-Json -Compress)) {
        throw 'Native Auto changed source bytes'
    }
    Write-Output 'Native Auto Unicode PASS: folder/metadata arguments, renamed response, exact output, unchanged source'
} finally {
    $env:LOCALAPPDATA = $previousData
    $env:SSMT_TRANSLATION_MEMORY = $previousMemory
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected native test cleanup path'
    }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
