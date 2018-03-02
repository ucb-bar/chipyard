#!/bin/sh

for fname in $@
do
    echo "==> $fname <=="
    grep '^m\(instret\|cycle\|hpmcounter[34]\) =' $fname | \
    sed -e 's/minstret/Instructions/' \
        -e 's/mcycle/Cycles/' \
        -e 's/mhpmcounter3/Mispredicts/' \
        -e 's/mhpmcounter4/Branches/'
    echo ""
done
