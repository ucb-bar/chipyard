#!/usr/bin/env python3
"""
Inject Verilator tracing_on / tracing_off pragmas into gen-collateral SV files
based on a list of hierarchy scope paths (WF_SCOPES). Verilator silently drops
the scope argument to $dumpvars at runtime; this script provides the spatial
filter at compile time instead.

Algorithm:
  1. Parse every .sv / .v file in gen-collateral to build:
        module_name  -> path to its file
        module_name  -> [(instance_name, child_module_name), ...]
  2. For each WF_SCOPES path (e.g. testHarness.chiptop0.system.coh_wrapper):
        walk down from TestHarness, resolving each instance to its module.
        The leaf is the user's target.
  3. Build keep_set = (each leaf module) UNION (every module reachable from
     any leaf via instantiation). These get `/* verilator tracing_on */`.
  4. Every other module file gets `/* verilator tracing_off */` prepended,
     including ancestors of leaves (so their own intra-module signals are not
     traced; child instances are still controlled by their own files' pragmas).
  5. The script is idempotent: a marker comment is checked to avoid
     double-prepending if the script runs twice without `make clean-sim-debug`.

Usage:
  wf_pragma_inject.py <gen_collateral_dir> "<scope1> <scope2> ..."

If WF_SCOPES is empty, the script is a no-op (every module traces by default).
"""

from __future__ import annotations
import re
import sys
from pathlib import Path

MARKER = "// WF_PRAGMA_INJECTED"
# Module names: any identifier (some SRAMs and ClockSinkDomain submodules
# in chipyard's gen-collateral start lowercase, e.g. `array_0_0_0`, `cc_dir`,
# `data`, `ebtb`, `ghist_0`, `bootromClockSinkDomain`).
MODULE_RE = re.compile(r'^\s*module\s+([A-Za-z_][A-Za-z0-9_]*)\b', re.MULTILINE)
# Instantiations: same allowance for the module-type name.
INST_RE = re.compile(r'^\s*([A-Za-z_][A-Za-z0-9_]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(', re.MULTILINE)
# Verilog/SV reserved words that look like instantiations to our regex but aren't.
RESERVED = frozenset({
    'always', 'always_ff', 'always_comb', 'always_latch', 'assign',
    'wire', 'reg', 'logic', 'bit', 'input', 'output', 'inout',
    'parameter', 'localparam', 'integer', 'real', 'time', 'genvar',
    'function', 'task', 'endmodule', 'endfunction', 'endtask',
    'if', 'else', 'for', 'while', 'case', 'casez', 'casex',
    'begin', 'end', 'fork', 'join', 'module', 'generate', 'endgenerate',
    'initial', 'final', 'import', 'export', 'package', 'endpackage',
    'interface', 'endinterface', 'class', 'endclass', 'typedef', 'enum',
    'struct', 'union', 'static', 'automatic', 'const', 'var', 'let',
    'return', 'break', 'continue', 'forever', 'repeat', 'do',
})


def parse_scope_paths(arg: str) -> list[list[str]]:
    arg = arg.strip()
    if not arg:
        return []
    return [s.split('.') for s in arg.split() if s]


def cleanup_testdriver(gen_dir: Path) -> None:
    """Strip any stale WF pragma marker line from TestDriver.v.
    TestDriver.v owns $dumpfile/$dumpvars and the wf_active edge-detect block;
    if it ever got a tracing_off pragma it would short-circuit signal
    registration entirely (empty VCD)."""
    f = gen_dir / 'TestDriver.v'
    if not f.exists():
        return
    text = f.read_text()
    lines = text.splitlines(keepends=True)
    if lines and MARKER in lines[0]:
        f.write_text(''.join(lines[1:]))


def build_module_index(gen_dir: Path):
    module_to_file = {}
    raw_children = {}
    for f in list(gen_dir.glob('*.sv')) + list(gen_dir.glob('*.v')):
        # Never inject pragmas into TestDriver.v: it owns $dumpfile/$dumpvars
        # and the wf_active edge-detect block. Disabling tracing here would
        # short-circuit signal registration entirely (empty VCD).
        if f.name == 'TestDriver.v':
            continue
        try:
            text = f.read_text()
        except UnicodeDecodeError:
            continue
        m = MODULE_RE.search(text)
        if not m:
            continue
        mod = m.group(1)
        module_to_file[mod] = f
        children = []
        for im in INST_RE.finditer(text):
            child_module = im.group(1)
            instance = im.group(2)
            if child_module in RESERVED or instance in RESERVED:
                continue
            children.append((instance, child_module))
        raw_children[mod] = children

    # Filter children: only keep those whose claimed module name is actually
    # a known module file. This rejects false positives (e.g. port-direction
    # decls "input wire x" matching as module instantiations).
    module_children = {
        mod: [(inst, cm) for inst, cm in kids if cm in module_to_file]
        for mod, kids in raw_children.items()
    }
    return module_to_file, module_children


