# adapted from https://stackoverflow.com/questions/55921914/how-to-source-a-script-with-environment-variables-in-a-docker-build-process
#!/bin/sh
. ./env.sh
exec "$@"
