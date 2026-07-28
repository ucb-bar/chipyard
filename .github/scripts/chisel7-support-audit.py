#!/usr/bin/env python3

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path


ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")


def strip_ansi(text):
    return ANSI_RE.sub("", text)


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def read_build_versions(repo_dir):
    build_sbt = repo_dir / "build.sbt"
    versions = {}
    if not build_sbt.exists():
        return versions
    text = build_sbt.read_text(encoding="utf-8")
    for key in ("chisel6Version", "chisel7Version", "chiselTestVersion", "chisel3Version"):
        match = re.search(rf'val\s+{key}\s*=\s*"([^"]+)"', text)
        if match:
            versions[key] = match.group(1)
    return versions


def sbt_base_command(repo_dir):
    return [
        "java",
        "-jar",
        str(repo_dir / "scripts" / "sbt-launch.jar"),
        f"-Dsbt.ivy.home={repo_dir / '.ivy2'}",
        f"-Dsbt.global.base={repo_dir / '.sbt'}",
        f"-Dsbt.boot.directory={repo_dir / '.sbt' / 'boot'}",
        "-Dsbt.color=false",
        "-Dsbt.supershell=false",
        "-Dsbt.server.forcestart=false",
    ]


def audit_env(repo_dir):
    env = os.environ.copy()
    env["USE_CHISEL7"] = "1"
    env.setdefault(
        "JAVA_TOOL_OPTIONS",
        f"-Xmx8G -Xss8M -Djava.io.tmpdir={repo_dir / '.java_tmp'}",
    )
    return env


def run_sbt(repo_dir, sbt_command, log_path, timeout_seconds):
    command = sbt_base_command(repo_dir) + [sbt_command]
    started = time.monotonic()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8", errors="replace") as log_file:
        log_file.write(f"$ {' '.join(command)}\n\n")
        log_file.flush()
        try:
            proc = subprocess.run(
                command,
                cwd=repo_dir,
                env=audit_env(repo_dir),
                stdout=log_file,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=timeout_seconds,
                check=False,
            )
            return proc.returncode, time.monotonic() - started, False
        except subprocess.TimeoutExpired:
            log_file.write(f"\nTimed out after {timeout_seconds} seconds\n")
            return 124, time.monotonic() - started, True


def tail_lines(path, limit):
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return [strip_ansi(line) for line in lines[-limit:]]


def error_summary(path, limit):
    if not path.exists():
        return []
    selected = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        clean = strip_ansi(line)
        if "[error]" in clean or "Compilation failed" in clean or "not found:" in clean:
            selected.append(clean)
        if len(selected) >= limit:
            break
    return selected


def discover_projects(repo_dir, log_dir, timeout_seconds):
    log_path = log_dir / "sbt-projects.log"
    returncode, _, timed_out = run_sbt(repo_dir, "projects", log_path, timeout_seconds)
    if returncode != 0 or timed_out:
        raise RuntimeError(f"sbt projects failed; see {log_path}")

    projects = []
    collecting = False
    for raw in log_path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = strip_ansi(raw)
        if line.startswith("[info] In file:"):
            collecting = True
            continue
        if not collecting:
            continue
        match = re.match(r"\[info\]\s+\*?\s+([A-Za-z0-9_.-]+)\s*$", line)
        if match:
            projects.append(match.group(1))
    return sorted(dict.fromkeys(projects))


def project_result(project, status, returncode, duration, log_path, args):
    return {
        "project": project,
        "status": status,
        "returncode": returncode,
        "duration_seconds": round(duration, 3),
        "log_path": str(log_path),
        "errors": error_summary(log_path, args.max_error_lines),
        "log_tail": tail_lines(log_path, args.tail_lines) if status != "pass" else [],
    }


def github_metadata():
    env = os.environ
    return {
        "repository": env.get("GITHUB_REPOSITORY"),
        "ref": env.get("GITHUB_REF"),
        "ref_name": env.get("GITHUB_REF_NAME"),
        "sha": env.get("GITHUB_SHA"),
        "workflow": env.get("GITHUB_WORKFLOW"),
        "run_id": env.get("GITHUB_RUN_ID"),
        "run_number": env.get("GITHUB_RUN_NUMBER"),
        "run_attempt": env.get("GITHUB_RUN_ATTEMPT"),
        "actor": env.get("GITHUB_ACTOR"),
        "server_url": env.get("GITHUB_SERVER_URL"),
    }


def main():
    parser = argparse.ArgumentParser(description="Audit SBT projects against Chisel 7.")
    parser.add_argument("--repo-dir", default=".")
    parser.add_argument("--output", default="chisel7-support-results.json")
    parser.add_argument("--log-dir", default="chisel7-support-logs")
    parser.add_argument("--projects", nargs="*", default=None)
    parser.add_argument("--exclude", nargs="*", default=["chipyardRoot"])
    parser.add_argument("--project-timeout-minutes", type=int, default=45)
    parser.add_argument("--projects-timeout-minutes", type=int, default=10)
    parser.add_argument("--max-error-lines", type=int, default=40)
    parser.add_argument("--tail-lines", type=int, default=80)
    args = parser.parse_args()

    repo_dir = Path(args.repo_dir).resolve()
    output_path = Path(args.output).resolve()
    log_dir = Path(args.log_dir).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    log_dir.mkdir(parents=True, exist_ok=True)

    projects = args.projects
    if not projects:
        print("Discovering SBT projects under USE_CHISEL7=1", flush=True)
        projects = discover_projects(repo_dir, log_dir, args.projects_timeout_minutes * 60)

    excluded = set(args.exclude or [])
    projects = [project for project in projects if project not in excluded]

    print(f"Auditing {len(projects)} SBT projects under USE_CHISEL7=1", flush=True)
    results = []
    for index, project in enumerate(projects, start=1):
        log_path = log_dir / f"{project}.log"
        print(f"::group::[{index}/{len(projects)}] {project}", flush=True)
        print(f"Running Chisel 7 compile check for {project}", flush=True)
        returncode, duration, timed_out = run_sbt(
            repo_dir,
            f";project {project}; compile",
            log_path,
            args.project_timeout_minutes * 60,
        )
        status = "timeout" if timed_out else "pass" if returncode == 0 else "fail"
        result = project_result(project, status, returncode, duration, log_path, args)
        results.append(result)
        print(f"{project}: {status} in {duration:.1f}s", flush=True)
        if status != "pass":
            for line in result["errors"][:10]:
                print(line, flush=True)
        print("::endgroup::", flush=True)

    summary = {
        "total": len(results),
        "passed": sum(1 for item in results if item["status"] == "pass"),
        "failed": sum(1 for item in results if item["status"] == "fail"),
        "timed_out": sum(1 for item in results if item["status"] == "timeout"),
    }

    document = {
        "schema_version": 1,
        "generated_at": utc_now(),
        "audit_type": "sbt_compile",
        "environment": {
            "use_chisel7": True,
            "versions": read_build_versions(repo_dir),
            "python": sys.version,
        },
        "github": github_metadata(),
        "summary": summary,
        "projects": results,
    }

    output_path.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, sort_keys=True), flush=True)
    print(f"Wrote {output_path}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
