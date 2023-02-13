#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

# exit script if not in Chipyard conda env
if [[ `basename $CONDA_PREFIX` != .conda-env ]]; then
    echo 'ERROR: Chipyard conda env not activated. Please source env.sh and run this script again.'
    exit
fi

# Get hammer submodules
package_names=$(git ls-files --stage | grep 160000 | awk '$4 ~/vlsi\/hammer.*/ {print $4}')
package_list=(${package_names})
plen="${#package_list[@]}"

if [[ ${plen} -gt 0 ]]; then
    for p in "${package_list[@]}"; do
        cd ${p}
        echo "Updating current directory: $PWD"
        git checkout master
        git pull
        cd - > /dev/null
        git add ${p}
        pip install -e ${p} --upgrade
    done
fi

# Upgrade hammer-vlsi separately.
pip install hammer-vlsi --upgrade



