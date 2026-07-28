#!/usr/bin/env bash
set -euo pipefail

KEEP_CONTAINER_ON_ERROR="${KEEP_CONTAINER_ON_ERROR:-1}"

keep_container_on_error() {
  local rc="$?"
  if [ "${rc}" -ne 0 ] && [ "${KEEP_CONTAINER_ON_ERROR}" = "1" ]; then
    echo "Entrypoint failed with exit code ${rc}; keeping container alive for debugging." >&2
    echo "Inspect with: docker exec -it firesim-manager bash" >&2
    sleep infinity
  fi
}

exit_immediately() {
  trap - EXIT
  exit 143
}

trap keep_container_on_error EXIT
trap exit_immediately INT TERM

export USER=ubuntu
export LOGNAME=ubuntu
export HOME=/home/ubuntu

CY_DIR="${CY_DIR:-/home/ubuntu/chipyard}"
FS_DIR="${CY_DIR}/sims/firesim"
PUBLIC_KEY_PATH="${FIRESIM_PUBLIC_KEY_PATH:-/home/ubuntu/firesim-public}"
EC2_SETUP_SCRIPT="${FIRESIM_EC2_SETUP_SCRIPT:-${CY_DIR}/scripts/firesim-ec2-setup.sh}"
BUILD_RECIPES="${FIRESIM_BUILD_RECIPES:-${CY_DIR}/sims/firesim-staging/sample_config_build_recipes.yaml}"
BUILD_CONFIG="${FIRESIM_BUILD_CONFIG:-${CY_DIR}/.github/firesim-bitstream-templates/f2/config_build.yaml}"
VIVADO_VERSION="${VIVADO_VERSION:-}"
FIRESIM_AWS_VPC_NAME="${FIRESIM_AWS_VPC_NAME:-chipyard-cicd}"
FIRESIM_AWS_KEY_NAME="${FIRESIM_AWS_KEY_NAME:-chipyard-cicd}"
FIRESIM_AWS_SECURITY_GROUP_NAME="${FIRESIM_AWS_SECURITY_GROUP_NAME:-chipyard-cicd-build-farm}"
FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME="${FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME:-chipyard-cicd-firesim-ssh}"
FIRESIM_AWS_ALLOWED_CIDR="${FIRESIM_AWS_ALLOWED_CIDR:-192.168.0.0/16}"
export FIRESIM_AWS_VPC_NAME
export FIRESIM_AWS_KEY_NAME
export FIRESIM_AWS_SECURITY_GROUP_NAME
export FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME
export FIRESIM_AWS_ALLOWED_CIDR

export LC_ALL="${LC_ALL:-en_US.UTF-8}"
export LANG="${LANG:-en_US.UTF-8}"

if ! ldconfig -p 2>/dev/null | grep -q 'libX11\.so\.6' || ! command -v git-lfs >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    git-lfs \
    locales \
    libx11-6 \
    libxext6 \
    libxft2 \
    libxi6 \
    libxinerama1 \
    libxrender1 \
    libxtst6
  sudo locale-gen en_US.UTF-8
fi

if [ -n "${VIVADO_VERSION}" ]; then
  VIVADO_PATH="/opt/Xilinx/${VIVADO_VERSION}/Vivado/settings64.sh"
else
  VIVADO_PATH="$(find /opt/Xilinx -path '*/Vivado/settings64.sh' -type f 2>/dev/null | sort -V | tail -n 1)"
fi

if [ ! -f "${VIVADO_PATH}" ]; then
  echo "Missing Vivado settings script. Set VIVADO_VERSION or mount /opt/Xilinx into the container." >&2
  exit 1
fi

echo "Sourcing Vivado settings: ${VIVADO_PATH}"
had_nounset=0
case "$-" in
  *u*)
    had_nounset=1
    set +u
    ;;
esac

# shellcheck disable=SC1090
source "${VIVADO_PATH}"

if [ "${had_nounset}" -eq 1 ]; then
  set -u
fi

if [ ! -s "${PUBLIC_KEY_PATH}" ]; then
  echo "Missing public key mounted at ${PUBLIC_KEY_PATH}." >&2
  exit 1
fi

mkdir -p "${HOME}/.ssh"
chmod 700 "${HOME}/.ssh"
touch "${HOME}/.ssh/authorized_keys"
chmod 600 "${HOME}/.ssh/authorized_keys"
grep -qxFf "${PUBLIC_KEY_PATH}" "${HOME}/.ssh/authorized_keys" || cat "${PUBLIC_KEY_PATH}" >> "${HOME}/.ssh/authorized_keys"

sudo tee -a /etc/ssh/sshd_config >/dev/null <<'EOF'
PermitRootLogin prohibit-password
PubkeyAuthentication yes
PasswordAuthentication no
EOF

sudo mkdir -p /run/sshd
sudo chmod 755 /run/sshd
sudo /usr/sbin/sshd

if [ ! -x "${EC2_SETUP_SCRIPT}" ]; then
  echo "Missing executable EC2 setup script: ${EC2_SETUP_SCRIPT}" >&2
  exit 1
fi
export FORCE_NON_EC2=0
"${EC2_SETUP_SCRIPT}"

cd "${FS_DIR}"

python - <<'PY'
import os
from pathlib import Path

path = Path("deploy/awstools/awstools.py")
text = path.read_text()
replacements = {
    '"vpcname": "firesim",': f'"vpcname": {os.environ["FIRESIM_AWS_VPC_NAME"]!r},',
    '"securitygroupname": "for-farms-only-firesim",': f'"securitygroupname": {os.environ["FIRESIM_AWS_SECURITY_GROUP_NAME"]!r},',
    '"securitygroupname-manager": "firesim",': f'"securitygroupname-manager": {os.environ["FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME"]!r},',
    '"keyname": "firesim",': f'"keyname": {os.environ["FIRESIM_AWS_KEY_NAME"]!r},',
    'allowed_cidr = "192.168.0.0/16"': f'allowed_cidr = {os.environ["FIRESIM_AWS_ALLOWED_CIDR"]!r}',
}
for old, new in replacements.items():
    if old in text:
        text = text.replace(old, new, 1)
    elif new in text:
        continue
    else:
        raise SystemExit(f"Could not find expected FireSim AWS resource-name text: {old}")
path.write_text(text)
print(
    "Configured FireSim AWS resources: "
    f"vpc={os.environ['FIRESIM_AWS_VPC_NAME']} "
    f"key={os.environ['FIRESIM_AWS_KEY_NAME']} "
    f"security_group={os.environ['FIRESIM_AWS_SECURITY_GROUP_NAME']} "
    f"allowed_cidr={os.environ['FIRESIM_AWS_ALLOWED_CIDR']}"
)
PY

had_nounset=0
case "$-" in
  *u*)
    had_nounset=1
    set +u
    ;;
esac

# shellcheck disable=SC1091
source sourceme-manager.sh

if [ "${had_nounset}" -eq 1 ]; then
  set -u
fi

printf '\n' | firesim managerinit --platform f2

python -m pip uninstall -y pyOpenSSL cryptography awscli botocore s3transfer urllib3
python -m pip install --no-cache-dir --upgrade \
  'urllib3<2' \
  'cryptography==42.0.8' \
  'pyOpenSSL==24.1.0' \
  awscli
hash -r

firesim buildbitstream \
  -r "${BUILD_RECIPES}" \
  -b "${BUILD_CONFIG}"

firesim shareagfi
