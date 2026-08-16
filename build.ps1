<#
.SYNOPSIS
    Android APK 项目唯一构建入口。

.DESCRIPTION
    .\build.ps1                 # Debug 构建
    .\build.ps1 -Install        # Debug 构建 + adb 安装
    .\build.ps1 -Release        # Release 签名打包到 dist/
    .\build.ps1 -SetupSigning   # 配置/重绑 Release 签名
    .\build.ps1 -Help           # 打印参数说明

.PARAMETER Clean
    构建前执行 clean。Release 默认不 clean（Windows 上易被 Studio/Gradle 锁文件）。
    显式 -Clean 时会先 gradlew --stop 并重试删除。

.PARAMETER Offline
    Gradle 离线模式（Release 默认开启；Debug 默认联网）。

.PARAMETER Online
    Release 时允许联网拉依赖。

.PARAMETER SkipChecks
    跳过 unit test / lint。

.PARAMETER SigningRoot
    签名元数据目录。默认 %LOCALAPPDATA%\<rootProject.name>\signing。

.PARAMETER AdoptDebugKeystore / AdoptKeystore / GenerateNewKey / ForceRebind
    仅 -SetupSigning 使用。默认沿用 ~/.android/debug.keystore。

.PARAMETER Help
    打印用法与参数说明后退出。
#>
param(
    [Alias("h")]
    [switch]$Help,
    [switch]$Install,
    [switch]$Release,
    [switch]$SetupSigning,
    [switch]$Clean,
    [switch]$Offline,
    [switch]$Online,
    [switch]$SkipChecks,
    [string]$SigningRoot = "",
    [switch]$AdoptDebugKeystore,
    [string]$AdoptKeystore = "C:\Users\lamuier\Code\release.keystore",
    [string]$KeyAlias = "release",
    [string]$StorePassword,
    [switch]$GenerateNewKey,
    [switch]$ForceRebind,
    [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$RemainingArgs
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$JavaHome = "C:\Program Files\Android\Android Studio\jbr"

Set-Location -LiteralPath $ProjectRoot

function Show-BuildHelp {
    @"
用法:
  .\build.ps1 [模式] [选项]

模式（互斥，默认 Debug）:
  (无)              Debug 构建 → app\build\outputs\apk\debug\app-debug.apk
  -Install          Debug 构建 + adb install -r
  -Release          Release 签名打包 → dist\<RootName>-v<version>-release.apk
  -SetupSigning     配置/重绑 Release 签名元数据

常用选项:
  -Clean            构建前清理 app\build（Release 默认不 clean）
  -Offline          Gradle --offline（Debug 默认联网；Release 默认离线）
  -Online           Release 允许联网拉依赖（覆盖默认离线）
  -SkipChecks       跳过 testDebugUnitTest 与 lint
  -Help / -h        显示本说明

SetupSigning 专用:
  -AdoptDebugKeystore           绑定本机 ~/.android/debug.keystore（默认）
  -AdoptKeystore <path>         绑定已有 keystore
  -KeyAlias <alias>             keystore 别名（AdoptKeystore 时常用）
  -StorePassword <pass>         keystore 密码
  -GenerateNewKey               生成新的 release keystore
  -ForceRebind                  覆盖已有签名绑定
  -SigningRoot <dir>            签名元数据目录（默认 %LOCALAPPDATA%\<RootName>\signing）

示例:
  .\build.ps1
  .\build.ps1 -Install
  .\build.ps1 -Release -Online
  .\build.ps1 -Release -SkipChecks -Clean
  .\build.ps1 -SetupSigning -AdoptDebugKeystore -ForceRebind
  .\build.ps1 -SetupSigning -AdoptKeystore C:\keys\release.keystore -KeyAlias release -StorePassword ****
"@ | Write-Host
}

# --- helpers ---

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-EffectiveOptions {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("Debug", "Install", "Release", "SetupSigning")]
        [string]$Mode
    )

    # Debug/Install：默认联网，仅 -Offline 离线
    # Release：默认离线，-Online 联网；若同时传 -Offline 仍离线
    if ($Mode -eq "Release") {
        $gradleOffline = -not $Online.IsPresent
        if ($Offline.IsPresent) { $gradleOffline = $true }
    } else {
        $gradleOffline = $Offline.IsPresent
    }

    $identity = $null
    try { $identity = Get-AppIdentity -Mode $Mode } catch { }

    Write-Host ""
    Write-Host "生效参数" -ForegroundColor Cyan
    Write-Host "  Mode          : $Mode"
    if ($null -ne $identity) {
        Write-Host "  RootName      : $($identity.RootName)"
        Write-Host "  applicationId : $($identity.ApplicationId)"
        Write-Host "  versionName   : $($identity.VersionName)"
        Write-Host "  versionCode   : $($identity.VersionCode)"
        if ($Mode -in @("Debug", "Install")) {
            Write-Host "  并存说明      : Debug 使用独立包名，不覆盖本机 Release"
        }
    }
    Write-Host "  Clean         : $($Clean.IsPresent) $(if ($Clean.IsPresent) { '(已开启)' } else { '(未开启)' })"
    Write-Host "  SkipChecks    : $($SkipChecks.IsPresent) $(if ($SkipChecks.IsPresent) { '(跳过 test/lint)' } else { '(将跑 test/lint)' })"
    if ($Mode -in @("Debug", "Install", "Release")) {
        Write-Host "  Offline 传入  : $($Offline.IsPresent)"
        Write-Host "  Online 传入   : $($Online.IsPresent)"
        Write-Host "  → Gradle离线  : $gradleOffline"
    }
    if ($Mode -eq "SetupSigning") {
        Write-Host "  AdoptDebugKeystore : $($AdoptDebugKeystore.IsPresent)"
        Write-Host "  AdoptKeystore      : $(if ([string]::IsNullOrWhiteSpace($AdoptKeystore)) { '(空)' } else { $AdoptKeystore })"
        Write-Host "  KeyAlias           : $(if ([string]::IsNullOrWhiteSpace($KeyAlias)) { '(空/默认)' } else { $KeyAlias })"
        Write-Host "  StorePassword      : $(if ([string]::IsNullOrWhiteSpace($StorePassword)) { '(空)' } else { '***' })"
        Write-Host "  GenerateNewKey     : $($GenerateNewKey.IsPresent)"
        Write-Host "  ForceRebind        : $($ForceRebind.IsPresent)"
    }
    if (-not [string]::IsNullOrWhiteSpace($SigningRoot)) {
        Write-Host "  SigningRoot   : $SigningRoot (显式指定)"
    } elseif ($null -ne $identity) {
        Write-Host "  SigningRoot   : $(Resolve-DefaultSigningRoot $identity.RootName) (默认)"
    }
}

