#!/usr/bin/env python3
"""Generate Starlight Markdown pages from the existing Sphinx/RST docs."""

from __future__ import annotations

import argparse
import html
import json
import os
import posixpath
import re
import shutil
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit


DOCS_ROOT = Path(__file__).resolve().parents[1]
SPHINX_BUILD = DOCS_ROOT / "_build" / "starlight-sphinx-html"
CONTENT_ROOT = DOCS_ROOT / "src" / "content" / "docs"
PUBLIC_ROOT = DOCS_ROOT / "public"
SPHINX_PUBLIC_ROOT = PUBLIC_ROOT / "sphinx"
MANIFEST_NAME = ".sphinx-generated.json"
LEGACY_REDIRECT_MANIFEST = DOCS_ROOT / "_build" / "starlight-legacy-redirects.json"
LEGACY_ROUTE_MANIFEST = DOCS_ROOT / "_build" / "starlight-legacy-routes.json"
VOID_TAGS = {
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target-subdir", default="", help="Subdirectory under src/content/docs to write into.")
    parser.add_argument("--asset-subdir", default="current", help="Subdirectory under public/sphinx to write assets into.")
    parser.add_argument("--skip-sphinx", action="store_true", help="Reuse the existing Sphinx HTML build.")
    args = parser.parse_args()

    target_root = CONTENT_ROOT / args.target_subdir
    asset_prefix = f"sphinx/{args.asset_subdir}"

    if not args.skip_sphinx:
        build_sphinx()

    copy_assets(args.asset_subdir)
    clean_generated(target_root)
    generated = convert_pages(target_root, asset_prefix)
    write_manifest(target_root, generated)
    redirect_count = write_legacy_redirects(generated, args.target_subdir)

    print(
        f"Generated {len(generated)} Starlight pages in {target_root.relative_to(DOCS_ROOT)} "
        f"and {redirect_count} legacy redirects"
    )
    return 0


def build_sphinx() -> None:
    env = os.environ.copy()
    env.setdefault("SKIP_URL_CHECK", "1")
    SPHINX_BUILD.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [sys.executable, "-m", "sphinx", "-b", "html", str(DOCS_ROOT), str(SPHINX_BUILD), "-q"],
        cwd=DOCS_ROOT,
        env=env,
        check=True,
    )


def copy_assets(asset_subdir: str) -> None:
    dest = SPHINX_PUBLIC_ROOT / asset_subdir
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)
    for name in ("_static", "_images"):
        src = SPHINX_BUILD / name
        if src.exists():
            shutil.copytree(src, dest / name)


def clean_generated(target_root: Path) -> None:
    manifest = target_root / MANIFEST_NAME
    if not manifest.exists():
        return
    for rel in json.loads(manifest.read_text(encoding="utf-8")):
        path = target_root / rel
        if path.exists():
            path.unlink()
    for path in sorted(target_root.rglob("*"), reverse=True):
        if path.is_dir() and not any(path.iterdir()):
            path.rmdir()


def convert_pages(target_root: Path, asset_prefix: str) -> list[str]:
    generated: list[str] = []
    sidebar_orders = collect_sidebar_orders()
    for html_path in sorted(SPHINX_BUILD.rglob("*.html")):
        rel_html = html_path.relative_to(SPHINX_BUILD)
        if rel_html.parts[0].startswith("_") or rel_html.name in {"genindex.html", "py-modindex.html", "search.html"}:
            continue

        page = html_path.read_text(encoding="utf-8")
        body = extract_article_body(page, html_path)
        body = rewrite_links(body, rel_html.as_posix(), asset_prefix)
        body = rewrite_code_blocks(body)
        body = rewrite_toctree_labels(body)
        title = extract_title(body, page, rel_html)
        body = rewrite_headings(body)

        out_rel = rel_html.with_suffix(".md")
        out_path = target_root / out_rel
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(
            render_markdown(title, body, sidebar_orders.get(rel_html.as_posix())),
            encoding="utf-8",
        )
        generated.append(out_rel.as_posix())
    return generated


