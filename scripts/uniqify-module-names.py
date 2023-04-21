#!/usr/bin/env python3

import json
import argparse
import shutil
import os
import datetime


parser = argparse.ArgumentParser(description="")
parser.add_argument("--top-filelist", type=str, required=True, help="Abs path to <top>.<model>.top.f")
parser.add_argument("--mod-filelist", type=str, required=True, help="Abs path to <top>.<model>.model.f")
parser.add_argument("--gen-collateral-path", dest="gcpath", type=str, required=True, help="Abs path to the gen-collateral directory")
parser.add_argument("--model-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument("--out-model-hier-json", type=str, required=True, help="Path to updated hierarchy JSON emitted by this script.")
parser.add_argument("--dut", type=str, required=True, help="Name of the DUT module.")
parser.add_argument("--model", type=str, required=True, help="Name of the Model module.")
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

def update_filelist(cur_file, new_file):
  bash(f"echo \"{args.gcpath}/{new_file}\" >> {os.path.join(args.gcpath, args.mod_filelist)}")

def generate_copy(c, sfx):
  (cur_name, ext) = os.path.splitext(c)
  new_name = cur_name + "_" + sfx
  new_file = new_name + ext

  cur_file = os.path.join(args.gcpath, c)
  new_file = os.path.join(args.gcpath, new_file)

  shutil.copy(cur_file, new_file)
  bash(f"sed -i s/\"module {cur_name}\"/\"module {new_name}\"/ {new_file}")
  return new_file

def dfs_update_modules(tree, common_fnames, visited, top_fnames):
  # List of direct submodules to update
  childs_to_update = list()
  for child in tree['instances']:
    # We don't have to change stuff that are under the dut
    if (child['module_name'] == args.dut) or (child['module_name'] in visited):
      continue
    if dfs_update_modules(child, common_fnames, visited, top_fnames):
      childs_to_update.append(child['module_name'])
      if (child['module_name'] + ".sv") in common_fnames:
        child['module_name'] = child['module_name'] + "_" + MODEL_SFX

  cur_module = tree['module_name']
  cur_file = cur_module + ".sv"
  new_file = None

  # cur_file is in the common list, or is a ancestor of of them, generate a new file
  if (cur_file in common_fnames) or len(childs_to_update) > 0:
    new_file = generate_copy(cur_file, MODEL_SFX)
    update_filelist(cur_file, os.path.basename(new_file))

  for submodule_name in childs_to_update:
    if (submodule_name + ".sv") in common_fnames:
      bash(f"sed -i s/\"{submodule_name}\"/\"{submodule_name}_{MODEL_SFX}\"/ {new_file}")

  visited.add(cur_module)
  return (new_file is not None)

def main():
  top_fnames = set(get_filelist(args.top_filelist))
  mod_fnames = set(get_filelist(args.mod_filelist))
  common_fnames = top_fnames.intersection(mod_fnames)

  with open(args.model_hier_json) as imhj:
    imhj_data = json.load(imhj)

    with open(args.out_model_hier_json, "w+") as out_file:
      visited = set()
      dfs_update_modules(imhj_data, common_fnames, visited, top_fnames)
      json.dump(imhj_data, out_file, indent=2)

if __name__ == "__main__":
  main()
