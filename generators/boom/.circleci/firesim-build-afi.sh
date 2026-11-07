#!/bin/bash

# -------------------------------------------------------------
# make an afi (run, detach so it runs in the background)
# spawn all jobs related to it (specific to this script)
# **runs all workloads that finished building**
#   - otherwise it just skips that workload
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

# set stricthostkeychecking to no (must happen before rsync)
run_aws "echo \"Ping $AWS_SERVER\""

# copy over the configuration collateral
copy $LOCAL_FSIM_CFGS_DIR/$AFI_NAME $AWS_SERVER:$REMOTE_AWS_FSIM_DEPLOY_DIR/

# DEBUG: use a previously built afi (use the $(CONFIG_KEY)_OVERRIDE name to write the agfi id)
USE_CUSTOM_HWDB=false
if [ -v ${CONFIG_KEY}_OVERRIDE ]; then
    echo "Using a previously built $CONFIG_KEY ($AFI_NAME) AFI"

    HWDB_FILE=$LOCAL_CHECKOUT_DIR/$AFI_NAME
    echo "[$AFI_NAME]" >> $HWDB_FILE
    eval "echo \"agfi=\${${CONFIG_KEY}_OVERRIDE}\" >> $HWDB_FILE"
    echo "deploytripletoverride=None" >> $HWDB_FILE
    echo "customruntimeconfig=None" >> $HWDB_FILE

    USE_CUSTOM_HWDB=true

    copy $HWDB_FILE $AWS_SERVER:$REMOTE_AWS_FSIM_HWDB_DIR/
else
    echo "Building a $CONFIG_KEY ($AFI_NAME) AFI from scratch"
fi

SCRIPT_NAME=firesim-build-$AFI_NAME-afi.sh

# create a script to run
cat <<EOF >> $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME
#!/bin/bash

set -ex

# setup firesim
cd $REMOTE_AWS_CHIPYARD_DIR
source env.sh
cd $REMOTE_AWS_FSIM_DIR
source sourceme-f1-manager.sh

set +e

# DEBUG: switch between override agfi or manually built
if $USE_CUSTOM_HWDB; then
    # wait for all workloads to build (est. completion time)
    sleep 45m

else

# build afi
if firesim buildafi -b $REMOTE_AWS_FSIM_DEPLOY_DIR/$AFI_NAME/config_build.ini -r $REMOTE_AWS_FSIM_DEPLOY_DIR/$AFI_NAME/config_build_recipes.ini; then
    echo "AFI successfully built"
else
    # spawn fail job
    echo "AFI failed... spawning failure workflow"

    # launch workloads related to this afi
    curl -u $API_TOKEN: \
        -X POST \
        $API_URL/project/github/$CIRCLE_PROJECT_USERNAME/$CIRCLE_PROJECT_REPONAME/pipeline \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -H "x-attribution-login: boom-ci" \
        -d '{
  "branch": "$CIRCLE_BRANCH",
  "parameters": {
    "build-and-test-boom-configs-run": false,
    "init-firesim-run": false,
    "finish-firesim-afi-run": true
  }
}'

    exit 1
fi

fi

# launch workloads
#   modify this to choose which workloads to run on what afi
curl -u $API_TOKEN: \
    -X POST \
    $API_URL/project/github/$CIRCLE_PROJECT_USERNAME/$CIRCLE_PROJECT_REPONAME/pipeline \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "x-attribution-login: boom-ci" \
    -d '{
  "branch": "$CIRCLE_BRANCH",
  "parameters": {
    "build-and-test-boom-configs-run": false,
    "init-firesim-run": false,
    "launch-firesim-workloads-run": true,
    "largefireboom_buildroot": true,
    "largefireboom_fedora": true,
    "largefireboom_coremark": true,
    "largefireboom_spec17-intspeed-test-600": true
  }
}'

EOF

echo "script created"
cat $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME

# execute the script and detach
chmod +x $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME
run_detach_script_aws $AFI_NAME $LOCAL_CHECKOUT_DIR $SCRIPT_NAME
