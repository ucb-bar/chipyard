#!/usr/bin/env python3

import sys
import os
import re
import json

if len(sys.argv) != 3:
    sys.exit(f"Usage: {sys.argv[0]} tech_name mems.instmap.txt")

tech = sys.argv[1]
memfile = sys.argv[2]

if tech != "asap7":
    sys.exit("FIXME: Only ASAP7 is supported")

macros = []

with open(memfile) as f:
    for line in f.readlines():
        entries = line.strip().split(" ")[1:]
        for entry in entries:
            macro = entry.split(":")[1]
            if macro not in macros:
                macros.append(macro)

this_dir = os.path.dirname(os.path.realpath(__file__))
lef_dir = os.path.join(this_dir, "hammer/src/hammer-vlsi/technology/asap7/sram_compiler/memories/lef/")

out = []

for macro in macros:
    with open(os.path.join(lef_dir, macro + "_x4.lef")) as f:
        for line in f.readlines():
            m = re.match(".*SIZE(.*)BY(.*);.*", line)
            if m:
                out.append({"ofModule": macro, "width": float(m.group(1).strip()), "height": float(m.group(2).strip())})
                break

print(json.dumps(out, sort_keys=True, indent=4))

