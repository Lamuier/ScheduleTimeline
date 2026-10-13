#!/usr/bin/env python3
"""Shared Windows/Linux/macOS build flow derived from agenT; standard library only."""
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SIGNING_KEYS = (
    "ANDROID_RELEASE_STORE_FILE", "ANDROID_RELEASE_STORE_PASSWORD",
    "ANDROID_RELEASE_KEY_ALIAS", "ANDROID_RELEASE_KEY_PASSWORD",
)


class BuildError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise BuildError(message)


def read_json(path):
    require(path.is_file(), "缺少文件：" + str(path))
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (ValueError, UnicodeError):
        raise BuildError("JSON 格式无效：" + str(path)) from None


def resolve_path(value, root):
    path = Path(value).expanduser()
    return (path if path.is_absolute() else root / path).resolve()


def app_config(root):
    source = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    result = {}
    for key in ("applicationId", "versionName", "buildToolsVersion"):
        match = re.search(r'\b' + key + r'\s*=\s*"([^"\n]+)"', source)
        require(match, "无法从 app/build.gradle.kts 读取 " + key)
        result[key] = match.group(1)
    for key in ("versionCode", "minSdk", "targetSdk", "compileSdk"):
        match = re.search(r"\b" + key + r"\s*=\s*(\d+)\b", source)
        if key == "compileSdk" and not match:
            match = re.search(r"compileSdk\s*\{\s*version\s*=\s*release\((\d+)\)", source)
        require(match, "无法从 app/build.gradle.kts 读取 " + key)
        result[key] = int(match.group(1))
    require(re.fullmatch(r"[A-Za-z0-9._-]+", result["versionName"]), "versionName 不适合用作产物文件名")
    settings = (root / "settings.gradle.kts").read_text(encoding="utf-8-sig")
    name = re.search(r'rootProject\.name\s*=\s*"([^"]+)"', settings)
    require(name and re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]*", name.group(1)), "rootProject.name 须为安全的项目名")
    result["projectName"] = name.group(1)
    policy = read_json(root / "build-policy.json")
    require(isinstance(policy, dict) and policy.get("schemaVersion") == 1, "build-policy.json 格式无效")
    for key in ("allowedAbis", "allowedPermissions", "forbiddenDexStrings"):
        values = policy.get(key)
        require(isinstance(values, list) and all(isinstance(v, str) and v for v in values), "构建策略字段无效：" + key)
    require(policy["allowedAbis"] and set(policy["allowedAbis"]) <= {"arm64-v8a", "x86_64"}, "模板仅支持 64 位 ABI")
    declared = re.search(r'abiFilters\s*\+=\s*setOf\(([^)]+)\)', source)
    require(declared and set(re.findall(r'"([^"]+)"', declared.group(1))) == set(policy["allowedAbis"]),
            "Gradle ABI 与 build-policy.json 不一致")
    result["policy"] = policy
    return result


def property_sdk(root):
    path = root / "local.properties"
    if not path.is_file():
        return None
    match = re.search(r"^\s*sdk\.dir\s*[:=]\s*(.*?)\s*$", path.read_text(encoding="utf-8"), re.M)
    if not match:
        return None
    # Android Studio writes Java-properties escaping (spaces, colons, backslashes).
    value = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), match.group(1))
    value = re.sub(r"\\(.)", r"\1", value)
    return resolve_path(value, root)


def find_sdk(root, explicit, env, host):
    local = property_sdk(root)
    if local and not local.is_dir():
        local = None  # Ignore stale paths copied from another OS.
    if explicit:
        sdk = resolve_path(explicit, root)
        require(not local or local == sdk, "--sdk 与 local.properties 的 sdk.dir 不一致；请先统一配置")
        return sdk
    if local:
        return local
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if env.get(key):
            return resolve_path(env[key], root)
    if host == "Windows":
        return Path(env.get("LOCALAPPDATA", str(Path.home() / "AppData/Local"))) / "Android/Sdk"
    suffix = "Library/Android/sdk" if host == "Darwin" else "Android/Sdk"
    return Path.home() / suffix


