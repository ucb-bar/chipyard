#!/usr/bin/env python3


import json
import argparse
import sys
import shutil
import os
import sh
import copy


parser = argparse.ArgumentParser(description="")
parser.add_argument("--tile-generated-src", type=str, required=True, help="")
parser.add_argument("--tile-module", type=str, required=True, help="")
parser.add_argument("--tile-mod-hier-json", type=str, required=True, help="")

parser.add_argument("--subsys-generated-src", type=str, required=True, help="")
parser.add_argument("--subsys-module", type=str, required=True, help="")
parser.add_argument("--subsys-mod-hier-json", type=str, required=True, help="")
args = parser.parse_args()


tile_generated_src_copy = args.tile_generated_src + "-copy"
tile_gen_collateral_copy = os.path.join(tile_generated_src_copy, "gen-collateral")

subsys_generated_src_copy = args.subsys_generated_src + "-copy"
subsys_gen_collateral_copy = os.path.join(subsys_generated_src_copy, "gen-collateral")



def bash(cmd):
  fail = os.system(cmd)
  if fail:
    print(f'[*] failed to execute {cmd}')
    sys.exit(1)
  else:
    print(cmd)


def copy_directories():
  bash(f"mkdir -p {tile_generated_src_copy}")
  bash(f"cp -r {args.tile_generated_src}/* {tile_generated_src_copy}/")

  bash(f"mkdir -p {subsys_generated_src_copy}")
  bash(f"cp -r {args.subsys_generated_src}/* {subsys_generated_src_copy}/")



def generate_copy(cur_name, sfx, cur_path, new_path):
  cur_file_name = cur_name.replace("_ext", "")
  new_file_name = cur_file_name + "_" + sfx
  new_name = cur_name + "_" + sfx

  try:
    new_file = os.path.join(new_path, new_file_name + ".sv")
    cur_file = os.path.join(cur_path, cur_file_name + ".sv")
    shutil.copy(cur_file, new_file)
  except:
    new_file = os.path.join(new_path, new_file_name + ".v")
    cur_file = os.path.join(cur_path, cur_file_name + ".v")
    shutil.copy(cur_file, new_file)
# print(new_file, cur_name, new_name)
  old_str = f"module {cur_name}"
  new_str = f"module {new_name}"
  sh.sed("-i", f"s/{old_str}/{new_str}/", new_file)
  return (cur_name, new_name, new_file)


def dfs_tile(tree, visited, should_copy, copied_files):
  childs_to_update = list()
  for child in tree['instances']:
    if (child['module_name'] in visited):
      continue

    new_should_copy = copy.deepcopy(should_copy)
    if (child['module_name'] == args.tile_module):
      new_should_copy = True
    if new_should_copy:
      childs_to_update.append(child['module_name'])
    dfs_tile(child, visited, new_should_copy, copied_files)

  cur_module_name = tree['module_name']
  new_module_name = cur_module_name + "_COPY"

  print(f"--{cur_module_name}, should_copy: {should_copy}")

  if should_copy:
    (cur_name, new_name, new_file) = generate_copy(cur_module_name, "COPY", tile_gen_collateral_copy, subsys_gen_collateral_copy)
    copied_files[cur_name] = (new_name, new_file)

    for c in childs_to_update:
      c_new_name = c + "_COPY"
      print(f"|-{c}, {c_new_name}, {new_file}")
      cmd = f"sed -i s/\"{c}\"/{c_new_name}/ {new_file}"
      bash(cmd)

  visited.add(tree['module_name'])


def traverse_tile():
  tile_json = open(args.tile_mod_hier_json, "r")
  tile_tree = json.load(tile_json)

  visited = set()
  copied_files = dict()
  dfs_tile(tile_tree, visited, False, copied_files)
  return copied_files



def dfs_subsys(tree, visited, should_remove, remove_files):
  childs_to_update = list()
  for child in tree['instances']:
    if (child['module_name'] in visited):
      continue


    new_should_remove = copy.deepcopy(should_remove)
    if (child['module_name'] == args.subsys_module):
      new_should_remove = True

    if new_should_remove:
      remove_files.append(child['module_name'])
    dfs_subsys(child, visited, new_should_remove, remove_files)

  visited.add(tree['module_name'])

def traverse_subsys():
  subsys_json = open(args.subsys_mod_hier_json, "r")
  subsys_tree = json.load(subsys_json)

  visited = set()
  remove_files = list()
  dfs_subsys(subsys_tree, visited, False, remove_files)
  return remove_files



def get_top_io(file_path):
  f = open(file_path, "r")
  lines = f.readlines()
  f.close()


  io_section = False

  pins = list()
  for line in lines:
    words = line.split()

    if io_section:
      if len(words) > 0 and ");" == words[0]:
        io_section = False
        break
      else:
        if len(words) == 1: # name only
          pins.append(words[0].replace(",", ""))
        elif len(words) == 2: # input/outpu name
          pins.append(words[1].replace(",", ""))
        elif len(words) == 3:
          pins.append(words[2].replace(",", ""))
        else:
          assert(False)
    else:
      if len(words) > 1 and "module" == words[0]:
        io_section = True

  return pins


def main():
  copy_directories()
  copied_files = traverse_tile()
  sim_file_common = os.path.join(subsys_generated_src_copy, "sim_files.common.f")
  cmd = f"sed -i s/\"DummyTileConfig\"/DummyTileConfig-copy/ {sim_file_common}"
  bash(cmd)
  pwd = os.path.abspath(os.getcwd())
  for (k, v) in copied_files.items():
    cmd = f"echo {os.path.join(pwd, v[1])} >> {sim_file_common}"
    bash(cmd)

  remove_files = traverse_subsys()
  print(remove_files)


  tile_top_pins = get_top_io(os.path.join(subsys_gen_collateral_copy, args.tile_module + "_COPY.sv"))
  subsys_top_pins = get_top_io(os.path.join(subsys_gen_collateral_copy, args.subsys_module + ".sv"))

  for r in remove_files:
    bash(f"rm {subsys_gen_collateral_copy}/{r}.*")
    if "BlackBox" in r:
      bash(f"sed -i /\"{r}.v\"/d {sim_file_common}")
    else:
      bash(f"sed -i /\"{r}.sv\"/d {sim_file_common}")



  tile_prci = os.path.join(subsys_gen_collateral_copy, "TilePRCIDomain.sv")
  sh.sed("-i", f"s/{args.subsys_module}/{args.tile_module}_COPY/", tile_prci)
  for (tp, sp) in zip(tile_top_pins, subsys_top_pins):
    print(tp, sp)
    cmd = f"sed -i s/\"{sp}\"/{tp}/ {tile_prci}"
    bash(cmd)

if __name__ == "__main__":
  main()
