#!/bin/bash

echo "export PATH=/home/centos/chipyard-afternoon/software/firemarshal:$PATH" >> ~/.bashrc
echo "export PATH=/home/centos/spike-local/bin:$PATH" >> ~/.bashrc

echo "* soft nofile 16384" | sudo tee --append /etc/security/limits.conf
echo "* hard nofile 16384" | sudo tee --append /etc/security/limits.conf