function Resolve-JavaHome {
    $java = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $java)) {
        throw "找不到 Android Studio JBR: $JavaHome（请安装 Android Studio 或调整 build.ps1 中的路径）"
    }
    return $JavaHome
}

function Resolve-AndroidSdk {
    $candidates = @()
    $localProps = Join-Path $ProjectRoot "local.properties"
    if (Test-Path -LiteralPath $localProps) {
        $line = Get-Content -LiteralPath $localProps |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($line -match 'sdk\.dir\s*=\s*(.+)$') {
            $backslash = [string][char]0x5C
            $candidates += (($Matches[1].Trim() -replace '\\:', ':') -replace '\\\\', $backslash)
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $candidates += $env:ANDROID_HOME }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { $candidates += $env:ANDROID_SDK_ROOT }
    $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk")

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        if (Test-Path -LiteralPath "$candidate\platforms\android-37.0\android.jar") {
            return $candidate
        }
    }
    throw "找不到 Android SDK 37。已检查: $($candidates -join '; ')"
}

function Get-AppIdentity([string]$Mode = "Release") {
    $gradleConfig = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot "app\build.gradle.kts")
    $settings = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot "settings.gradle.kts")
    $versionCodeMatch = [regex]::Match($gradleConfig, 'versionCode\s*=\s*(\d+)')
    $versionNameMatch = [regex]::Match($gradleConfig, 'versionName\s*=\s*"([^"]+)"')
    $appIdMatch = [regex]::Match($gradleConfig, 'applicationId\s*=\s*"([^"]+)"')
    $debugSuffixMatch = [regex]::Match(
        $gradleConfig,
        'debug\s*\{[\s\S]*?applicationIdSuffix\s*=\s*"([^"]+)"'
    )
    $rootNameMatch = [regex]::Match($settings, 'rootProject\.name\s*=\s*"([^"]+)"')
    if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success -or -not $appIdMatch.Success) {
        throw "无法从 app\build.gradle.kts 读取 versionCode / versionName / applicationId"
    }
    $applicationId = $appIdMatch.Groups[1].Value
    $versionName = $versionNameMatch.Groups[1].Value
    if ($Mode -in @("Debug", "Install")) {
        if ($debugSuffixMatch.Success) {
            $applicationId = "$applicationId$($debugSuffixMatch.Groups[1].Value)"
        }
        $versionName = "$versionName-debug"
    }
    return [pscustomobject]@{
        VersionCode = [int]$versionCodeMatch.Groups[1].Value
        VersionName = $versionName
        ApplicationId = $applicationId
        RootName = if ($rootNameMatch.Success) { $rootNameMatch.Groups[1].Value } else { "App" }
    }
}

