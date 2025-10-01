#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

# exit script if not in Chipyard conda env
if [[ `basename $CONDA_PREFIX` != .conda-env ]]; then
    echo 'ERROR: Chipyard conda env not activated. Please source env.sh and run this script again.'
    exit
fi

pip install -e vlsi/hammer-synopsys-plugins

hammer_root_dir=$1
if [[ ! -d "$hammer_root_dir" ]] ; then 
echo "Hammer root dir does not exist, please provide a valid path to your Hammer installation"
echo "For this flow you neeed to fetch the repository at this address https://github.com/cad-polito-it/hammer"

else
pip install -e ${hammer_root_dir}
fi 