#!/bin/bash

# used with the dockerfile to set up enviroment variables by running env.sh
# adapted from https://stackoverflow.com/questions/55921914/how-to-source-a-script-with-environment-variables-in-a-docker-build-process

. /root/chipyard/env.sh

exec "$@"