def java_major(output):
    match = re.search(r'(?:openjdk|java) version "(\d+)(?:\.(\d+))?', output)
    require(match, "无法识别 java -version 的结果")
    return int(match.group(2)) if match.group(1) == "1" else int(match.group(1))


def java_tool(home, tool):
    return home / "bin" / (tool + (".exe" if platform.system() == "Windows" else ""))


def sdk_tool(tools, tool, env):
    # Invoke apksigner's JAR directly: no .bat/cmd interpolation on Windows.
    if tool == "apksigner":
        return [java_tool(Path(env["JAVA_HOME"]), "java"), "-jar", tools / "lib/apksigner.jar"]
    return [tools / (tool + (".exe" if platform.system() == "Windows" else ""))]


def find_java(root, explicit, env, host):
    configured = explicit or env.get("JAVA_HOME")
    candidates = []
    if configured:
        candidates.append(resolve_path(configured, root))
    else:
        # PATH covers package-manager JDKs on Linux and many macOS installations.
        binary = shutil.which("javac.exe" if host == "Windows" else "javac", path=env.get("PATH"))
        if binary:
            candidates.append(Path(binary).resolve().parent.parent)
        if host == "Darwin":
            java_home = Path("/usr/libexec/java_home")
            if java_home.is_file():
                probe = subprocess.run([str(java_home), "-v", "17+"], capture_output=True, text=True)
                if probe.returncode == 0:
                    candidates.append(Path(probe.stdout.strip()))
            candidates.extend([
                Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home"),
                Path.home() / "Applications/Android Studio.app/Contents/jbr/Contents/Home",
            ])
            candidates.extend(sorted((Path.home() / ".jdks").glob("*/Contents/Home"), reverse=True))
            candidates.extend(sorted(Path("/Library/Java/JavaVirtualMachines").glob("*/Contents/Home"), reverse=True))
        elif host == "Windows":
            candidates.append(Path(env.get("ProgramFiles", "C:/Program Files")) / "Android/Android Studio/jbr")
            candidates.extend(sorted((Path.home() / ".jdks").glob("*"), reverse=True))
        else:
            candidates.extend([Path("/opt/android-studio/jbr"), Path.home() / "android-studio/jbr"])
            candidates.extend(sorted(Path("/usr/lib/jvm").glob("*"), reverse=True))
            candidates.extend(sorted((Path.home() / ".jdks").glob("*"), reverse=True))
    for home in candidates:
        if not all(java_tool(home, tool).is_file() for tool in ("java", "javac", "keytool")):
            continue
        probe = subprocess.run([str(java_tool(home, "java")), "-version"], capture_output=True, text=True)
        if probe.returncode == 0:
            major = java_major(probe.stdout + probe.stderr)
            if major >= 17:
                return home.resolve(), major
    raise BuildError("需要完整 JDK 17+（推荐 21）；请设置 JAVA_HOME 或 --java-home，见 docs/technical.md")


def fingerprint(value):
    require(isinstance(value, str), "签名证书 SHA-256 格式无效")
    value = value.strip().replace(":", "").upper()
    require(re.fullmatch(r"[0-9A-F]{64}", value), "签名证书 SHA-256 格式无效")
    return value


