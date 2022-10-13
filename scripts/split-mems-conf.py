#!/usr/bin/env python

import os
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

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Use CIRCT (firtool) smems JSONs to create DUT and test harness smems confs')
    parser.add_argument('--in-smems-conf', type=str, required=True, help='Overall smems conf file that contains all memory definitions')
    parser.add_argument('--in-dut-smems-json', type=str, required=True, help='JSON indicating which mem modules are in the DUT')
    parser.add_argument('--in-model-smems-json', type=str, required=True, help='JSON indicating which mem modules are in the top-most level module (but not in the DUT)')
    parser.add_argument('--out-dut-smems-conf', type=str, required=True, help='Smems conf with only DUT mem module definitions')
    parser.add_argument('--out-model-smems-conf', type=str, required=True, help='Smems conf with only top-most level mem module definitions (not including DUT modules)')
    args = parser.parse_args()

    with open(args.in_smems_conf) as isc, \
         open(args.in_dut_smems_json) as idsj, \
         open(args.in_model_smems_json) as itsj:
        idsj_data = json.load(idsj)
        itsj_data  = json.load(itsj)

        dut_mods = set()
        for e in idsj_data:
            dut_mods.add(e['module_name'])

        model_mods = set()
        for e in itsj_data:
            model_mods.add(e['module_name'])

        with open(args.out_dut_smems_conf, "w") as odsc, \
             open(args.out_model_smems_conf, "w") as otsc:
            for l in isc:
                sl = l.split()

                # the line can't be split then stop immediately (normally an empty file)
                if len(sl) > 2:
                    name = sl[1]

                    if name in dut_mods:
                        odsc.write(l)
                    elif name in model_mods:
                        otsc.write(l)
                    else:
                        assert False, "Unable to find smem CONF module in firtool emitted JSON files."
                else:
                    exit(0)