def collect_sidebar_orders() -> dict[str, int]:
    """Map section pages to their order in each directory index's rendered toctree."""
    orders: dict[str, int] = {}
    toctree_link_re = re.compile(
        r"<li\b[^>]*\bclass=[\"'][^\"']*\btoctree-l1\b[^\"']*[\"'][^>]*>"
        r"\s*<a\b[^>]*\bhref=(?P<quote>[\"'])(?P<href>.*?)(?P=quote)",
        re.IGNORECASE | re.DOTALL,
    )

    for index_path in sorted(SPHINX_BUILD.rglob("index.html")):
        rel_index = index_path.relative_to(SPHINX_BUILD)
        if rel_index.as_posix() == "index.html":
            continue

        # Keep each directory landing page first so the custom Starlight
        # sidebar can promote it to the clickable dropdown label.
        orders[rel_index.as_posix()] = 0

        page = index_path.read_text(encoding="utf-8")
        body = extract_article_body(page, index_path)
        current_dir = rel_index.parent.as_posix()
        seen: set[str] = set()
        position = 1

        for match in toctree_link_re.finditer(body):
            href = html.unescape(match.group("href"))
            split = urlsplit(href)
            if split.scheme or split.netloc or split.path.startswith("/") or not split.path:
                continue

            normalized = posixpath.normpath(posixpath.join(current_dir, split.path))
            if not normalized.endswith(".html") or normalized in seen:
                continue

            seen.add(normalized)
            orders[normalized] = position
            position += 1

    return orders


def extract_article_body(page: str, html_path: Path) -> str:
    match = re.search(r"<div\s+itemprop=[\"']articleBody[\"'][^>]*>", page)
    if not match:
        raise RuntimeError(f"Could not find Sphinx article body in {html_path}")

    depth = 1
    pos = match.end()
    tag_re = re.compile(r"<!--.*?-->|</?([a-zA-Z][\w:-]*)(?:\s[^<>]*?)?>", re.DOTALL)
    for tag_match in tag_re.finditer(page, pos):
        token = tag_match.group(0)
        tag = (tag_match.group(1) or "").lower()
        if not tag:
            continue
        if token.startswith("</"):
            if tag == "div":
                depth -= 1
                if depth == 0:
                    return page[pos : tag_match.start()].strip()
        elif tag == "div" and not token.endswith("/>"):
            depth += 1

    raise RuntimeError(f"Could not find end of Sphinx article body in {html_path}")


def extract_title(body: str, page: str, rel_html: Path) -> str:
    h1 = re.search(r"<h1[^>]*>(.*?)</h1>", body, re.DOTALL)
    if h1:
        return strip_section_number(clean_text(h1.group(1)))
    title = re.search(r"<title[^>]*>(.*?)</title>", page, re.DOTALL)
    if title:
        page_title = clean_text(title.group(1).split(" — ")[0].split(" &mdash; ")[0])
        return strip_section_number(page_title)
    return rel_html.with_suffix("").name.replace("-", " ")


def clean_text(value: str) -> str:
    value = re.sub(r"<a class=[\"']headerlink[\"'].*?</a>", "", value, flags=re.DOTALL)
    value = re.sub(r"<[^>]+>", "", value)
    value = html.unescape(value)
    return " ".join(value.split())


def strip_section_number(value: str) -> str:
    """Remove a Sphinx section-number prefix from a heading or TOC label."""
    return re.sub(r"^(?:\d+\.)+\s+", "", value)