def load_signing(root, signing_root, env):
    metadata = read_json(signing_root / "release-signing.json")
    require(isinstance(metadata, dict) and metadata.get("schemaVersion") == 2,
            "签名元数据须为 schemaVersion=2，见 docs/technical.md")
    for key in ("storeFile", "keyAlias", "certificateSha256"):
        require(isinstance(metadata.get(key), str) and metadata[key].strip(), "签名元数据缺少 " + key)
    pin_file = signing_root / "pinned-certificate.sha256"
    require(pin_file.is_file(), "缺少证书指纹 pin：" + str(pin_file))
    certificate = fingerprint(metadata["certificateSha256"])
    require(certificate == fingerprint(pin_file.read_text(encoding="utf-8-sig")), "签名元数据与 pinned-certificate.sha256 不一致")
    store = resolve_path(metadata["storeFile"], root)
    require(store.is_file(), "签名密钥库不存在：" + str(store))
    require(metadata.get("credentialFormat") == "text", "跨平台构建需要 text 密码文件；旧 Windows DPAPI/clixml 请用原密码迁移，见 docs/technical.md")

    def password_file(key):
        require(isinstance(metadata.get(key), str) and metadata[key], "签名元数据缺少 " + key)
        path = resolve_path(metadata[key], root)
        require(path.is_file(), "密码文件不存在：" + str(path))
        password = path.read_text(encoding="utf-8").rstrip("\r\n")
        require(password and "\x00" not in password and "\n" not in password and "\r" not in password,
                "密码文件须包含一行非空密码：" + str(path))
        return password

    password = password_file("credentialFile")
    same_password = metadata.get("keyPasswordSameAsStore", True)
    require(isinstance(same_password, bool), "keyPasswordSameAsStore 须为布尔值")
    key_password = password if same_password else password_file("keyCredentialFile")
    env.update(dict(zip(SIGNING_KEYS, (str(store), password, metadata["keyAlias"], key_password))))
    return certificate


def capture(command, env, label, binary=False):
    result = subprocess.run([str(x) for x in command], env=env, capture_output=True)
    # Tool failures never echo credentials or raw signing configuration.
    require(result.returncode == 0, label + "失败（退出码 " + str(result.returncode) + "）")
    return result.stdout if binary else (result.stdout + result.stderr).decode("utf-8", errors="replace")


def validate_signing_key(java_home, env, certificate):
    der = capture([java_tool(java_home, "keytool"), "-exportcert", "-alias", env[SIGNING_KEYS[2]],
                   "-keystore", env[SIGNING_KEYS[0]], "-storepass:env", SIGNING_KEYS[1]],
                  env, "读取密钥库证书", binary=True)
    require(hashlib.sha256(der).hexdigest().upper() == certificate, "密钥库证书与固定指纹不一致；构建已停止")
    # A readable certificate does not prove the private-key password is usable.
    # Sign a CSR in memory; never create or convert the key store during diagnosis.
    capture([java_tool(java_home, "keytool"), "-certreq", "-alias", env[SIGNING_KEYS[2]],
             "-keystore", env[SIGNING_KEYS[0]], "-storepass:env", SIGNING_KEYS[1],
             "-keypass:env", SIGNING_KEYS[3]], env, "原私钥解密与签名校验", binary=True)
    return der


def setup_signing(root, signing_root, args, java_home, env):
    require(args.adopt_keystore and args.key_alias and args.credential_file,
            "配置签名需要 --adopt-keystore、--key-alias、--credential-file（已有原密钥，不生成新密钥）")
    require(args.certificate_sha256, "配置签名需要 --certificate-sha256 原证书指纹，防止绑定错误的密钥")
    require(not signing_root.exists() or not any(signing_root.iterdir()), "签名目录非空；拒绝覆盖或重绑原签名")
    store = resolve_path(args.adopt_keystore, root)
    password_path = resolve_path(args.credential_file, root)
    require(store.is_file() and password_path.is_file(), "原密钥库或密码文件不存在")
    password = password_path.read_text(encoding="utf-8").rstrip("\r\n")
    require(password and not any(c in password for c in "\x00\r\n"), "密码文件须为非空 UTF-8 单行")
    key_path = resolve_path(args.key_credential_file, root) if args.key_credential_file else password_path
    require(key_path.is_file(), "私钥密码文件不存在")
    key_password = key_path.read_text(encoding="utf-8").rstrip("\r\n")
    require(key_password and not any(c in key_password for c in "\x00\r\n"), "私钥密码文件须为非空 UTF-8 单行")
    certificate = fingerprint(args.certificate_sha256)
    env.update(dict(zip(SIGNING_KEYS, (str(store), password, args.key_alias, key_password))))
    der = validate_signing_key(java_home, env, certificate)
    def portable(path):
        try:
            return Path(os.path.relpath(path, root)).as_posix()
        except ValueError:  # Windows assets on a different drive.
            return str(path)
    metadata = {
        "schemaVersion": 2, "storeFile": portable(store), "keyAlias": args.key_alias,
        "credentialFile": portable(password_path), "credentialFormat": "text",
        "certificateFile": portable(signing_root / "release-cert.cer"),
        "certificateSha256": certificate, "keyPasswordSameAsStore": key_path == password_path,
        "source": "existing-portable-keystore",
    }
    if key_path != password_path:
        metadata["keyCredentialFile"] = portable(key_path)
    signing_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    files = {
        "release-signing.json": (json.dumps(metadata, ensure_ascii=False, indent=2) + "\n").encode("utf-8"),
        "pinned-certificate.sha256": (certificate + "\n").encode("ascii"),
        "release-cert.cer": der,
    }
    for name, data in files.items():
        with (signing_root / name).open("xb") as stream:
            stream.write(data)
        (signing_root / name).chmod(0o600)
    print("已绑定原签名并固定证书指纹：" + str(signing_root), flush=True)


