#!/usr/bin/env python

import sys

# $1 - rst file to parse
# $2 - output bash file to write

if len(sys.argv) != 3:
    sys.exit("[ERROR] Incorrect # of args.")

rstFile = sys.argv[1]
bashFile = sys.argv[2]

codeBlocks = [] # ordered list of blocks, each block is (type: String, lines: List[Strings])

with open(rstFile, 'r') as rstF:
    inBlock = False
    skipEmpty = False
    curBlockType = ""
    curBlockLines = []
    for line in rstF.readlines():
        if inBlock:
            if len(line) == 1 and line == '\n':
                # empty line
                if not skipEmpty:
                    # empty line (done with block)
                    inBlock = False
                    codeBlocks.append((curBlockType, curBlockLines))
                    curBlockType = ""
                    curBlockLines = []
                skipEmpty = False
            else:
                assert (line[0:4] == ' ' * 4), "Must only strip whitespace (ensure RST only has 4 spaces before code lines)"
                curBlockLines.append(line[4:]) # strip the first 4 spaces (indent)
        else:
            if ".. code-block:: shell" in line:
                inBlock = True
                curBlockType = "code-block"
                skipEmpty = True
            elif ".. only:: replace-code-above" in line:
                inBlock = True
                curBlockType = "replace-above"
                skipEmpty = True

idxToDelete = []
for idx, cb in enumerate(codeBlocks):
    if cb[0] == "replace-above":
        idxToDelete.append(idx)

# TODO: could check that replace-code-above directives cannot follow one another

idxToDelete.reverse()
for idx in idxToDelete:
    assert idx - 1 >= 0, "replace-code-above directives must come after a code-block directive"
    codeBlocks.pop(idx - 1)

with open(bashFile, 'w') as bashF:
    header = """#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

# $CHIPYARD_DIR should be defined and pointing to the top-level folder in Chipyard
if [[ -z "${CHIPYARD_DIR}" ]]; then
  echo "Environment variable \$CHIPYARD_DIR is undefined. Unable to run script."
  exit 1
fi
"""
    bashF.writelines([header, '\n'])
    for cb in codeBlocks:
        bashF.writelines(cb[1])