function Resolve-DefaultSigningRoot([string]$RootName) {
    if (-not [string]::IsNullOrWhiteSpace($SigningRoot)) { return $SigningRoot }
    return (Join-Path $env:LOCALAPPDATA "$RootName\signing")
}

function Invoke-Native([string]$Executable, [string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        return @(& $Executable @Arguments 2>&1)
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Invoke-Gradle([string[]]$GradleArgs) {
    $env:JAVA_HOME = Resolve-JavaHome
    $env:ANDROID_HOME = Resolve-AndroidSdk
    & (Join-Path $ProjectRoot "gradlew.bat") @GradleArgs 2>&1 | ForEach-Object {
        Write-Host $_
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle 失败 (exit $LASTEXITCODE): $($GradleArgs -join ' ')"
    }
}

function Stop-GradleDaemons {
    Write-Step "停止 Gradle daemon（释放 app\build 文件锁）"
    $env:JAVA_HOME = Resolve-JavaHome
    $env:ANDROID_HOME = Resolve-AndroidSdk
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & (Join-Path $ProjectRoot "gradlew.bat") "--stop" | Out-Host
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Clear-LintCache {
    $lintCache = Join-Path $ProjectRoot "app\build\intermediates\lint-cache"
    if (-not (Test-Path -LiteralPath $lintCache)) { return }
    try {
        Remove-Item -LiteralPath $lintCache -Recurse -Force -ErrorAction Stop
        Write-Host "  已清理 lint-cache"
    } catch {
        Write-Host "  lint-cache 仍被占用（可关闭 Android Studio 后重试）" -ForegroundColor Yellow
    }
}

function Clear-AppBuildDirectory {
    $buildDir = Join-Path $ProjectRoot "app\build"
    if (-not (Test-Path -LiteralPath $buildDir)) { return }

    Stop-GradleDaemons

    $attempts = 3
    for ($i = 1; $i -le $attempts; $i++) {
        try {
            Remove-Item -LiteralPath $buildDir -Recurse -Force -ErrorAction Stop
            Write-Host "  已删除 app\build"
            return
        } catch {
            if ($i -eq $attempts) {
                throw @"
无法删除 app\build（文件被占用）。

请关闭 Android Studio 对本工程的打开/索引，或手动删除：
  $buildDir

然后重试。也可不加 -Clean 做增量构建。
"@
            }
            Write-Host "  删除失败，第 $i/$attempts 次重试…" -ForegroundColor Yellow
            Start-Sleep -Seconds 2
            Stop-GradleDaemons
        }
    }
}

function Get-LatestBuildTools([string]$Sdk) {
    $dir = Get-ChildItem -LiteralPath (Join-Path $Sdk "build-tools") -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
    if ($null -eq $dir) { throw "Android build-tools 未找到: $Sdk" }
    return $dir.FullName
}

# --- signing ---

function Get-CertSha256([string]$CertPath) {
    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($CertPath)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($certificate.RawData))).Replace("-", "").ToUpperInvariant()
    } finally {
        $sha.Dispose()
        $certificate.Dispose()
    }
}

