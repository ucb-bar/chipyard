#!/usr/bin/env python3

import json
import argparse
import shutil
import os
import datetime
import sys

parser = argparse.ArgumentParser(description="")
parser.add_argument("--model-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument("--top-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument('--in-all-filelist', type=str, required=True, help='Path to input filelist that has all modules (relative paths).')
parser.add_argument("--dut", type=str, required=True, help="Name of the DUT module.")
parser.add_argument("--model", type=str, required=True, help="Name of the Model module.")
parser.add_argument('--target-dir', type=str, required=True, help='Path to where module sources are located (combined with --in-all-filelist gives the absolute path to module sources).')
parser.add_argument('--out-dut-filelist', type=str, required=True, help='Path to output filelist including all modules under the DUT.')
parser.add_argument('--out-model-filelist', type=str, required=True, help='Path to output filelist including all modules under the MODEL.')
parser.add_argument("--out-model-hier-json", type=str, required=True, help="Path to updated hierarchy JSON emitted by this script.")
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

def get_filelist(filelist):
  fnames = []
  with open(filelist) as f:
    lines = f.readlines()
    for line in lines:
      try:
        fname = line.split("/")[-1].strip()
        fnames.append(fname)
      except:
        print(f"Something is wrong about this line '{line}'")
  return fnames

def generate_copy(c, sfx):
  (cur_name, ext) = os.path.splitext(c)
  new_name = cur_name + "_" + sfx
  new_file = new_name + ext

  cur_file = os.path.join(args.gcpath, c)
  new_file = os.path.join(args.gcpath, new_file)

  shutil.copy(cur_file, new_file)
  bash(f"sed -i s/\"module {cur_name}\"/\"module {new_name}\"/ {new_file}")
  return new_file

def dfs_update_modules(tree, common_fnames, visited, ext_dict):
  # List of direct submodules to update
  childs_to_update = list()
  for child in tree['instances']:
    # We don't have to change stuff that are under the dut
    if (child['module_name'] == args.dut):
      continue
    if dfs_update_modules(child, common_fnames, visited, ext_dict):
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

def bfs_update(tree, common_fnames, ext_dict, filelist):
  q = [(tree['instance_name'], tree['module_name'], tree['instances'], None)]

  updated_submodule = set()

  while len(q) != 0:
    front = q[0]
    q.pop(0)
    (inst, mod, child, parent) = front

    try:
      cur_file = mod + "." + ext_dict[mod]
    except:
      cur_file = mod + ".sv"

    mod_updated = False

    # if the module is common, make a copy & update its instance in its parent
    if mod in common_fnames:
      try:
        new_file = generate_copy(cur_file, MODEL_SFX)
        filelist.append((mod, new_file))
        if parent is not None and ((parent, mod) not in updated_submodule):
          parent_file = os.path.join(args.gcpath, parent + "." + ext_dict[parent])
          bash(f"sed -i s/\"{mod} \"/\"{mod}_{MODEL_SFX} \"/ {parent_file}")
          updated_submodule.add((parent, mod))
        mod_updated = True
      except:
        print(f"No corresponding file for {cur_file}")
    else:
      filelist.append((mod, cur_file))

    # set the parent module name
    new_mod = mod
    if mod_updated:
      new_mod = mod + "_" + MODEL_SFX
      ext_dict[new_mod] = ext_dict[mod]

    # traverse its children
    for c in child:
      if c['module_name'] != args.dut:
        q.append((c['instance_name'], c['module_name'], c['instances'], new_mod))

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

def write_filelist(modules, out_file):
  with open(out_file, "w") as df, \
       open(args.in_all_filelist) as fl:
    # add paths that correspond to modules to output file
    for path in fl:
        writeOut = False
        for dm in modules:
            if dm in path:
                writeOut = True
                break

        # prepend the target directory to get filelist with absolute paths
        if writeOut:
            if not args.target_dir in path:
                df.write(f"{args.target_dir}/{path}")
            else:
                df.write(f"{path}")

def write_filelist_model(modules, out_file, ext_dict):
  with open(out_file, "w") as df:
    for (m, fname) in modules:
      if m in ext_dict.keys():
        if not args.target_dir in fname:
          df.write(f"{args.target_dir}/{fname}\n")
        else:
          df.write(f"{fname}\n")

def get_file_ext(all_filelist):
  ext_dict = dict()
  with open(all_filelist) as fl:
    for path in fl:
      fname = os.path.basename(path)
      fname_strip = fname.strip().split(".")
      ext = fname_strip[-1]
      fname_strip.pop()
      module = ".".join(fname_strip)
      ext_dict[module] = ext
  return ext_dict

def main():
  with open(args.model_hier_json) as imhj:
    imhj_data = json.load(imhj)
    modules_under_model = set(bfs_collect_modules(imhj_data, child_to_ignore=args.dut))

  with open(args.top_hier_json) as imhj:
    imhj_data = json.load(imhj)
    modules_under_top = set(bfs_collect_modules(imhj_data))

  common_modules = modules_under_top.intersection(modules_under_model)
  write_filelist(modules_under_top, args.out_dut_filelist)
  ext_dict = get_file_ext(args.in_all_filelist)

  with open(args.model_hier_json) as imhj:
    imhj_data = json.load(imhj)

    with open(args.out_model_hier_json, "w+") as out_file:
      visited = set()
      filelist = list()
      bfs_update(imhj_data, common_modules, ext_dict, filelist)
      dfs_update_modules(imhj_data, common_modules, visited, ext_dict)
      json.dump(imhj_data, out_file, indent=2)
      write_filelist_model(set(filelist), args.out_model_filelist, ext_dict)

if __name__ == "__main__":
  main()
