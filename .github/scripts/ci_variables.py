import os

from typing import TypedDict

# This package contains utilities that rely on environment variable
# definitions present only on the CI container instance.

# environment variables needed by CI
class CIEnvironment(TypedDict):
    # If not running under a CI pipeline defaults are provided that
    # will suffice to run scripts that do not use GHA API calls.
    # To manually provide environment variable settings, export GITHUB_ACTIONS=true, and provide
    # values for all of the environment variables listed.
    GITHUB_ACTIONS: str

    # This is used as a unique tag for all instances launched in a workflow
    GITHUB_RUN_ID: str

    GITHUB_SHA: str

    # Multiple clones of the FireSim repository exists on manager. We expect state
    # to persist between jobs in a workflow and faciliate that by having jobs run
    # out of a centralized clone (MANAGER_FIRESIM_LOCATION)-- not the default clones setup by
    # the GHA runners (GITHUB_WORKSPACE)

    # This is the location of the clone setup by the GHA runner infrastructure by default
    # expanduser to replace the ~ present in the default, for portability
    GITHUB_WORKSPACE: str

    GITHUB_API_URL: str

    # We look this up, instead of hardcoding "firesim/firesim", to support running
    # this CI pipeline under forks.
    GITHUB_REPOSITORY: str

    GITHUB_EVENT_PATH: str

    # Chipyard repo used on local CI machine to run tests from (cached across all workflow CI jobs)
    # CI scripts should refer variables
    # derived from this path so that they may be reused across workflows that may
    # initialize the Chipyard repository differently (e.g., as a submodule of a
    # larger project.)
    REMOTE_WORK_DIR: str

    # Github token with more permissions to access repositories across the FireSim org.
    PERSONAL_ACCESS_TOKEN: str


GITHUB_ACTIONS_ENV_VAR_NAME = 'GITHUB_ACTIONS'
RUN_LOCAL = os.environ.get(GITHUB_ACTIONS_ENV_VAR_NAME, 'false') == 'false'
# When running locally (not in a CI pipeline) run commands out of the clone hosting this file.
local_cy_dir = os.path.normpath((os.path.realpath(__file__)) + "/../../..")

def get_ci_value(env_var: str, default_value: str = "") -> str:
    if RUN_LOCAL:
        return default_value
    else:
        return os.environ[env_var]

# Create a env. dict that is populated from the environment or from defaults.
# See above for descriptions.
ci_env: CIEnvironment = {
    GITHUB_ACTIONS_ENV_VAR_NAME: 'false' if RUN_LOCAL else 'true', # type: ignore
    'GITHUB_RUN_ID': get_ci_value('GITHUB_RUN_ID'),
    'GITHUB_SHA': get_ci_value('GITHUB_RUN_ID'),
    'GITHUB_WORKSPACE': os.path.expanduser(os.environ['GITHUB_WORKSPACE']) if not RUN_LOCAL else local_cy_dir,
    'GITHUB_API_URL': get_ci_value('GITHUB_API_URL'),
    'GITHUB_REPOSITORY': get_ci_value('GITHUB_REPOSITORY'),
    'GITHUB_EVENT_PATH': get_ci_value('GITHUB_EVENT_PATH'),

    'REMOTE_WORK_DIR': get_ci_value('REMOTE_WORK_DIR', local_cy_dir if RUN_LOCAL else ""),
    'PERSONAL_ACCESS_TOKEN': get_ci_value('PERSONAL_ACCESS_TOKEN'),
}

manager_fsim_dir = ci_env['REMOTE_WORK_DIR'] + "/sims/firesim"
