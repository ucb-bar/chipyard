from github import Github
import time

from ci_variables import ci_env

GH_REPO = 'firesim-public-bitstreams'
GH_ORG = 'firesim'

# taken from https://stackoverflow.com/questions/63427607/python-upload-files-directly-to-github-using-pygithub
# IMPORTANT: only works for binary files! (i.e. tar.gz files)
def upload_binary_file(local_file_path, gh_file_path):
    print(f":DEBUG: Attempting to upload {local_file_path} to {gh_file_path}")

    g = Github(ci_env['PERSONAL_ACCESS_TOKEN'])

    repo = g.get_repo(f'{GH_ORG}/{GH_REPO}')
    all_files = []
    contents = repo.get_contents("")
    while contents:
        file_content = contents.pop(0)
        if file_content.type == "dir":
            contents.extend(repo.get_contents(file_content.path))
        else:
            file = file_content
            all_files.append(str(file).replace('ContentFile(path="','').replace('")',''))

    with open(local_file_path, 'rb') as file:
        content = file.read()

    tries = 10
    delay = 15
    msg = f"Committing files from {ci_env['GITHUB_SHA']}"
    upload_branch = 'main'
    r = None

    # Upload to github
    git_file = gh_file_path
    if git_file in all_files:
        contents = repo.get_contents(git_file)
        for n in range(tries):
            try:
                r = repo.update_file(contents.path, msg, content, contents.sha, branch=upload_branch)
                break
            except Exception as e:
                print(f"Got exception: {e}")
                time.sleep(delay)
        assert r is not None, f"Unable to poll 'update_file' API {tries} times"
        print(f"Updated: {git_file}")
    else:
        for n in range(tries):
            try:
                r = repo.create_file(git_file, msg, content, branch=upload_branch)
                break
            except Exception as e:
                print(f"Got exception: {e}")
                time.sleep(delay)
        assert r is not None, f"Unable to poll 'create_file' API {tries} times"
        print(f"Created: {git_file}")

    return r['commit'].sha