function Write-SigningMetadata {
    param(
        [string]$Root,
        [string]$StoreFile,
        [string]$KeyAliasName,
        [string]$PlainPassword,
        [string]$Source
    )

    $keytool = Join-Path (Resolve-JavaHome) "bin\keytool.exe"
    $metadataFile = Join-Path $Root "release-signing.json"
    $certificateFile = Join-Path $Root "release-cert.cer"
    $credentialFile = Join-Path $Root "release-password.clixml"
    $pinnedFile = Join-Path $Root "pinned-certificate.sha256"

    New-Item -ItemType Directory -Force -Path $Root | Out-Null

    $env:ANDROID_KEYTOOL_PASS = $PlainPassword
    try {
        $exportArgs = @(
            "-exportcert",
            "-keystore", $StoreFile,
            "-alias", $KeyAliasName,
            "-file", $certificateFile,
            "-storepass:env", "ANDROID_KEYTOOL_PASS"
        )
        if ($StoreFile.ToLowerInvariant().EndsWith(".p12")) {
            $exportArgs = @(
                "-exportcert",
                "-keystore", $StoreFile,
                "-storetype", "PKCS12",
                "-alias", $KeyAliasName,
                "-file", $certificateFile,
                "-storepass:env", "ANDROID_KEYTOOL_PASS"
            )
        }
        $exportOutput = Invoke-Native $keytool $exportArgs
        if ($LASTEXITCODE -ne 0) {
            $exportOutput | ForEach-Object { Write-Host $_ }
            throw "keytool 导出证书失败"
        }
    } finally {
        Remove-Item Env:ANDROID_KEYTOOL_PASS -ErrorAction SilentlyContinue
    }

    $securePassword = ConvertTo-SecureString $PlainPassword -AsPlainText -Force
    [pscredential]::new($KeyAliasName, $securePassword) | Export-Clixml -LiteralPath $credentialFile

    $fingerprint = Get-CertSha256 $certificateFile
    [ordered]@{
        schemaVersion = 2
        storeFile = $StoreFile
        credentialFile = $credentialFile
        keyAlias = $KeyAliasName
        certificateFile = $certificateFile
        certificateSha256 = $fingerprint
        source = $Source
        keyPasswordSameAsStore = $true
        createdAt = (Get-Date).ToString("o")
    } | ConvertTo-Json | Set-Content -LiteralPath $metadataFile -Encoding utf8

    Set-Content -LiteralPath $pinnedFile -Value $fingerprint -Encoding ascii

    Write-Host "Release 签名已配置: $metadataFile"
    Write-Host "  Store     : $StoreFile"
    Write-Host "  Alias     : $KeyAliasName"
    Write-Host "  Cert SHA256: $fingerprint"
}

