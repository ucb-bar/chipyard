#!/usr/bin/env bash

# This script updates Hammer and plugins.
# ./scripts/upgrade-vlsi.sh <pattern(s)> will upgrade plugins matching the pattern list.
# ./scripts/upgrade-vlsi.sh will upgrade all plugins and bump hammer-vlsi to the latest released version. Only do this upon a new Hammer release.

# exit script if any command fails
set -e
set -o pipefail
# except for grep in the pipe
clgrep() { grep $@ || test $? = 1; }

# exit script if not in Chipyard conda env
if [[ `basename $CONDA_PREFIX` != .conda-env ]]; then
    echo 'ERROR: Chipyard conda env not activated. Please source env.sh and run this script again.'
    exit
fi

# Get hammer submodules
if [ $# -gt 0 ]; then
    patterns=()
    for arg in $@; do
        patterns+=("-e" $arg)
    done
    package_list=($(git ls-files --stage | grep 160000 | clgrep ${patterns[@]} | awk '$4 ~/vlsi\/hammer.*/ {print $4}'))
else
    package_list=($(git ls-files --stage | grep 160000 | awk '$4 ~/vlsi\/hammer.*/ {print $4}'))
    # Also upgrade hammer-vlsi.
    pip install hammer-vlsi --upgrade
fi

# exit if requested package not found (case of an unmatched pattern in a list is not handled)
if [ -z ${package_list} ]; then
    echo "No Hammer plugins matching these patterns found: $@"
    exit
fi

# upgrade to latest commit in default branch
for p in ${package_list[@]}; do
    echo "Updating ${p}"
    cd ${p}
    git checkout `basename "$(git rev-parse --abbrev-ref origin/HEAD)"`
    git pull
    cd - > /dev/null
    git add ${p}
    pip install -e ${p} --upgrade
done
