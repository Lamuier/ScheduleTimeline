import io
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('build_support', Path(__file__).resolve().parents[1] / 'build_support.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class BuildSupportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.signing = self.root / '.signing'
        self.signing.mkdir()
        (self.root / 'key with spaces.p12').touch()
        (self.root / 'password.txt').write_text(' password with spaces \n')
        self.der = b'test-certificate'
        self.cert = hashlib.sha256(self.der).hexdigest().upper()
        self.metadata = dict(schemaVersion=2, storeFile='key with spaces.p12', keyAlias='release',
                             credentialFile='password.txt', credentialFormat='text', certificateSha256=self.cert)
        self.save_metadata()
        (self.signing / 'pinned-certificate.sha256').write_text(self.cert)

    def tearDown(self):
        self.temp.cleanup()

    def save_metadata(self):
        (self.signing / 'release-signing.json').write_text(json.dumps(self.metadata))

    def test_source_config_sdk_dsl_version_and_abi_policy(self):
        config = build.app_config(build.ROOT)
        self.assertRegex(config['versionName'], r'^\d+\.\d+\.\d+$')
        self.assertGreater(config['versionCode'], 0)
        self.assertEqual(config['compileSdk'], 37)
        self.assertEqual(config['policy']['allowedAbis'], ['arm64-v8a'])
        self.assertEqual(build.artifact_base({'projectName': 'ScheduleTimeline', 'versionName': '1.2.3'}), 'ScheduleTimeline-v1.2.3-release')

    def test_stale_cross_os_local_sdk_path_does_not_override_environment(self):
        (self.root / 'local.properties').write_text('sdk.dir=C\\:\\\\Android\\\\Sdk\n')
        sdk = self.root / 'SDK with spaces'
        sdk.mkdir()
        for host in ('Darwin', 'Windows', 'Linux'):
            self.assertEqual(build.find_sdk(self.root, None, {'ANDROID_HOME': str(sdk)}, host), sdk)

    def test_valid_local_sdk_and_conflicting_explicit_sdk(self):
        sdk = self.root / 'sdk'
        sdk.mkdir()
        (self.root / 'local.properties').write_text('sdk.dir=' + str(sdk))
        self.assertEqual(build.find_sdk(self.root, None, {}, 'Linux'), sdk)
        with self.assertRaises(build.BuildError):
            build.find_sdk(self.root, '/other', {}, 'Linux')

    def test_windows_sdk_tool_avoids_batch_command_interpolation(self):
        with patch.object(build.platform, 'system', return_value='Windows'):
            tools = self.root / 'tools with spaces'
            java = self.root / 'JDK with spaces'
            command = build.sdk_tool(tools, 'apksigner', {'JAVA_HOME': str(java)})
            self.assertEqual(command, [java / 'bin/java.exe', '-jar', tools / 'lib/apksigner.jar'])
            self.assertEqual(build.sdk_tool(tools, 'zipalign', {})[0], tools / 'zipalign.exe')

    def test_password_stays_out_of_command_line_and_private_key_is_tested(self):
        env = {}
        build.load_signing(self.root, self.signing, env)
        self.assertEqual(env[build.SIGNING_KEYS[1]], ' password with spaces ')
        with patch.object(build, 'capture', return_value=self.der) as capture:
            build.validate_signing_key(self.root, env, self.cert)
            self.assertIn('-certreq', capture.call_args.args[0])
            self.assertIn('-keypass:env', capture.call_args.args[0])
            self.assertNotIn(env[build.SIGNING_KEYS[1]], capture.call_args.args[0])

    def test_wrong_certificate_or_private_key_password_fails(self):
        env = {}
        build.load_signing(self.root, self.signing, env)
        with patch.object(build, 'capture', return_value=b'other'):
            with self.assertRaises(build.BuildError):
                build.validate_signing_key(self.root, env, self.cert)
        with patch.object(build, 'capture', side_effect=[self.der, build.BuildError('private key failed')]):
            with self.assertRaisesRegex(build.BuildError, 'private key failed'):
                build.validate_signing_key(self.root, env, self.cert)

    def test_dpapi_and_pin_mismatch_fail_without_replacing_key(self):
        self.metadata['credentialFormat'] = 'clixml'
        self.save_metadata()
        with self.assertRaisesRegex(build.BuildError, 'clixml'):
            build.load_signing(self.root, self.signing, {})
        self.metadata['credentialFormat'] = 'text'
        self.save_metadata()
        (self.signing / 'pinned-certificate.sha256').write_text('0' * 64)
        with self.assertRaises(build.BuildError):
            build.load_signing(self.root, self.signing, {})
        self.assertTrue((self.root / 'key with spaces.p12').exists())

    def test_release_tasks_include_tests_debug_lint_and_release_vital_lint(self):
        (self.root / 'gradle/wrapper').mkdir(parents=True)
        (self.root / 'gradle/wrapper/gradle-wrapper.jar').touch()
        args = build.parser().parse_args(['--release'])
        command = build.gradle_command(self.root, self.root, args, True)
        for item in ('--offline', ':app:testDebugUnitTest', ':app:lintDebug', ':app:lintVitalRelease', ':app:assembleRelease'):
            self.assertIn(item, command)
        args.skip_checks = True
        command = build.gradle_command(self.root, self.root, args, True)
        self.assertNotIn(':app:testDebugUnitTest', command)
        self.assertIn(':app:assembleRelease', command)

    def test_staged_export_rolls_back_whole_set_on_replace_failure(self):
        stage, dist = self.root / 'stage', self.root / 'dist'
        stage.mkdir(); dist.mkdir()
        for name in ('app.apk', 'app.sha256'):
            (stage / name).write_text('new-' + name)
            (dist / name).write_text('old-' + name)
        real_replace = Path.replace
        calls = []
        def fail_once(path, target):
            calls.append(str(path))
            if len(calls) == 2:
                raise OSError('simulated filesystem failure')
            return real_replace(path, target)
        with patch.object(Path, 'replace', fail_once):
            with self.assertRaises(OSError):
                build.publish_staged(stage, dist)
        for name in ('app.apk', 'app.sha256'):
            self.assertEqual((dist / name).read_text(), 'old-' + name)

    def test_apk_abi_and_test_runtime_leak_checks(self):
        apk = self.root / 'app.apk'
        policy = {'allowedAbis': ['arm64-v8a'], 'forbiddenDexStrings': ['org/robolectric/']}
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('classes.dex', b'org/robolectric/SomeClass')
        with self.assertRaises(build.BuildError):
            build.verify_archive(apk, True, policy)
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('lib/x86_64/test.so', b'elf')
        with self.assertRaises(build.BuildError):
            build.verify_archive(apk, True, policy)

    def test_export_failure_keeps_previous_apk(self):
        dist = self.root / 'dist'; dist.mkdir()
        old = dist / 'ScheduleTimeline-v1.13.0-release.apk'
        old.write_bytes(b'original')
        apk = self.root / 'new.apk'; apk.write_bytes(b'new')
        with self.assertRaises(build.BuildError):
            build.export_release(self.root, apk, {}, self.cert, 'ScheduleTimeline-v1.13.0-release', [], True, True)
        self.assertEqual(old.read_bytes(), b'original')

    def test_parse_preserves_build_switches_and_rejects_conflicting_network_flags(self):
        args = build.parser().parse_args(['--install', '--clean', '--offline', '--skip-checks'])
        self.assertTrue(args.install and args.clean and args.offline and args.skip_checks)
        with patch('sys.stderr', io.StringIO()), self.assertRaises(SystemExit):
            build.parser().parse_args(['--offline', '--online'])


if __name__ == '__main__':
    unittest.main()