function Invoke-SetupSigning {
    $identity = Get-AppIdentity
    $SigningRoot = Resolve-DefaultSigningRoot $identity.RootName

    $adoptExisting = -not [string]::IsNullOrWhiteSpace($AdoptKeystore)
    $useDebug = $AdoptDebugKeystore
    $useNew = $GenerateNewKey

    if (-not $useDebug -and -not $useNew -and -not $adoptExisting) {
        throw "请指定签名来源之一: -GenerateNewKey（正式密钥）| -AdoptKeystore <path> -KeyAlias ... -StorePassword ... | -AdoptDebugKeystore（仅本机调试）"
    }
    if ((@($useDebug, $useNew, $adoptExisting) | Where-Object { $_ }).Count -gt 1) {
        throw "-AdoptDebugKeystore、-AdoptKeystore 与 -GenerateNewKey 只能选其一"
    }
    if ($adoptExisting) {
        if (-not (Test-Path -LiteralPath $AdoptKeystore)) { throw "找不到密钥库: $AdoptKeystore" }
        if ([string]::IsNullOrWhiteSpace($KeyAlias)) { throw "-AdoptKeystore 需要 -KeyAlias" }
        if ([string]::IsNullOrWhiteSpace($StorePassword)) { throw "-AdoptKeystore 需要 -StorePassword" }
    }

    $metadataFile = Join-Path $SigningRoot "release-signing.json"
    if ((Test-Path -LiteralPath $metadataFile) -and -not $ForceRebind) {
        $metadata = Get-Content -Raw -LiteralPath $metadataFile | ConvertFrom-Json
        foreach ($requiredPath in @($metadata.storeFile, $metadata.credentialFile, $metadata.certificateFile)) {
            if (-not (Test-Path -LiteralPath $requiredPath)) {
                throw "签名配置不完整: $requiredPath"
            }
        }
        Write-Host "Release 签名已存在: $metadataFile"
        Write-Host "  Cert SHA256: $($metadata.certificateSha256)"
        Write-Host "  Source     : $($metadata.source)"
        Write-Host "重绑请加: -SetupSigning -GenerateNewKey -ForceRebind  （或 -AdoptKeystore / -AdoptDebugKeystore）"
        return
    }

    if ((Test-Path -LiteralPath $metadataFile) -and $ForceRebind) {
        $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $backupRoot = Join-Path $SigningRoot "backup-$stamp"
        New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
        Get-ChildItem -LiteralPath $SigningRoot -File | ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $backupRoot $_.Name) -Force
        }
        Write-Host "已备份旧签名资产到: $backupRoot"
    }

    if ($adoptExisting) {
        $resolvedStore = (Resolve-Path -LiteralPath $AdoptKeystore).Path
        Write-SigningMetadata -Root $SigningRoot -StoreFile $resolvedStore `
            -KeyAliasName $KeyAlias -PlainPassword $StorePassword -Source "existing-keystore"
        return
    }

    if ($useDebug) {
        $debugStore = Join-Path $env:USERPROFILE ".android\debug.keystore"
        if (-not (Test-Path -LiteralPath $debugStore)) {
            throw "未找到 debug.keystore: $debugStore（请先用 Android Studio 跑一次 Debug 构建）"
        }
        Write-SigningMetadata -Root $SigningRoot -StoreFile $debugStore `
            -KeyAliasName "androiddebugkey" -PlainPassword "android" -Source "android-debug-keystore"
        return
    }

    $alias = "android-apk-release"
    $storeFile = Join-Path $SigningRoot "android-apk-release.p12"
    if ((Test-Path -LiteralPath $storeFile) -and -not $ForceRebind) {
        throw "拒绝覆盖已有密钥库: $storeFile（请加 -ForceRebind）"
    }

    New-Item -ItemType Directory -Force -Path $SigningRoot | Out-Null
    $passwordBytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
    $plainPassword = [Convert]::ToBase64String($passwordBytes)
    $keytool = Join-Path (Resolve-JavaHome) "bin\keytool.exe"
    $env:ANDROID_KEYTOOL_PASS = $plainPassword
    try {
        $generateArgs = @(
            "-genkeypair", "-noprompt",
            "-keystore", $storeFile,
            "-storetype", "PKCS12",
            "-alias", $alias,
            "-keyalg", "RSA",
            "-keysize", "4096",
            "-sigalg", "SHA256withRSA",
            "-validity", "10000",
            "-dname", "CN=$($identity.RootName) Release,O=Lamuier,C=CN",
            "-storepass:env", "ANDROID_KEYTOOL_PASS",
            "-keypass:env", "ANDROID_KEYTOOL_PASS"
        )
        $generateOutput = Invoke-Native $keytool $generateArgs
        if ($LASTEXITCODE -ne 0) {
            $generateOutput | ForEach-Object { Write-Host $_ }
            throw "keytool 生成 Release 密钥失败"
        }
        Write-Host "警告: 已生成全新 Release 密钥，旧安装包无法覆盖升级。" -ForegroundColor Yellow
        Write-SigningMetadata -Root $SigningRoot -StoreFile $storeFile `
            -KeyAliasName $alias -PlainPassword $plainPassword -Source "generated-pkcs12"
    } finally {
        Remove-Item Env:ANDROID_KEYTOOL_PASS -ErrorAction SilentlyContinue
        [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
        $plainPassword = $null
    }
}

function Import-ReleaseSigningEnv([string]$MetadataFile) {
    $metadata = Get-Content -Raw -LiteralPath $MetadataFile | ConvertFrom-Json
    foreach ($requiredPath in @($metadata.storeFile, $metadata.credentialFile, $metadata.certificateFile)) {
        if (-not (Test-Path -LiteralPath $requiredPath)) {
            throw "签名资产缺失: $requiredPath"
        }
    }
    $credential = Import-Clixml -LiteralPath $metadata.credentialFile
    $plainPassword = $credential.GetNetworkCredential().Password
    $env:ANDROID_RELEASE_STORE_FILE = [string]$metadata.storeFile
    $env:ANDROID_RELEASE_STORE_PASSWORD = $plainPassword
    $env:ANDROID_RELEASE_KEY_ALIAS = [string]$metadata.keyAlias
    $env:ANDROID_RELEASE_KEY_PASSWORD = $plainPassword
    return [pscustomobject]@{
        Metadata = $metadata
        PlainPassword = $plainPassword
    }
}

function Clear-ReleaseSigningEnv {
    foreach ($name in @(
        "ANDROID_RELEASE_STORE_FILE",
        "ANDROID_RELEASE_STORE_PASSWORD",
        "ANDROID_RELEASE_KEY_ALIAS",
        "ANDROID_RELEASE_KEY_PASSWORD"
    )) {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}

function Ensure-ReleaseSigningConfigured([string]$Root) {
    $metadataFile = Join-Path $Root "release-signing.json"
    if (Test-Path -LiteralPath $metadataFile) { return $metadataFile }

    throw @"
尚未配置 Release 签名，已禁止自动使用 debug.keystore。
请先执行其一：
  .\build.ps1 -SetupSigning -GenerateNewKey
  .\build.ps1 -SetupSigning -AdoptKeystore <path> -KeyAlias <alias> -StorePassword <pass>
仅本机调试覆盖安装时可：
  .\build.ps1 -SetupSigning -AdoptDebugKeystore
"@
}

# --- verify ---

function Test-ReleaseApk {
    param(
        [string]$Apk,
        [string]$MetadataFile,
        [int]$ExpectedVersionCode,
        [string]$ExpectedVersionName,
        [string]$ExpectedApplicationId,
        [string]$Sdk
    )

    $env:JAVA_HOME = Resolve-JavaHome
    $env:ANDROID_HOME = $Sdk
    $apkPath = (Resolve-Path -LiteralPath $Apk).Path

    $buildTools = Get-LatestBuildTools $Sdk
    $apkSigner = Join-Path $buildTools "apksigner.bat"
    $zipAlign = Join-Path $buildTools "zipalign.exe"
    $aapt = Join-Path $buildTools "aapt.exe"
    $metadata = Get-Content -Raw -LiteralPath $MetadataFile | ConvertFrom-Json

    $signatureOutput = Invoke-Native $apkSigner @("verify", "--verbose", "--print-certs", $apkPath)
    $signatureOutput | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "APK 签名校验失败" }

    $digestLine = $signatureOutput | Where-Object { $_ -match "certificate SHA-256 digest:\s*([0-9a-fA-F]+)" } | Select-Object -First 1
    if ($null -eq $digestLine) { throw "无法读取 APK 证书指纹" }
    $actualFingerprint = ([regex]::Match($digestLine, "certificate SHA-256 digest:\s*([0-9a-fA-F]+)").Groups[1].Value).ToUpperInvariant()
    $expectedFingerprint = ([string]$metadata.certificateSha256).Replace(":", "").ToUpperInvariant()
    if ($actualFingerprint -ne $expectedFingerprint) {
        throw "签名证书不匹配。期望 $expectedFingerprint，实际 $actualFingerprint"
    }

    [void](Invoke-Native $zipAlign @("-c", "-v", "4", $apkPath))
    if ($LASTEXITCODE -ne 0) { throw "zipalign 校验失败" }

    $badging = Invoke-Native $aapt @("dump", "badging", $apkPath)
    if ($LASTEXITCODE -ne 0) { throw "无法读取 APK badging" }
    $packageLine = $badging | Where-Object { $_ -like "package:*" } | Select-Object -First 1
    $escapedAppId = [regex]::Escape($ExpectedApplicationId)
    if ($packageLine -notmatch "name='$escapedAppId'") { throw "包名不正确: $packageLine" }
    if ($packageLine -notmatch "versionCode='$ExpectedVersionCode'") { throw "versionCode 不正确: $packageLine" }
    $escapedVersionName = [regex]::Escape($ExpectedVersionName)
    if ($packageLine -notmatch "versionName='$escapedVersionName'") { throw "versionName 不正确: $packageLine" }
    if (($badging | Where-Object { $_ -like "sdkVersion:*" } | Select-Object -First 1) -notmatch "'26'") {
        throw "minSdkVersion 不正确"
    }
    if (($badging | Where-Object { $_ -like "targetSdkVersion:*" } | Select-Object -First 1) -notmatch "'37'") {
        throw "targetSdkVersion 不正确"
    }

    $permissions = Invoke-Native $aapt @("dump", "permissions", $apkPath)
    if ($LASTEXITCODE -ne 0) { throw "无法读取 APK 权限" }
    $permissionNames = foreach ($line in $permissions) {
        if ($line -match "uses-permission: name='([^']+)'") { $Matches[1] }
    }
    $unexpectedPermissions = $permissionNames | Where-Object {
        $_ -notin @(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.POST_PROMOTED_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED"
        ) -and -not $_.EndsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }
    if ($unexpectedPermissions) { throw "意外权限: $($unexpectedPermissions -join ', ')" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        $nativeAbis = $archive.Entries |
            Where-Object { $_.FullName -match '^lib/[^/]+/[^/]+\.so$' } |
            ForEach-Object { $_.FullName.Split('/')[1] } |
            Sort-Object -Unique
    } finally {
        $archive.Dispose()
    }
    if ($nativeAbis) {
        $unexpectedAbis = $nativeAbis | Where-Object { $_ -notin @('arm64-v8a') }
        if ($unexpectedAbis) { throw "不支持的 ABI: $($unexpectedAbis -join ', ')" }
    }

    $manifestLines = Invoke-Native $aapt @("dump", "xmltree", $apkPath, "AndroidManifest.xml")
    if ($LASTEXITCODE -ne 0) { throw "无法读取 AndroidManifest.xml" }
    $manifest = $manifestLines -join "`n"
    if ($manifest -match "android:debuggable") { throw "Release APK 可调试" }
    if ($manifest -match "android:networkSecurityConfig") { throw "存在意外 networkSecurityConfig" }
    if ($manifest -notmatch "android:allowBackup[^\n]*0x0") { throw "allowBackup 不是 false" }
    if ($manifest -notmatch "android:usesCleartextTraffic[^\n]*0x0") { throw "usesCleartextTraffic 不是 false" }

    Write-Host "校验通过: $ExpectedApplicationId $ExpectedVersionName ($ExpectedVersionCode)"
    Write-Host "  ABIs      : $(if ($nativeAbis) { $nativeAbis -join ', ' } else { '(none)' })"
    Write-Host "  Cert SHA256: $actualFingerprint"
}

