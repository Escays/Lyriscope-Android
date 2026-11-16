#!/usr/bin/env python3
import hashlib
import json
import os
import re
import subprocess
import sys
import textwrap
import urllib.error
import urllib.request
from pathlib import Path


TEMPLATE_PATH = Path(".github/release-notes-template.md")
# Release bodies link back here. Kept in one place so a repository move is one edit.
REPOSITORY_URL = "https://github.com/Escays/Lyriscope-Android"


def run_git(args, allow_fail=False):
    result = subprocess.run(
        ["git", *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if result.returncode != 0 and not allow_fail:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout.strip()


def version_key(tag):
    value = tag[1:] if tag.startswith("v") else tag
    parts = []
    for chunk in re.split(r"[^0-9A-Za-z]+", value):
        if not chunk:
            continue
        if chunk.isdigit():
            parts.append((0, int(chunk)))
        else:
            parts.append((1, chunk.lower()))
    return parts


def previous_tag(current_tag):
    tags = [
        tag
        for tag in run_git(["tag", "--list", "v*"]).splitlines()
        if tag and tag != current_tag
    ]
    older = []
    current_key = version_key(current_tag)
    for tag in tags:
        key = version_key(tag)
        if key < current_key:
            older.append(tag)
    if older:
        return sorted(older, key=version_key)[-1]
    return ""


def commit_range(previous, current):
    if previous:
        return f"{previous}..{current}"
    return current


def resolve_commit(ref):
    output = run_git(["rev-parse", "--verify", f"{ref}^{{commit}}"], allow_fail=True)
    if output:
        return output.splitlines()[0]
    return run_git(["rev-parse", "HEAD"])


def resolve_range_ref(ref):
    if run_git(["rev-parse", "--verify", f"{ref}^{{commit}}"], allow_fail=True):
        return ref
    return "HEAD"


def git_diff_stat(range_spec):
    return run_git(["diff", "--stat", range_spec], allow_fail=True)


def parse_numstat(text):
    files = []
    for line in text.splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        added, deleted, path = parts
        files.append({
            "path": path.strip(),
            "added": int(added) if added.isdigit() else None,
            "deleted": int(deleted) if deleted.isdigit() else None,
        })
    return files


def release_commits(range_spec, current_ref):
    raw = run_git([
        "log",
        "--no-merges",
        "--pretty=format:%h%x1f%s%x1f%b%x1e",
        range_spec,
    ], allow_fail=True)
    commits = []
    for record in raw.split("\x1e"):
        record = record.strip()
        if not record:
            continue
        parts = record.split("\x1f", 2)
        if len(parts) < 2:
            continue
        commit_hash = parts[0].strip()
        subject = parts[1].strip()
        body = parts[2].strip() if len(parts) > 2 else ""
        files = parse_numstat(
            run_git(["show", "--format=", "--numstat", commit_hash], allow_fail=True)
        )
        commits.append({
            "hash": commit_hash,
            "subject": subject,
            "body": body,
            "files": files,
        })
    if commits:
        return commits
    return [{
        "hash": run_git(["rev-parse", "--short", current_ref], allow_fail=True)
        or "HEAD",
        "subject": "Build and release APK assets.",
        "body": "",
        "files": [],
    }]


def commit_evidence(commits):
    blocks = []
    for commit in commits:
        file_lines = []
        for item in commit["files"][:40]:
            if item["added"] is None or item["deleted"] is None:
                stats = "binary"
            else:
                stats = f"+{item['added']}/-{item['deleted']}"
            file_lines.append(f"  - {item['path']} ({stats})")
        if len(commit["files"]) > 40:
            file_lines.append(
                f"  - ... and {len(commit['files']) - 40} more files"
            )
        blocks.append("\n".join([
            f"Commit: {commit['hash']}",
            f"Subject: {commit['subject']}",
            f"Body: {commit['body'][:2000].strip() or '(none)'}",
            "Files:",
            *(file_lines or ["  - (no file stats)"]),
        ]))
    return "\n\n".join(blocks)


def read_gradle_version(gradle_path="app/build.gradle"):
    gradle = Path(gradle_path)
    text = gradle.read_text(encoding="utf-8") if gradle.exists() else ""
    # Both Android products read the checked-in root properties. Resolve relative
    # to the supplied module, so absolute paths also work outside the checkout.
    properties_path = gradle.parent.parent / "gradle.properties"
    properties = {}
    if properties_path.exists():
        for line in properties_path.read_text(encoding="utf-8").splitlines():
            match = re.match(r"^\s*([^#!\s:=]+)\s*[:=]\s*(.*?)\s*$", line)
            if match:
                properties[match.group(1)] = match.group(2)

    def value(field):
        # Keep historical literal build.gradle releases readable. Only resolve
        # explicitly referenced properties; their presence must not override a
        # product that still declares its own literal version.
        declaration = re.search(rf"^\s*{field}\s+(?:=\s*)?([^\r\n]+)", text, re.MULTILINE)
        if not declaration:
            return None
        expression = declaration.group(1).strip()
        reference = re.match(
            r'''providers\s*\.\s*gradleProperty\s*\(\s*(["'])([^"']+)\1\s*\)'''
            r"\s*\.\s*get\s*\(\s*\)", expression)
        if reference:
            key = reference.group(2)
            suffix = expression[reference.end():]
            conversion = r"\s*\.\s*toInteger\s*\(\s*\)" if field == "versionCode" else ""
            if not re.fullmatch(conversion + r"\s*;?\s*(?://.*)?", suffix):
                raise ValueError(f"Unsupported {field} property expression in {gradle}")
            if not properties.get(key):
                raise ValueError(f"Missing {field} property {key} in {properties_path}")
            return properties[key]
        if field == "versionCode":
            literal = re.match(r"([0-9]+)\b", expression)
        else:
            literal = re.match(r'''(["'])([^"']+)\1''', expression)
        return literal.group(1 if field == "versionCode" else 2) if literal else None

    code = value("versionCode")
    name = value("versionName")
    if code is not None and not re.fullmatch(r"[0-9]+", code):
        raise ValueError(f"Invalid versionCode in {gradle}")
    return {
        "versionCode": int(code) if code is not None else None,
        "versionName": name or "",
    }


def apk_product(name):
    normalized = name.lower()
    if normalized.startswith("lyriscope-module-") or normalized.startswith("lyriscope-spotify-"):
        return "spotify-module", "Lyriscope Module (LSPosed)", "spotify-module/build.gradle"
    if normalized.startswith("lyriscope-android-"):
        return "standalone", "Lyriscope", "app/build.gradle"
    return "unknown", "Android APK", None


def apk_identity(name):
    product, label, gradle_path = apk_product(name)
    identity = {"product": product, "label": label, "packageName": "",
                "versionName": "", "versionCode": None}
    if gradle_path is not None:
        gradle = Path(gradle_path)
        text = gradle.read_text(encoding="utf-8") if gradle.exists() else ""
        package_match = re.search(r'applicationId\s+"([^"]+)"', text)
        identity["packageName"] = package_match.group(1) if package_match else ""
        identity.update(read_gradle_version(gradle_path))
    return identity


def apk_assets(apk_dir):
    assets = []
    for path in sorted(Path(apk_dir).glob("*.apk")):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assets.append({
            "name": path.name,
            "path": str(path),
            "size": path.stat().st_size,
            "sha256": digest,
            **apk_identity(path.name),
        })
    return assets


def compare_url(current_tag, previous):
    if previous:
        return f"{REPOSITORY_URL}/compare/{previous}...{current_tag}"
    return f"{REPOSITORY_URL}/commits/{current_tag}"


def markdown_bullets(values):
    items = [str(value).strip() for value in values if str(value).strip()]
    if not items:
        items = ["No notable changes."]
    return "\n".join(f"- {item}" for item in items)


def asset_downloads(assets, lang):
    zh = lang == "zh"
    if not assets:
        return ["APK assets were not found." if lang == "en" else "没有找到 APK 文件。"]
    lines = []
    for asset in assets:
        name = asset["name"]
        product = asset.get("product") or apk_product(name)[0]
        if product == "spotify-module":
            description = ("Spotify 的 LSPosed 模块，需要在模块管理器里启用" if zh
                           else "Spotify module for LSPosed; enable it in your module manager")
        elif product == "standalone":
            description = "独立 Android 应用" if zh else "Standalone Android app"
        else:
            description = "Android 安装包" if zh else "Android APK"
        version_name = asset.get("versionName")
        if version_name:
            version = f"v{version_name}"
            if asset.get("versionCode") is not None:
                version += f" ({asset['versionCode']})"
            description += ("，" if zh else ", ") + version
        if zh:
            if "unsigned" in name:
                note = "未签名的发布包。"
            elif "debug" in name:
                note = "用于安装测试的调试包。"
            else:
                note = "已签名的发布包。"
            lines.append(f"`{name}`: {description}，{note}")
        else:
            if "unsigned" in name:
                note = "Unsigned release APK."
            elif "debug" in name:
                note = "Debug APK for install testing."
            else:
                note = "Signed release APK."
            lines.append(f"`{name}`: {description}. {note}")
    return lines


def checksum_lines(assets):
    if not assets:
        return ["No APK assets."]
    return [
        f"`{asset['name']}`: `{asset['sha256']}`"
        for asset in assets
    ]


def parse_commit_subject(subject):
    match = re.match(
        r"^(?P<type>build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)"
        r"(?:\([^)]+\))?!?:\s*",
        subject,
        re.IGNORECASE,
    )
    if not match:
        return "", subject.strip()
    return match.group("type").lower(), subject[match.end():].strip()


def fallback_category(subject):
    value = subject.lower()
    if re.search(
        r"lyrics?|translation|pronunciation|cultural|provider|open[- ]lyrics|"
        r"instrumental|karaoke|overlay|language",
        value,
    ):
        return "lyrics"
    if re.search(
        r"playback|now playing|vinyl|\blp\b|player|video|scroll|track|spotify dj",
        value,
    ):
        return "playback"
    if re.search(r"\bui\b|dialog|notice|popup|settings?|layout|design", value):
        return "ui"
    return "maintenance"


def fallback_item(commit, language):
    commit_type, text = parse_commit_subject(commit["subject"])
    files = commit["files"]
    additions = sum(item["added"] or 0 for item in files)
    deletions = sum(item["deleted"] or 0 for item in files)
    paths = ", ".join(f"`{item['path']}`" for item in files[:4])
    if len(files) > 4:
        paths += f", +{len(files) - 4}"
    if language == "zh":
        details = (
            f"改动 {len(files)} 个文件，+{additions}/-{deletions} 行。"
            + (f" 主要范围：{paths}。" if paths else "")
        )
    else:
        details = (
            f"Changed {len(files)} files with +{additions}/-{deletions} lines."
            + (f" Main scope: {paths}." if paths else "")
        )
    if commit_type in {"build", "chore", "ci", "docs", "style", "test"}:
        details += (
            " 属于主要功能之外的维护性改动。"
            if language == "zh"
            else " This is a maintenance change outside the main user features."
        )
    return {
        "title": text or commit["subject"],
        "details": details,
        "commits": [commit["hash"]],
    }


def fallback_sections(commits, language):
    labels = {
        "zh": {
            "lyrics": "歌词、AI 与悬浮层",
            "playback": "播放与黑胶模式",
            "ui": "界面与设置",
            "maintenance": "稳定性与维护",
        },
        "en": {
            "lyrics": "Lyrics, AI, and Overlay",
            "playback": "Playback and LP Mode",
            "ui": "UI and Settings",
            "maintenance": "Reliability and Maintenance",
        },
    }
    grouped = {key: [] for key in labels[language]}
    for commit in commits:
        grouped[fallback_category(commit["subject"])].append(
            fallback_item(commit, language)
        )
    return [
        {"title": labels[language][key], "items": grouped[key]}
        for key in labels[language]
        if grouped[key]
    ]


def fallback_content(current_tag, commits):
    count = len(commits)
    return {
        "zh": {
            "summary": (
                f"{current_tag} 整理了上个版本以来的 {count} 项改动，并附上可直接安装的 APK。"
            ),
            "sections": fallback_sections(commits, "zh"),
        },
        "en": {
            "summary": (
                f"{current_tag} contains {count} changes since the previous release, "
                "organized by product area and accompanied by installable APK assets."
            ),
            "sections": fallback_sections(commits, "en"),
        },
    }


def load_template():
    if TEMPLATE_PATH.exists():
        return TEMPLATE_PATH.read_text(encoding="utf-8")
    return textwrap.dedent("""
        # Lyriscope {tag}

        ## 中文
        {zh_summary}

        {zh_sections}

        ## English
        {en_summary}

        {en_sections}

        **Full Changelog**: {compare_url}
    """).strip() + "\n"


def markdown_sections(sections, fallback_title, fallback_text):
    rendered = []
    for section in sections:
        title = str(section.get("title") or "").strip()
        items = section.get("items") or []
        if not title or not items:
            continue
        bullets = []
        for item in items:
            item_title = str(item.get("title") or "").strip()
            details = str(item.get("details") or "").strip()
            if item_title and details:
                bullets.append(f"- **{item_title}**: {details}")
        if bullets:
            rendered.append(f"### {title}\n" + "\n".join(bullets))
    return "\n\n".join(rendered) or f"### {fallback_title}\n- {fallback_text}"


def render_notes(current_tag, previous, version, assets, content):
    zh = content.get("zh") or {}
    en = content.get("en") or {}
    return load_template().format(
        tag=current_tag,
        version_name=version.get("versionName") or "unknown",
        version_code=version.get("versionCode") or "unknown",
        previous_tag=previous or "None",
        compare_url=compare_url(current_tag, previous),
        zh_summary=zh.get("summary") or "已生成发布说明。",
        zh_sections=markdown_sections(
            zh.get("sections") or [],
            "改动",
            "这里是上个版本以来的改动。",
        ),
        zh_downloads=markdown_bullets(asset_downloads(assets, "zh")),
        en_summary=en.get("summary") or "Release notes were generated.",
        en_sections=markdown_sections(
            en.get("sections") or [],
            "Changes",
            "Changes since the previous release are listed here.",
        ),
        en_downloads=markdown_bullets(asset_downloads(assets, "en")),
        checksums=markdown_bullets(checksum_lines(assets)),
    )


def normalize_chat_url(base_url):
    base = (base_url or "").strip().rstrip("/")
    if not base:
        return ""
    if base.endswith("/chat/completions"):
        return base
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def parse_ai_json(text, commits):
    value = (text or "").strip()
    value = re.sub(r"^```(?:json)?\s*", "", value, flags=re.IGNORECASE)
    value = re.sub(r"\s*```$", "", value)
    try:
        data = json.loads(value)
    except json.JSONDecodeError:
        return {}
    if not isinstance(data, dict):
        return {}
    zh = data.get("zh") if isinstance(data.get("zh"), dict) else {}
    en = data.get("en") if isinstance(data.get("en"), dict) else {}
    if not zh or not en:
        return {}
    content = {"zh": normalize_note_section(zh), "en": normalize_note_section(en)}
    if not has_complete_commit_coverage(content, commits):
        return {}
    return content


def normalize_note_item(item):
    if not isinstance(item, dict):
        return {}
    title = str(item.get("title") or "").strip()
    details = str(item.get("details") or "").strip()
    commit_list = item.get("commits")
    commits = (
        [str(value).strip() for value in commit_list if str(value).strip()]
        if isinstance(commit_list, list)
        else []
    )
    if not title or not details or not commits:
        return {}
    return {"title": title, "details": details, "commits": commits}


def normalize_note_section(section):
    if not isinstance(section, dict):
        return {}
    sections = []
    for group in section.get("sections") or []:
        if not isinstance(group, dict):
            continue
        title = str(group.get("title") or "").strip()
        items = []
        for item in group.get("items") or []:
            normalized = normalize_note_item(item)
            if normalized:
                items.append(normalized)
        if title and items:
            sections.append({"title": title, "items": items})
    return {
        "summary": str(section.get("summary") or "").strip(),
        "sections": sections,
    }


def covered_commits(section):
    return [
        commit
        for group in section.get("sections") or []
        for item in group.get("items") or []
        for commit in item.get("commits") or []
    ]


def has_complete_commit_coverage(content, commits):
    expected = [commit["hash"] for commit in commits]
    if not expected:
        return False
    for language in ("zh", "en"):
        actual = covered_commits(content.get(language) or {})
        if len(actual) != len(expected) or set(actual) != set(expected):
            return False
    return True


def ai_release_content(current_tag, previous, version, commits, stat_text, assets):
    api_key = os.environ.get("AI_API_KEY", "").strip()
    api_url = normalize_chat_url(os.environ.get("AI_BASE_URL", ""))
    model = os.environ.get("AI_MODEL", "").strip() or "gpt-4o-mini"
    if not api_key or not api_url:
        return {}

    asset_text = "\n".join(
        f"- {asset['name']} ({asset['size']} bytes, sha256={asset['sha256']})"
        for asset in assets
    )
    prompt = textwrap.dedent(f"""
        You write bilingual GitHub release note content for an Android lyrics app and LSPosed module
        named Lyriscope, which shows lyrics inside Spotify. Return JSON only. Do not return Markdown.

        Current tag: {current_tag}
        Previous tag: {previous or "(none)"}
        Compare URL: {compare_url(current_tag, previous)}
        Android versionName: {version.get("versionName") or "(unknown)"}
        Android versionCode: {version.get("versionCode") or "(unknown)"}

        Output JSON schema:
        {{
          "zh": {{
            "summary": "用两到四句中文概括这个版本",
            "sections": [
              {{
                "title": "按功能划分的中文小标题",
                "items": [
                  {{
                    "title": "简短的中文改动标题",
                    "details": "用一到三句中文说清行为、触发条件和用户会感知到的变化。",
                    "commits": ["short commit hash"]
                  }}
                ]
              }}
            ]
          }},
          "en": {{
            "summary": "Equivalent English summary in two to four sentences",
            "sections": [
              {{
                "title": "Equivalent English product-area heading",
                "items": [
                  {{
                    "title": "Short English change title",
                    "details": "One to three detailed English sentences describing behavior, conditions, and user impact.",
                    "commits": ["same short commit hash"]
                  }}
                ]
              }}
            ]
          }}
        }}

        Requirements:
        - Write both Chinese and English.
        - Keep the Chinese and English sections semantically equivalent.
        - Compare this release against the previous tag.
        - Create descriptive product-area sections such as Lyrics and AI, Playback and Vinyl Mode, UI and Settings, or Reliability. Use only sections supported by the changes.
        - Cover every supplied commit hash exactly once in Chinese and exactly once in English. Equivalent items in both languages must list the same hashes.
        - Combine commits only when they are tightly related parts of one user-facing change. Do not cap the number of sections or items.
        - Make every details field explain what changed, when it matters, and what the user will notice. Include defaults, compatibility behavior, localization, cache handling, and edge cases when supported.
        - Put user-facing changes first and maintenance changes last.
        - The template already describes each APK and its signature state, so do not duplicate download instructions inside the change sections.
        - Do not invent changes not supported by the commit evidence.
        - Do not mention secrets, private URLs, or internal token endpoints.
        - Do not include a Full Changelog link; the template adds it.

        Commit evidence:
        {commit_evidence(commits)}

        Aggregate diff stat:
        {stat_text or "(no diff stat)"}

        APK assets:
        {asset_text or "(no APK assets)"}
    """).strip()
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "Generate accurate, detailed, and complete release notes from "
                    "git evidence. Never omit a supplied commit."
                ),
            },
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.15,
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "Lyriscope-ReleaseBot/1.0",
        },
        method="POST",
    )
    try:
        timeout_seconds = int(os.environ.get("AI_TIMEOUT_SECONDS", "300"))
    except ValueError:
        timeout_seconds = 300
    timeout_seconds = max(60, min(timeout_seconds, 900))

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        try:
            body = exc.read().decode("utf-8", errors="replace").strip()
        except Exception:
            body = ""
        if len(body) > 1200:
            body = body[:1200] + "...(truncated)"
        detail = f"HTTP {exc.code}: {exc.reason or ''}".strip()
        if body:
            detail += f" / {body}"
        print(f"AI release note generation failed: {detail}", file=sys.stderr)
        return {}
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"AI release note generation failed: {exc}", file=sys.stderr)
        return {}
    choices = data.get("choices") or []
    if not choices:
        return {}
    message = choices[0].get("message") or {}
    return parse_ai_json(message.get("content") or "", commits)


