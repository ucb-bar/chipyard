#!/bin/sh

for fname in $@
do
    echo "==> $fname <=="
    grep '^m\(instret\|cycle\|hpmcounter[3456]\) =' $fname | \
    sed -e 's/minstret/Instructions/' \
        -e 's/mcycle/Cycles/' \
        -e 's/mhpmcounter3/Mispredicts/' \
        -e 's/mhpmcounter4/Branches/' \
        -e 's/mhpmcounter5/Load forwards/' \
        -e 's/mhpmcounter6/Store-Load Failures/' 
    echo ""
done
