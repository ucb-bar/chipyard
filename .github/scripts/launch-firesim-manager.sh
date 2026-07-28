#!/usr/bin/env bash
set -euo pipefail

# Launch and provision an EC2 instance that can act as a FireSim manager.
#
# Required CI environment:
#   AWS_ACCESS_KEY_ID
#   AWS_SECRET_ACCESS_KEY
#   AWS_SESSION_TOKEN              optional, required only for temporary creds
#   AWS_DEFAULT_REGION             optional, defaults to us-west-2
#   FIRESIM_MANAGER_SSH_CIDR       optional, defaults to CI runner public IP/32
#   FIRESIM_MANAGER_SSH_PRIVATE_KEY
#       Required if EC2 key pair chipyard-cicd already exists. EC2 does not
#       retain private key material after key-pair creation, so it cannot be
#       pulled back from AWS later.
#   FIRESIM_MANAGER_IMAGE
#       Optional, defaults to ghcr.io/${GITHUB_REPOSITORY,,}:main.
#   GHCR_USERNAME / GHCR_TOKEN
#       Optional, needed only when pulling a private GHCR image.
#
# Useful overrides:
#   FIRESIM_MANAGER_KEY_NAME=chipyard-cicd
#   FIRESIM_MANAGER_INSTANCE_TYPE=m6a.xlarge
#   FIRESIM_MANAGER_AMI_ID=ami-... or public AMI name
#   FIRESIM_MANAGER_VPC_NAME=chipyard-cicd
#   FIRESIM_MANAGER_ROOT_VOLUME_GB=100
#   FIRESIM_MANAGER_CONTAINER_SCRIPT=path/to/local/script.sh
#   FIRESIM_MANAGER_DETACH_CONTAINER=1

# Set this to an AMI ID or public AMI name to pin the manager image. Leave
# empty to resolve the current Ubuntu 24.04 amd64 EBS gp3 AMI from AWS SSM in
# the selected region.

# This is FPGA Dev AMI 1.19.2-prod-rhng4b6alkhdq
manager_ami_id="${FIRESIM_MANAGER_AMI_ID:-ami-07a164f1a402ab274}"

aws_region="${AWS_REGION:-${AWS_DEFAULT_REGION:-us-west-2}}"
export AWS_DEFAULT_REGION="${aws_region}"

managed_by="${FIRESIM_MANAGER_MANAGED_BY:-chipyard-cicd}"
resource_prefix="${FIRESIM_MANAGER_RESOURCE_PREFIX:-chipyard-cicd-firesim}"
key_name="${FIRESIM_MANAGER_KEY_NAME:-chipyard-cicd}"
instance_type="${FIRESIM_MANAGER_INSTANCE_TYPE:-m6a.xlarge}"
vpc_cidr="${FIRESIM_MANAGER_VPC_CIDR:-192.168.0.0/16}"
subnet_cidr="${FIRESIM_MANAGER_SUBNET_CIDR:-192.168.1.0/24}"
root_volume_gb="${FIRESIM_MANAGER_ROOT_VOLUME_GB:-170}"
remote_user="${FIRESIM_MANAGER_REMOTE_USER:-ubuntu}"
remote_region="${FIRESIM_AWS_REGION:-${aws_region}}"
detach_container="${FIRESIM_MANAGER_DETACH_CONTAINER:-0}"
keep_container_on_error="${FIRESIM_MANAGER_KEEP_CONTAINER_ON_ERROR:-1}"
firesim_build_security_group_name="${FIRESIM_AWS_SECURITY_GROUP_NAME:-chipyard-cicd-build-farm}"

vpc_name="${FIRESIM_MANAGER_VPC_NAME:-chipyard-cicd}"
subnet_name="${resource_prefix}-subnet"
igw_name="${resource_prefix}-igw"
route_table_name="${resource_prefix}-rt"
sg_name="${resource_prefix}-ssh"
run_id="${GITHUB_RUN_ID:-$(date -u +%Y%m%d%H%M%S)}"
instance_name="${FIRESIM_MANAGER_INSTANCE_NAME:-${resource_prefix}-manager-${run_id}}"

if [ -n "${FIRESIM_MANAGER_IMAGE:-}" ]; then
  image_ref="${FIRESIM_MANAGER_IMAGE}"
elif [ -n "${GITHUB_REPOSITORY:-}" ]; then
  image_ref="ghcr.io/${GITHUB_REPOSITORY,,}:main"
