"""Regression checks for shell activation, package additions, and host sysroots.

Run with: python3 -m unittest discover -s tests -p test_pixi_setup.py
"""
import importlib.util
import json
import os
from pathlib import Path
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
        for shell in ("bash", "zsh"):
            if not shutil.which(shell):
                continue
            with self.subTest(shell=shell), tempfile.TemporaryDirectory() as tmp:
                base = Path(tmp)
                fake = base / "pixi"
                # Exercise the real activation parser and wrappers with isolated
                # environment metadata, without resolving the hardware toolchain.
                fake.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
args=sys.argv[1:]
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
source "$CY_TEST_ROOT/scripts/pixi-env.sh" lean
[ "$$" = "$old_pid" ]
[ "$RISCV" = /toolchain ]
[ "$PIXI_ENVIRONMENT_NAME" = lean ]
[ "$CONDA_PREFIX" = /prefix/lean ]
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


if __name__ == '__main__':
    unittest.main()
