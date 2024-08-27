import os

# This package contains utilities that rely on environment variable
# definitions present only on the CI container instance.

GITHUB_ACTIONS_ENV_VAR_NAME = 'GITHUB_ACTIONS'
RUN_LOCAL = os.environ.get(GITHUB_ACTIONS_ENV_VAR_NAME, 'false') == 'false'

# When running locally (not in a CI pipeline) run commands out of the clone hosting this file.
# Should be equivalent to GITHUB_WORKSPACE if running locally
local_cy_dir = os.path.normpath((os.path.realpath(__file__)) + "/../../..")

def get_ci_value(env_var: str, default_value: str = "") -> str:
    if RUN_LOCAL:
        return default_value
    else:
        return os.environ.get(env_var, default_value)

# Create a env. dict that is populated from the environment or from defaults.
ci_env = {
    # If not running under a CI pipeline defaults are provided that
    # will suffice to run scripts that do not use GHA API calls.
    # To manually provide environment variable settings, export GITHUB_ACTIONS=true, and provide
    # values for all of the environment variables listed.
    GITHUB_ACTIONS_ENV_VAR_NAME: 'false' if RUN_LOCAL else 'true', # type: ignore
    # This is used as a unique tag for all instances launched in a workflow
    'GITHUB_RUN_ID': get_ci_value('GITHUB_RUN_ID'),
    # Self explanatory
    'GITHUB_SHA': get_ci_value('GITHUB_SHA'),
    # Multiple clones of the Chipyard repository exists on a CI machine. We expect state
    # to persist between jobs in a workflow and faciliate that by having jobs run
    # out of a centralized clone (REMOTE_WORK_DIR)-- not the default clones setup by
    # the GHA runners (GITHUB_WORKSPACE)
    # This is the location of the clone setup by the GHA runner infrastructure by default
    # expanduser to replace the ~ present in the default, for portability
    'GITHUB_WORKSPACE': os.path.expanduser(os.environ['GITHUB_WORKSPACE']) if not RUN_LOCAL else local_cy_dir,
    # Self explanatory
    'GITHUB_API_URL': get_ci_value('GITHUB_API_URL'),
    # We look this up, instead of hardcoding "ucb-bar/chipyard", to support running
    # this CI pipeline under forks.
    'GITHUB_REPOSITORY': get_ci_value('GITHUB_REPOSITORY'),
    # Path to webhook payload on the runner machine
    'GITHUB_EVENT_PATH': get_ci_value('GITHUB_EVENT_PATH'),
    # Chipyard repo used on local CI machine to run tests from (cached across all workflow CI jobs)
    # CI scripts should refer variables
    # derived from this path so that they may be reused across workflows that may
    # initialize the Chipyard repository differently (e.g., as a submodule of a
    # larger project.)
    'REMOTE_WORK_DIR': get_ci_value('REMOTE_WORK_DIR', local_cy_dir if RUN_LOCAL else ""),
    # Github token with more permissions to access repositories across the FireSim org.
    'PERSONAL_ACCESS_TOKEN': get_ci_value('PERSONAL_ACCESS_TOKEN'),
    # Path to Chipyard's HWDB file (if it exists)
    'CHIPYARD_HWDB_PATH': get_ci_value('CHIPYARD_HWDB_PATH'),
    # Org/repo name of repository to store build bitstreams
    'GH_ORG': get_ci_value('GH_ORG'),
    'GH_REPO': get_ci_value('GH_REPO'),
}

# for most uses these should be used (over using GITHUB_WORKSPACE)
remote_cy_dir = ci_env['REMOTE_WORK_DIR']
remote_fsim_dir = ci_env['REMOTE_WORK_DIR'] + "/sims/firesim"
