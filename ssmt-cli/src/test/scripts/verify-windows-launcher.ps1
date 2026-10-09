param([Parameter(Mandatory=$true)][string]$Launcher, [Parameter(Mandatory=$true)][string]$Fixture)
$ErrorActionPreference = 'Stop'
# pwsh CI parents can omit Windows PowerShell modules from the inherited path.
$env:PSModulePath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\Modules;$env:PSModulePath"
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('project-go-launcher-' + [Guid]::NewGuid().ToString('N'))
$cjkName = ([char]0x4e2d).ToString() + [char]0x6587 + ' path'
try {
    $source = Join-Path $testRoot $cjkName
    New-Item -ItemType Directory -Path $source | Out-Null
    Get-ChildItem -LiteralPath $Fixture -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $source -Recurse }
    $before = @(Get-ChildItem -LiteralPath $source -Recurse -File | Get-FileHash | Sort-Object Path)
    $document = Join-Path $testRoot ($cjkName + '.json')
    & $Launcher translate export $source --out $document
    if ($LASTEXITCODE -ne 0) { throw 'Unicode launcher export failed' }
    $data = [IO.File]::ReadAllText($document, [Text.Encoding]::UTF8) | ConvertFrom-Json
    if ($data.entries.Count -ne 18) { throw 'Unicode launcher lost source units' }
    & $Launcher --version
    if ($LASTEXITCODE -ne 0) { throw 'Launcher version failed' }
    & $Launcher translate export $source --out $document
    if ($LASTEXITCODE -eq 0) { throw 'Launcher lost overwrite-refusal exit code' }
    $after = @(Get-ChildItem -LiteralPath $source -Recurse -File | Get-FileHash | Sort-Object Path)
    if (($before | ConvertTo-Json -Compress) -cne ($after | ConvertTo-Json -Compress)) {
        throw 'Launcher modified source files'
    }
    Write-Output 'Windows launcher PASS: spaced installation/JDK, Unicode source/output, exit codes, unchanged source'
} finally {
    $resolvedRoot = [IO.Path]::GetFullPath($testRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolvedRoot.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected launcher test cleanup path'
    }
    if (Test-Path -LiteralPath $resolvedRoot) { Remove-Item -LiteralPath $resolvedRoot -Recurse -Force }
}
