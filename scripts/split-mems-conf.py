#!/usr/bin/env python

import json
import argparse
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
    parser = argparse.ArgumentParser(description='Use CIRCT (firtool) smems JSONs to create DUT and test harness smems confs')
    parser.add_argument('--in-smems-conf', type=str, required=True, help='')
    parser.add_argument('--in-dut-smems-json', type=str, required=True, help='')
    parser.add_argument('--in-tb-smems-json', type=str, required=True, help='')
    parser.add_argument('--out-dut-smems-conf', type=str, required=True, help='')
    parser.add_argument('--out-tb-smems-conf', type=str, required=True, help='')
    args = parser.parse_args()

    with open(args.in_smems_conf) as isc:
        with open(args.in_dut_smems_json) as idsj:
            with open(args.in_tb_smems_json) as itsj:
                idsj_data = json.load(idsj)
                itsj_data  = json.load(itsj)

                dut_mods = set()
                for e in idsj_data:
                    dut_mods.add(e['module_name'])

                tb_mods = set()
                for e in itsj_data:
                    tb_mods.add(e['module_name'])

                with open(args.out_dut_smems_conf, "w") as odsc:
                    with open(args.out_tb_smems_conf, "w") as otsc:
                        for l in isc:
                            sl = l.split()
                            name = sl[1]

                            if name in dut_mods:
                                odsc.write(l)
                            elif name in tb_mods:
                                otsc.write(l)
                            else:
                                assert False, "Unable to find smem CONF module in firtool emitted JSON files."
