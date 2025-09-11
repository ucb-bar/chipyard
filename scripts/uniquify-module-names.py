#!/usr/bin/env python3
"""
Uniquify module names and split filelists for DUT vs Model.

This script post-processes Verilog emitted by firtool to:
 - Identify modules that appear in both the DUT and the Model hierarchies
 - Create uniquified copies of shared module sources for the Model tree
 - Rewrite parent instantiations in the Model tree to reference uniquified names
 - Write separate filelists for the DUT and Model, plus an updated Model hierarchy JSON

Inputs (CLI):
 - --model-hier-json / --top-hier-json: firtool hierarchy JSONs
 - --in-all-filelist / --in-bb-filelist: combined and blackbox filelists
 - --dut / --model: root module names for the DUT and Model trees
 - --target-dir: base directory holding generated collateral
 - --gcpath: path to gen-collateral (for resolving relative paths)

Outputs:
 - --out-dut-filelist: filelist containing all Verilog/sidecars used by the DUT
 - --out-model-filelist: filelist for the Model tree (with uniquified modules as needed)
 - --out-model-hier-json: updated Model hierarchy JSON reflecting uniquified names

Failure policy: fail fast with clear, actionable error messages.
"""

import json
import argparse
import shutil
import os
import sys
import platform
import re
from typing import List, Dict, Set, Iterable, Tuple, Any, Optional, Match


