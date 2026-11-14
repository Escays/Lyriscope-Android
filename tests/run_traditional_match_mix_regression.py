#!/usr/bin/env python3
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar
work = REPORTS / 'traditional-match-mix'
work.mkdir(parents=True, exist_ok=True)
cp = classpath(work, json_jar(), compiled_classes('shared'), android_jar())
subprocess.run([java_tool('javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(work),
                str(SHARED / 'HanFold.java'),
                str(SHARED / 'PlatformRecordingMatcher.java'),
                str(SHARED / 'PlatformTranslationSettings.java'),
                str(SHARED / 'PlatformTranslationService.java'),
                str(ROOT / 'tests/dev/lyriscope/core/TraditionalMatchAndMixRegression.java')], check=True)
subprocess.run([java_tool('java'), '-cp', cp, 'dev.lyriscope.core.TraditionalMatchAndMixRegression'], check=True)
