"""Regression checks for shell activation, package additions, and host sysroots.

Run with: python3 -m unittest discover -s tests -p test_pixi_setup.py
"""
import importlib.util
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("sysroot", ROOT / "scripts/pixi-sysroot.py")
sysroot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sysroot)


class Sysroots(unittest.TestCase):
    def test_published_floor_and_runtime_baseline(self):
        text = (ROOT / "pixi-reqs/pixi.toml").read_text()
        for host, expected in (("2.17", "2.17"), ("2.27", "2.17"), ("2.31", "2.28"), ("2.33", "2.28"), ("2.35", "2.34"), ("2.39", "2.34"), ("2.41", "2.34")):
            with self.subTest(host=host):
                result = sysroot.adapt_manifest(text, host)
                self.assertIn('sysroot_linux-64 = "' + expected + '.*"', result)
                self.assertEqual(result, sysroot.adapt_manifest(result, host))
        self.assertIn('version = "2.17"', sysroot.adapt_manifest(text, "2.27"))

    def test_unsupported_host_is_an_error(self):
        with self.assertRaises(ValueError):
            sysroot.adapt_manifest('', '2.11')


class ShellActivation(unittest.TestCase):
    def test_shells_and_package_updates(self):
        for shell, cache_kind in ((s, c) for s in ("bash", "zsh")
                                  for c in ("default", "PIXI_CACHE_DIR", "RATTLER_CACHE_DIR")):
            if not shutil.which(shell):
                continue
            with self.subTest(shell=shell, cache=cache_kind), tempfile.TemporaryDirectory() as tmp:
                base = Path(tmp)
                fake = base / "pixi"
                # Exercise the real activation parser and wrappers with isolated
                # environment metadata, without resolving the hardware toolchain.
                fake.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
args=sys.argv[1:]
assert os.environ['PIXI_CACHE_DIR'] == os.environ['EXPECTED_PIXI_CACHE']
base=pathlib.Path(os.environ['TEST_PIXI_DIR'])
with (base/'calls').open('a') as f: f.write(json.dumps(args)+'\\n')
if args[0]=='shell-hook':
    assert '--json' in args and '--no-completions' in args
    if (base/'bad-json').exists(): print('invalid'); sys.exit(0)
    if (base/'hook-fails').exists(): sys.exit(17)
    env=args[args.index('--environment')+1]
    print(json.dumps({'environment_variables':{'PATH':str(base/'packages')+':'+os.environ['PATH'], 'RISCV':'/toolchain', 'CONDA_PREFIX':'/prefix/'+env, 'PIXI_ENVIRONMENT_NAME':env, 'ADDED_VARIABLE':"quote' dollar$ backtick` newline\\n"}}))
elif args[0]=='install':
    if (base/'install-fails').exists(): sys.exit(19)
    (base/'packages').mkdir(exist_ok=True)
    tool=base/'packages'/'added-tool'; tool.write_text('#!/bin/sh\\necho added\\n');tool.chmod(0o755)
elif args[0]=='add' and 'broken' in args:
    sys.exit(23)
''')
                fake.chmod(0o755)
                script = r'''
