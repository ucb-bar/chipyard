#!/usr/bin/env python3

# Replace text in a file given a key identifying a block to replace.
# If the file doesn't exist, create it.
#
# args
# $1 - file to replace text in
# $2 - key used to find block of text to replace
# $3 - text to fill in block that is replaced

import re
import sys

def CY_INITIALIZE_RE_BLOCK(k):
    return (
        r"^# >>> " + f"{k}" + r" initialize >>>(?:\n|\r\n)"
        r"([\s\S]*?)"
        r"# <<< " + f"{k}" + r" initialize <<<(?:\n|\r\n)?"
    )

def CY_INITIALIZE_START_TOKEN(k):
    return "# >>> " + f"{k}" + " initialize >>>"

def CY_INITIALIZE_END_TOKEN(k):
    return "# <<< " + f"{k}" + " initialize <<<"

# ------------------------------

try:
    with open(sys.argv[1]) as fh:
        fh_content = fh.read()
except FileNotFoundError:
    fh_content = ""
except:
    raise

initialize_comment_key = sys.argv[2]
inner_contents = CY_INITIALIZE_START_TOKEN(initialize_comment_key) + "\n" + sys.argv[3] + "\n" + CY_INITIALIZE_END_TOKEN(initialize_comment_key) + "\n"

# ------------------------------

replace_str = "__CY_REPLACE_ME_123__"
fh_content = re.sub(
    CY_INITIALIZE_RE_BLOCK(initialize_comment_key),
    replace_str,
    fh_content,
    flags=re.MULTILINE,
)
# TODO: maybe remove all but last of replace_str, if there's more than one occurrence
fh_content = fh_content.replace(replace_str, inner_contents)

if CY_INITIALIZE_START_TOKEN(initialize_comment_key) not in fh_content:
    fh_content += "\n%s\n" % inner_contents

with open(sys.argv[1], "w") as fh:
    fh.write(fh_content)
