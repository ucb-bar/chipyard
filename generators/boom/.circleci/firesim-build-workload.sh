#!/bin/bash

# -------------------------------------------------------------
# build a specific afi's workload
#
# run location: circle ci docker image
# usage:
#   $1 - config string (translates to afi folder inside firesim-configs/*)
#   $2 - workload name (folder inside firesim-configs/afi-longname/*)
#-------------------------------------------------------------

# turn echo on and error on earliest command
set -ex

# setup AWS_SERVER variable
AWS_SERVER=centos@$(sed -n '2p' /tmp/FSIM_MANAGER_INSTANCE_DATA.txt)

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

# setup arguments
AFI_NAME=${afis[$1]}
WORKLOAD_NAME=$2

FMRSHL_CFG=$LOCAL_FSIM_CFGS_DIR/$AFI_NAME/$WORKLOAD_NAME/firemarshal_config
FMRSHL_NAME=$(sed -n '2p' $FMRSHL_CFG)
FMRSHL_DIR=$(sed -n '1p' $FMRSHL_CFG)

SCRIPT_NAME=firesim-$AFI_NAME-$WORKLOAD_NAME-build.sh

cat <<EOF >> $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME
#!/bin/bash

set -ex

# setup firesim to get toolchain
cd $REMOTE_AWS_CHIPYARD_DIR
source env.sh
cd $REMOTE_AWS_FSIM_DIR
source sourceme-f1-manager.sh

# get the SPEC2017 path
export SPEC_DIR=$SPEC_DIR

cd $REMOTE_AWS_MARSHAL_DIR
./marshal -v build $REMOTE_AWS_WORK_DIR/$FMRSHL_DIR/$FMRSHL_NAME.json
./marshal -v install $REMOTE_AWS_WORK_DIR/$FMRSHL_DIR/$FMRSHL_NAME.json

# add file to indicate that the workload is done
cd $REMOTE_AWS_WORK_DIR
touch $AFI_NAME-$WORKLOAD_NAME-FINISHED
EOF

# execute the script
chmod +x $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME
run_script_aws $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME

