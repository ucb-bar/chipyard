#!/usr/bin/env bash

# FireSim EC2-only setup steps.
#
# These are the EC2-specific setup steps that normally run automatically as
# part of FireSim's build-setup (sims/firesim/build-setup-nolog.sh, triggered
# by ./build-setup.sh) whenever it detects it is running on an EC2 instance.
#
# Run this script manually if you pulled a Chipyard/FireSim Docker container
# that was NOT built on an EC2 instance but you now want to use it on EC2. In
# that case the EC2-specific setup was skipped when the container was built,
# so it needs to be performed once on the instance. It sources sdk_setup.sh
# and hdk_setup.sh to pull down the AWS shell DCP and IP so worker instances
# don't have to redo it each time.
#
# NOTE: This is a duplicate of the "EC2-only setup" section of
# sims/firesim/build-setup-nolog.sh. That script keeps its own inline copy
# because FireSim can be used standalone (without Chipyard), so it cannot
# depend on this Chipyard-only helper. Keep the two in sync if either changes.

# exit script if any command fails
set -e
set -o pipefail

# Locate the FireSim directory. Prefer an explicit FS_DIR override, otherwise
# derive it relative to this script's location (chipyard/scripts/) so this
# works even in a Docker container where git metadata may be unavailable.
if [ -n "${FS_DIR:-}" ]; then
    FDIR="$FS_DIR"
else
    SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
    CYDIR=$( cd -- "$SCRIPT_DIR/.." &> /dev/null && pwd )
    FDIR="$CYDIR/sims/firesim"
fi

if [ ! -d "$FDIR" ]; then
    echo "ERROR: could not find the FireSim directory at '$FDIR'."
    echo "Set the FS_DIR environment variable to point at sims/firesim and re-run."
    exit 1
fi

cd "$FDIR"

#### EC2-only setup ####

# see if the instance info page exists. if not, we are not on ec2.
# Skip EC2 setup if FORCE_NON_EC2 is set
if [ "${FORCE_NON_EC2:-0}" != "1" ]; then # todo(jimfang) remove this
    # see if the instance info page exists. if not, we are not on ec2.
    # rh: yet another HTTPS issue that needs to be fixed. i swear on god they use this for the most random things sometimes
    TOKEN=""
    for attempt in 1 2 3; do
        # Metadata is unreachable off-EC2; keep setup non-fatal in that case.
        set +e
        TOKEN=$(curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" \
            --connect-timeout 1 -m 3 2>/dev/null)
        curl_rc=$?
        set -e

        if [ $curl_rc -eq 0 ] && [ -n "$TOKEN" ]; then
            break
        fi

        TOKEN=""
    done
    if [ -n "$TOKEN" ]; then

        (
            echo $'IMDSv2 check passed, this is an EC2 instance'

            # ensure that we're using the system toolchain to build the kernel modules
            # newer gcc has --enable-default-pie and older kernels think the compiler
            # is broken unless you pass -fno-pie but then I was encountering a weird
            # error about string.h not being found
            export PATH=/usr/bin:$PATH

            # TODO: Update for xdma for f2
            # cd "$FDIR/platforms/f1/aws-fpga/sdk/linux_kernel_drivers/xdma"
            # make
        )

        (
            if [[ "${CPPFLAGS:-zzz}" != "zzz" ]]; then
                # don't set it if it isn't already set but strip out -DNDEBUG because
                # the sdk software has assertion-only variable usage that will end up erroring
                # under NDEBUG with -Wall and -Werror
                export CPPFLAGS="${CPPFLAGS/-DNDEBUG/}"
            fi

            # Source hdk_setup.sh once on this machine to pull down shell DCP and IP,
            # so we don't have to waste time doing it each time on worker instances
            AWSFPGA="$FDIR/platforms/f2/aws-fpga-firesim-f2"
            cd "$AWSFPGA"
            bash -c "source ./sdk_setup.sh"
            bash -c "source ./hdk_setup.sh"
        )

    else
        echo $'IMDSv2 token empty/failed, skipping EC2 specific setup stuff'
    fi
else
    echo $'FORCE_NON_EC2 set, skipping EC2 specific setup'
fi

echo "FireSim EC2-only setup complete!"