def rewrite_code_blocks(body: str) -> str:
    """Convert Sphinx-highlighted HTML into fences while preserving lexer IDs."""
    code_block_re = re.compile(
        r"<div\s+class=[\"']highlight-(?P<language>[\w+-]+)\s+notranslate[\"']"
        r"(?P<attributes>[^>]*)>"
        r"\s*<div\s+class=[\"']highlight[\"']>\s*"
        r"<pre(?:\s[^>]*)?>(?P<code>.*?)</pre>\s*</div>\s*</div>",
        re.DOTALL,
    )

    def replace(match: re.Match[str]) -> str:
        # Preserve Sphinx's lexer identifier in the generated fence. Starlight's
        # Shiki configuration handles the few identifiers that need aliases.
        language = match.group("language")
        code = re.sub(r"<[^>]+>", "", match.group("code"))
        code = html.unescape(code).removesuffix("\n")
        longest_backtick_run = max((len(run) for run in re.findall(r"`+", code)), default=0)
        fence = "`" * max(3, longest_backtick_run + 1)
        id_match = re.search(r"\bid=[\"'](?P<id>[^\"']+)[\"']", match.group("attributes"))
        anchor = (
            f'<span id="{html.escape(id_match.group("id"), quote=True)}"></span>\n\n'
            if id_match
            else ""
        )
        return f'\n\n{anchor}{fence}{language} frame="code"\n{code}\n{fence}\n\n'

    return code_block_re.sub(replace, body)


def rewrite_toctree_labels(body: str) -> str:
    """Remove Sphinx numbering from links in rendered toctrees."""
    toctree_link_re = re.compile(
        r"(?P<open><li\b[^>]*\bclass=[\"'][^\"']*\btoctree-l\d+\b[^\"']*[\"'][^>]*>"
        r"\s*<a\b[^>]*>)(?P<number>(?:\d+\.)+\s+)",
        re.IGNORECASE,
    )
    return toctree_link_re.sub(r"\g<open>", body)


def rewrite_headings(body: str) -> str:
    heading_re = re.compile(r"<h(?P<level>[1-6])[^>]*>(?P<body>.*?)</h(?P=level)>", re.DOTALL)

    def replace(match: re.Match[str]) -> str:
        level = int(match.group("level"))
        if level == 1:
            # Starlight renders the document title from frontmatter.
            return ""
        title = markdown_heading_text(match.group("body"))
        return f"\n\n{'#' * level} {title}\n\n"

    return heading_re.sub(replace, body)


def markdown_heading_text(value: str) -> str:
    code_spans: list[str] = []

    def replace_code(match: re.Match[str]) -> str:
        token = f"STARLIGHTCODETOKEN{len(code_spans)}"
        code_spans.append(f"`{clean_text(match.group('body'))}`")
        return token

    value = re.sub(r"<code\b[^>]*>(?P<body>.*?)</code>", replace_code, value, flags=re.DOTALL)
    value = strip_section_number(clean_text(value))
    value = re.sub(r"([\\`*{}\[\]<>#+.!_|-])", r"\\\1", value)
    for index, code_span in enumerate(code_spans):
        value = value.replace(f"STARLIGHTCODETOKEN{index}", code_span)
    return value


def render_markdown(title: str, body: str, sidebar_order: int | None = None) -> str:
    frontmatter = {
        "title": title,
        "template": "doc",
    }
    if sidebar_order is not None:
        frontmatter["sidebar"] = {"order": sidebar_order}
    yaml = "\n".join(f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in frontmatter.items())
    return f"---\n{yaml}\n---\n\n<div class=\"sphinx-page\">\n\n{body}\n\n</div>\n"


def rewrite_links(body: str, current_html: str, asset_prefix: str) -> str:
    attr_re = re.compile(r"(?P<attr>href|src)=(?P<quote>[\"'])(?P<url>.*?)(?P=quote)")

    def replace(match: re.Match[str]) -> str:
        url = html.unescape(match.group("url"))
        rewritten = rewrite_url(url, current_html, asset_prefix)
        return f'{match.group("attr")}={match.group("quote")}{html.escape(rewritten, quote=True)}{match.group("quote")}'

    return attr_re.sub(replace, body)


