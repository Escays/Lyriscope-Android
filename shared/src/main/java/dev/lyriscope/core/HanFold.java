package dev.lyriscope.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Folds traditional Chinese into simplified so two spellings of one word compare equal.
 *
 * <p>A reader who types 王菲《臉》 into the manual search was shown "30%" for the recording that is
 * actually playing: the catalogue answers "脸", the comparison saw two unrelated characters, and the
 * title came out at 0 — which also triggers the "title and artist disagree" cap, so every result in the
 * list landed on exactly the same score. The same happens in the platform translation stage, where a
 * traditional title finds the recording and then refuses to match it.
 *
 * <p>Only comparison uses this: display text is never rewritten. Single-character titles make it
 * essential — there is no bigram to partly match, one folded character is the whole title.
 */
final class HanFold {
    /**
     * Traditional and simplified side by side, space separated ("愛爱" is 愛 → 爱). Deliberately limited
     * to characters that actually differ; an unmapped character stays as it is, which can only cost a
     * match, never invent one.
     */
    private static final String PAIRS =
            "愛爱 礙碍 襖袄 罷罢 擺摆 敗败 辦办 幫帮 寶宝 飽饱 報报 幣币 畢毕 閉闭 邊边 編编 變变 標标 別别 賓宾 補补 "
            + "財财 參参 慘惨 燦灿 蒼苍 側侧 層层 產产 顫颤 場场 嘗尝 長长 償偿 腸肠 廠厂 暢畅 車车 徹彻 塵尘 陳陈 稱称 "
            + "誠诚 遲迟 齒齿 沖冲 蟲虫 寵宠 籌筹 綢绸 醜丑 廚厨 鋤锄 儲储 觸触 處处 傳传 創创 純纯 詞词 賜赐 聰聪 "
            + "從从 叢丛 錯错 達达 帶带 貸贷 擔担 單单 膽胆 彈弹 當当 擋挡 黨党 蕩荡 檔档 島岛 導导 盜盗 燈灯 鄧邓 "
            + "敵敌 遞递 點点 墊垫 電电 釣钓 調调 頂顶 訂订 東东 動动 凍冻 鬥斗 獨独 讀读 賭赌 斷断 隊队 對对 噸吨 "
            + "頓顿 奪夺 鵝鹅 額额 惡恶 餓饿 兒儿 爾尔 發发 罰罚 髮发 範范 飯饭 訪访 飛飞 廢废 費费 紛纷 墳坟 奮奋 "
            + "憤愤 豐丰 楓枫 鋒锋 風风 瘋疯 縫缝 諷讽 鳳凤 膚肤 撫抚 輔辅 復复 複复 負负 婦妇 該该 蓋盖 幹干 趕赶 "
            + "剛刚 鋼钢 綱纲 個个 給给 宮宫 鞏巩 貢贡 溝沟 構构 購购 夠够 穀谷 顧顾 關关 觀观 館馆 慣惯 貫贯 廣广 "
            + "規规 龜龟 歸归 軌轨 貴贵 滾滚 鍋锅 國国 過过 駭骇 韓韩 漢汉 號号 賀贺 橫横 轟轰 鴻鸿 紅红 後后 壺壶 "
            + "護护 戶户 華华 畫画 劃划 話话 懷怀 壞坏 歡欢 環环 還还 緩缓 換换 喚唤 煥焕 黃黄 謊谎 揮挥 輝辉 毀毁 "
            + "賄贿 會会 匯汇 繪绘 葷荤 渾浑 獲获 貨货 禍祸 擊击 機机 積积 饑饥 譏讥 雞鸡 績绩 緝缉 極极 輯辑 級级 "
            + "擠挤 幾几 劑剂 濟济 計计 記记 際际 繼继 紀纪 夾夹 莢荚 頰颊 賈贾 價价 駕驾 殲歼 監监 堅坚 間间 艱艰 "
            + "檢检 簡简 儉俭 減减 薦荐 鑒鉴 踐践 賤贱 見见 鍵键 艦舰 劍剑 漸渐 將将 漿浆 蔣蒋 槳桨 獎奖 講讲 醬酱 "
            + "膠胶 澆浇 驕骄 嬌娇 攪搅 矯矫 僥侥 腳脚 餃饺 繳缴 絞绞 轎轿 較较 階阶 節节 傑杰 潔洁 結结 誡诫 屆届 "
            + "緊紧 錦锦 僅仅 謹谨 進进 晉晋 燼烬 盡尽 勁劲 荊荆 莖茎 鯨鲸 驚惊 經经 頸颈 靜静 鏡镜 徑径 競竞 淨净 "
            + "糾纠 舊旧 駒驹 舉举 據据 鋸锯 懼惧 劇剧 鵑鹃 絹绢 覺觉 決决 訣诀 絕绝 鈞钧 軍军 駿骏 開开 凱凯 顆颗 "
            + "殼壳 課课 墾垦 懇恳 庫库 褲裤 誇夸 塊块 寬宽 礦矿 曠旷 況况 虧亏 窺窥 饋馈 潰溃 擴扩 闊阔 臘腊 蠟蜡 "
            + "萊莱 來来 賴赖 藍蓝 欄栏 攔拦 籃篮 蘭兰 瀾澜 攬揽 覽览 懶懒 纜缆 爛烂 濫滥 撈捞 勞劳 澇涝 樂乐 壘垒 "
            + "類类 淚泪 籬篱 離离 裡里 裏里 鯉鲤 禮礼 麗丽 厲厉 勵励 歷历 曆历 瀝沥 隸隶 倆俩 聯联 蓮莲 連连 鐮镰 "
            + "憐怜 漣涟 簾帘 斂敛 臉脸 鏈链 戀恋 煉炼 練练 糧粮 涼凉 兩两 輛辆 諒谅 療疗 遼辽 鐐镣 獵猎 臨临 鄰邻 "
            + "鱗鳞 凜凛 賃赁 齡龄 鈴铃 靈灵 嶺岭 領领 餾馏 劉刘 龍龙 聾聋 嚨咙 籠笼 壟垄 攏拢 隴陇 樓楼 婁娄 摟搂 "
            + "簍篓 蘆芦 盧卢 顱颅 廬庐 爐炉 擄虏 鹵卤 魯鲁 賂赂 祿禄 錄录 陸陆 驢驴 呂吕 鋁铝 侶侣 屢屡 縷缕 慮虑 "
            + "濾滤 綠绿 巒峦 攣挛 孿孪 亂乱 掄抡 輪轮 倫伦 侖仑 淪沦 綸纶 論论 蘿萝 羅罗 邏逻 鑼锣 籮箩 騾骡 駱骆 "
            + "絡络 媽妈 瑪玛 碼码 螞蚂 馬马 罵骂 嗎吗 買买 麥麦 賣卖 邁迈 脈脉 瞞瞒 饅馒 蠻蛮 滿满 謾谩 貓猫 錨锚 "
            + "鉚铆 貿贸 麼么 沒没 鎂镁 門门 悶闷 們们 錳锰 夢梦 謎谜 彌弥 覓觅 綿绵 緬缅 廟庙 滅灭 憫悯 閩闽 鳴鸣 "
            + "銘铭 謬谬 謀谋 畝亩 鈉钠 納纳 難难 撓挠 腦脑 惱恼 鬧闹 餒馁 內内 擬拟 膩腻 釀酿 鳥鸟 聶聂 鑷镊 鎳镍 "
            + "檸柠 獰狞 寧宁 濘泞 紐纽 膿脓 濃浓 農农 瘧疟 諾诺 歐欧 鷗鸥 毆殴 嘔呕 盤盘 龐庞 賠赔 噴喷 鵬鹏 騙骗 "
            + "飄飘 頻频 貧贫 蘋苹 憑凭 評评 潑泼 頗颇 撲扑 鋪铺 樸朴 譜谱 棲栖 淒凄 臍脐 齊齐 騎骑 豈岂 啟启 氣气 "
            + "棄弃 牽牵 鉛铅 遷迁 簽签 謙谦 錢钱 鉗钳 潛潜 淺浅 譴谴 塹堑 槍枪 嗆呛 牆墙 薔蔷 強强 搶抢 鍬锹 橋桥 "
            + "喬乔 僑侨 翹翘 竅窍 欽钦 親亲 寢寝 輕轻 氫氢 傾倾 頃顷 請请 慶庆 瓊琼 窮穷 趨趋 區区 軀躯 驅驱 齲龋 "
            + "權权 勸劝 卻却 鵲鹊 確确 讓让 饒饶 擾扰 繞绕 熱热 韌韧 認认 紉纫 榮荣 絨绒 軟软 銳锐 閏闰 潤润 灑洒 "
            + "薩萨 鰓鳃 賽赛 傘伞 喪丧 騷骚 掃扫 澀涩 殺杀 紗纱 篩筛 曬晒 閃闪 陝陕 贍赡 繕缮 傷伤 賞赏 燒烧 紹绍 "
            + "賒赊 攝摄 懾慑 設设 紳绅 審审 嬸婶 腎肾 滲渗 聲声 繩绳 勝胜 聖圣 師师 獅狮 濕湿 詩诗 屍尸 時时 蝕蚀 "
            + "實实 識识 駛驶 勢势 適适 釋释 飾饰 視视 試试 壽寿 獸兽 樞枢 輸输 書书 贖赎 屬属 術术 樹树 豎竖 數数 "
            + "帥帅 雙双 誰谁 順顺 說说 碩硕 爍烁 絲丝 飼饲 聳耸 慫怂 頌颂 訟讼 誦诵 擻擞 蘇苏 訴诉 肅肃 雖虽 隨随 "
            + "綏绥 歲岁 孫孙 損损 筍笋 縮缩 瑣琐 鎖锁 獺獭 撻挞 態态 攤摊 貪贪 癱瘫 灘滩 壇坛 譚谭 談谈 歎叹 嘆叹 "
            + "湯汤 燙烫 濤涛 討讨 騰腾 謄誊 題题 體体 屜屉 條条 貼贴 鐵铁 廳厅 聽听 銅铜 統统 頭头 圖图 塗涂 團团 "
            + "頹颓 蛻蜕 脫脱 鴕鸵 馱驮 駝驼 橢椭 窪洼 襪袜 彎弯 灣湾 頑顽 萬万 網网 韋韦 違违 圍围 為为 濰潍 維维 "
            + "葦苇 偉伟 偽伪 緯纬 謂谓 衛卫 溫温 聞闻 紋纹 穩稳 問问 甕瓮 蝸蜗 渦涡 窩窝 臥卧 嗚呜 鎢钨 烏乌 汙污 "
            + "誣诬 無无 蕪芜 吳吴 塢坞 霧雾 務务 誤误 錫锡 犧牺 襲袭 習习 戲戏 細细 蝦虾 轄辖 峽峡 俠侠 狹狭 廈厦 "
            + "嚇吓 銜衔 鮮鲜 纖纤 鹹咸 賢贤 嫻娴 顯显 險险 現现 獻献 縣县 餡馅 羨羡 憲宪 線线 廂厢 鑲镶 鄉乡 詳详 "
            + "響响 項项 蕭萧 囂嚣 銷销 曉晓 嘯啸 蠍蝎 協协 挾挟 攜携 脅胁 諧谐 寫写 瀉泻 謝谢 鋅锌 釁衅 興兴 洶汹 "
            + "鏽锈 繡绣 虛虚 噓嘘 須须 許许 敘叙 緒绪 續续 軒轩 懸悬 選选 癬癣 絢绚 學学 勳勋 詢询 尋寻 馴驯 訓训 "
            + "訊讯 遜逊 壓压 鴉鸦 鴨鸭 啞哑 亞亚 訝讶 閹阉 煙烟 鹽盐 嚴严 顏颜 閻阎 豔艳 厭厌 硯砚 彥彦 諺谚 驗验 "
            + "鴦鸯 楊杨 揚扬 瘍疡 陽阳 癢痒 養养 樣样 瑤瑶 搖摇 堯尧 遙遥 窯窑 謠谣 藥药 爺爷 頁页 業业 葉叶 醫医 "
            + "頤颐 遺遗 儀仪 蟻蚁 藝艺 億亿 憶忆 義义 詣诣 議议 誼谊 譯译 異异 繹绎 蔭荫 陰阴 銀银 飲饮 隱隐 櫻樱 "
            + "嬰婴 鷹鹰 應应 纓缨 瑩莹 螢萤 營营 熒荧 蠅蝇 贏赢 穎颖 喲哟 擁拥 傭佣 踴踊 詠咏 湧涌 優优 憂忧 郵邮 "
            + "鈾铀 猶犹 遊游 誘诱 與与 嶼屿 語语 籲吁 禦御 獄狱 譽誉 預预 馭驭 淵渊 轅辕 園园 員员 圓圆 緣缘 遠远 "
            + "願愿 約约 躍跃 鑰钥 嶽岳 粵粤 悅悦 閱阅 雲云 鄖郧 勻匀 隕陨 運运 蘊蕴 醞酝 暈晕 韻韵 雜杂 災灾 載载 "
            + "攢攒 暫暂 贊赞 贓赃 髒脏 鑿凿 棗枣 責责 擇择 則则 澤泽 賊贼 贈赠 紮扎 軋轧 閘闸 詐诈 齋斋 債债 氈毡 "
            + "盞盏 斬斩 輾辗 嶄崭 棧栈 戰战 綻绽 張张 漲涨 帳帐 賬账 脹胀 趙赵 蟄蛰 轍辙 這这 貞贞 針针 偵侦 診诊 "
            + "鎮镇 陣阵 掙挣 睜睁 猙狰 爭争 幀帧 鄭郑 證证 織织 職职 執执 紙纸 摯挚 擲掷 幟帜 製制 質质 滯滞 鍾钟 "
            + "終终 種种 腫肿 眾众 軸轴 皺皱 晝昼 驟骤 豬猪 諸诸 誅诛 燭烛 囑嘱 貯贮 鑄铸 築筑 註注 駐驻 專专 磚砖 "
            + "轉转 賺赚 樁桩 莊庄 裝装 妝妆 壯壮 狀状 錐锥 贅赘 墜坠 綴缀 諄谆 準准 濁浊 茲兹 資资 漬渍 蹤踪 綜综 "
            + "總总 縱纵 鄒邹 詛诅 組组 鑽钻";

