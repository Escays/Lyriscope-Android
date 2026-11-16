package dev.lyriscope.core;

import java.util.Map;

/** Shared PC/mobile settings vocabulary; values must match AppI18nStrings on iOS. */
final class SettingsExperienceTranslations {
    private SettingsExperienceTranslations() {}
    static void apply(String language, Map<String, String> target) {
        String[] values = switch (language) {
            case "ko" -> new String[]{"일반", "가사 제공자", "AI 제공자", "외관", "동작", "고급", "설정 검색...", "검색 결과가 없습니다", "설정은 자동으로 저장됩니다. 다시 생성하면 현재 곡에 적용됩니다."};
            case "en" -> new String[]{"General", "Lyrics Providers", "AI Providers", "Appearance", "Behavior", "Advanced", "Search settings...", "No results found", "Settings save automatically. Regenerate to apply them to the current song."};
            case "zh-CN" -> new String[]{"常规", "歌词提供商", "AI 提供商", "外观", "行为", "高级", "搜索设置...", "无搜索结果", "设置会自动保存。重新生成以应用到当前歌曲。"};
            case "zh-TW" -> new String[]{"一般", "歌詞提供者", "AI 提供者", "外觀", "動作", "進階", "搜尋設定...", "找不到搜尋結果", "設定會自動儲存。重新產生以套用至目前歌曲。"};
            case "ja" -> new String[]{"一般", "歌詞プロバイダー", "AIプロバイダー", "外観", "動作", "詳細", "設定を検索...", "検索結果がありません", "設定は自動保存されます。現在の曲に適用するには再生成してください。"};
            case "hi" -> new String[]{"सामान्य", "गीत प्रदाता", "AI प्रदाता", "दिखावट", "व्यवहार", "उन्नत", "सेटिंग्स खोजें...", "कोई परिणाम नहीं मिला", "सेटिंग अपने आप सहेजी जाती हैं। मौजूदा गीत पर लागू करने के लिए दोबारा बनाएँ।"};
            case "es" -> new String[]{"General", "Proveedores de letras", "Proveedores de IA", "Apariencia", "Comportamiento", "Avanzado", "Buscar configuración...", "No hay resultados", "Los ajustes se guardan automáticamente. Regenera para aplicarlos a la canción actual."};
            case "fr" -> new String[]{"Général", "Fournisseurs de paroles", "Fournisseurs d'IA", "Apparence", "Comportement", "Avancé", "Rechercher des paramètres...", "Aucun résultat", "Les réglages sont enregistrés automatiquement. Régénérez pour les appliquer au morceau actuel."};
            case "ar" -> new String[]{"عام", "موفرو الكلمات", "موفرو الذكاء الاصطناعي", "المظهر", "السلوك", "متقدم", "البحث في الإعدادات...", "لا توجد نتائج", "تُحفظ الإعدادات تلقائيًا. أعد الإنشاء لتطبيقها على الأغنية الحالية."};
            case "fa" -> new String[]{"عمومی", "ارائه‌دهندگان متن آهنگ", "ارائه‌دهندگان هوش مصنوعی", "ظاهر", "رفتار", "پیشرفته", "جستجوی تنظیمات...", "نتیجه‌ای یافت نشد", "تنظیمات خودکار ذخیره می‌شوند. برای اعمال روی آهنگ فعلی، دوباره تولید کنید."};
            case "de" -> new String[]{"Allgemein", "Songtext-Anbieter", "KI-Anbieter", "Aussehen", "Verhalten", "Erweitert", "Einstellungen suchen...", "Keine Ergebnisse", "Einstellungen werden automatisch gespeichert. Für den aktuellen Titel erneut generieren."};
            case "ru" -> new String[]{"Общие", "Провайдеры текстов", "Провайдеры ИИ", "Внешний вид", "Поведение", "Дополнительно", "Поиск настроек...", "Нет результатов", "Настройки сохраняются автоматически. Повторите генерацию для текущей песни."};
            case "sv" -> new String[]{"Allmänt", "Textleverantörer", "AI-leverantörer", "Utseende", "Beteende", "Avancerat", "Sök i inställningar...", "Inga resultat hittades", "Inställningar sparas automatiskt. Generera på nytt för den aktuella låten."};
            case "pt" -> new String[]{"Geral", "Provedores de Letras", "Provedores de IA", "Aparência", "Comportamento", "Avançado", "Pesquisar configurações...", "Nenhum resultado encontrado", "As configurações são salvas automaticamente. Gere novamente para aplicá-las à música atual."};
            case "bn" -> new String[]{"সাধারণ", "লিরিক্স প্রদানকারী", "এআই প্রদানকারী", "চেহারা", "আচরণ", "উন্নত", "সেটিংস খুঁজুন...", "কোনো ফলাফল পাওয়া যায়নি", "সেটিংস স্বয়ংক্রিয়ভাবে সংরক্ষিত হয়। বর্তমান গানে প্রয়োগ করতে আবার তৈরি করুন।"};
            case "cs" -> new String[]{"Obecné", "Poskytovatelé textů", "Poskytovatelé AI", "Vzhled", "Chování", "Pokročilé", "Hledat nastavení...", "Žádné výsledky nenalezeny", "Nastavení se ukládají automaticky. Pro aktuální skladbu spusťte nové generování."};
            case "it" -> new String[]{"Generale", "Provider di testi", "Provider AI", "Aspetto", "Comportamento", "Avanzate", "Cerca impostazioni...", "Nessun risultato", "Le impostazioni si salvano automaticamente. Rigenera per applicarle al brano attuale."};
            case "th" -> new String[]{"ทั่วไป", "ผู้ให้บริการเนื้อเพลง", "ผู้ให้บริการ AI", "รูปลักษณ์", "การทำงาน", "ขั้นสูง", "ค้นหาการตั้งค่า...", "ไม่พบผลลัพธ์", "บันทึกการตั้งค่าอัตโนมัติ สร้างใหม่เพื่อใช้กับเพลงปัจจุบัน"};
            case "vi" -> new String[]{"Chung", "Nhà cung cấp lời bài hát", "Nhà cung cấp AI", "Giao diện", "Hành vi", "Nâng cao", "Tìm kiếm cài đặt...", "Không tìm thấy kết quả", "Cài đặt được lưu tự động. Tạo lại để áp dụng cho bài hát hiện tại."};
            case "id" -> new String[]{"Umum", "Penyedia Lirik", "Penyedia AI", "Tampilan", "Perilaku", "Lanjutan", "Cari pengaturan...", "Tidak ada hasil", "Pengaturan tersimpan otomatis. Buat ulang untuk menerapkannya pada lagu saat ini."};
            case "ms" -> new String[]{"Umum", "Penyedia Lirik", "Penyedia AI", "Penampilan", "Gelagat", "Lanjutan", "Cari tetapan...", "Tiada keputusan carian", "Tetapan disimpan secara automatik. Jana semula untuk lagu semasa."};
            case "tr" -> new String[]{"Genel", "Söz Sağlayıcıları", "Yapay Zeka Sağlayıcıları", "Görünüm", "Davranış", "Gelişmiş", "Ayarlarda ara...", "Sonuç bulunamadı", "Ayarlar otomatik kaydedilir. Geçerli şarkıya uygulamak için yeniden oluşturun."};
            default -> null;
        };
        if (values == null) return;
        String[] keys = {"tab.general", "tab.providers", "tab.ai", "tab.appearance", "tab.lyrics", "tab.system", "settings.search", "settings.no_results", "settings.ai_autosave"};
        for (int i = 0; i < keys.length; i++) target.put(keys[i], values[i]);
        target.put("tab.current_track", switch (language) {
            case "ko" -> "현재 재생 곡 설정";
            case "en" -> "Current track settings";
            case "zh-CN" -> "当前播放歌曲设置";
            case "zh-TW" -> "目前播放歌曲設定";
            case "ja" -> "再生中の曲の設定";
            case "hi" -> "वर्तमान गीत की सेटिंग्स";
            case "es" -> "Ajustes de la canción actual";
            case "fr" -> "Réglages du morceau en cours";
            case "ar" -> "إعدادات الأغنية الحالية";
            case "fa" -> "تنظیمات آهنگ در حال پخش";
            case "de" -> "Einstellungen des aktuellen Titels";
            case "ru" -> "Настройки текущего трека";
            case "sv" -> "Inställningar för aktuell låt";
            case "pt" -> "Configurações da música atual";
            case "bn" -> "বর্তমান গানের সেটিংস";
            case "cs" -> "Nastavení aktuální skladby";
            case "it" -> "Impostazioni del brano in riproduzione";
            case "th" -> "การตั้งค่าเพลงที่กำลังเล่น";
            case "vi" -> "Cài đặt bài hát đang phát";
            case "id" -> "Pengaturan lagu yang sedang diputar";
            case "ms" -> "Tetapan lagu yang sedang dimainkan";
            case "tr" -> "Çalan şarkının ayarları";
            default -> "Current track settings";
        });
        // The home groups are named per language, but a language that has no wording of its own must
        // still end up with text: an earlier version copied whatever key it hoped was translated, and
        // a missing one wrote a blank string that killed the module at startup.
        String[] englishHome = {"Current song", "Lyrics", "Display", "Playback", "Application"};
        String[] home = switch (language) {
            case "zh-CN" -> new String[]{"当前歌曲", "歌词", "外观", "播放", "其他"};
            case "zh-TW" -> new String[]{"目前歌曲", "歌詞", "外觀", "播放", "其他"};
            case "ko" -> new String[]{"현재 곡", "가사", "화면", "재생", "앱"};
            case "ja" -> new String[]{"再生中の曲", "歌詞", "表示", "再生", "アプリ"};
            case "en" -> englishHome;
            default -> null;
        };
        String[] homeKeys = {"home.track", "home.lyrics", "home.display", "home.playback", "home.app"};
        for (int i = 0; i < homeKeys.length; i++) {
            target.put(homeKeys[i], home != null && i < home.length ? home[i] : englishHome[i]);
        }

        // Page names of the shorter, more numerous menus. Languages without their own wording keep the
        // English pair here; a missing key falls back to the English table rather than a blank label.
        String[][] pages = switch (language) {
            case "zh-CN" -> new String[][]{
                    {"tab.providers", "歌词来源"}, {"tab.general", "翻译与注音"},
                    {"tab.ai", "AI 服务"}, {"tab.enhance", "歌词增强"},
                    {"tab.style", "歌词样式"}, {"tab.background", "背景与效果"},
                    {"tab.screen", "歌词页元素"}, {"tab.vinyl", "LP 唱机"},
                    {"tab.player", "播放与手势"}, {"tab.system", "语言与地区"},
                    {"tab.data", "调试与数据"}, {"tab.credits", "关于与鸣谢"},
                    {"settings.section.lyrics_page", "歌词页布局"},
                    {"settings.section.lyrics_page_desc", "正文边距与播放按钮的位置。"}};
            case "zh-TW" -> new String[][]{
                    {"tab.providers", "歌詞來源"}, {"tab.general", "翻譯與注音"},
                    {"tab.ai", "AI 服務"}, {"tab.enhance", "歌詞增強"},
                    {"tab.style", "歌詞樣式"}, {"tab.background", "背景與效果"},
                    {"tab.screen", "歌詞頁元素"}, {"tab.vinyl", "LP 唱機"},
                    {"tab.player", "播放與手勢"}, {"tab.system", "語言與地區"},
                    {"tab.data", "偵錯與資料"}, {"tab.credits", "關於與鳴謝"},
                    {"settings.section.lyrics_page", "歌詞頁版面"},
                    {"settings.section.lyrics_page_desc", "內文邊距與播放按鈕的位置。"}};
            case "ko" -> new String[][]{
                    {"tab.providers", "가사 소스"}, {"tab.general", "번역과 발음"},
                    {"tab.ai", "AI 서비스"}, {"tab.enhance", "가사 확장"},
                    {"tab.style", "가사 스타일"}, {"tab.background", "배경과 효과"},
                    {"tab.screen", "가사 화면 요소"}, {"tab.vinyl", "LP 플레이어"},
                    {"tab.player", "재생과 제스처"}, {"tab.system", "일반"},
                    {"tab.data", "권한 및 데이터"}, {"tab.credits", "정보 및 크레딧"},
                    {"settings.section.lyrics_page", "가사 화면 배치"},
                    {"settings.section.lyrics_page_desc", "본문 여백과 재생 버튼 위치."}};
            case "ja" -> new String[][]{
                    {"tab.providers", "歌詞ソース"}, {"tab.general", "翻訳と発音"},
                    {"tab.ai", "AIサービス"}, {"tab.enhance", "歌詞の拡張"},
                    {"tab.style", "歌詞スタイル"}, {"tab.background", "背景と効果"},
                    {"tab.screen", "歌詞ページ要素"}, {"tab.vinyl", "LPプレーヤー"},
                    {"tab.player", "再生とジェスチャー"}, {"tab.system", "一般"},
                    {"tab.data", "権限とデータ"}, {"tab.credits", "このアプリとクレジット"},
                    {"settings.section.lyrics_page", "歌詞ページの配置"},
                    {"settings.section.lyrics_page_desc", "本文の余白と再生ボタンの位置。"}};
            case "en" -> new String[][]{
                    {"tab.providers", "Lyrics sources"}, {"tab.general", "Translation & pronunciation"},
                    {"tab.ai", "AI services"}, {"tab.enhance", "Lyrics extras"},
                    {"tab.style", "Lyrics style"}, {"tab.background", "Background & effects"},
                    {"tab.screen", "Page elements"}, {"tab.vinyl", "LP player"},
                    {"tab.player", "Playback & gestures"}, {"tab.system", "General"},
                    {"tab.data", "Permissions & data"}, {"tab.credits", "About & credits"},
                    {"settings.section.lyrics_page", "Page layout"},
                    {"settings.section.lyrics_page_desc", "Text margin and where the play button sits."}};
            default -> null;
        };
        if (pages != null) {
            for (String[] pair : pages) target.put(pair[0], pair[1]);
        }
    }

}
