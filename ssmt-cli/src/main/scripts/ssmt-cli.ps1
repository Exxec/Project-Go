# Keep Unicode application arguments out of the JVM's Windows ANSI argv conversion.
# Picocli already supports UTF-8 argument files; no application-side path repair is needed.
$ErrorActionPreference = 'Stop'
$cliArguments = @($args)
$argumentFile = $null
$exitCode = 1
try {
    $javaExecutable = $env:JAVA_EXE
    if ([string]::IsNullOrWhiteSpace($javaExecutable)) {
        if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
            $javaExecutable = (Get-Command java.exe -ErrorAction Stop).Source
        } else {
            $javaExecutable = Join-Path $env:JAVA_HOME.Trim('"') 'bin\java.exe'
        }
    }
    $classPath = $env:CLASSPATH
    if ([string]::IsNullOrWhiteSpace($classPath)) {
        $cliInstallRoot = Split-Path -Parent $PSScriptRoot
        $classPath = Join-Path $cliInstallRoot 'lib\*'
    }
    $argumentFile = Join-Path ([IO.Path]::GetTempPath()) ('ssmt-cli-' + [Guid]::NewGuid().ToString('N') + '.args')
    $lines = foreach ($argument in $cliArguments) {
        '"' + ([string]$argument).Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n') + '"'
    }
    [IO.File]::WriteAllLines($argumentFile, [string[]]@($lines), [Text.UTF8Encoding]::new($false))
    $jvmOptions = @()
    foreach ($optionText in @($env:DEFAULT_JVM_OPTS, $env:JAVA_OPTS, $env:SSMT_CLI_OPTS)) {
        if (-not [string]::IsNullOrWhiteSpace($optionText)) {
            $jvmOptions += @([regex]::Matches($optionText, '(?:[^\s"]+|"[^"]*")+') | ForEach-Object {
                $_.Value.Replace('"', '')
            })
        }
    }
    # Native stderr warnings must retain the JVM exit code rather than become PowerShell failures.
    $ErrorActionPreference = 'Continue'
    & $javaExecutable @jvmOptions -classpath $classPath com.ssmt.cli.Main ('@' + $argumentFile)
    $exitCode = $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
} finally {
    if ($null -ne $argumentFile) {
        Remove-Item -LiteralPath $argumentFile -Force -ErrorAction SilentlyContinue
    }
}
exit $exitCode