def rewrite_url(url: str, current_html: str, asset_prefix: str) -> str:
    split = urlsplit(url)
    if split.scheme or split.netloc or split.path.startswith("/") or split.path == "":
        return url
    if split.path.startswith("#") or split.path.startswith(("mailto:", "javascript:")):
        return url

    current_dir = posixpath.dirname(current_html)
    normalized = posixpath.normpath(posixpath.join(current_dir, split.path))

    if normalized.startswith("_static/") or normalized.startswith("_images/"):
        current_route = html_path_to_route(current_html)
        asset_route = f"{asset_prefix}/{normalized}"
        rel = posixpath.relpath(asset_route, current_route or ".")
        return urlunsplit(("", "", rel, "", split.fragment))

    if normalized.endswith(".html"):
        route = html_path_to_route(normalized)
        current_route = html_path_to_route(current_html)
        rel = posixpath.relpath(route or ".", current_route or ".")
        if rel == ".":
            rel = "."
        rel = rel.rstrip("/") + "/"
        return urlunsplit(("", "", rel, "", split.fragment))

    return url


def html_path_to_route(path: str) -> str:
    without_ext = path[:-5] if path.endswith(".html") else path
    if without_ext == "index":
        return ""
    if without_ext.endswith("/index"):
        return without_ext[: -len("/index")].lower()
    return without_ext.lower()


def write_manifest(target_root: Path, generated: list[str]) -> None:
    target_root.mkdir(parents=True, exist_ok=True)
    (target_root / MANIFEST_NAME).write_text(json.dumps(generated, indent=2) + "\n", encoding="utf-8")


def write_legacy_redirects(generated: list[str], target_subdir: str) -> int:
    clean_legacy_redirects()
    redirects: list[str] = []
    routes: dict[str, str] = {}

    for generated_page in generated:
        legacy_path = Path(generated_page).with_suffix(".html")
        if legacy_path.as_posix() == "index.html":
            # Astro owns the root index.html, which already serves this route.
            continue

        route = html_path_to_route(legacy_path.as_posix())
        if target_subdir:
            route = posixpath.join(target_subdir.strip("/"), route)

        current_dir = legacy_path.parent.as_posix()
        relative_target = posixpath.relpath(route or ".", current_dir).rstrip("/") + "/"
        output_path = PUBLIC_ROOT / legacy_path
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(render_legacy_redirect(relative_target), encoding="utf-8")
        redirects.append(legacy_path.as_posix())

        legacy_route = "/" + legacy_path.with_suffix("").as_posix()
        routes[legacy_route] = "/" + route.rstrip("/") + "/"

    LEGACY_REDIRECT_MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    LEGACY_REDIRECT_MANIFEST.write_text(json.dumps(redirects, indent=2) + "\n", encoding="utf-8")
    LEGACY_ROUTE_MANIFEST.write_text(json.dumps(routes, indent=2) + "\n", encoding="utf-8")
    return len(redirects) + len(routes)


def clean_legacy_redirects() -> None:
    if not LEGACY_REDIRECT_MANIFEST.exists():
        return

    parent_dirs: set[Path] = set()
    for rel in json.loads(LEGACY_REDIRECT_MANIFEST.read_text(encoding="utf-8")):
        path = PUBLIC_ROOT / rel
        if path.exists():
            path.unlink()
        parent_dirs.update(
            parent for parent in path.parents if parent != PUBLIC_ROOT and PUBLIC_ROOT in parent.parents
        )

    for path in sorted(parent_dirs, key=lambda item: len(item.parts), reverse=True):
        if path.is_dir() and not any(path.iterdir()):
            path.rmdir()


def render_legacy_redirect(relative_target: str) -> str:
    escaped_target = html.escape(relative_target, quote=True)
    javascript_target = json.dumps(relative_target)
    return f"""<!doctype html>
<html lang=\"en\" data-pagefind-ignore>
<head>
  <meta charset=\"utf-8\">
  <meta name=\"robots\" content=\"noindex\">
  <meta http-equiv=\"refresh\" content=\"0; url={escaped_target}\">
  <link rel=\"canonical\" href=\"{escaped_target}\">
  <title>Redirecting...</title>
  <script>
    const target = new URL({javascript_target}, window.location.href);
    target.hash = window.location.hash;
    window.location.replace(target);
  </script>
</head>
<body>
  <p>This page has moved to <a href=\"{escaped_target}\">{escaped_target}</a>.</p>
</body>
</html>
"""


if __name__ == "__main__":
    raise SystemExit(main())
