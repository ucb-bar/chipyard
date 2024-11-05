#!/bin/bash

# Exit if any command fails
set -e

# Step 1: Download and install Miniforge
echo "Downloading and installing Miniforge..."
curl -L -O "https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-$(uname)-$(uname -m).sh"
bash Miniforge3-$(uname)-$(uname -m).sh -b  # -b flag for batch mode (no interactive prompts)

# Step 2: Detect current shell and initialize Conda
echo "Initializing Conda for the current shell..."
SHELL_NAME=$(basename "$SHELL")
eval "$(~/miniforge3/bin/conda shell.${SHELL_NAME} hook)"  # Assuming Miniforge installed in user home directory

# Step 3: Add Miniforge to the PATH
export PATH=$PATH:~/miniforge3/bin

# Step 4: Update and install git
echo "Updating system and installing git..."
sudo apt-get update
sudo apt-get install -y git kmod

# Step 5: Install and configure Conda's libmamba solver
echo "Installing and configuring libmamba solver for Conda..."
conda install -n base conda-libmamba-solver -y
conda config --set solver libmamba

# Step 6: Activate Conda base environment
echo "Activating base Conda environment..."
conda activate base

# Step 7: Clone Chipyard repository
echo "Cloning Chipyard repository..."
git clone https://github.com/osowatzke/chipyard.git
cd chipyard

# Step 8: Ensure we are on the correct branch
git checkout HEAD

# Step 9: Update Conda base environment
echo "Updating Conda base environment..."
conda update -n base --all -y

# Step 10: Run the Chipyard build setup script (for RISC-V tools)
echo "Running Chipyard build setup for RISC-V tools..."
./build-setup.sh riscv-tools -s 6 -s 7 -s 8 -s 9

# Step 11: Verify Conda environments
echo "Listing Conda environments..."
conda env list

# Step 12: Source Chipyard environment setup script
echo "Sourcing Chipyard environment variables..."
source ./env.sh

echo "Chipyard setup complete!"
