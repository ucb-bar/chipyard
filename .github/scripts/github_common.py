from fabric.api import prefix, run # type: ignore

# move and commit a binary file to a Github repository.
# it is another persons job to create the repository and push it (to avoid needing an SSH key here).
# by default this uses the bar-tender bot credentials.
def move_and_commit_gh_file(local_abs_file_path, gh_rel_file_path, repo_path, commit_msg):
    print(f":DEBUG: Attempting to commit {local_abs_file_path} to {repo_path}/{gh_rel_file_path}")

    with prefix(f"cd {repo_path}"):
        run(f"mv {local_abs_file_path} ./{gh_rel_file_path}")
        run(f"git add -u")
        run(f"""git commit -m "{commit_msg}" --author="bar-tender <bar-tender@users.noreply.github.com>" """)
        # return the sha of the commit you added
        return run("git rev-parse HEAD")