def gradle_command(root, java_home, args, offline):
    wrapper = root / "gradle/wrapper/gradle-wrapper.jar"
    require(wrapper.is_file(), "缺少 Gradle Wrapper JAR，请恢复仓库文件")
    variant = "Release" if args.release else "Debug"
    command = [str(java_tool(java_home, "java")), "-classpath", str(wrapper),
               "org.gradle.wrapper.GradleWrapperMain", "--no-daemon", "--console=plain"]
    if offline:
        command.append("--offline")
    if args.clean:
        command.append(":app:clean")
    if not args.skip_checks:
        command.extend([":app:testDebugUnitTest", ":app:lintDebug"])
        if args.release:
            command.append(":app:lintVitalRelease")
    command.append(":app:assemble" + variant)
    return command


def run_gradle(command, root, env):
    secrets = sorted({env[key] for key in (SIGNING_KEYS[1], SIGNING_KEYS[3]) if env.get(key)}, key=len, reverse=True)
    with subprocess.Popen(command, cwd=root, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, errors="replace") as process:
        try:
            for line in process.stdout:
                for secret in secrets:
                    line = line.replace(secret, "[REDACTED]")
                print(line, end="", flush=True)
            code = process.wait()
        except KeyboardInterrupt:
            process.terminate()
            process.wait()
            raise
    require(code == 0, "Gradle 构建失败（退出码 " + str(code) + "），不会导出产物")


def elf_alignments(data, name):
    require(len(data) >= 64 and data[:6] == b"\x7fELF\x02\x01", "无效的 64 位 ELF：" + name)
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    require(size >= 56 and offset + size * count <= len(data), "ELF 程序头越界：" + name)
    alignments = []
    for i in range(count):
        p = offset + i * size
        if struct.unpack_from("<I", data, p)[0] == 1:  # PT_LOAD
            file_offset, address = struct.unpack_from("<QQ", data, p + 8)
            alignment = struct.unpack_from("<Q", data, p + 48)[0]
            require(alignment >= 16384 and alignment & (alignment - 1) == 0
                    and (address - file_offset) % 16384 == 0, "ELF 不满足 16 KiB 对齐：" + name)
            alignments.append(alignment)
    require(alignments, "ELF 缺少加载段：" + name)
    return alignments


def verify_archive(apk, release, policy):
    elf = []
    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, "APK ZIP CRC 校验失败")
        if release:
            libraries = [x for x in archive.namelist() if re.fullmatch(r"lib/[^/]+/[^/]+\.so", x)]
            require({x.split("/")[1] for x in libraries} <= set(policy["allowedAbis"]), "Release 出现构建策略之外的原生 ABI")
            for name in libraries:
                elf.append({"library": name, "loadSegmentAlignments": elf_alignments(archive.read(name), name)})
            for name in archive.namelist():
                if re.fullmatch(r"classes\d*\.dex", name):
                    data = archive.read(name)
                    require(not any(x.encode("utf-8") in data for x in policy["forbiddenDexStrings"]),
                            "Release 混入 Debug 验收代码或测试运行库")
    return elf


