#!/bin/bash

#-------------------------------------------------------------
# run the afi's workload (run, detach)
#
# run location: circle ci docker image
# usage:
#   $1 - config string (translates to afi folder inside firesim-configs/*)
#   $2 - workload name (folder inside firesim-configs/afi-longname/*)
#   $3 - timeout amount (amount of time to run the workload before killing it)
#-------------------------------------------------------------

# turn echo on and error on earliest command
set -ex

# DEBUG: setup AWS_SERVER variable (override with AWS_IP_ADDR_OVERRIDE if defined)
if [ -v AWS_IP_ADDR_OVERRIDE ]; then
    echo "Override AWS IP address with $AWS_IP_ADDR_OVERRIDE"
    AWS_SERVER=centos@$AWS_IP_ADDR_OVERRIDE
else
    echo "Using default IP address"
    AWS_SERVER=centos@$(sed -n '2p' /tmp/FSIM_MANAGER_INSTANCE_DATA.txt)
fi

# get shared variables
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
source $SCRIPT_DIR/defaults.sh

if [ -v AWS_IP_ADDR_OVERRIDE ]; then
    echo "Reset the \"workloads_running\" file"
    run_aws "rm -f $REMOTE_AWS_WORK_DIR/workloads_running"
fi

# setup arguments
CONFIG_KEY=$1
AFI_NAME=${afis[$1]}
WORKLOAD_NAME=$2
TIMEOUT=$3

# set stricthostkeychecking to no (must happen before rsync)
run_aws "echo \"Ping $AWS_SERVER\""

# copy collateral needed
copy $LOCAL_FSIM_CFGS_DIR/$AFI_NAME $AWS_SERVER:$REMOTE_AWS_FSIM_DEPLOY_DIR/

REMOTE_CFG_DIR=$REMOTE_AWS_FSIM_DEPLOY_DIR/$AFI_NAME
BUILD_ARGS="-c $REMOTE_CFG_DIR/$WORKLOAD_NAME/config_runtime.ini -a $REMOTE_AWS_FSIM_DEPLOY_DIR/built-hwdb-entries/$AFI_NAME -r $REMOTE_CFG_DIR/config_build_recipes.ini"
SCRIPT_NAME=firesim-run-$AFI_NAME-$WORKLOAD_NAME.sh

cat <<EOF >> $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME
#!/bin/bash

set -ex

# setup firesim
cd $REMOTE_AWS_CHIPYARD_DIR
source env.sh
cd $REMOTE_AWS_FSIM_DIR
source sourceme-f1-manager.sh

set +e

# call workload finished
# \$1 - launchrunfarm failed - true/false
# \$2 - infrasetup failed - true/false
# \$3 - runworkload failed - true/false
workload_finished () {
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
    "finish-firesim-workload-run": true,
    "${CONFIG_KEY}_${WORKLOAD_NAME}": true,
    "launchrunfarm_passed": '"\$1"',
    "infrasetup_passed": '"\$2"',
    "runworkload_passed": '"\$3"'
  }
}'
}

# acquire lock on the afi name (race condition in the firesim make during infrasetup)
exec {lock_fd}>$REMOTE_AWS_FSIM_DIR/$AFI_NAME.lock || exit 1
flock "\$lock_fd" || { echo "ERROR: flock() failed." >&2; exit 1; }

# indicate that this workload is running (used in finish)
echo "$AFI_NAME-$WORKLOAD_NAME" >> $REMOTE_AWS_WORK_DIR/workloads_running

if firesim launchrunfarm $BUILD_ARGS; then
    echo "launchrunfarm passed"
else
    echo "launchrunfarm failed"
    firesim terminaterunfarm -q $BUILD_ARGS
    workload_finished false true true

    # release the lock
    flock -u "\$lock_fd"

    exit 1
fi

if firesim infrasetup $BUILD_ARGS; then
    echo "infrasetup passed"
else
    echo "infrasetup failed"
    firesim terminaterunfarm -q $BUILD_ARGS
    workload_finished true false true

    # release the lock
    flock -u "\$lock_fd"

    exit 1
fi

# release the lock
flock -u "\$lock_fd"

if timeout -k 3m $TIMEOUT firesim runworkload $BUILD_ARGS; then
    echo "runworkload passed"
    firesim terminaterunfarm -q $BUILD_ARGS
    workload_finished true true true
    exit 0
else
    echo "runworkload failed"
    firesim terminaterunfarm -q $BUILD_ARGS
    workload_finished true true false
    exit 1
fi
EOF

echo "script created"
cat $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME

# execute the script and detach
chmod +x $LOCAL_CHECKOUT_DIR/$SCRIPT_NAME
run_detach_script_aws $WORKLOAD_NAME $LOCAL_CHECKOUT_DIR $SCRIPT_NAME
