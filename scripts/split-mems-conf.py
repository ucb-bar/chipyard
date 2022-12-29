#!/usr/bin/env python

import os
import json
import argparse
import sys
from typing import List, Optional

# Schema of json emitted by circt
"""
{
  "module_name": "mem_ext",
  "depth": 512,
  "width": 64,
  "masked": true,
  "read": false,
  "write": false,
  "readwrite": true,
  "mask_granularity": 8,
  "extra_ports": [],
  "hierarchy": [
    "TestHarness.ram.srams.mem.mem_ext"
  ]
}
"""

sys.setrecursionlimit(100)



# Performing DFS recursively is not a problem here since the modules that
# we are interested in are close to the root of the module hierarchy
def dfs_find_root(tree, module_name):
  if tree['module_name'] == module_name:
    return tree
  if len(tree['instances']) == 0:
    exit(0)
  for c in tree['instances']:
    ret = dfs_find_root(c, module_name)
    if ret is not None:
      return ret
  return None


def bfs_collect_module(tree):
  output = set()
  q = [(tree['instance_name'], tree['module_name'], tree['instances'])]

  while len(q) != 0:
    front = q[0]
    q.pop(0)

    (inst, mod, child) = front
    output.add(mod)
    for c in child:
      q.append((c['instance_name'], c['module_name'], c['instances']))
  return output


if __name__ == "__main__":
  parser = argparse.ArgumentParser(description='Use CIRCT (firtool) model-hrchy JSONs to create DUT and test harness smems confs or ')
  parser.add_argument('--in-smems-conf', type=str, required=True, help='Overall smems conf file that contains all memory definitions')
  parser.add_argument('--in-model-hrchy-json', type=str, required=True, help='JSON indicating which mem modules are in the DUT')
  parser.add_argument('--dut-module-name', type=str, required=True, help='Module name of the DUT')
  parser.add_argument('--model-module-name', type=str, required=True, help='Module name of the model')
  parser.add_argument('--out-dut-smems-conf', type=str, required=True, help='Smems conf with only DUT mem module definitions')
  parser.add_argument('--out-model-smems-conf', type=str, required=True, help='Smems conf with only top-most level mem module definitions (not including DUT modules)')
  args = parser.parse_args()

  with open(args.in_smems_conf) as isc, \
       open(args.in_model_hrchy_json) as imhj:
    imhj_data = json.load(imhj)

    dut_root = dfs_find_root(imhj_data, args.dut_module_name)
    dut_submodules = bfs_collect_module(dut_root)

    model_root = dfs_find_root(imhj_data, args.model_module_name)
    model_submodules = bfs_collect_module(model_root)

    with open(args.out_dut_smems_conf, "w") as odsc, \
         open(args.out_model_smems_conf, "w") as otsc:
      for l in isc:
        sl = l.split()

        # the line can't be split then stop immediately (normally an empty file)
        if len(sl) > 2:
          name = sl[1]

          if name in dut_submodules:
            odsc.write(l)
          elif name in model_submodules:
            otsc.write(l)
          else:
            assert False, "Unable to find smem CONF module in firtool emitted JSON files."
        else:
            exit(0)
