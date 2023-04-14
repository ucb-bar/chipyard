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
parser.add_argument('--model', type=str, required=True, help='Name of the MODEL module.')
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


def generate_copy(c, sfx):
  (cur_name, ext) = os.path.splitext(c)
  new_name = cur_name + "_" + sfx
  new_file = new_name + ext

  cur_file = os.path.join(args.gcpath, c)
  new_file = os.path.join(args.gcpath, new_file)

  shutil.copy(cur_file, new_file)
  sh.sed("-i", f"s/{cur_name}/{new_name}/", new_file)

  return new_file



def dfs_update_modules(tree, common_fnames, visited):
  # List of direct submodules to update
  childs_to_update = list()
  for child in tree['instances']:
    if (child['module_name'] == args.dut) or (child['module_name'] in visited):
      continue
    if dfs_update_modules(child, common_fnames, visited):
      childs_to_update.append(child['module_name'])

  cur_module = tree['module_name']
  cur_file = cur_module + ".sv"
  new_file = None

  # cur_file is in the common list, generate a new file
  for c in common_fnames:
    if cur_file == c:
      new_file = generate_copy(c, "Model")

  # has some child to update, but new_file wasn't generated
  if (new_file is None) and len(childs_to_update) > 0:
    if cur_module == args.model:
      new_file = os.path.join(args.gcpath, cur_file)
    else:
      new_file = generate_copy(cur_file, "Model")

  if new_file is not None:
    print(f"-- {cur_module}")

  for submodule_name in childs_to_update:
    print(f"|- {submodule_name}")
    sh.sed("-i", f"s/{submodule_name}/{submodule_name}_Model/", new_file)

  visited.add(cur_module)
  return (new_file is not None)


def main():
  top_fnames = set(get_filelist(args.top_filelist))
  mod_fnames = set(get_filelist(args.mod_filelist))

  common_fnames = top_fnames.intersection(mod_fnames)
  for c in common_fnames:
    print(c)

  imhj = open(args.model_hier_json, "r")
  imhj_data = json.load(imhj)

  visited = set()
  dfs_update_modules(imhj_data, common_fnames, visited)



if __name__ == "__main__":
  main()
