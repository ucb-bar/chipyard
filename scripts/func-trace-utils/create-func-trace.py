#!/usr/bin/env python3

import sys
import re

back_args = sys.argv[1:]
files_to_check = back_args[:-1]
outfile = back_args[-1]

print(f"Checking {files_to_check}")

func_names = {}

for dumpfile in files_to_check:
    with open(dumpfile, 'r') as ld:
        for l in ld:
            match = re.match(r"^([a-fA-F0-9]+) <(.*)>:", l)
            if match:
                pc, name = match.groups()
                if pc in func_names:
                    func_names[pc] = func_names[pc] + " or " + name
                else:
                    func_names[pc] = name

print(f"Got {len(func_names)} functions from files")
print(f"Creating function trace from {outfile}")

with open(outfile, 'r') as of:
    with open(outfile + ".functrace", 'w') as wf:
        lines = of.readlines()
        tlc = len(lines)
        lno = 0
        for l in lines:
            match = re.search(r"\[1\] pc=\[([a-fA-F0-9]+)\]", l)
            if match:
                pc = match.groups()[0]
                if pc in func_names:
                    wf.write(f"{pc}: {func_names[pc]}\n")
                #wf.write(f"{(100*lno/tlc):.1f}: {pc}: {func_names[pc]}\n")
            else:
                match = re.search(r": 0x([a-fA-F0-9]+) ", l)
                if match:
                    pc = match.groups()[0]
                    if pc in func_names:
                        wf.write(f"{pc}: {func_names[pc]}\n")
                    #wf.write(f"{(100*lno/tlc):.1f}: {pc}: {func_names[pc]}\n")
            #wf.write(l)
            match = re.search(r": exception.*,", l)
            if match:
                wf.write(l)

            match = re.search(r"\[0\] pc=\[0000000000193f80\]", l)
            if match:
                wf.write(l)
            lno = lno + 1

print(f"Done: {outfile}.functrace")