set -e
export RISCV='old riscv'
export C_INCLUDE_PATH='old includes'
export CPLUS_INCLUDE_PATH='old cpp includes'
pixi() { echo old-function; }
old_path=$PATH
old_pid=$$
old_cache_set=${PIXI_CACHE_DIR+x}
old_cache=${PIXI_CACHE_DIR-}
source "$CY_TEST_ROOT/scripts/pixi-env.sh" lean
[ "$$" = "$old_pid" ]
[ "$RISCV" = /toolchain ]
[ "$PIXI_ENVIRONMENT_NAME" = lean ]
[ "$CONDA_PREFIX" = /prefix/lean ]
[ "$PIXI_CACHE_DIR" = "$EXPECTED_PIXI_CACHE" ]
first_path=$PATH
first_includes=$C_INCLUDE_PATH
touch "$TEST_PIXI_DIR/hook-fails"
if chipyard_activate missing; then exit 39; else [ "$?" = 17 ]; fi
[ "$PATH" = "$first_path" ]
[ "$PIXI_ENVIRONMENT_NAME" = lean ]
rm "$TEST_PIXI_DIR/hook-fails"
source "$CY_TEST_ROOT/scripts/pixi-env.sh" lean
[ "$PATH" = "$first_path" ]
[ "$C_INCLUDE_PATH" = "$first_includes" ]
cd /
pixi add added-tool
[ "$(added-tool)" = added ]
pixi add --no-install deferred
pixi add --manifest-path /other/pixi.toml other-package
if pixi add broken; then exit 40; else [ "$?" = 23 ]; fi
touch "$TEST_PIXI_DIR/install-fails"
if pixi add package; then exit 41; else [ "$?" = 19 ]; fi
rm "$TEST_PIXI_DIR/install-fails"
pixi install
pixi install -e default
pixi install --environment=default
pixi install --all
pixi install --help
pixi install --script /other/tool.py
[ "$PIXI_ENVIRONMENT_NAME" = lean ]
pixi deactivate
[ "$PATH" = "$old_path" ]
[ "$RISCV" = 'old riscv' ]
[ "$C_INCLUDE_PATH" = 'old includes' ]
[ "$CPLUS_INCLUDE_PATH" = 'old cpp includes' ]
[ "${ADDED_VARIABLE+x}" != x ]
[ "${PIXI_CACHE_DIR+x}" = "$old_cache_set" ]
[ "${PIXI_CACHE_DIR-}" = "$old_cache" ]
[ "$(pixi)" = old-function ]
chipyard_activate default
pixi deactivate
# Invalid metadata and hook failure must fail, rather than eval an empty string.
touch "$TEST_PIXI_DIR/bad-json"
if chipyard_activate; then exit 42; fi
[ "$PATH" = "$old_path" ]
rm "$TEST_PIXI_DIR/bad-json"
touch "$TEST_PIXI_DIR/hook-fails"
if chipyard_activate; then exit 43; else [ "$?" = 17 ]; fi
'''
                env = dict(os.environ, PATH=tmp + ":" + os.environ["PATH"], TEST_PIXI_DIR=tmp, CY_TEST_ROOT=str(ROOT))
                env.pop('PIXI_CACHE_DIR', None)
                env.pop('RATTLER_CACHE_DIR', None)
                env['EXPECTED_PIXI_CACHE'] = str(ROOT / 'pixi-reqs/.pixi/cache')
                if cache_kind != 'default':
                    env[cache_kind] = env['EXPECTED_PIXI_CACHE'] = tmp + '/custom cache'
                if cache_kind == 'PIXI_CACHE_DIR':
                    env['RATTLER_CACHE_DIR'] = tmp + '/lower priority cache'
                result = subprocess.run([shell, "-f" if shell == "zsh" else "--noprofile", "-c", script], env=env, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                calls = [json.loads(line) for line in (base / "calls").read_text().splitlines()]
                additions = [args for args in calls if args[0] == 'add']
                installs = [args for args in calls if args[0] == 'install']
                self.assertTrue(all('--manifest-path' in args for args in additions))
                self.assertTrue(all(args.count('--no-install') <= 1 for args in additions))
                self.assertEqual(len(installs), 8)
                self.assertTrue(all(args[args.index('--environment') + 1] == 'lean' for args in installs[:3]))
                for args, options in zip(installs[3:7], [['-e', 'default'], ['--environment=default'], ['--all'], ['--help']]):
                    expected = ['install'] + options + ['--manifest-path', str(ROOT / 'pixi-reqs/pixi.toml')]
                    if options == ['--help']:
                        expected += ['--environment', 'lean']
                    self.assertEqual(args, expected)
                self.assertEqual(installs[7], ['install', '--script', '/other/tool.py'])
                self.assertEqual(additions[2], ['add', '--manifest-path', '/other/pixi.toml', 'other-package'])


class SetupProgress(unittest.TestCase):
    def run_setup(self, interactive, install_exit=0, verbose=False, cache_override=None):
        with tempfile.TemporaryDirectory(prefix="chipyard-setup-") as tmp:
            root = Path(tmp)
            for name in ('build-setup.sh', 'utils.sh', 'replace-content.py', 'fix-open-files.sh',
                         'pixi-env.sh', 'pixi-activation.py', 'pixi-sysroot.py'):
                target = root / 'scripts' / name
                target.parent.mkdir(exist_ok=True)
                shutil.copy2(ROOT / 'scripts' / name, target)
            (root / 'pixi-reqs').mkdir()
            shutil.copy2(ROOT / 'pixi-reqs/pixi.toml', root / 'pixi-reqs/pixi.toml')
            subprocess.run(['git', 'init', '-q', tmp], check=True)
            (root / 'bin').mkdir()
            fake = root / 'bin/pixi'
            fake.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ['SETUP_CALLS'], 'a') as f:
    f.write(json.dumps({'args': args, 'tty': os.isatty(1) and os.isatty(2),
        'cache': os.environ.get('PIXI_CACHE_DIR')}) + '\\n')
if 'shell-hook' in args:
    print(json.dumps({'environment_variables': {'PATH': os.environ['PATH'],
        'CONDA_PREFIX': '/test-prefix', 'PIXI_ENVIRONMENT_NAME': 'default'}}))
elif 'lock' in args:
    sys.exit(1 if '--check' in args else 0)
elif 'install' in args:
    sys.exit(int(os.environ['SETUP_INSTALL_EXIT']))
''')
            fake.chmod(0o755)
            env = dict(os.environ, PATH=str(root / 'bin') + ':' + os.environ['PATH'],
                       SETUP_CALLS=str(root / 'calls'), SETUP_INSTALL_EXIT=str(install_exit),
                       SHELL=shutil.which('bash'))
            env.pop('PIXI_CACHE_DIR', None)
            env.pop('RATTLER_CACHE_DIR', None)
            expected_cache = str(root / 'pixi-reqs/.pixi/cache')
            if cache_override:
                env[cache_override] = expected_cache = str(root / 'custom cache')
            command = ['bash', str(root / 'scripts/build-setup.sh')]
            for step in range(2, 12):
                command += ['-s', str(step)]
            if verbose:
                command += ['--verbose']
            if interactive:
                command = ['script', '-qefc', shlex.join(command), '/dev/null']
            result = subprocess.run(command, cwd=root, env=env, capture_output=True, text=True,
                                    input='', timeout=30)
            self.assertTrue((root / 'calls').exists(), result.stdout + result.stderr)
            calls = [json.loads(line) for line in (root / 'calls').read_text().splitlines()]
            self.assertTrue(all(call['cache'] == expected_cache for call in calls), calls)
            return result, calls, (root / 'build-setup.log').read_text()

    def test_explicit_cache_overrides(self):
        for variable in ('PIXI_CACHE_DIR', 'RATTLER_CACHE_DIR'):
            with self.subTest(variable=variable):
                result, _, _ = self.run_setup(interactive=False, cache_override=variable)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    @unittest.skipUnless(shutil.which('script'), 'script is needed to preserve terminal progress')
    def test_progress_survives_tee(self):
        result, calls, log = self.run_setup(interactive=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(all(call['tty'] for call in calls if 'shell-hook' not in call['args']))
        self.assertIn('Resolving Pixi dependencies...', log)
        self.assertIn("Installing Pixi environment 'default'...", log)

    def test_noninteractive_verbose_logs(self):
        result, calls, log = self.run_setup(interactive=False, verbose=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for call in calls:
            self.assertFalse(call['tty'])
            if 'shell-hook' not in call['args']:
                self.assertIn('-vv', call['args'])
        self.assertIn('Setup complete!', log)

    @unittest.skipUnless(shutil.which('script'), 'script is needed to preserve terminal progress')
    def test_terminal_install_failure_is_preserved(self):
        result, calls, log = self.run_setup(interactive=True, install_exit=7)
        self.assertEqual(result.returncode, 7, result.stdout + result.stderr)
        self.assertIn('failed with exit code 7 at step 1', log)
        self.assertFalse(any('shell-hook' in call['args'] for call in calls))


if __name__ == '__main__':
    unittest.main()
