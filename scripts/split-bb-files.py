#!/usr/bin/env python3

import os
import json
import argparse
import sys
from pathlib import Path
from typing import List, Optional

# Schema of *.f emitted by circt
"""
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/SimUART.cc
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncQueueSource.sv
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncQueueSink.sv
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncQueueSource_1.sv
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncQueueSink_1.sv
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncQueueSource_2.sv
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncQueueSink_2.sv
/scratch/joonho.whangbo/coding/chipyard/vlsi/generated-src/chipyard.TestHarness.RocketConfig/gen-collateral/AsyncResetSynchronizerShiftReg_w4_d3_i0.sv
"""

def bfs_collect_submodules(tree):
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

def get_modules(f):
  lines = f.readlines()
  module_list = list()
  for line in lines:
    try:
      module_list.append(os.path.basename(line))
    except:
      print("Excepted a linux path, got something else")
  return module_list

def get_inner_modules(f):
  lines = f.readlines()
  inner_module_list = list()
  for line in lines:
    words = line.split()
    if len(words) >= 2 and "module" == words[0]:
      inner_module_list.append(words[1].replace("(", ""))
  return inner_module_list

def write_lines_to_file(lines, file_path):
  with open(file_path, "w") as fp:
    for line in lines:
      fp.write("%s" % line)


if __name__ == "__main__":
  parser = argparse.ArgumentParser(description='Use *.model.f and *.top.f to restore the MODEL/TOP blackbox separation')
  parser.add_argument('--in-top-f', type=str, required=True, help='List of generated files specific for TOP(DUT)')
  parser.add_argument('--in-model-f', type=str, required=True, help='List of generated files specific for MODEL')
  parser.add_argument('--in-bb-f', type=str, required=True, help='List of generated files specific for MODEL')
  parser.add_argument('--in-top-hrchy-json', type=str, required=True, help='List containing hierarchy of top modules (top-module-hierarchy.json)')
  parser.add_argument('--out-top-bb-f', type=str, required=True, help='List of blackbox modules for TOP')
  parser.add_argument('--out-model-bb-f', type=str, required=True, help='List of blackbox modules for MODEL')
  args = parser.parse_args()

  itf = open(args.in_top_f)
  top_modules = set(get_modules(itf))
  itf.close()

  imf = open(args.in_model_f)
  model_modules = set(get_modules(imf))
  imf.close()

  ihj = open(args.in_top_hrchy_json)
  ihj_data = json.load(ihj)
  top_inner_modules = bfs_collect_submodules(ihj_data)
  ihj.close()

  ibf = open(args.in_bb_f)
  lines = ibf.readlines()

  """
  " model  top
  " o      o   -> model
  " x      o   -> top
  " x      x   -> model
  "  - check inner module
  "  - currently, there is no way of knowing if certain inner modules(actual verilog
  "  modules inside the files are all included in TOP or MODEL)
  "  - for now, assume that if a innter module is included in TOP, the file itself
  "  is also for TOP
  """
  tbf = list()
  mbf = list()
  unknown = list()
  for line in lines:
    module = os.path.basename(line)
    extension = os.path.splitext(module)[1]
    if module in model_modules:
      mbf.append(line)
    elif module in top_modules:
      tbf.append(line)
    elif ".v" not in extension and ".sv" not in extension:
      mbf.append(line)
    else:
      unknown.append(line)


  for line in unknown:
    f = open(Path(line.replace("\n", "")))
    inner_modules = get_inner_modules(f)
    f.close()

    inner_module_in_top = False
    for im in inner_modules:
      if im in top_inner_modules:
        inner_module_in_top = True
        break
    if inner_module_in_top:
      tbf.append(line)
    else:
      mbf.append(line)

  write_lines_to_file(tbf, args.out_top_bb_f)
  write_lines_to_file(mbf, args.out_model_bb_f)

  ibf.close()