def main():
    current_tag = os.environ.get("RELEASE_TAG", "").strip() or run_git(["describe", "--tags", "--exact-match"], allow_fail=True)
    if not current_tag:
        current_tag = run_git(["rev-parse", "--short", "HEAD"])
    current_sha = resolve_commit(current_tag)
    previous = previous_tag(current_tag)
    current_ref = resolve_range_ref(current_tag)
    range_spec = commit_range(previous, current_ref)
    stat_text = git_diff_stat(range_spec)
    commits = release_commits(range_spec, current_ref)
    version = read_gradle_version()
    assets = apk_assets(os.environ.get("APK_DIR", "release-apks"))

    content = ai_release_content(
        current_tag, previous, version, commits, stat_text, assets
    )
    if not content:
        content = fallback_content(current_tag, commits)
    notes = render_notes(current_tag, previous, version, assets, content)

    metadata = {
        "tag": current_tag,
        "commit": current_sha,
        "previousTag": previous,
        "versionName": version.get("versionName"),
        "versionCode": version.get("versionCode"),
        "compareUrl": (
            compare_url(current_tag, previous)
        ),
        "apks": assets,
        "commitCount": len(commits),
        "coveredCommits": [commit["hash"] for commit in commits],
    }

    out_dir = Path(os.environ.get("RELEASE_METADATA_DIR", "release-metadata"))
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "release-notes.md").write_text(notes.strip() + "\n", encoding="utf-8")
    # One manifest per release, named after the release rather than a product: it describes
    # every APK in it, and the in-app updater only looks for the '-version.json' suffix.
    manifest = out_dir / f"Lyriscope-{current_tag}-version.json"
    manifest.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"previous_tag={previous}")
    print(f"notes={out_dir / 'release-notes.md'}")
    print(f"version_file={manifest}")


if __name__ == "__main__":
    main()
