#!/bin/bash

export PATH=/home/centos/chipyard-afternoon/software/firemarshal:$PATH
export PATH=/home/centos/spike-local/bin:$PATH

echo "* soft nofile 16384" | sudo tee --append /etc/security/limits.conf
echo "* hard nofile 16384" | sudo tee --append /etc/security/limits.conf