else
  echo "Set FIRESIM_MANAGER_IMAGE or GITHUB_REPOSITORY so the GHCR image can be derived." >&2
  exit 1
fi

require_command() {
  local command="$1"
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Missing required command: ${command}" >&2
    exit 1
  fi
}

aws_text_or_empty() {
  local value
  value="$(aws "$@" --output text)"
  if [ "${value}" = "None" ]; then
    value=""
  fi
  printf '%s\n' "${value}"
}

tag_resource() {
  local resource_id="$1"
  local name="$2"
  aws ec2 create-tags \
    --resources "${resource_id}" \
    --tags "Key=Name,Value=${name}" "Key=ManagedBy,Value=${managed_by}" \
    >/dev/null
}

ensure_ssh_cidr() {
  if [ -n "${FIRESIM_MANAGER_SSH_CIDR:-}" ]; then
    printf '%s\n' "${FIRESIM_MANAGER_SSH_CIDR}"
    return
  fi

  if command -v curl >/dev/null 2>&1; then
    local public_ip
    public_ip="$(curl -fsS --max-time 10 https://checkip.amazonaws.com 2>/dev/null | tr -d '[:space:]' || true)"
    if [[ "${public_ip}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      printf '%s/32\n' "${public_ip}"
      return
    fi
  fi

  echo "Set FIRESIM_MANAGER_SSH_CIDR to the CI runner's SSH source CIDR, for example 203.0.113.10/32." >&2
  exit 1
}

ensure_vpc() {
  local vpc_id
  vpc_id="$(aws_text_or_empty ec2 describe-vpcs \
    --filters "Name=tag:Name,Values=${vpc_name}" \
    --query 'Vpcs[0].VpcId')"

  if [ -z "${vpc_id}" ]; then
    vpc_id="$(aws ec2 create-vpc --cidr-block "${vpc_cidr}" --query 'Vpc.VpcId' --output text)"
    tag_resource "${vpc_id}" "${vpc_name}"
  fi

  aws ec2 modify-vpc-attribute --vpc-id "${vpc_id}" --enable-dns-support '{"Value":true}' >/dev/null
  aws ec2 modify-vpc-attribute --vpc-id "${vpc_id}" --enable-dns-hostnames '{"Value":true}' >/dev/null
  printf '%s\n' "${vpc_id}"
}

ensure_subnet() {
  local vpc_id="$1"
  local subnet_id
  subnet_id="$(aws_text_or_empty ec2 describe-subnets \
    --filters "Name=vpc-id,Values=${vpc_id}" "Name=tag:Name,Values=${subnet_name}" "Name=tag:ManagedBy,Values=${managed_by}" \
    --query 'Subnets[0].SubnetId')"

  if [ -z "${subnet_id}" ]; then
    local az
    az="$(aws ec2 describe-availability-zones \
      --filters 'Name=state,Values=available' \
      --query 'AvailabilityZones[0].ZoneName' \
      --output text)"
    subnet_id="$(aws ec2 create-subnet \
      --vpc-id "${vpc_id}" \
      --cidr-block "${subnet_cidr}" \
      --availability-zone "${az}" \
      --query 'Subnet.SubnetId' \
      --output text)"
    tag_resource "${subnet_id}" "${subnet_name}"
  fi

  aws ec2 modify-subnet-attribute --subnet-id "${subnet_id}" --map-public-ip-on-launch '{"Value":true}' >/dev/null
  printf '%s\n' "${subnet_id}"
}

ensure_internet_gateway() {
  local vpc_id="$1"
  local igw_id
  igw_id="$(aws_text_or_empty ec2 describe-internet-gateways \
    --filters "Name=attachment.vpc-id,Values=${vpc_id}" \
    --query 'InternetGateways[0].InternetGatewayId')"

  if [ -z "${igw_id}" ]; then
    igw_id="$(aws_text_or_empty ec2 describe-internet-gateways \
      --filters "Name=tag:Name,Values=${igw_name}" "Name=tag:ManagedBy,Values=${managed_by}" \
      --query 'InternetGateways[0].InternetGatewayId')"
  fi

  if [ -z "${igw_id}" ]; then
    igw_id="$(aws ec2 create-internet-gateway --query 'InternetGateway.InternetGatewayId' --output text)"
    tag_resource "${igw_id}" "${igw_name}"
  fi

  local attached_vpc
  attached_vpc="$(aws_text_or_empty ec2 describe-internet-gateways \
    --internet-gateway-ids "${igw_id}" \
    --query 'InternetGateways[0].Attachments[0].VpcId')"
  if [ -z "${attached_vpc}" ]; then
    aws ec2 attach-internet-gateway --internet-gateway-id "${igw_id}" --vpc-id "${vpc_id}" >/dev/null
  elif [ "${attached_vpc}" != "${vpc_id}" ]; then
    echo "Internet gateway ${igw_id} is already attached to ${attached_vpc}, not ${vpc_id}." >&2
    exit 1
  fi

  printf '%s\n' "${igw_id}"
}

ensure_route_table() {
  local vpc_id="$1"
  local subnet_id="$2"
  local igw_id="$3"
  local route_table_id
  route_table_id="$(aws_text_or_empty ec2 describe-route-tables \
    --filters "Name=vpc-id,Values=${vpc_id}" "Name=tag:Name,Values=${route_table_name}" "Name=tag:ManagedBy,Values=${managed_by}" \
    --query 'RouteTables[0].RouteTableId')"

  if [ -z "${route_table_id}" ]; then
    route_table_id="$(aws ec2 create-route-table --vpc-id "${vpc_id}" --query 'RouteTable.RouteTableId' --output text)"
    tag_resource "${route_table_id}" "${route_table_name}"
  fi

  local route_target
  route_target="$(aws_text_or_empty ec2 describe-route-tables \
    --route-table-ids "${route_table_id}" \
    --query "RouteTables[0].Routes[?DestinationCidrBlock=='0.0.0.0/0'].GatewayId | [0]")"
  if [ -z "${route_target}" ]; then
    aws ec2 create-route \
      --route-table-id "${route_table_id}" \
      --destination-cidr-block 0.0.0.0/0 \
      --gateway-id "${igw_id}" \
      >/dev/null
  elif [ "${route_target}" != "${igw_id}" ]; then
    aws ec2 replace-route \
      --route-table-id "${route_table_id}" \
      --destination-cidr-block 0.0.0.0/0 \
      --gateway-id "${igw_id}" \
      >/dev/null
  fi

  local association_id
  association_id="$(aws_text_or_empty ec2 describe-route-tables \
    --route-table-ids "${route_table_id}" \
    --query "RouteTables[0].Associations[?SubnetId=='${subnet_id}'].RouteTableAssociationId | [0]")"
  if [ -z "${association_id}" ]; then
    aws ec2 associate-route-table --route-table-id "${route_table_id}" --subnet-id "${subnet_id}" >/dev/null
  fi
}

ensure_security_group() {
  local vpc_id="$1"
  local ssh_cidr="$2"
  local security_group_id
  security_group_id="$(aws_text_or_empty ec2 describe-security-groups \
    --filters "Name=vpc-id,Values=${vpc_id}" "Name=group-name,Values=${sg_name}" \
    --query 'SecurityGroups[0].GroupId')"

  if [ -z "${security_group_id}" ]; then
    security_group_id="$(aws ec2 create-security-group \
      --vpc-id "${vpc_id}" \
      --group-name "${sg_name}" \
      --description "SSH access for Chipyard CI FireSim manager" \
      --query 'GroupId' \
      --output text)"
    tag_resource "${security_group_id}" "${sg_name}"
  fi

  local err_file
  err_file="$(mktemp)"
  if ! aws ec2 authorize-security-group-ingress \
    --group-id "${security_group_id}" \
    --ip-permissions "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=${ssh_cidr},Description=CI-SSH}]" \
    >/dev/null 2>"${err_file}"; then
    if ! grep -q 'InvalidPermission.Duplicate' "${err_file}"; then
      cat "${err_file}" >&2
      rm -f "${err_file}"
      exit 1
    fi
  fi
  rm -f "${err_file}"

  printf '%s\n' "${security_group_id}"
}

prepare_key_files() {
  local key_file="$1"
  local public_key_file="$2"

  if aws ec2 describe-key-pairs --key-names "${key_name}" >/dev/null 2>&1; then
    if [ -n "${FIRESIM_MANAGER_SSH_PRIVATE_KEY_FILE:-}" ]; then
      cp "${FIRESIM_MANAGER_SSH_PRIVATE_KEY_FILE}" "${key_file}"
    elif [ -n "${FIRESIM_MANAGER_SSH_PRIVATE_KEY:-}" ]; then
      printf '%s\n' "${FIRESIM_MANAGER_SSH_PRIVATE_KEY}" > "${key_file}"
    else
      echo "EC2 key pair ${key_name} exists, but its private key cannot be retrieved from AWS." >&2
      echo "Set FIRESIM_MANAGER_SSH_PRIVATE_KEY or FIRESIM_MANAGER_SSH_PRIVATE_KEY_FILE in CI." >&2
      exit 1
    fi
  else
    aws ec2 create-key-pair \
      --key-name "${key_name}" \
      --key-type rsa \
      --key-format pem \
      --query 'KeyMaterial' \
      --output text > "${key_file}"
  fi

  chmod 600 "${key_file}"
  ssh-keygen -y -f "${key_file}" > "${public_key_file}"
  chmod 644 "${public_key_file}"
}

resolve_ami_id() {
  if [ -n "${manager_ami_id}" ]; then
    if [[ "${manager_ami_id}" == ami-* ]]; then
      printf '%s\n' "${manager_ami_id}"
      return
    fi

    local image_id
    image_id="$(aws_text_or_empty ec2 describe-images \
      --filters "Name=name,Values=${manager_ami_id}" "Name=is-public,Values=true" \
      --query 'sort_by(Images, &CreationDate)[-1].ImageId')"
    if [ -z "${image_id}" ]; then
      echo "Could not resolve public AMI name: ${manager_ami_id}" >&2
      exit 1
    fi

    printf '%s\n' "${image_id}"
    return
  fi

  aws ssm get-parameter \
    --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
    --query 'Parameter.Value' \
    --output text
}

launch_instance() {
  local ami_id="$1"
  local subnet_id="$2"
  local security_group_id="$3"

  aws ec2 run-instances \
    --image-id "${ami_id}" \
    --instance-type "${instance_type}" \
    --key-name "${key_name}" \
    --subnet-id "${subnet_id}" \
    --security-group-ids "${security_group_id}" \
    --associate-public-ip-address \
    --metadata-options 'HttpEndpoint=enabled,HttpTokens=required' \
    --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=${root_volume_gb},VolumeType=gp3,DeleteOnTermination=true}" \
    --tag-specifications \
      "ResourceType=instance,Tags=[{Key=Name,Value=${instance_name}},{Key=ManagedBy,Value=${managed_by}}]" \
      "ResourceType=volume,Tags=[{Key=Name,Value=${instance_name}-root},{Key=ManagedBy,Value=${managed_by}}]" \
    --query 'Instances[0].InstanceId' \
    --output text
}

write_remote_aws_files() {
  local aws_dir="$1"
  mkdir -p "${aws_dir}"
  chmod 700 "${aws_dir}"

  local access_key="${FIRESIM_AWS_ACCESS_KEY_ID:-${AWS_ACCESS_KEY_ID:-}}"
  local secret_key="${FIRESIM_AWS_SECRET_ACCESS_KEY:-${AWS_SECRET_ACCESS_KEY:-}}"
  local session_token="${FIRESIM_AWS_SESSION_TOKEN:-${AWS_SESSION_TOKEN:-}}"

  if [ -z "${access_key}" ] || [ -z "${secret_key}" ]; then
    echo "Set AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY or FIRESIM_AWS_ACCESS_KEY_ID/FIRESIM_AWS_SECRET_ACCESS_KEY." >&2
    exit 1
  fi

  {
    printf '[default]\n'
    printf 'aws_access_key_id = %s\n' "${access_key}"
    printf 'aws_secret_access_key = %s\n' "${secret_key}"
    if [ -n "${session_token}" ]; then
      printf 'aws_session_token = %s\n' "${session_token}"
    fi
  } > "${aws_dir}/credentials"

  {
    printf '[default]\n'
    printf 'region = %s\n' "${remote_region}"
    printf 'output = json\n'
  } > "${aws_dir}/config"

  chmod 600 "${aws_dir}/credentials" "${aws_dir}/config"
}

write_container_script() {
  local destination="$1"
  local default_container_script=".github/scripts/firesim-manager-entrypoint.sh"

  if [ -n "${FIRESIM_MANAGER_CONTAINER_SCRIPT:-}" ]; then
    cp "${FIRESIM_MANAGER_CONTAINER_SCRIPT}" "${destination}"
  elif [ -f "${default_container_script}" ]; then
    cp "${default_container_script}" "${destination}"
  else
    {
      printf '#!/usr/bin/env bash\n'
      printf 'set -euo pipefail\n'
      printf 'echo "FireSim manager container is ready; no workload script was provided."\n'
    } > "${destination}"
  fi

  chmod 755 "${destination}"
}

write_runtime_env() {
  local destination="$1"

  {
    printf 'IMAGE_REF=%q\n' "${image_ref}"
    printf 'GHCR_USERNAME=%q\n' "${GHCR_USERNAME:-}"
    printf 'GHCR_TOKEN=%q\n' "${GHCR_TOKEN:-}"
    printf 'DETACH_CONTAINER=%q\n' "${detach_container}"
    printf 'KEEP_CONTAINER_ON_ERROR=%q\n' "${keep_container_on_error}"
    printf 'FIRESIM_AWS_VPC_NAME=%q\n' "${vpc_name}"
    printf 'FIRESIM_AWS_KEY_NAME=%q\n' "${key_name}"
    printf 'FIRESIM_AWS_SECURITY_GROUP_NAME=%q\n' "${firesim_build_security_group_name}"
    printf 'FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME=%q\n' "${sg_name}"
    printf 'FIRESIM_AWS_ALLOWED_CIDR=%q\n' "${vpc_cidr}"
  } > "${destination}"

  chmod 600 "${destination}"
}

wait_for_ssh() {
  local public_ip="$1"
  local key_file="$2"

  local ssh_options=(
    -i "${key_file}"
    -o StrictHostKeyChecking=no
    -o UserKnownHostsFile=/dev/null
    -o ConnectTimeout=10
    -o ServerAliveInterval=15
  )

  for _ in $(seq 1 60); do
    if ssh "${ssh_options[@]}" "${remote_user}@${public_ip}" 'true' >/dev/null 2>&1; then
      return
    fi
    sleep 10
  done

  echo "Timed out waiting for SSH on ${public_ip}." >&2
  exit 1
}

provision_instance() {
  local public_ip="$1"
  local key_file="$2"
  local public_key_file="$3"
  local aws_dir="$4"
  local container_script="$5"
  local runtime_env="$6"

  local ssh_options=(
    -i "${key_file}"
    -o StrictHostKeyChecking=no
    -o UserKnownHostsFile=/dev/null
    -o ServerAliveInterval=15
  )
  local scp_options=(
    -i "${key_file}"
    -o StrictHostKeyChecking=no
    -o UserKnownHostsFile=/dev/null
  )

  scp "${scp_options[@]}" \
    "${key_file}" \
    "${public_key_file}" \
    "${aws_dir}/credentials" \
    "${aws_dir}/config" \
    "${container_script}" \
    "${runtime_env}" \
    "${remote_user}@${public_ip}:/tmp/"

  ssh "${ssh_options[@]}" "${remote_user}@${public_ip}" 'bash -s' <<'REMOTE'
set -euo pipefail

source /tmp/firesim-manager-runtime.env
export IMAGE_REF GHCR_USERNAME GHCR_TOKEN DETACH_CONTAINER
export KEEP_CONTAINER_ON_ERROR
export FIRESIM_AWS_VPC_NAME FIRESIM_AWS_KEY_NAME FIRESIM_AWS_SECURITY_GROUP_NAME FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME FIRESIM_AWS_ALLOWED_CIDR

if command -v cloud-init >/dev/null 2>&1; then
  if ! sudo cloud-init status --wait; then
    echo "cloud-init reported warnings/errors after reaching status; continuing provisioning." >&2
    sudo cloud-init status --long || true
  fi
fi
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates docker.io
sudo systemctl enable --now docker
sudo usermod -aG docker "${USER}"

sudo install -m 700 -o "${USER}" -g "${USER}" -d "${HOME}/.aws"
sudo install -m 600 -o "${USER}" -g "${USER}" /tmp/credentials "${HOME}/.aws/credentials"
sudo install -m 600 -o "${USER}" -g "${USER}" /tmp/config "${HOME}/.aws/config"
sudo install -m 600 -o "${USER}" -g "${USER}" /tmp/firesim.pem "${HOME}/firesim.pem"
sudo install -m 644 -o "${USER}" -g "${USER}" /tmp/firesim-public "${HOME}/firesim-public"
sudo install -m 755 -o "${USER}" -g "${USER}" /tmp/firesim-manager-entrypoint.sh "${HOME}/firesim-manager-entrypoint.sh"

rm -f /tmp/credentials /tmp/config /tmp/firesim.pem /tmp/firesim-public /tmp/firesim-manager-entrypoint.sh /tmp/firesim-manager-runtime.env

newgrp docker <<'DOCKER_GROUP'
set -e
if [ -n "${GHCR_TOKEN}" ]; then
  printf '%s\n' "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME:-oauth2}" --password-stdin
fi

docker pull "${IMAGE_REF}"
docker_args=(
  --name firesim-manager
  --add-host=host.docker.internal:host-gateway
  -v "${HOME}/.aws:/home/ubuntu/.aws:ro"
  -v "${HOME}/firesim.pem:/home/ubuntu/firesim.pem:ro"
  -v "${HOME}/firesim-public:/home/ubuntu/firesim-public:ro"
  -v "${HOME}/firesim-manager-entrypoint.sh:/home/ubuntu/firesim-manager-entrypoint.sh:ro"
  -v "/opt/Xilinx:/opt/Xilinx:ro"
  -e "FIRESIM_AWS_VPC_NAME=${FIRESIM_AWS_VPC_NAME}"
  -e "FIRESIM_AWS_KEY_NAME=${FIRESIM_AWS_KEY_NAME}"
  -e "FIRESIM_AWS_SECURITY_GROUP_NAME=${FIRESIM_AWS_SECURITY_GROUP_NAME}"
  -e "FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME=${FIRESIM_AWS_MANAGER_SECURITY_GROUP_NAME}"
  -e "FIRESIM_AWS_ALLOWED_CIDR=${FIRESIM_AWS_ALLOWED_CIDR}"
  -e "KEEP_CONTAINER_ON_ERROR=${KEEP_CONTAINER_ON_ERROR}"
)

if [ "${DETACH_CONTAINER}" = "1" ]; then
  docker rm -f firesim-manager >/dev/null 2>&1 || true
  container_id="$(docker run -d "${docker_args[@]}" "${IMAGE_REF}" bash /home/ubuntu/firesim-manager-entrypoint.sh)"
  echo "Started detached FireSim manager container: ${container_id}"
  echo "Inspect with: docker ps -a --filter name=firesim-manager && docker logs firesim-manager"
else
  docker run --rm "${docker_args[@]}" "${IMAGE_REF}" bash /home/ubuntu/firesim-manager-entrypoint.sh
fi
DOCKER_GROUP
REMOTE
}

main() {
  require_command aws
  require_command ssh
  require_command scp
  require_command ssh-keygen

  local ssh_cidr
  ssh_cidr="$(ensure_ssh_cidr)"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap "rm -rf '${tmp_dir}'" EXIT

  local key_file="${tmp_dir}/firesim.pem"
  local public_key_file="${tmp_dir}/firesim-public"
  local aws_dir="${tmp_dir}/aws"
  local container_script="${tmp_dir}/firesim-manager-entrypoint.sh"
  local runtime_env="${tmp_dir}/firesim-manager-runtime.env"

  echo "Preparing EC2 key pair ${key_name}."
  prepare_key_files "${key_file}" "${public_key_file}"
  write_remote_aws_files "${aws_dir}"
  write_container_script "${container_script}"

  echo "Ensuring FireSim manager network in ${aws_region}."
  local vpc_id
  local subnet_id
  local igw_id
  local security_group_id
  vpc_id="$(ensure_vpc)"
  subnet_id="$(ensure_subnet "${vpc_id}")"
  igw_id="$(ensure_internet_gateway "${vpc_id}")"
  ensure_route_table "${vpc_id}" "${subnet_id}" "${igw_id}"
  security_group_id="$(ensure_security_group "${vpc_id}" "${ssh_cidr}")"
  vpc_cidr="$(aws ec2 describe-vpcs --vpc-ids "${vpc_id}" --query 'Vpcs[0].CidrBlock' --output text)"
  write_runtime_env "${runtime_env}"

  local ami_id
  ami_id="$(resolve_ami_id)"

  echo "Launching ${instance_type} FireSim manager from ${ami_id}."
  local instance_id
  instance_id="$(launch_instance "${ami_id}" "${subnet_id}" "${security_group_id}")"
  aws ec2 wait instance-running --instance-ids "${instance_id}"
  aws ec2 wait instance-status-ok --instance-ids "${instance_id}"

  local public_ip
  public_ip="$(aws ec2 describe-instances \
    --instance-ids "${instance_id}" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' \
    --output text)"

  echo "Instance ${instance_id} is running at ${public_ip}. Waiting for SSH."
  wait_for_ssh "${public_ip}" "${key_file}"

  echo "Provisioning FireSim manager and running ${image_ref}."
  provision_instance "${public_ip}" "${key_file}" "${public_key_file}" "${aws_dir}" "${container_script}" "${runtime_env}"

  echo "FireSim manager instance: ${instance_id}"
  echo "FireSim manager public IP: ${public_ip}"
}

main "$@"
