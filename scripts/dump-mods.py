#!/usr/bin/python

# dump all modules (deduped) given a specific module
#
# args
# $1 - module to dump all modules underneath (inclusive)
# $2 - module heirarchy file

import sys
import json


mod_name = sys.argv[1]
mod_file = sys.argv[2]

# open file
with open(mod_file, 'r') as modFile:
    #print(json.load(modFile))
