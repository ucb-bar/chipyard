#!/usr/bin/env python3

import argparse
import subprocess
from collections import defaultdict
import re

if __name__ == "__main__":
  parser = argparse.ArgumentParser(description='Pretty print all configs given a list of scala files')
  parser.add_argument('FILES', metavar="FILE", type=str, nargs="+", help='File(s) to search within')
  args = parser.parse_args()

  cmd = ['grep', '-o', r"class \+.* \+extends \+Config"] + args.FILES
  r = subprocess.run(cmd, check=True, capture_output=True)

  file_path_dict = defaultdict(list)
  for l in r.stdout.decode("UTF-8").splitlines():
    match = re.match(r"^(.*):class +([a-zA-Z_$][a-zA-Z\d_$]*).* +extends", l)
    if match:
      file_path_dict[match.group(1)].append(match.group(2))

  for k, v in file_path_dict.items():
    print(f"In file: {k}")
    for e in v:
      print(f"  {e}")
    print("\n")