def verify_apk(root, apk, tools, env, config, release, certificate):
    require(apk.is_file(), "未生成 APK：" + str(apk))
    signing = capture(sdk_tool(tools, "apksigner", env) + ["verify", "--verbose", "--print-certs", apk], env, "APK 签名校验")
    certificates = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", signing)
    require(certificates, "无法读取 APK 签名证书")
    if release:
        require(len(certificates) == 1 and fingerprint(certificates[0]) == certificate, "APK 未使用固定的原签名证书")
    capture(sdk_tool(tools, "zipalign", env) + ["-c", "-P", "16", "-v", "4", apk], env, "APK 16 KiB ZIP 对齐校验")
    aapt = sdk_tool(tools, "aapt", env)
    badging = capture(aapt + ["dump", "badging", apk], env, "APK 包信息校验")
    package = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    version = config["versionName"] + ("" if release else "-debug")
    application_id = config["applicationId"] + ("" if release else ".debug")
    for key, value in (("name", application_id), ("versionCode", config["versionCode"]), ("versionName", version)):
        require(key + "='" + str(value) + "'" in package, "APK " + key + " 与源码声明不一致")
    for key, value in (("sdkVersion", config["minSdk"]), ("targetSdkVersion", config["targetSdk"])):
        require(key + ":'" + str(value) + "'" in badging, "APK " + key + " 与源码声明不一致")
    if release:
        permissions = re.findall(r"uses-permission(?:-[\w-]+)?: name='([^']+)'", capture(aapt + ["dump", "permissions", apk], env, "APK 权限校验"))
        allowed = {p.replace("${applicationId}", config["applicationId"]) for p in config["policy"]["allowedPermissions"]}
        require(set(permissions) <= allowed, "Release 出现未审核权限：" + ", ".join(sorted(set(permissions) - allowed)))
        manifest = capture(aapt + ["dump", "xmltree", apk, "AndroidManifest.xml"], env, "APK Manifest 校验")
        require(not re.search(r"android:debuggable[^\n]*(?:0xffffffff|0x1\b)", manifest), "Release APK 可调试")
        require(not any(x in manifest for x in ("UiReviewActivity", "PreviewActivity")), "Release Manifest 混入 Debug 入口")
        for attribute in ("allowBackup", "usesCleartextTraffic", "fullBackupContent"):
            require(re.search(r"android:" + attribute + r"[^\n]*0x0\b", manifest), "Release " + attribute + " 须为 false")
    output = read_json(apk.parent / "output-metadata.json")
    elements = output.get("elements", [])
    require(output.get("applicationId") == application_id and len(elements) == 1
            and elements[0].get("versionCode") == config["versionCode"] and elements[0].get("versionName") == version
            and elements[0].get("outputFile") == apk.name, "APK 输出元数据与源码不一致")
    return verify_archive(apk, release, config["policy"])


def artifact_base(config):
    return config["projectName"] + "-v" + config["versionName"] + "-release"


