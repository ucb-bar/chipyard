
# first, check if the system allows sufficient limits (the hard limit)
HARD_LIMIT=$(ulimit -Hn)
REQUIRED_LIMIT=16384

if [ "$HARD_LIMIT" -lt "$REQUIRED_LIMIT" ]; then
    echo "WARNING: Your system does not support an open files limit (the output of 'ulimit -Sn' and 'ulimit -Hn') of at least $REQUIRED_LIMIT, which is required to workaround a bug in buildroot. You will not be able to build a Linux distro with FireMarshal until this is addressed."
fi

# in any case, set the soft limit to the same value as the hard limit
ulimit -Sn $(ulimit -Hn)

