# Windows PowerShell 5.1 / PowerShell 7 frontend; shared flow: scripts/build_support.py.
param(
    [Alias('h')][switch]$Help,
    [switch]$Install, [switch]$Release, [switch]$OfficialRelease, [switch]$SetupSigning,
    [switch]$Clean, [switch]$Offline, [switch]$Online, [switch]$SkipChecks,
    [switch]$Doctor, [string]$JavaHome, [string]$Sdk,
    [string]$SigningRoot, [string]$AdoptKeystore, [string]$KeyAlias,
    [string]$CredentialFile, [string]$KeyCredentialFile, [string]$CertificateSha256,
    [switch]$AdoptDebugKeystore, [string]$StorePassword,
    [switch]$GenerateNewKey, [switch]$ForceRebind,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$RemainingArgs
)
$ErrorActionPreference = 'Stop'
if ($AdoptDebugKeystore -or $GenerateNewKey -or $ForceRebind -or $PSBoundParameters.ContainsKey('StorePassword')) {
    throw 'Legacy signing mutation/password flags are disabled. Keep the original keystore; use -SetupSigning -AdoptKeystore -KeyAlias -CredentialFile -CertificateSha256. See docs/technical.md.'
}
. (Join-Path $PSScriptRoot 'scripts/python.ps1')
$pythonArgs = @()
foreach ($item in @(
    @('Help', '--help'), @('Install', '--install'), @('Release', '--release'),
    @('OfficialRelease', '--official-release'),
    @('SetupSigning', '--setup-signing'), @('Doctor', '--doctor'), @('Clean', '--clean'),
    @('Offline', '--offline'), @('Online', '--online'), @('SkipChecks', '--skip-checks')
)) {
    if ($PSBoundParameters.ContainsKey($item[0]) -and $PSBoundParameters[$item[0]]) { $pythonArgs += $item[1] }
}
foreach ($item in @(
    @('JavaHome', '--java-home'), @('Sdk', '--sdk'), @('SigningRoot', '--signing-root'),
    @('AdoptKeystore', '--adopt-keystore'), @('KeyAlias', '--key-alias'),
    @('CredentialFile', '--credential-file'), @('KeyCredentialFile', '--key-credential-file'),
    @('CertificateSha256', '--certificate-sha256')
)) {
    if ($PSBoundParameters.ContainsKey($item[0])) { $pythonArgs += @($item[1], [string]$PSBoundParameters[$item[0]]) }
}
$pythonArgs += $RemainingArgs
$code = Invoke-TemplatePython -Script (Join-Path $PSScriptRoot 'scripts/build_support.py') -ScriptArgs $pythonArgs
exit $code
