#!/usr/bin/env python3

import argparse
import subprocess
from collections import defaultdict
import re
from copy import deepcopy
import os

cy_path = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))

# from https://gist.github.com/angstwad/bf22d1822c38a92ec0a9
def deep_merge(a: dict, b: dict) -> dict:
    """Merge two dicts and return a singular dict"""
    result = deepcopy(a)
    for bk, bv in b.items():
        av = result.get(bk)
        if isinstance(av, dict) and isinstance(bv, dict):
            result[bk] = deep_merge(av, bv)
        else:
            result[bk] = deepcopy(bv)
    return result

if __name__ == "__main__":
  parser = argparse.ArgumentParser(description='Pretty print all configs given a filelist of scala files')
  parser.add_argument('FILE', type=str, help='Filelist of scala files to search within')
  parser.add_argument('-l', '--levels', default=0, type=int, help='Number of levels to recursively look for configs')
  args = parser.parse_args()

  files = []
  with open(args.FILE, 'r') as f:
    files = f.read().splitlines()

  cmd = ['grep', '-o', r"class \+.* \+extends \+Config"] + files
  r = subprocess.run(cmd, check=True, capture_output=True)

  base_file_path_dict = defaultdict(list)
  for l in r.stdout.decode("UTF-8").splitlines():
    match = re.match(r"^(.*):class +([a-zA-Z_$][a-zA-Z\d_$]*).* +extends", l)
    if match:
      base_file_path_dict[match.group(1)].append(match.group(2))

  levels = []
  for level in range(args.levels):
    if level == 0:
      # use the base
      dict_to_use = base_file_path_dict
    else:
      # use the level-1 dict
      assert len(levels) > 0
      dict_to_use = levels[-1]

    file_path_dict = defaultdict(list)

    for configs in dict_to_use.values():
      for config in configs:
        cmd = ['grep', '-o', r"class \+.* \+extends \+" + f"{config}"] + files
        r = subprocess.run(cmd, capture_output=True)

        for l in r.stdout.decode("UTF-8").splitlines():
          match = re.match(r"^(.*):class +([a-zA-Z_$][a-zA-Z\d_$]*).* +extends", l)
          if match:
            file_path_dict[match.group(1)].append(match.group(2))

    levels.append(file_path_dict)

  final_dict = base_file_path_dict
  for dct in levels:
    final_dict = deep_merge(final_dict, dct)

  print(f"Finding all one-line config. fragments (up to {args.levels} levels)\n")
  for k, v in final_dict.items():
    print(f"{k.replace(cy_path, 'chipyard')}:")
    for e in v:
      print(f"  {e}")
    print("")
