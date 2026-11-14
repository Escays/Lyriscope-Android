#!/usr/bin/env python3
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar
work = REPORTS / 'provider-type-gate'
work.mkdir(parents=True, exist_ok=True)
cp = classpath(work, json_jar(), compiled_classes('shared'), android_jar())
subprocess.run([java_tool('javac'), '-cp', cp, '-d', str(work), str(SHARED / 'LyricsProviderSettings.java'), str(SHARED / 'LyricsProviderSelectionPlan.java'), str(ROOT / 'tests/dev/lyriscope/core/ProviderTypeGateRegression.java')], check=True)
subprocess.run([java_tool('java'), '-cp', cp, 'dev.lyriscope.core.ProviderTypeGateRegression'], check=True)