    private static final Map<Character, Character> TRADITIONAL_TO_SIMPLIFIED = build();

    private HanFold() {
    }

    private static Map<Character, Character> build() {
        Map<Character, Character> table = new HashMap<>();
        for (String token : PAIRS.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            // Pairs are written next to each other ("愛爱"), or with an explicit "=" ("愛=爱").
            int traditional = 0;
            int simplified = token.length() == 3 && token.charAt(1) == '=' ? 2 : -1;
            if (simplified < 0 && token.length() == 2) {
                simplified = 1;
            }
            // Malformed pairs are skipped rather than mapped wrongly: a bad mapping would corrupt every
            // comparison that contains the character, while a missing one only loses a match.
            if (simplified < 0) {
                continue;
            }
            table.put(token.charAt(traditional), token.charAt(simplified));
        }
        return table;
    }

    /** Size of the fold table, for the regression that keeps it honest. */
    static int size() {
        return TRADITIONAL_TO_SIMPLIFIED.size();
    }

    /** The input with every traditional character replaced by its simplified counterpart. */
    static String toSimplified(String value) {
        if (value == null || value.isEmpty() || TRADITIONAL_TO_SIMPLIFIED.isEmpty()) {
            return value == null ? "" : value;
        }
        char[] chars = null;
        for (int index = 0; index < value.length(); index++) {
            Character simplified = TRADITIONAL_TO_SIMPLIFIED.get(value.charAt(index));
            if (simplified == null) {
                if (chars != null) {
                    chars[index] = value.charAt(index);
                }
                continue;
            }
            if (chars == null) {
                chars = value.toCharArray();
            }
            chars[index] = simplified;
        }
        return chars == null ? value : new String(chars);
    }
}
