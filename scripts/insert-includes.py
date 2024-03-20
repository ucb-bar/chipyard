#!/usr/bin/env python

# replaces a `include with the full include file.
# recursively replaces `include's until none are left
#
# args
# $1 - file to remove includes from
# $2 - file to write output to
# $3 - list of directories to search for includes in (note: NON-RECURSIVE must specify all dirs)
#      includes are found relative to this path
#      this is equivalent to something like +incdir+

import sys
import re
import os
import tempfile
import shutil

inVlog = sys.argv[1]
outVlog = sys.argv[2]
print("[INFO] Replaces includes from: " + str(inVlog))

if inVlog == outVlog:
    sys.exit("[ERROR] The input and output file cannot be the same.")

# add directories to search list
incDirs = sys.argv[3:]
print("[INFO] Searching following dirs for includes: " + str(incDirs))

def process(inF, outF):
    # open file
    with open(inF, 'r') as inFile:
        with open(outF, 'w') as outFile:
            # for each include found, search through all dirs and replace if found, error if not
            for num, line in enumerate(inFile, 1):
                match = re.match(r"^ *`include +\"(.*)\"", line)
                if match and match.group(1) != "uvm_macros.svh":
                    print("[INFO] Replacing includes for {}".format(match.group(1)))
                    # search for include and replace
                    found = False
                    for d in incDirs:
                        potentialIncFileName = d + "/" + match.group(1)
                        if os.path.exists(potentialIncFileName):
                            found = True
                            print("[INFO] Found missing include in {}".format(potentialIncFileName))
                            with open(potentialIncFileName, 'r') as incFile:
                                for iline in incFile:
                                    outFile.write(iline)
                            break

                    # must find something to include with
                    if not found:
                        sys.exit("[ERROR] Couldn't replace include \"" + str(match.group(1)) + "\" found on line " + str(num))
                else:
                    outFile.write(line)

inF = inVlog

while True:
    # create a copy of the input
    fd, temp_path = tempfile.mkstemp()
    shutil.copy2(inF, temp_path)

    with open(temp_path, 'r') as inFile:
        anyIncludes = False
        for line in inFile:
            match = re.match(r"^ *`include +\"(.*)\"", line)
            if match:
                anyIncludes = True
                break

        if anyIncludes:
            process(temp_path, outVlog)
            inF = outVlog
            os.remove(temp_path)
        else:
            os.remove(temp_path)
            break

print("[INFO] Success. Writing output to: " + str(outVlog))
