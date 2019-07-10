#!/bin/bash

# clean directories that are older than 30 days
# argument is used as the directory to look in

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
	if [ $DIR_AGE -ge 30 ]; then
		echo "Deleting $d since is it $DIR_AGE old"
		rm -rf $d
	else
	    echo "Keep $d since it is $DIR_AGE old"
	fi
done
