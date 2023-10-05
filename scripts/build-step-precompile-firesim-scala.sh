#!/usr/bin/env bash

# This script is intended to be used as a sub-step of build-setup.sh.
pushd $CYDIR/sims/firesim
(
    echo $CYDIR
    source sourceme-manager.sh --skip-ssh-setup
    pushd sim
    make sbt SBT_COMMAND="project {file:$CYDIR}firechip; compile" TARGET_PROJECT=firesim
    popd
)
popd
