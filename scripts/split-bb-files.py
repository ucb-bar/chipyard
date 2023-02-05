#!/usr/bin/env python3

import json
import argparse
from collections import defaultdict

# Schema of *.f emitted by circt
"""
<gen-src-dir>/<long-name>/gen-collateral/SimUART.cc
<gen-src-dir>/<long-name>/gen-collateral/AsyncQueueSource.sv
<gen-src-dir>/<long-name>/gen-collateral/AsyncQueueSink.sv
<gen-src-dir>/<long-name>/gen-collateral/AsyncQueueSource_1.sv
<gen-src-dir>/<long-name>/gen-collateral/AsyncQueueSink_1.sv
<gen-src-dir>/<long-name>/gen-collateral/AsyncQueueSource_2.sv
<gen-src-dir>/<long-name>/gen-collateral/AsyncQueueSink_2.sv
<gen-src-dir>/<long-name>/gen-collateral/AsyncResetSynchronizerShiftReg_w4_d3_i0.sv
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

def write_lines_to_file(lines, file_path):
  with open(file_path, "w") as fp:
    for line in lines:
      fp.write("%s\n" % line)

if __name__ == "__main__":
  parser = argparse.ArgumentParser(description='Create *.model.bb.f and *.top.bb.f blackbox filelists')
  parser.add_argument('--in-bb-f', type=str, required=True, help='All blackbox files filelist (includes both MODEL/TOP files)')
  parser.add_argument('--in-top-hrchy-json', type=str, required=True, help='List containing hierarchy of top modules (top-module-hierarchy.json)')
  parser.add_argument('--in-anno-json', type=str, required=True, help='Anno. file with blackbox annotations')
  parser.add_argument('--out-top-bb-f', type=str, required=True, help='List of blackbox files for TOP')
  parser.add_argument('--out-model-bb-f', type=str, required=True, help='List of blackbox files for MODEL')
  args = parser.parse_args()

  # module_path -> list of bb paths (not fully resolved paths)
  mod_bb_dict = defaultdict(list)
  with open(args.in_anno_json, "r") as f:
    anno_data = json.load(f)
    for anno in anno_data:
      if 'BlackBoxInlineAnno' in anno['class']:
        mod_bb_dict[anno['target']].append(anno['name'])
      if 'BlackBoxPathAnno' in anno['class']:
        mod_bb_dict[anno['target']].append(anno['path'])

  with open(args.in_top_hrchy_json) as ihj:
    ihj_data = json.load(ihj)
    top_inner_modules = bfs_collect_submodules(ihj_data)

    with open(args.in_bb_f) as ibf:
      lines = ibf.read().splitlines()

      tbfs = set()
      for mod_path, bb_files in mod_bb_dict.items():
        leaf_mod = mod_path.split('.')[-1]

        # if matched, add the fully resolved path to the top bb filelist
        if leaf_mod in top_inner_modules:
          for line in lines:
            for bb_file in bb_files:
              if bb_file in line:
                tbfs.add(line)

      # now tbfs should be complete (need to remove tbf files from original bb file for model bb)
      mbfs = set()
      for line in lines:
        if not line in tbfs:
          mbfs.add(line)

      write_lines_to_file(tbfs, args.out_top_bb_f)
      write_lines_to_file(mbfs, args.out_model_bb_f)
