#!/usr/bin/env python
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
    parser = argparse.ArgumentParser(description='Convert circt hierarchy json into DUT and test harness filelists')
    parser.add_argument('--hier-json', type=str, required=True, help='path to hierarchy JSON emitted by firtool')
    parser.add_argument('--dut-top', type=str, required=True, help='name of the DUT top-level module')
    parser.add_argument('--filelist', type=str, required=True, help='input filelist')
    parser.add_argument('--build_dir', type=str, required=True, help='build_dir')
    parser.add_argument('--dut-mods', type=str, required=True, help='dut mods')
    args = parser.parse_args()
    with open(args.hier_json) as f:
        j = json.load(f)
        dut_tops = find_mod_by_name(j, args.dut_top)
        assert dut_tops is not None
        assert len(dut_tops) == 1
        dut_top = dut_tops[0]
        dut_mods = set(get_modules(dut_top))
        tb_mods = set(get_modules(j)) - dut_mods
        both_mods = dut_mods.intersection(tb_mods)
        #print(dut_mods)
        #print(tb_mods)
        #print(both_mods)
        assert len(both_mods) == 0


        with open(args.dut_mods, 'w') as df:
            with open(args.filelist) as fl:
                for path in fl:
                    writeOut = True
                    for dm in dut_mods:
                        if dm in path:
                            # don't write
                            writeOut = False
                            break

                    if writeOut:
                        df.write(f"{args.build_dir}/{path}")
