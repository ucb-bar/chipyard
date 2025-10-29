#!/bin/bash

# -------------------------------------------------------------
# retrieve and cat the output log from the failed build
#
# run location: circle ci docker image
# usage:
#   $1 - config string (translates to afi folder inside firesim-configs/*)
#-------------------------------------------------------------

# turn echo on and error on earliest command
set -ex

# setup AWS_SERVER variable
AWS_SERVER=centos@$(sed -n '2p' /tmp/FSIM_MANAGER_INSTANCE_DATA.txt)

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# setup arguments
CONFIG_KEY=$1
AFI_NAME=${afis[$1]}

# install rsync (since runs on aws executor)
sudo apt-get update
sudo apt-get install -y rsync

# set stricthostkeychecking to no (must happen before rsync)
run_aws "echo \"Ping $AWS_SERVER\""

# copy over the logs
copy $AWS_SERVER:$REMOTE_AWS_FSIM_DEPLOY_DIR/logs/ $HOME/build-result-logs

echo "[AFIFAILED] printing log"
UNIQUE_LOG=$(grep -irl "$AFI_NAME" $HOME/build-result-logs/*buildafi*)
cat $UNIQUE_LOG

# if afi failed... just stop the manager instance
MANAGER_ID=$(sed -n '1p' /tmp/FSIM_MANAGER_INSTANCE_DATA.txt)
aws ec2 stop-instances --instance-ids $MANAGER_ID

# this is just to fail
exit 1
