# Shared launcher for Windows PowerShell 5.1 and PowerShell 7.
function Invoke-TemplatePython {
    param([string]$Script, [string[]]$ScriptArgs)
    foreach ($candidate in @('py', 'python3', 'python')) {
        $command = Get-Command $candidate -CommandType Application -ErrorAction SilentlyContinue
        if ($null -eq $command) { continue }
        $prefix = @()
        if ($candidate -eq 'py') { $prefix = @('-3') }
        & $command.Source @prefix -X utf8 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)' 2>$null
        if ($LASTEXITCODE -ne 0) { continue }
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            # Output must go to Host, otherwise $code captures logs as well as the exit code.
            & $command.Source @prefix -X utf8 $Script @ScriptArgs 2>&1 | ForEach-Object { Write-Host $_ }
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        return $code
    }
    throw 'Python 3.9+ is required. Install Python and add it to PATH; see build.md.'
}
