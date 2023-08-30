#!/usr/bin/env python3

import json
import argparse
import shutil
import os
import sys


parser = argparse.ArgumentParser(description="")
parser.add_argument("--model-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument("--top-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument('--in-all-filelist', type=str, required=True, help='Path to input filelist that has all modules (relative paths).')
parser.add_argument("--dut", type=str, required=True, help="Name of the DUT module.")
parser.add_argument("--model", type=str, required=True, help="Name of the Model module.")
parser.add_argument('--out-dut-filelist', type=str, required=True, help='Path to output filelist including all modules under the DUT.')
parser.add_argument('--out-model-filelist', type=str, required=True, help='Path to output filelist including all modules under the MODEL.')
parser.add_argument("--out-model-hier-json", type=str, required=True, help="Path to updated hierarchy JSON emitted by this script.")
parser.add_argument('--target-dir', type=str, required=True, help='Path to where module sources are located (combined with --in-all-filelist gives the absolute path to module sources).')
parser.add_argument("--gcpath", type=str, required=True, help="Path to gen-collateral")
args = parser.parse_args()

MODEL_SFX=args.model + "_UNIQUIFIED"


def bash(cmd):
  fail = os.system(cmd)
  if fail:
    print(f'[*] failed to execute {cmd}')
    sys.exit(1)
  else:
    print(cmd)

def bfs_collect_modules(tree, child_to_ignore = None):
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

def get_modules_in_verilog_file(file):
  module_names = list()
  with open(file) as f:
    lines = f.readlines()
    for line in lines:
      words = line.split()
      if len(words) > 0 and words[0] == "module":
        module_names.append(words[1].replace("(", "").replace(")", "").replace(";", ""))
  return module_names

def get_modules_in_filelist(verilog_module_filename, cc_filelist):
  with open(args.in_all_filelist) as fl:
    lines = fl.readlines()
    for line in lines:
      path = line.strip()
      basepath = os.path.basename(path)
      ext = basepath.split(".")[-1]

      if (ext == "v") or (ext == "sv"):
        modules = get_modules_in_verilog_file(os.path.join(args.gcpath, basepath))
        for module in modules:
          verilog_module_filename[module] = basepath
      else:
        cc_filelist.append(basepath)
  return (verilog_module_filename, cc_filelist)

def get_modules_under_hier(hier, child_to_ignore=None):
  with open(hier) as hj:
    hj_data = json.load(hj)
    modules_under_hier = set(bfs_collect_modules(hj_data, child_to_ignore=child_to_ignore))
  return modules_under_hier

def write_verilog_filelist(modules, verilog_module_filename, out_filelist):
  written_files = set()
  existing_modules = verilog_module_filename.keys()

  with open(out_filelist, "w") as df:
    for module in modules:
      if module in existing_modules:
        verilog_filename = verilog_module_filename[module]
        if verilog_filename not in written_files:
          written_files.add(verilog_filename)
          if args.target_dir in verilog_filename:
            df.write(f"{verilog_filename}\n")
          else:
            df.write(f"{args.target_dir}/{verilog_filename}\n")
  return written_files

def write_cc_filelist(filelist, out_filelist):
  with open(out_filelist, "a") as df:
    for path in filelist:
      file = os.path.basename(path)
      df.write(f"{args.target_dir}/{file}\n")

def generate_copy(c, sfx):
  (cur_name, ext) = os.path.splitext(c)
  new_name = cur_name + "_" + sfx
  new_file = new_name + ext

  cur_file = os.path.join(args.gcpath, c)
  new_file = os.path.join(args.gcpath, new_file)

  shutil.copy(cur_file, new_file)
  bash(f"sed -i s/\"module {cur_name}\"/\"module {new_name}\"/ {new_file}")
  return new_file

def bfs_uniquify_modules(tree, common_fnames, verilog_module_filename):
  q = [(tree['instance_name'], tree['module_name'], tree['instances'], None)]
  updated_submodule = set()
  existing_modules = verilog_module_filename.keys()

  while len(q) != 0:
    front = q[0]
    q.pop(0)
    (inst, mod, child, parent) = front

    # external module
    if mod not in existing_modules:
      assert(len(child) == 0)
      continue

    cur_file = verilog_module_filename[mod]

    # if the module is common, make a copy & update its instance in its parent
    new_mod = mod
    if mod in common_fnames:
      try:
        new_file = generate_copy(cur_file, MODEL_SFX)
        if parent is not None and ((parent, mod) not in updated_submodule):
          parent_file = os.path.join(args.gcpath, verilog_module_filename[parent])
          bash(f"sed -i s/\"{mod} \"/\"{mod}_{MODEL_SFX} \"/ {parent_file}")
          updated_submodule.add((parent, mod))

        # add the uniquified module to the verilog_modul_filename dict
        new_mod = mod + "_" + MODEL_SFX
        verilog_module_filename[new_mod] = new_file
      except:
        print(f"No corresponding file for {cur_file}")

    # traverse its children
    for c in child:
      if c['module_name'] != args.dut:
        q.append((c['instance_name'], c['module_name'], c['instances'], new_mod))

def dfs_update_modules(tree, common_fnames, visited):
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

def uniquify_modules_under_model(modules_under_model, common_modules, verilog_module_filename):
  with open(args.model_hier_json) as imhj:
    imhj_data = json.load(imhj)
    visited = set()
    bfs_uniquify_modules(imhj_data, common_modules, verilog_module_filename)
    dfs_update_modules  (imhj_data, common_modules, visited)

    with open(args.out_model_hier_json, "w+") as out_file:
      json.dump(imhj_data, out_file, indent=2)

def main():
  verilog_module_filename = dict()
  cc_filelist = list()
  get_modules_in_filelist(verilog_module_filename, cc_filelist)

  modules_under_model = get_modules_under_hier(args.model_hier_json, args.dut)
  modules_under_top   = get_modules_under_hier(args.top_hier_json)
  common_modules      = modules_under_top.intersection(modules_under_model)

  # write top filelist
  write_verilog_filelist(modules_under_top, verilog_module_filename, args.out_dut_filelist)

  # rename modules that are common
  uniquify_modules_under_model(modules_under_model, common_modules, verilog_module_filename)
  uniquified_modules_under_model = get_modules_under_hier(args.out_model_hier_json, args.dut)

  # write model filelist
  write_verilog_filelist(uniquified_modules_under_model, verilog_module_filename, args.out_model_filelist)
  write_cc_filelist     (cc_filelist, args.out_model_filelist)


if __name__=="__main__":
  main()