# --- build actions ---

function Invoke-Assemble {
    param(
        [ValidateSet("Debug", "Release")]
        [string]$Variant,
        [switch]$DoClean,
        [switch]$DoOffline,
        [switch]$DoSkipChecks
    )

    if ($DoClean) {
        Write-Step "Clean app\build"
        Clear-AppBuildDirectory
    } else {
        Stop-GradleDaemons
        Clear-LintCache
    }

    $arguments = @()
    $arguments += "--no-daemon"
    if ($DoOffline) { $arguments += "--offline" }
    if (-not $DoSkipChecks) {
        $arguments += "testDebugUnitTest"
        if ($Variant -eq "Release") {
            $arguments += "lintVitalRelease"
        } else {
            $arguments += "lintDebug"
        }
    }
    $arguments += ":app:assemble$Variant"

    Write-Step "Gradle assemble$Variant"
    Write-Host "  命令行: gradlew.bat $($arguments -join ' ')" -ForegroundColor DarkGray
    $attempts = 2
    for ($i = 1; $i -le $attempts; $i++) {
        try {
            Invoke-Gradle $arguments
            break
        } catch {
            $msg = "$_"
            $isLock = $msg -match 'FileSystemException|正在使用此文件|cannot access|AccessDenied|Unable to delete'
            if (-not $isLock -or $i -eq $attempts) { throw }
            Write-Host "  检测到文件锁，停止 daemon、清理 lint-cache 后重试 ($i/$attempts)…" -ForegroundColor Yellow
            Stop-GradleDaemons
            Start-Sleep -Seconds 2
            Clear-LintCache
        }
    }

    $variantFolder = $Variant.ToLowerInvariant()
    $apk = Join-Path $ProjectRoot "app\build\outputs\apk\$variantFolder\app-$variantFolder.apk"
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "未生成 APK: $apk"
    }
    Write-Host "APK: $apk"
    return $apk
}

