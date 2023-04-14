#!/usr/bin/env python3

import json
import argparse
import shutil
import os
import sh



parser = argparse.ArgumentParser(description="")
parser.add_argument("--top-filelist", type=str, required=True, help="Abs path to <top>.<model>.top.f")
parser.add_argument("--mod-filelist", type=str, required=True, help="Abs path to <top>.<model>.model.f")
parser.add_argument("--gen-collateral-path", dest="gcpath", type=str, required=True, help="Abs path to the gen-collateral directory")
parser.add_argument("--model-hier-json", type=str, required=True, help="Path to hierarchy JSON emitted by firtool. Must include DUT as a module.")
parser.add_argument('--dut', type=str, required=True, help='Name of the DUT module.')
args = parser.parse_args()


def get_filelist(filelist):
  f = open(filelist, "r")
  lines = f.readlines()
  f.close()

  fnames = []
  for line in lines:
    try:
      fname = line.split("/")[-1].replace("\n", "")
      fnames.append(fname)
    except:
      print(f"Something is wrong about this line {line}")

  return fnames

def update_filelist(cur_file, new_file):
  sh.sed("-i", f"s/\b{cur_file}\b/{new_file}/", os.path.join(args.gcpath, args.mod_filelist))


def generate_copy(c, sfx):
  (cur_name, ext) = os.path.splitext(c)
  new_name = cur_name + "_" + sfx
  new_file = new_name + ext

  cur_file = os.path.join(args.gcpath, c)
  new_file = os.path.join(args.gcpath, new_file)

  shutil.copy(cur_file, new_file)
  sh.sed("-i", f"s/\b{cur_name}\b/{new_name}/", new_file)
  return new_file



def dfs_update_modules(tree, common_fnames, visited, top_fnames, updated_modules):
  # List of direct submodules to update
  childs_to_update = list()
  for child in tree['instances']:
    # We don't have to change stuff that are under the dut
    if (child['module_name'] == args.dut) or (child['module_name'] in visited):
      continue
    if dfs_update_modules(child, common_fnames, visited, top_fnames, updated_modules):
      childs_to_update.append(child['module_name'])
      if (child['module_name'] + ".sv") in common_fnames:
        child['module_name'] = child['module_name'] + "_Model"
        updated_modules.append(child['module_name'])

  cur_module = tree['module_name']
  cur_file = cur_module + ".sv"
  new_file = None

  # cur_file is in the common list, generate a new file
  if cur_file in common_fnames:
    new_file = generate_copy(cur_file, "Model")
    update_filelist(cur_file, os.path.basename(new_file))

  # has some child to update, but new_file wasn't generated
  if (new_file is None) and len(childs_to_update) > 0:
    new_file = os.path.join(args.gcpath, cur_file)
    assert(cur_file not in top_fnames)

  for submodule_name in childs_to_update:
    if (submodule_name + ".sv") in common_fnames:
      sh.sed("-i", f"s/\b{submodule_name}\b/{submodule_name}_Model/", new_file)

  visited.add(cur_module)
  return (new_file is not None)


def main():
  top_fnames = set(get_filelist(args.top_filelist))
  mod_fnames = set(get_filelist(args.mod_filelist))
  common_fnames = top_fnames.intersection(mod_fnames)

  imhj = open(args.model_hier_json, "r")
  imhj_data = json.load(imhj)

  visited = set()
  updated_modules = list()
  dfs_update_modules(imhj_data, common_fnames, visited, top_fnames, updated_modules)

  out_file = open(args.model_hier_json, "w")
  json.dump(imhj_data, out_file, indent=2)
  out_file.close()



if __name__ == "__main__":
  main()
