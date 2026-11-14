#!/usr/bin/env python3
"""Exercise production overlap activation and interlude display logic with synthetic rows."""
from pathlib import Path
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, compiled_classes, classpath, java_tool

source = (SHARED / "LyricsView.java").read_text()

def declaration(signature):
    """The declaration that starts at `signature`: a field up to its `;`, otherwise the whole body.

    Fields have no braces, so the old version walked to the next `{` and swallowed every member in
    between — which is how this fixture ended up defining half of LyricsView and failing to compile.
    """
    assert source.count(signature) == 1, signature
    start = source.index(signature)
    brace = source.index("{", start)
    semicolon = source.find(";", start)
    if semicolon != -1 and semicolon < brace:
        return source[start:semicolon + 1]
    cursor = brace + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]

production = "\n".join(declaration(signature) for signature in (
    "private String footerText",
    "private List<DisplayLine> buildDisplayLines()",
    "private LineVisualState lineVisualState(",
    "private long displayLineContentEndTime(",
    "private InterludeInfo interludeInfoForLine(",
    "private InterludeInfo trailingInterludeInfo(",
    "private long nextRenderableLineStartAfter(",
    "private boolean hasRenderableInterludeMarkerBeforeNextRenderableLine(",
    "private long lastLyricEndTime(",
    "private long maxSyllableEnd(",
    "private boolean isPositionInside(",
    "private String instrumentalKind(",
    "private long cacheIntervalStart(",
    "private long cacheIntervalEnd(",
    "private boolean hasVisibleInterludeOverlap(",
    "private boolean interludesOverlap(",
    "private static final class DisplayLine {",
    "private static final class LineVisualState {",
    "private static final class InterludeInfo {",
))
work = REPORTS / "renderer-overlap"
work.mkdir(parents=True, exist_ok=True)
test = work / "RendererOverlapRegression.java"
test.write_text(Path(__file__).with_name("RendererOverlapRegression.java.in").read_text()
                .replace("// INSERT_PRODUCTION_DECLARATIONS", production))
dependencies = classpath(work, compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work),
                str(SHARED / "LyricsLine.java"), str(test)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies,
                         "dev.lyriscope.core.RendererOverlapRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