def export_release(root, apk, config, certificate, base, elf, checks, offline,
                   started_at=None, official_release=False):
    mapping = root / "app/build/outputs/mapping/release/mapping.txt"
    require(mapping.is_file() and mapping.stat().st_size > 0, "缺少 R8 mapping.txt，无法完成 Release 导出")
    dist = root / "dist"
    dist.mkdir(exist_ok=True)
    sha = hashlib.sha256(apk.read_bytes()).hexdigest().upper()
    destination = dist / (base + ".apk")
    report = {
        "packageName": config["applicationId"], "versionCode": config["versionCode"],
        "versionName": config["versionName"], "apk": str(destination), "apkSha256": sha,
        "bytes": apk.stat().st_size, "certificateSha256": certificate,
        "builtAt": datetime.now(timezone.utc).isoformat(), "variant": "release",
        "buildStartedAt": (started_at or datetime.now(timezone.utc)).isoformat(),
        "officialRelease": official_release, "artifactTimeZone": "Asia/Shanghai",
        "minSdk": config["minSdk"], "targetSdk": config["targetSdk"],
        "platform": platform.system(), "offline": offline,
        "checks": {"unitTests": checks, "lintDebug": checks, "lintVitalRelease": checks, "signature": True,
                   "zipalign16KiB": True, "crc": True, "abiPolicy": True,
                   "debugFixturesAbsent": True, "manifestAndPermissions": True}, "elf": elf,
    }
    # Stage all files after successful verification; failed builds never copy stale APKs.
    with tempfile.TemporaryDirectory(prefix=".build-", dir=dist) as staging:
        stage = Path(staging)
        shutil.copy2(apk, stage / destination.name)
        shutil.copy2(mapping, stage / (base.removesuffix("-release") + "-mapping.txt"))
        (stage / (base + ".sha256")).write_text(sha + "  " + destination.name + "\n", encoding="ascii")
        (stage / (base.removesuffix("-release") + "-build.json")).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        publish_staged(stage, dist)
    print("Release 校验通过，导出：" + str(destination), flush=True)
    print("SHA-256：" + sha)


def publish_staged(stage, dist):
    """Rollback the whole artifact set if any replacement fails."""
    files = list(stage.iterdir())
    with tempfile.TemporaryDirectory(prefix=".previous-", dir=dist) as backup:
        old = Path(backup)
        replaced = []
        try:
            for path in files:
                target = dist / path.name
                if target.exists():
                    shutil.copy2(target, old / path.name)
                path.replace(target)
                replaced.append(target)
        except OSError:
            for target in reversed(replaced):
                previous = old / target.name
                if previous.exists():
                    previous.replace(target)
                else:
                    target.unlink()
            raise


def parser():
    result = argparse.ArgumentParser(description="Android Windows / Linux / macOS 标准构建入口（详见 docs/technical.md）")
    mode = result.add_mutually_exclusive_group()
    mode.add_argument("--debug", action="store_true", help="Debug 构建（默认）")
    mode.add_argument("--release", action="store_true", help="签名 Release 构建、校验与导出")
    mode.add_argument("--setup-signing", action="store_true", help="显式绑定已有原密钥与证书指纹；不生成或重绑密钥")
    network = result.add_mutually_exclusive_group()
    network.add_argument("--offline", action="store_true", help="离线依赖解析")
    network.add_argument("--online", action="store_true", help="允许下载依赖（默认离线；需显式启用）")
    result.add_argument("--install", action="store_true", help="构建并安装 Debug；不覆盖正式版")
    result.add_argument("--doctor", action="store_true", help="只检查环境；加 --release 检查签名配置")
    result.add_argument("--clean", action="store_true", help="先执行 :app:clean")
    result.add_argument("--skip-checks", action="store_true", help="显式跳过单元测试和 Lint；APK 校验仍执行")
    result.add_argument("--java-home", help="指定 JDK 17+ 根目录")
    result.add_argument("--sdk", help="指定 Android SDK 根目录（须与 local.properties 一致）")
    result.add_argument("--signing-root", default=".signing", help="签名元数据目录；默认 .signing")
    result.add_argument("--adopt-keystore", help="已有原签名密钥库，仅用于 --setup-signing")
    result.add_argument("--key-alias", help="原密钥别名，仅用于 --setup-signing")
    result.add_argument("--credential-file", help="原密钥库密码文件（UTF-8 单行），不在命令行传密码")
    result.add_argument("--key-credential-file", help="私钥密码与库密码不同时使用的密码文件")
    result.add_argument("--certificate-sha256", help="可信原签名证书的 SHA-256 指纹")
    return result


