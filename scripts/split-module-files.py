#!/usr/bin/env python3

import json
import argparse
from typing import List, Optional

# Schema of json emitted by circt
"""
{
  "instance_name": "TestHarness",
  "module_name": "TestHarness",
  "instances": [
    {
      "instance_name": "chiptop",
      "module_name": "ChipTop",
      "instances": [
        {
          "instance_name": "system",
          "module_name": "DigitalTop",
          "instances": [ ]
        }, ...
      ]
    },
    {
      "instance_name": "simdram",
      "module_name": "SimDRAM",
      "instances": []
    },
  ]
}
"""

def get_modules(js: dict) -> List[str]:
    if 'instances' not in js:
        return js['module_name']
    else:
        mods = []
        for mod in js['instances']:
            mods.extend(get_modules(mod))
        return [js['module_name']] + mods

def find_mod_by_name(js: dict, name: str) -> Optional[List[dict]]:
    if 'instances' not in js:
        return None
    else:
        mods = []
        for mod in js['instances']:
            if mod['module_name'] == name:
                mods.append(mod)
            other_mods = find_mod_by_name(mod, name)
            if other_mods is not None:
                mods.extend(other_mods)
        return mods

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Convert CIRCT (firtool) hierarchy JSON into DUT and test harness filelists')
    parser.add_argument('--model-hier-json', type=str, required=True, help='Path to hierarchy JSON emitted by firtool. Must include DUT as a module.')
    parser.add_argument('--dut', type=str, required=True, help='Name of the DUT module.')
    parser.add_argument('--out-dut-filelist', type=str, required=True, help='Path to output filelist including all modules under the DUT.')
    parser.add_argument('--out-model-filelist', type=str, required=True, help='Path to output filelist including all modules under the top-most module but not modules under the DUT.')
    parser.add_argument('--in-all-filelist', type=str, required=True, help='Path to input filelist that has all modules (relative paths).')
    parser.add_argument('--target-dir', type=str, required=True, help='Path to where module sources are located (combined with --in-all-filelist gives the absolute path to module sources).')
    args = parser.parse_args()

    with open(args.model_hier_json) as f:
        j = json.load(f)

        dut_tops = find_mod_by_name(j, args.dut)
        assert dut_tops is not None
        assert len(dut_tops) == 1
        dut_top = dut_tops[0]

        dut_mods = set(get_modules(dut_top))
        model_mods = set(get_modules(j)) - dut_mods
        both_mods = dut_mods.intersection(model_mods)

        assert len(both_mods) == 0

        with open(args.out_dut_filelist, 'w') as df, \
             open(args.in_all_filelist) as fl:
            # add paths that correspond to modules to output file
            for path in fl:
                writeOut = False
                for dm in dut_mods:
                    if dm in path:
                        writeOut = True
                        break

                # prepend the target directory to get filelist with absolute paths
                if writeOut:
                    if not args.target_dir in path:
                        df.write(f"{args.target_dir}/{path}")
                    else:
                        df.write(f"{path}")

        with open(args.out_model_filelist, 'w') as df, \
             open(args.in_all_filelist) as fl:
            # add paths that correspond to modules to output file
            for path in fl:
                writeOut = False
                for dm in model_mods:
                    if dm in path:
                        writeOut = True
                        break

                # prepend the target directory to get filelist with absolute paths
                if writeOut:
                    if not args.target_dir in path:
                        df.write(f"{args.target_dir}/{path}")
                    else:
                        df.write(f"{path}")
