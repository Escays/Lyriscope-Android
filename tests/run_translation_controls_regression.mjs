// Exercise the production state transitions without launching a device Activity.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = fs.readFileSync(path.join(root, 'shared/src/main/java/dev/lyriscope/core/BaseLyricsActivity.java'), 'utf8');
function method(signature) {
  const start = source.indexOf(signature);
  assert(start >= 0, `Missing ${signature}`);
  let end = source.indexOf('{', start) + 1, depth = 1;
  while (depth) { depth += (source[end] === '{') - (source[end] === '}'); end++; }
  return source.slice(start, end);
}
assert.match(source, /if \(!suppressLanguageRuleEvents\) setCurrentTrackTranslationEnabled\(isChecked\)/);
assert.match(method('private void renderTranslationSettings()'), /setCurrentTrackTranslationEnabled\(checked\)/);
assert.match(method('private void setLyricsResultOnViews('), /if \(!translationEnabled\) result = withoutTranslationRows\(result\)/);
const popup = method('private void showLyricsMetaMenuPopup(');
assert(popup.indexOf('buildLyricsLanguageSettingsPanel(SettingsPalette.ARTWORK)')
  < popup.indexOf('populateSelectedLanguageRule(aiLyricsSettings.snapshot())'), 'bind the newly rebuilt popup, not the old controls');
const panel = method('private LinearLayout buildLyricsLanguageSettingsPanel(');
assert(panel.lastIndexOf('populateSelectedLanguageRule(aiLyricsSettings.snapshot())')
  > panel.indexOf('languageTranslationSwitch.setOnCheckedChangeListener'), 'new controls must be initialized from saved policy');
const methods = ['private void toggleLyricsTranslation()', 'private boolean currentTrackTranslationEnabled()',
  'private void setCurrentTrackTranslationEnabled(', 'private void onTranslationPolicyChanged()',
  'private void populateSelectedLanguageRule('].map(method).join('\n');
const fixture = `
public class TranslationControlsRegression {
  static class Toggle {
    boolean checked; float alpha;
    void setChecked(boolean value) { checked = value; }
    void setAlpha(float value) { alpha = value; }
  }
  static class PlatformPolicy {
    Boolean track; boolean global = true;
    boolean enabledForTrack(String key) { return track == null ? global : track; }
    void setTrackEnabled(String key, boolean value) { track = value; }
  }
  static class AiLyricsSettings {
    static class LanguageRule {
      boolean translationEnabled, pronunciationEnabled = true; String targetLang = "zh";
    }
    static class Snapshot {
      LanguageRule rule = new LanguageRule();
      LanguageRule ruleForSource(String source) { return rule; }
    }
    Snapshot snapshot = new Snapshot();
    Snapshot snapshot() { return snapshot; }
    void setLanguageRule(String source, boolean translate, boolean pronounce, String target) {
      snapshot.rule.translationEnabled = translate;
      snapshot.rule.pronunciationEnabled = pronounce; snapshot.rule.targetLang = target;
    }
  }
  static class Popup { boolean isShowing() { return true; } }
  PlatformPolicy platformTranslationSettings = new PlatformPolicy();
  AiLyricsSettings aiLyricsSettings = new AiLyricsSettings();
  Toggle languageTranslationSwitch = new Toggle(), languagePronunciationSwitch = new Toggle();
  Toggle lyricsTranslationButton = new Toggle(); Popup translationSettingsPopup = new Popup();
  boolean suppressLanguageRuleEvents; String currentLyricsKey = "song-a";
  Object currentTrack = null, currentLyricsResult = new Object(), rendered;
  int reloads; boolean sheetChecked;
  String effectiveSelectedSourceLang() { return "ja"; }
  void updateSelectedLanguageRuleStatusFromUi() {}
  void updateLyricsLanguageButtonState() {}
  void renderTranslationSettings() { sheetChecked = currentTrackTranslationEnabled(); }
  void setLyricsResultOnViews(Object result) { rendered = result; }
  long currentLyricsPlaybackPosition(Object track) { return 0; }
  void updateLyricPreview(long position) {}
  void requestAiLyrics(boolean clearCache) { if (clearCache) throw new AssertionError("cache discarded"); reloads++; }
  ${methods}
  static void check(boolean condition) { if (!condition) throw new AssertionError(); }
  void checkState(boolean enabled) {
    check(languageTranslationSwitch.checked == enabled);
    check(sheetChecked == enabled);
    check((lyricsTranslationButton.alpha == 1f) == enabled);
    check(aiLyricsSettings.snapshot.rule.translationEnabled == enabled);
    check(aiLyricsSettings.snapshot.rule.pronunciationEnabled);
    check("zh".equals(aiLyricsSettings.snapshot.rule.targetLang));
    check(rendered == currentLyricsResult);
  }
  public static void main(String[] args) {
    var test = new TranslationControlsRegression();
    // Reproduce the report: song policy on, AI language rule off.
    test.populateSelectedLanguageRule(test.aiLyricsSettings.snapshot());
    check(test.languageTranslationSwitch.checked);
    test.setCurrentTrackTranslationEnabled(false); test.checkState(false); check(test.reloads == 0);
    test.setCurrentTrackTranslationEnabled(true); test.checkState(true); check(test.reloads == 1);
    test.toggleLyricsTranslation(); test.checkState(false);
    test.platformTranslationSettings.track = null; test.platformTranslationSettings.global = true;
    test.onTranslationPolicyChanged(); test.checkState(true);
    // Each palette rebuild creates fresh, initially unchecked native controls.
    for (boolean enabled : new boolean[]{true, false, true}) {
      test.platformTranslationSettings.track = enabled;
      test.languageTranslationSwitch = new Toggle();
      test.languagePronunciationSwitch = new Toggle();
      test.populateSelectedLanguageRule(test.aiLyricsSettings.snapshot());
      check(test.languageTranslationSwitch.checked == enabled);
      check(test.languagePronunciationSwitch.checked);
    }
    check(!test.suppressLanguageRuleEvents);
    System.out.println("Translation control state regression passed");
  }
}`;
const work = path.join(root, 'build', 'translation-controls-regression');
fs.mkdirSync(work, { recursive: true });
const file = path.join(work, 'TranslationControlsRegression.java');
fs.writeFileSync(file, fixture);
for (const [tool, args] of [['javac', ['-encoding', 'UTF-8', '-d', work, file]],
  ['java', ['-cp', work, 'TranslationControlsRegression']]]) {
  const binary = process.platform === 'win32' ? `${tool}.exe` : tool;
  const executable = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', binary) : tool;
  const result = spawnSync(executable, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `${tool} failed`);
}