def main(argv=None, root=ROOT):
    args = parser().parse_args(argv)
    require(sys.version_info >= (3, 9), "需要 Python 3.9+")
    host = platform.system()
    require(host in ("Darwin", "Linux", "Windows"), "仅支持 Windows / Linux / macOS")
    require(not args.install or not (args.release or args.setup_signing or args.doctor), "--install 仅用于 Debug 构建")
    require(args.setup_signing or not any((args.adopt_keystore, args.key_alias, args.credential_file,
                                           args.key_credential_file, args.certificate_sha256)), "签名配置参数仅用于 --setup-signing")
    require(not args.setup_signing or not any((args.doctor, args.clean, args.skip_checks, args.offline, args.online, args.sdk)),
            "--setup-signing 不接受构建或 doctor 参数")
    env = os.environ.copy()
    # Always use the selected metadata; partial/stale caller signing env must not affect Debug.
    for key in SIGNING_KEYS:
        env.pop(key, None)
    java_home, major = find_java(root, args.java_home, env, host)
    if args.setup_signing:
        try:
            setup_signing(root, resolve_path(args.signing_root, root), args, java_home, env)
            return 0
        finally:
            for key in SIGNING_KEYS:
                env.pop(key, None)
    config = app_config(root)
    started_at = datetime.now(timezone.utc)
    base = artifact_base(config) if args.release else None
    sdk = find_sdk(root, args.sdk, env, host)
    env.update(JAVA_HOME=str(java_home), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    env["PATH"] = str(java_home / "bin") + os.pathsep + env.get("PATH", "")
    compile_sdk = config["compileSdk"]
    require(any((sdk / "platforms" / ("android-" + version) / "android.jar").is_file()
                for version in (str(compile_sdk), str(compile_sdk) + ".0")),
            "缺少 Android SDK Platform " + str(compile_sdk) + "：" + str(sdk))
    tools = sdk / "build-tools" / config["buildToolsVersion"]
    for tool in ("aapt", "zipalign"):
        path = sdk_tool(tools, tool, env)[0]
        require(path.is_file() and (host == "Windows" or os.access(path, os.X_OK)), "缺少可执行 SDK 工具：" + str(path))
    require((tools / "lib/apksigner.jar").is_file(), "缺少 SDK 签名校验 JAR：" + str(tools))
    offline = not args.online
    command = gradle_command(root, java_home, args, offline)
    print("JDK：" + str(java_home) + "（" + str(major) + "）", flush=True)
    print("Android SDK：" + str(sdk), flush=True)
    certificate = None
    try:
        if args.release:
            certificate = load_signing(root, resolve_path(args.signing_root, root), env)
            validate_signing_key(java_home, env, certificate)
            print("Release 原签名证书校验通过", flush=True)
        if args.doctor:
            print("环境检查通过；未执行构建或下载依赖", flush=True)
            return 0
        if args.skip_checks:
            print("已显式跳过单元测试和 Lint；APK 产物校验仍执行", flush=True)
        print("流程：" + ", ".join(x for x in command if x.startswith(":app:"))
              + ("（离线）" if offline else "（允许联网）"), flush=True)
        print("生效参数：" + str({"release": args.release, "offline": offline, "checks": not args.skip_checks, "install": args.install}), flush=True)
        print("Gradle：" + subprocess.list2cmdline(command), flush=True)
        run_gradle(command, root, env)
        variant = "release" if args.release else "debug"
        apk = root / "app/build/outputs/apk" / variant / ("app-" + variant + ".apk")
        elf = verify_apk(root, apk, tools, env, config, args.release, certificate)
        if args.release:
            export_release(root, apk, config, certificate, base, elf, not args.skip_checks, offline,
                           started_at, False)
        else:
            print("Debug 构建与 APK 校验通过：" + str(apk), flush=True)
        if args.install:
            adb = sdk / "platform-tools" / ("adb.exe" if host == "Windows" else "adb")
            capture([adb, "install", "-r", apk], env, "Debug 安装")
            print("Debug 安装完成（.debug 包名）", flush=True)
        return 0
    finally:
        for key in SIGNING_KEYS:
            env.pop(key, None)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n构建已中断", file=sys.stderr)
        sys.exit(130)
    except (BuildError, OSError, ValueError, zipfile.BadZipFile, struct.error) as error:
        print("构建失败：" + str(error), file=sys.stderr)
        sys.exit(1)
