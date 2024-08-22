import math
import requests
import json

import fabric_cfg
from ci_variables import ci_env

from typing import Dict, List, Any

# Github URL related constants
gh_repo_api_url      = f"{ci_env['GITHUB_API_URL']}/repos/{ci_env['GITHUB_REPOSITORY']}"
gh_issues_api_url    = f"{gh_repo_api_url}/issues"
gha_api_url          = f"{gh_repo_api_url}/actions"
gha_runners_api_url  = f"{gha_api_url}/runners"
gha_runs_api_url     = f"{gha_api_url}/runs"
gha_workflow_api_url = f"{gha_runs_api_url}/{ci_env['GITHUB_RUN_ID']}"

def get_header(gh_token: str) -> Dict[str, str]:
    return {
        "Authorization": f"token {gh_token.strip()}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "bar-tender",
    }

def get_runners(gh_token: str) -> List[Dict[str, Any]]:
    r = requests.get(gha_runners_api_url, headers=get_header(gh_token))
    if r.status_code != 200:
        raise Exception(f"Unable to retrieve count of GitHub Actions Runners\nFull Response Below:\n{r}")
    res_dict = r.json()
    runner_count = res_dict["total_count"]

    runners: List[Dict[str, Any]] = []
    for page_idx in range(math.ceil(runner_count / 30)):
        r = requests.get(gha_runners_api_url, params={"per_page" : 30, "page" : page_idx + 1}, headers=get_header(gh_token))
        if r.status_code != 200:
            raise Exception(f"Unable to retrieve (sub)list of GitHub Actions Runners\nFull Response Below\n{r}")
        res_dict = r.json()
        runners = runners + res_dict["runners"]

    return runners

def delete_runner(gh_token: str, runner: Dict[str, Any]) -> bool:
    r = requests.delete(f"""{gha_runners_api_url}/{runner["id"]}""", headers=get_header(gh_token))
    if r.status_code != 204:
        print(f"""Unable to delete runner {runner["name"]} with id: {runner["id"]}\nFull Response Below\n{r}""")
        return False
    return True

def deregister_offline_runners(gh_token: str) -> None:
    runners = get_runners(gh_token)
    for runner in runners:
        if runner["status"] == "offline":
            delete_runner(gh_token, runner)

def deregister_runners(gh_token: str, runner_name: str) -> None:
    runners = get_runners(gh_token)
    for runner in runners:
        if runner_name in runner["name"]:
            delete_runner(gh_token, runner)

# obtain issue number separately since workflow-monitor shouldn't query the GH-A runner area
# since it's separate from it
def get_issue_number() -> int:
    with open(ci_env['GITHUB_EVENT_PATH']) as f:
        event_payload = json.load(f)
        gh_issue_id = event_payload["number"]
        return gh_issue_id
    raise Exception(f"Unable to return an issue number using {ci_env['GITHUB_EVENT_PATH']}")

def issue_post(gh_token: str, issue_num: int, body: str) -> None:
    res = requests.post(f"{gh_issues_api_url}/{issue_num}/comments",
            json={"body": body}, headers=get_header(gh_token))
    if res.status_code != 201:
        raise Exception(f"HTTP POST error: {res} {res.json()}\nUnable to post GitHub PR comment.")
