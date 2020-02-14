#!/usr/bin/env python2

import sys

if len(sys.argv) < 3:
    print "%s: <mcf in> <threshold>" % sys.argv[0]
    sys.exit(0)

lines = open(sys.argv[1]).readlines()
ts = int(sys.argv[2])

trips = int(lines[0].split()[0])
arcs = int(lines[0].split()[1])

new_trips = []
for line in lines[1:trips+1]:
    args = line.split()
    if int(args[0]) < ts and int(args[1]) < ts:
        new_trips.append(line)

new_arcs = []
for line in lines[trips+1:trips+arcs+1]:
    args = line.split()
    if int(args[0]) < ts and int(args[1]) < ts:
        new_arcs.append(line)

print "%d %d" % (len(new_trips), len(new_arcs))
for line in new_trips:
    sys.stdout.write(line)
for line in new_arcs:
    sys.stdout.write(line)