parser = argparse.ArgumentParser(description="Uniquify shared modules and split DUT/Model filelists")
parser.add_argument("--model-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument("--top-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument('--in-all-filelist', type=str, required=True, help='Path to input filelist that has all modules (relative paths).')
parser.add_argument('--in-bb-filelist', type=str, required=True, help='Path to input blackbox filelist')
parser.add_argument("--dut", type=str, required=True, help="Name of the DUT module.")
parser.add_argument("--model", type=str, required=True, help="Name of the Model module.")
parser.add_argument('--out-dut-filelist', type=str, required=True, help='Path to output filelist including all modules under the DUT.')
parser.add_argument('--out-model-filelist', type=str, required=True, help='Path to output filelist including all modules under the MODEL.')
parser.add_argument("--out-model-hier-json", type=str, required=True, help="Path to updated hierarchy JSON emitted by this script.")
parser.add_argument('--target-dir', type=str, required=True, help='Path to where module sources are located (combined with --in-all-filelist gives the absolute path to module sources).')
parser.add_argument("--gcpath", type=str, required=True, help="Path to gen-collateral")
args = parser.parse_args()

MODEL_SFX=args.model + "_UNIQUIFIED"

def die(msg: str) -> None:
  """Print a fatal error message and terminate.

  Args:
    msg: Description of the error condition.
  """
  print(f"[uniquify-module-names] ERROR: {msg}", file=sys.stderr)
  sys.exit(1)

def replace_module_decl(path: str, old: str, new: str) -> None:
  """Rename a SystemVerilog module declaration identifier in-place.

  Matches a declaration line (optionally with attributes) and replaces the
  module name token 'old' with 'new'. Only the first occurrence is updated.

  Args:
    path: Absolute path to the SV file to modify.
    old: Existing module identifier to replace.
    new: New module identifier to write.
  """
  with open(path, 'r', encoding='utf-8') as f:
    src = f.read()
  # Match the start of a module declaration and replace only the module identifier
  pat = re.compile(rf'^(?P<lead>\s*(?:\(\*.*?\*\)\s*)*module\s+)(?P<name>{re.escape(old)})\b',
                   flags=re.MULTILINE | re.DOTALL)
  new_src, n = pat.subn(rf'\g<lead>{new}', src, count=1)
  if n == 0:
    # Fallback without attributes
    pat2 = re.compile(rf'^(?P<lead>\s*module\s+)(?P<name>{re.escape(old)})\b', flags=re.MULTILINE)
    new_src, _ = pat2.subn(rf'\g<lead>{new}', src, count=1)
  if new_src != src:
    with open(path, 'w', encoding='utf-8') as f:
      f.write(new_src)


def replace_module_instantiation(path: str, old: str, new: str) -> None:
  """Rename a module identifier at the start of an instantiation header.

  This updates lines of the form:
    [attrs] <old> [#(...)] <instname> (
  to instead start with <new> while preserving attributes, parameters,
  instance name, and following punctuation.

  Args:
    path: Absolute path to the SV file to modify.
    old: Module identifier to search for at instantiation header.
    new: Replacement module identifier.
  """
  with open(path, 'r', encoding='utf-8') as f:
    src = f.read()
  # Match an instantiation header: optional attributes, module id, optional params, instance name, followed by '(' or '['
  pattxt = rf'''^
    (?P<prefix>\s*(?:\(\*.*?\*\)\s*)*)      # optional attributes
    (?P<mod>{re.escape(old)})\b                 # module id token
    (?P<params>\s*\#\s*\([^;]*?\))?          # optional parameterization
    (?P<spaces>\s+)                             # whitespace before instance name
    (?P<inst>[A-Za-z_][\w$]*)\s*               # instance name
    (?=\(|\[)                                  # followed by ( or [ (ports or array)
  '''
  pat = re.compile(pattxt, flags=re.MULTILINE | re.DOTALL | re.VERBOSE)

  def _repl(m: re.Match) -> str:
    return f"{m.group('prefix')}{new}{m.group('params') or ''}{m.group('spaces')}{m.group('inst')}"

  new_src, _ = pat.subn(_repl, src)
  if new_src != src:
    with open(path, 'w', encoding='utf-8') as f:
      f.write(new_src)


def bash(cmd: str) -> None:
  """Execute a shell command, exiting on failure (debug helper)."""
  fail = os.system(cmd)
  if fail:
    die(f"failed to execute shell command: {cmd}")
  else:
    print(cmd)

def bfs_collect_modules(tree: Dict[str, Any], child_to_ignore: Optional[str] = None) -> List[str]:
  """Breadth-first traversal collecting module names from a hierarchy tree.

  Args:
    tree: Parsed JSON object with keys 'instance_name', 'module_name', 'instances'.
    child_to_ignore: Optional module name to skip descending into.

  Returns:
    A list of module names in BFS order (may include duplicates).
  """
  q = [(tree['instance_name'], tree['module_name'], tree['instances'])]

  modules = list()
  while len(q) != 0:
    front = q[0]
    q.pop(0)

    (inst, mod, child) = front
    modules.append(mod)
    for c in child:
      if c['module_name'] != child_to_ignore:
        q.append((c['instance_name'], c['module_name'], c['instances']))
  return modules

def get_modules_in_verilog_file(file: str) -> List[str]:
  """Extract declared module names from a SystemVerilog file.

  Supports optional attributes ("(* ... *)") and lifetime (automatic/static).

  Args:
    file: Absolute path to the .sv/.v file.

  Returns:
    A list of declared module identifiers in the file.
  """
  module_names: List[str] = []
  if not os.path.exists(file):
    die(f"Verilog source not found while scanning filelists: {file}")
  try:
    with open(file, encoding='utf-8', errors='ignore') as f:
      src = f.read()
  except Exception as e:
    die(f"Failed to read Verilog source '{file}': {e}")
  # Match SV module declarations with optional attributes and optional lifetime
  # Examples:
  #   module Foo (
  #   (* keep_hierarchy *) module automatic Bar #(
  mod_decl = re.compile(r'^\s*(?:\(\*.*?\*\)\s*)*module\s+(?:automatic\s+|static\s+)?(?P<name>[A-Za-z_][\w$]*)\b',
                        flags=re.MULTILINE | re.DOTALL)
  for m in mod_decl.finditer(src):
    module_names.append(m.group('name'))
  return module_names

scanned_sv_files: List[str] = []
support_sv_files: List[str] = []  # .sv/.v files without any module declarations (packages, binds, etc.)
all_sv_files: List[str] = []      # all .sv/.v paths from filelist (relative or absolute)

def _normalize_path_token(tok: str) -> str:
  """Normalize a token from a filelist line into a path.

  - Strips quotes
  - Converts absolute paths under target-dir to gcpath-relative

  Args:
    tok: Raw token string from a filelist.

  Returns:
    Normalized path token.
  """
  tok = tok.strip()
  # Strip surrounding quotes if present
  if (tok.startswith('"') and tok.endswith('"')) or (tok.startswith("'") and tok.endswith("'")):
    tok = tok[1:-1]
  # Normalize absolute paths under target-dir to gcpath-relative
  if os.path.isabs(tok) and os.path.abspath(tok).startswith(os.path.abspath(args.target_dir) + os.sep):
    rel = os.path.relpath(os.path.abspath(tok), os.path.abspath(args.target_dir))
    return rel
  return tok

def _iter_filelist_entries(root_filelist: str) -> Iterable[str]:
  """Yield all file entries recursively from a root filelist.

  Supports -f/-F and @file includes, comment stripping, and basic flag skipping.
  Paths are normalized via _normalize_path_token.
  """
  visited: Set[str] = set()
  stack: List[str] = [root_filelist]
  while stack:
    fl = stack.pop()
    fkey = os.path.abspath(fl)
    if fkey in visited:
      continue
    visited.add(fkey)
    if not os.path.exists(fl):
      # Try relative to gcpath
      alt = os.path.join(args.gcpath, fl)
      if os.path.exists(alt):
        fl = alt
      else:
        die(f"Included filelist not found: {fl}")
    try:
      with open(fl, encoding='utf-8', errors='ignore') as f:
        for raw_line in f:
          # Drop comments after // and #
          line = raw_line.split('//',1)[0].split('#',1)[0].strip()
          if not line:
            continue
          tokens = line.split()
          i = 0
          while i < len(tokens):
            tok = tokens[i]
            if tok in ('-f','-F'):
              if i+1 >= len(tokens):
                die(f"Malformed filelist include line in {fl}: '{raw_line.strip()}'")
              inc = _normalize_path_token(tokens[i+1])
              stack.append(inc)
              i += 2
              continue
            # Explicit single-file include flags (e.g., '-v <file>')
            if tok == '-v':
              if i+1 >= len(tokens):
                die(f"Malformed -v entry in {fl}: '{raw_line.strip()}'")
              path_tok = _normalize_path_token(tokens[i+1])
              yield path_tok
              i += 2
              continue
            # Some flows use '-sv <file>'; treat next token as a file iff it looks like one
            if tok == '-sv' and i+1 < len(tokens):
              nxt = _normalize_path_token(tokens[i+1])
              if nxt.lower().endswith(('.sv', '.v')):
                yield nxt
                i += 2
                continue
            if tok.startswith('@') and len(tok) > 1:
              inc = _normalize_path_token(tok[1:])
              stack.append(inc)
              i += 1
              continue
            # Skip flags we don't consume
            if tok.startswith(('+incdir+','-I','-y','-timescale','+define+')) or tok in ('-sv',):
              i += 1
              continue
            yield _normalize_path_token(tok)
            i += 1
    except Exception as e:
      die(f"Failed to read filelist '{fl}': {e}")

def get_modules_in_filelist(filelist: str,
                            verilog_module_filename: Dict[str, str],
                            cc_filelist: List[str]) -> Tuple[Dict[str, str], List[str]]:
  """Populate a module→file mapping by scanning a (possibly nested) filelist.

  Args:
    filelist: Path to the root filelist.
    verilog_module_filename: Mapping to update with module name → source path.
    cc_filelist: List to append non-Verilog sidecar files to.

  Returns:
    Tuple of (verilog_module_filename, cc_filelist).
  """
  if not os.path.exists(filelist):
    # Try relative to gcpath
    alt = os.path.join(args.gcpath, filelist)
    if not os.path.exists(alt):
      die(f"Input filelist not found: {filelist}")
    filelist = alt
  for path in _iter_filelist_entries(filelist):
    ext = os.path.basename(path).split('.')[-1]
    if ext in ("v","sv"):
      abs_for_read = path if os.path.isabs(path) else os.path.join(args.gcpath, path)
      scanned_sv_files.append(abs_for_read)
      all_sv_files.append(path)
      modules = get_modules_in_verilog_file(abs_for_read)
      for module in modules:
        verilog_module_filename[module] = path
      if not modules:
        # Track Verilog/SystemVerilog files without module declarations (packages, binds, etc.)
        support_sv_files.append(path)
    else:
      cc_filelist.append(path)
  return (verilog_module_filename, cc_filelist)

def get_modules_under_hier(hier: str, child_to_ignore: Optional[str] = None) -> Set[str]:
  """Load a hierarchy JSON from disk and collect module names in it."""
  if not os.path.exists(hier):
    die(f"Hierarchy JSON not found: {hier}")
  try:
    with open(hier, encoding='utf-8') as hj:
      hj_data = json.load(hj)
  except Exception as e:
    die(f"Failed to parse hierarchy JSON '{hier}': {e}")
  try:
    modules_under_hier = set(bfs_collect_modules(hj_data, child_to_ignore=child_to_ignore))
  except Exception as e:
    die(f"Failed to traverse hierarchy JSON '{hier}': {e}")
  return modules_under_hier

def get_modules_under_hier_obj(hj_data: Dict[str, Any],
                               child_to_ignore: Optional[str] = None) -> Set[str]:
  """Collect module names from an in-memory hierarchy JSON object."""
  try:
    return set(bfs_collect_modules(hj_data, child_to_ignore=child_to_ignore))
  except Exception as e:
    die(f"Failed to traverse in-memory hierarchy JSON: {e}")

def write_verilog_filelist(modules: Iterable[str],
                           verilog_module_filename: Dict[str, str],
                           out_filelist: str) -> Set[str]:
  """Write a filelist containing Verilog sources for the given module set.

  Returns the set of unique file paths written.
  """
  written_files: Set[str] = set()
  existing_modules = verilog_module_filename.keys()

  with open(out_filelist, "w") as df:
    for module in modules:
      if module in existing_modules:
        verilog_filename = verilog_module_filename[module]  # relative to gcpath
        if verilog_filename not in written_files:
          written_files.add(verilog_filename)
          # Always prefix with target_dir unless the path is already absolute
          if os.path.isabs(verilog_filename):
            df.write(f"{verilog_filename}\n")
          else:
            df.write(f"{args.target_dir}/{verilog_filename}\n")
  return written_files

def write_cc_filelist(filelist: Iterable[str], out_filelist: str) -> None:
  """Append non-Verilog sidecar paths to an existing filelist file."""
  with open(out_filelist, "a") as df:
    for path in filelist:
      # Preserve relative layout for non-Verilog files as well
      if os.path.isabs(path):
        df.write(f"{path}\n")
      else:
        df.write(f"{args.target_dir}/{path}\n")

def write_support_sv_files(files: Iterable[str], out_filelist: str) -> None:
  """Append SV/V files that contain no module declarations (e.g., packages, binds)."""
  with open(out_filelist, "a") as df:
    for path in files:
      if os.path.isabs(path):
        df.write(f"{path}\n")
      else:
        df.write(f"{args.target_dir}/{path}\n")

def write_additional_sv_files(all_files: Iterable[str], already_written: Set[str], out_filelist: str) -> None:
  """Append any SV/V files from filelist not already emitted (e.g., verification/* helpers)."""
  with open(out_filelist, "a") as df:
    for path in all_files:
      # Normalize compare key to the same style as written (stored without target_dir prefix)
      rel = path if not os.path.isabs(path) else os.path.relpath(path, args.target_dir)
      # The 'already_written' set tracks gen-collateral-relative paths; strip any leading './'
      key = rel.lstrip('./')
      if key in already_written:
        continue
      if os.path.isabs(path):
        df.write(f"{path}\n")
      else:
        df.write(f"{args.target_dir}/{path}\n")

def generate_copy(rel_path: str, sfx: str) -> str:
  """Duplicate a Verilog file under gcpath with a suffixed module name.

  The new file is placed alongside the original (preserving directories) and
  its module declaration is renamed to include '_<sfx>'.

  Args:
    rel_path: Source file path (relative to gcpath or absolute).
    sfx: Suffix to append to the module identifier and file base name.

  Returns:
    New relative path (from gcpath) of the copied file.
  """
  # rel_path may be relative to args.gcpath or absolute
  dirname = os.path.dirname(rel_path)
  basename = os.path.basename(rel_path)
  (base_no_ext, ext) = os.path.splitext(basename)

  # New module/file name with suffix, preserving directory structure
  new_basename = base_no_ext + "_" + sfx
  new_rel_path = os.path.join(dirname, new_basename + ext) if dirname else (new_basename + ext)

  # Resolve absolute paths
  src_abs = rel_path if os.path.isabs(rel_path) else os.path.join(args.gcpath, rel_path)
  dst_abs = os.path.join(args.gcpath, new_rel_path)

  if not os.path.exists(src_abs):
    raise FileNotFoundError(f"source not found: {src_abs}")

  os.makedirs(os.path.dirname(dst_abs) or args.gcpath, exist_ok=True)
  shutil.copy(src_abs, dst_abs)
  # Update module declaration inside the copied file (Python-based, no sed)
  replace_module_decl(dst_abs, base_no_ext, new_basename)
  # Return the new file path relative to gcpath
  return new_rel_path

def bfs_uniquify_modules(tree: Dict[str, Any],
                         common_fnames: Set[str],
                         verilog_module_filename: Dict[str, str]) -> None:
  """Breadth-first pass to copy shared modules and update parent instantiations."""
  q = [(tree['instance_name'], tree['module_name'], tree['instances'], None)]
  updated_submodule = set()
  existing_modules = verilog_module_filename.keys()

  while len(q) != 0:
    front = q[0]
    q.pop(0)
    (inst, mod, child, parent) = front

    # external or unmapped module from filelists
    if mod not in existing_modules:
      if len(child) != 0:
        # Heuristic 1: find a scanned SV file whose basename matches the module name
        matched_path = None
        for fpath in scanned_sv_files:
          base = os.path.splitext(os.path.basename(fpath))[0]
          if base == mod:
            matched_path = fpath
            break
        if matched_path is not None:
          # Add a mapping and proceed (relative to gcpath if applicable)
          rel = os.path.relpath(matched_path, os.path.abspath(args.gcpath))
          verilog_module_filename[mod] = rel if not rel.startswith('..') else matched_path
          cur_file = verilog_module_filename[mod]
        else:
          die(
            "Hierarchy references a module not found in filelists with children: "
            f"module='{mod}', parent='{parent}', children={len(child)}. "
            "Ensure this module's Verilog is included in filelist.f or blackbox filelist, "
            "or that it is a leaf (e.g., a pure blackbox)."
          )
      # Leaf external is acceptable; nothing to copy/rename
      continue

    cur_file = verilog_module_filename[mod]

    # if the module is common, make a copy & update its instance in its parent
    new_mod = mod
    if mod in common_fnames:
      try:
        new_file = generate_copy(cur_file, MODEL_SFX)
        if parent is not None and ((parent, mod) not in updated_submodule):
          parent_path = verilog_module_filename[parent]
          parent_file = parent_path if os.path.isabs(parent_path) else os.path.join(args.gcpath, parent_path)
          # Update the parent instantiation to reference the uniquified module name
          replace_module_instantiation(parent_file, mod, f"{mod}_{MODEL_SFX}")
          updated_submodule.add((parent, mod))

        # add the uniquified module to the verilog_modul_filename dict
        new_mod = mod + "_" + MODEL_SFX
        verilog_module_filename[new_mod] = new_file
      except Exception as e:
        die(f"Failed to uniquify module '{mod}' from source '{cur_file}': {e}")

    # traverse its children
    for c in child:
      if c['module_name'] != args.dut:
        q.append((c['instance_name'], c['module_name'], c['instances'], new_mod))

def dfs_update_modules(tree: Dict[str, Any],
                       common_fnames: Set[str],
                       visited: Set[str]) -> bool:
  """Depth-first pass to rewrite child module names in the hierarchy JSON."""
  # List of direct submodules to update
  childs_to_update = list()
  for child in tree['instances']:
    # We don't have to change stuff that are under the dut
    if (child['module_name'] == args.dut):
      continue
    if dfs_update_modules(child, common_fnames, visited):
      childs_to_update.append(child['module_name'])
      if (child['module_name']) in common_fnames:
        child['module_name'] = child['module_name'] + "_" + MODEL_SFX

  cur_module = tree['module_name']
  new_file = None

  # cur_file is in the common list, or is a ancestor of of them, generate a new file
  if (cur_module in common_fnames) or len(childs_to_update) > 0:
    new_file = 1

  visited.add(cur_module)
  return (new_file is not None)

def uniquify_modules_under_model(modules_under_model: Set[str],
                                 common_modules: Set[str],
                                 verilog_module_filename: Dict[str, str]) -> Set[str]:
  """Uniquify common modules under the model tree and return updated module set."""
  try:
    with open(args.model_hier_json, encoding='utf-8') as imhj:
      imhj_data = json.load(imhj)
  except Exception as e:
    die(f"Failed to parse model hierarchy JSON '{args.model_hier_json}': {e}")

  visited = set()
  bfs_uniquify_modules(imhj_data, common_modules, verilog_module_filename)
  dfs_update_modules  (imhj_data, common_modules, visited)

  try:
    out_dir = os.path.dirname(args.out_model_hier_json)
    if out_dir:
      os.makedirs(out_dir, exist_ok=True)
    with open(args.out_model_hier_json, "w+", encoding='utf-8') as out_file:
      json.dump(imhj_data, out_file, indent=2)
  except Exception as e:
    die(f"Failed to write updated model hierarchy JSON '{args.out_model_hier_json}': {e}")

  return get_modules_under_hier_obj(imhj_data, args.dut)

def main() -> None:
  """Program entry: parse inputs, uniquify, and emit outputs."""
  verilog_module_filename = dict()
  cc_filelist = list()
  get_modules_in_filelist(args.in_all_filelist, verilog_module_filename, cc_filelist)
  get_modules_in_filelist(args.in_bb_filelist , verilog_module_filename, cc_filelist)

  modules_under_model = get_modules_under_hier(args.model_hier_json, args.dut)
  modules_under_top   = get_modules_under_hier(args.top_hier_json)
  common_modules      = modules_under_top.intersection(modules_under_model)

  # write top filelist
  written_top = write_verilog_filelist(modules_under_top, verilog_module_filename, args.out_dut_filelist)
  # Only include DUT hierarchy-driven Verilog in top filelist; do not append
  # verification/* helpers or model-only sources to avoid pulling TestHarness, etc.

  # rename modules that are common and compute updated model hierarchy
  uniquified_modules_under_model = uniquify_modules_under_model(modules_under_model, common_modules, verilog_module_filename)

  # write model filelist
  written_model = write_verilog_filelist(uniquified_modules_under_model, verilog_module_filename, args.out_model_filelist)
  write_support_sv_files(support_sv_files, args.out_model_filelist)
  write_additional_sv_files(all_sv_files, written_model, args.out_model_filelist)
  write_cc_filelist     (cc_filelist, args.out_model_filelist)


if __name__=="__main__":
  main()
