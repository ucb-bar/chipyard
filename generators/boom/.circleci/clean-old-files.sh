#!/bin/bash

#-------------------------------------------------------------
# clean directories that are older than 14 days
#
# run location: build server (located at UCB)
# usage:
#   $1 - the directory to search in
#-------------------------------------------------------------

#-------------------------------------------------------------
# get age of the folder in days
#
# usage:
#   $1 - the folder to get the age of
#-------------------------------------------------------------
age () {
    local AGE_SEC
    local CUR_SEC
    local DIFF_SEC
    local SEC_PER_DAY

    SEC_PER_DAY=86400

    CUR_SEC=$(date +%s)
    AGE_SEC=$(stat -c %Y -- "$1")
    DIFF_SEC=$(expr $CUR_SEC - $AGE_SEC)

    echo $(expr $DIFF_SEC / $SEC_PER_DAY)
}

for d in $1/*/ ; do
    DIR_AGE="$(age $d)"
    if [ $DIR_AGE -ge 14 ]; then
        if [[ $d == *"bin"* ]]; then
            echo "$d is getting skipped"
            continue
        fi
        if [[ $d == *"config"* ]]; then
            echo "$d is getting skipped"
            continue
        fi

        echo "Deleting $d since is it $DIR_AGE old"
        rm -rf $d
    else
        echo "Keep $d since it is $DIR_AGE old"
    fi
done
