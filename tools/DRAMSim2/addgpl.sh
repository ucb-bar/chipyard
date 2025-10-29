#!/bin/bash 

#script to add or remove the GPL header from all *.cpp and *.h files
# in a directory. Usage:
#
# ./addgpl.sh [add|remove] directory/


DIRECTORY=$2

if [ -d "$DIRECTORY" ]; then 
	FILES=`find $DIRECTORY -iname '*.h' -or -iname '*.cpp'`
else
	echo "Bad directory"
	exit
fi

if [ "$1" == "add" ] ; then 
	for f in $FILES
	do
		echo "adding to $f"
		mv $f $f.tmp
		cat gpl.txt $f.tmp > $f
	done
elif [ "$1" == "remove" ] ; then 
	NUMLINES=`wc -l gpl.txt | cut -f1 -d' '`
	for f in $FILES
	do 
		HEADER=`head --lines=$NUMLINES $f | diff -w gpl.txt -`
		if [ -z "$HEADER" ] ; then 
			echo "deleting from $f"
			mv $f $f.tmp
			tail --lines=+$NUMLINES $f.tmp > $f
		else 
			echo "header does not match, skipping $f"
		fi 

	done
fi


