#!/usr/bin/env python3
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar
work = REPORTS / 'instrumental-verdict'
work.mkdir(parents=True, exist_ok=True)
cp = classpath(work, json_jar(), compiled_classes('shared'), android_jar())
subprocess.run([java_tool('javac'), '-cp', cp, '-d', str(work),
                str(SHARED / 'InstrumentalDetector.java'),
                str(SHARED / 'TrackLyricsVerdict.java'),
                str(SHARED / 'RecordingIdentity.java'),
                str(SHARED / 'PlatformLyricSource.java'),
                str(SHARED / 'PlatformLyricRow.java'),
                str(ROOT / 'tests/dev/lyriscope/core/InstrumentalVerdictRegression.java')], check=True)
subprocess.run([java_tool('java'), '-cp', cp, 'dev.lyriscope.core.InstrumentalVerdictRegression'], check=True)