function Invoke-DebugBuild {
    $useOffline = $Offline.IsPresent
    Invoke-Assemble -Variant Debug -DoClean:$Clean -DoOffline:$useOffline -DoSkipChecks:$SkipChecks | Out-Null
}

function Invoke-InstallDebug {
    $apk = Invoke-Assemble -Variant Debug -DoClean:$Clean -DoOffline:$Offline -DoSkipChecks:$SkipChecks
    $sdk = Resolve-AndroidSdk
    $adb = Join-Path $sdk "platform-tools\adb.exe"
    if (-not (Test-Path -LiteralPath $adb)) { throw "找不到 adb: $adb" }

    $identity = Get-AppIdentity -Mode Install
    Write-Step "adb install ($($identity.ApplicationId)，不覆盖 Release)"
    & $adb devices
    if ($LASTEXITCODE -ne 0) { throw "adb devices 失败" }
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "adb install 失败" }
    Write-Host "已安装: $apk" -ForegroundColor Green
    Write-Host "  applicationId : $($identity.ApplicationId)" -ForegroundColor Green
}

function Invoke-ReleasePackage {
    $identity = Get-AppIdentity
    $sdk = Resolve-AndroidSdk
    $SigningRoot = Resolve-DefaultSigningRoot $identity.RootName
    $artifactBaseName = "$($identity.RootName)-v$($identity.VersionName)"

    Write-Host "$($identity.RootName) Release 打包"
    Write-Host "  applicationId : $($identity.ApplicationId)"
    Write-Host "  versionName   : $($identity.VersionName)"
    Write-Host "  versionCode   : $($identity.VersionCode)"
    Write-Host "  Android SDK   : $sdk"
    Write-Host "  SigningRoot   : $SigningRoot"
    Write-Host "  Clean         : $($Clean.IsPresent)"

    $metadataFile = Ensure-ReleaseSigningConfigured -Root $SigningRoot

    $pinnedFile = Join-Path $SigningRoot "pinned-certificate.sha256"
    if (Test-Path -LiteralPath $pinnedFile) {
        $pinned = (Get-Content -Raw -LiteralPath $pinnedFile).Trim().Replace(":", "").ToUpperInvariant()
        $metaProbe = Get-Content -Raw -LiteralPath $metadataFile | ConvertFrom-Json
        $current = ([string]$metaProbe.certificateSha256).Replace(":", "").ToUpperInvariant()
        if ($pinned -and $current -ne $pinned) {
            throw "签名证书与 pinned-certificate.sha256 不一致。请运行: .\build.ps1 -SetupSigning -AdoptDebugKeystore -ForceRebind"
        }
    }

    $signing = $null
    try {
        Write-Step "加载 Release 签名"
        $signing = Import-ReleaseSigningEnv -MetadataFile $metadataFile
        Write-Host "  keyAlias    : $($signing.Metadata.keyAlias)"
        Write-Host "  cert SHA256 : $($signing.Metadata.certificateSha256)"
        Write-Host "  source      : $($signing.Metadata.source)"

        # Release 默认离线；-Online 可联网。默认不 clean。
        $useOffline = -not $Online.IsPresent
        if ($Offline.IsPresent) { $useOffline = $true }

        $apk = Invoke-Assemble -Variant Release -DoClean:$Clean -DoOffline:$useOffline -DoSkipChecks:$SkipChecks

        Write-Step "校验 Release APK"
        Test-ReleaseApk `
            -Apk $apk `
            -MetadataFile $metadataFile `
            -ExpectedVersionCode $identity.VersionCode `
            -ExpectedVersionName $identity.VersionName `
            -ExpectedApplicationId $identity.ApplicationId `
            -Sdk $sdk

        Write-Step "导出产物到 dist/"
        $dist = Join-Path $ProjectRoot "dist"
        New-Item -ItemType Directory -Force -Path $dist | Out-Null

        $destination = Join-Path $dist "$artifactBaseName-release.apk"
        Copy-Item -LiteralPath $apk -Destination $destination -Force

        $mapping = Join-Path $ProjectRoot "app\build\outputs\mapping\release\mapping.txt"
        if (Test-Path -LiteralPath $mapping) {
            Copy-Item -LiteralPath $mapping -Destination (Join-Path $dist "$artifactBaseName-mapping.txt") -Force
        }

        $hash = Get-FileHash -LiteralPath $destination -Algorithm SHA256
        "$($hash.Hash)  $([IO.Path]::GetFileName($destination))" |
            Set-Content -LiteralPath (Join-Path $dist "$artifactBaseName-release.sha256") -Encoding ascii

        [ordered]@{
            packageName = $identity.ApplicationId
            versionCode = $identity.VersionCode
            versionName = $identity.VersionName
            apk = $destination
            apkSha256 = $hash.Hash
            certificateSha256 = [string]$signing.Metadata.certificateSha256
            builtAt = (Get-Date).ToString("o")
        } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $dist "$artifactBaseName-build.json") -Encoding utf8

        Write-Host ""
        Write-Host "打包完成" -ForegroundColor Green
        Write-Host "  APK     : $destination"
        Write-Host "  SHA-256 : $($hash.Hash)"
        Write-Host "  Size    : $([math]::Round((Get-Item $destination).Length / 1MB, 2)) MB"
    }
    finally {
        Clear-ReleaseSigningEnv
        if ($null -ne $signing) {
            $signing.PlainPassword = $null
        }
    }
}

# --- dispatch ---

$wantHelp = $Help.IsPresent -or (
    $RemainingArgs | Where-Object { $_ -in @("--help", "-help", "/?", "/help") }
)
if ($wantHelp) {
    Show-BuildHelp
    exit 0
}
if ($RemainingArgs) {
    throw "未知参数: $($RemainingArgs -join ' ')。查看帮助: .\build.ps1 -Help"
}

$modeCount = (@($Install, $Release, $SetupSigning) | Where-Object { $_ }).Count
if ($modeCount -gt 1) {
    throw "-Install、-Release、-SetupSigning 只能选其一"
}

if ($SetupSigning) {
    Write-EffectiveOptions -Mode SetupSigning
    Invoke-SetupSigning
} elseif ($Release) {
    Write-EffectiveOptions -Mode Release
    Invoke-ReleasePackage
} elseif ($Install) {
    Write-EffectiveOptions -Mode Install
    Invoke-InstallDebug
} else {
    Write-EffectiveOptions -Mode Debug
    Invoke-DebugBuild
}