def resolve_scopes(paths: list[list[str]], module_children):
    """Walk each path top-down. Returns (leaf_modules, ancestor_modules)
    where ancestor_modules are every module on the path from TestHarness down to
    each leaf (excluding the leaf itself). Ancestors must NOT be tagged
    tracing_off — Verilator's tracing_off pragma propagates into instantiated
    submodules, so disabling an ancestor would suppress everything below it,
    including the keep-set leaves we care about."""
    leaves = set()
    ancestors = {'TestHarness'}  # always an ancestor
    for path in paths:
        if not path:
            continue
        if path[0] != 'testHarness':
            print(f'WF_PRAGMA: warning: scope must start with "testHarness", got "{path[0]}"',
                  file=sys.stderr)
            continue
        current = 'TestHarness'
        chain = ['TestHarness']
        ok = True
        for instance in path[1:]:
            children = module_children.get(current, [])
            match = next((cm for inst, cm in children if inst == instance), None)
            if match is None:
                print(f'WF_PRAGMA: warning: cannot find instance "{instance}" inside module "{current}" '
                      f'(scope: {".".join(path)})', file=sys.stderr)
                ok = False
                break
            current = match
            chain.append(current)
        if ok:
            leaves.add(current)
            # Everything before the leaf is an ancestor.
            ancestors.update(chain[:-1])
    return leaves, ancestors


def closure(seeds: set[str], module_children) -> set[str]:
    """All modules reachable by walking instantiations from seeds."""
    keep = set()
    work = list(seeds)
    while work:
        m = work.pop()
        if m in keep:
            continue
        keep.add(m)
        for _, child in module_children.get(m, []):
            if child not in keep:
                work.append(child)
    return keep


def strip_marker(file: Path) -> str:
    """Read file and return text with any leading marker line removed."""
    text = file.read_text()
    lines = text.splitlines(keepends=True)
    if lines and MARKER in lines[0]:
        return ''.join(lines[1:])
    return text


def write_pragma(file: Path, pragma: str | None) -> None:
    """Write file with `pragma` line prepended (or no pragma if None).
    Idempotent: strips any existing marker line first."""
    text = strip_marker(file)
    if pragma is None:
        new_text = text
    else:
        new_text = f'{pragma} {MARKER}\n{text}'
    if new_text != file.read_text():
        file.write_text(new_text)


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print('Usage: wf_pragma_inject.py <gen_collateral_dir> "<scope1> <scope2> ..."',
              file=sys.stderr)
        return 1

    gen_dir = Path(argv[1])
    if not gen_dir.is_dir():
        print(f'WF_PRAGMA: gen-collateral dir "{gen_dir}" not found', file=sys.stderr)
        return 1

    scopes = parse_scope_paths(argv[2])
    cleanup_testdriver(gen_dir)
    if not scopes:
        print('WF_PRAGMA: WF_SCOPES is empty, no pragmas injected.')
        return 0

    module_to_file, module_children = build_module_index(gen_dir)
    if 'TestHarness' not in module_children:
        print('WF_PRAGMA: TestHarness module not found in gen-collateral; skipping injection.',
              file=sys.stderr)
        return 0

    leaves, ancestors = resolve_scopes(scopes, module_children)
    if not leaves:
        print('WF_PRAGMA: no scopes resolved successfully; skipping injection.',
              file=sys.stderr)
        return 0

    keep_set = closure(leaves, module_children)

    on_count = 0
    off_count = 0
    pass_count = 0
    for mod, f in module_to_file.items():
        if mod in keep_set:
            # Explicitly tracing_on (mostly redundant since default is on, but
            # makes the intent visible and resists accidental parent-off).
            write_pragma(f, '/* verilator tracing_on */')
            on_count += 1
        elif mod in ancestors:
            # Ancestors must be left at default tracing — tracing_off here
            # would propagate into our leaf scopes and suppress them. Leave
            # them with no pragma; only their OWN intra-module signals leak,
            # which is the price of routing through them.
            write_pragma(f, None)
            pass_count += 1
        else:
            # Sibling / unrelated modules: disable tracing.
            write_pragma(f, '/* verilator tracing_off */')
            off_count += 1

    print(f'WF_PRAGMA: scopes resolved to leaf modules: {sorted(leaves)}')
    print(f'WF_PRAGMA: ancestors (default tracing): {sorted(ancestors)}')
    print(f'WF_PRAGMA: {on_count} modules tracing_on, '
          f'{pass_count} ancestors pass-through, {off_count} tracing_off')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
