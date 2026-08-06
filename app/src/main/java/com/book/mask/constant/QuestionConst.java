package com.book.mask.constant;

public final class QuestionConst {

    private QuestionConst() {
    }

    // 算术题-悬浮窗：默认数字位数
    public static final int ADD_LEN_DEFAULT = 5;
    public static final int SUB_LEN_DEFAULT = 5;
    public static final int MUL_FIRST_LEN_DEFAULT = 2;
    public static final int MUL_SECOND_LEN_DEFAULT = 2;
    // 混合题型比例默认值：推理题 20%，其余 80% 为本地算术题
    public static final double MIXED_REASONING_QUIZ_RATIO_DEFAULT = 0.2D;
    // 算术题-设置：自定义难度 位数范围
    public static final int ADD_LEN_MIN = 4;
    public static final int ADD_LEN_MAX = 7;
    public static final int MUL_LEN_MIN = 2;
    public static final int MUL_LEN_MAX = 4;
    // 英文阅读功能开关，默认不启用
    public static final boolean ENGLISH_READING_ENABLED = false;
    // 英文阅读字数范围
    public static final int ENGLISH_READING_LENGTH_MIN = 200;
    public static final int ENGLISH_READING_LENGTH_MAX = 1000;
    public static final int ENGLISH_READING_LENGTH_DEFAULT = 300;
    // 算术题-卡片难度
    public static final int ADD_LEN_CARD = 7;
    public static final int SUB_LEN_CARD = 7;
    public static final int MUL_FIRST_CARD = 4;
    public static final int MUL_SECOND_CARD = 4;
    // 复述题：故事字数
    public static final int RETELLING_STORY_LENGTH_DEFAULT = 150;
    public static final int RETELLING_STORY_LENGTH_MIN = 80;
    public static final int RETELLING_STORY_LENGTH_MAX = 300;
    // 复述题：限时展示时长（秒）
    public static final int RETELLING_DISPLAY_SECONDS_DEFAULT = 30;
    public static final int RETELLING_DISPLAY_SECONDS_MIN = 10;
    public static final int RETELLING_DISPLAY_SECONDS_MAX = 120;
    // 复述题：通过分数
    public static final int RETELLING_PASS_SCORE_DEFAULT = 75;
    public static final int RETELLING_PASS_SCORE_MIN = 50;
    public static final int RETELLING_PASS_SCORE_MAX = 95;
    // 复述题：默认录音上限（秒）。未指定动态值时使用（正常按故事长度计算，见 retellingRecordSeconds）。
    public static final int RETELLING_RECORD_MAX_SECONDS = 60;
    // 复述题：录音上限下限 / 硬顶（秒）。录音上限是安全网，用户说完可随时提前结束；
    // 下限放宽到 60s 照顾说话慢的用户，硬顶对齐《复述训练指南》建议的 3 分钟复述。
    public static final int RETELLING_RECORD_MIN_SECONDS = 60;
    public static final int RETELLING_RECORD_HARD_MAX_SECONDS = 180;
    // 复述题：识别文本过短的本地粗检阈值（占原文长度比例）
    public static final double RETELLING_TEXT_TOO_SHORT_RATIO = 0.15D;
    // 听力题：选项个数
    public static final int LISTENING_OPTION_COUNT = 4;

    /**
     * 复述题录音上限随故事长度动态化：约 0.6 秒/字（按偏慢语速估算，余量充足），
     * 落在 [60, 180] 区间。150 字 → 90 秒，300 字 → 180 秒，80 字 → 60 秒。
     */
    public static int retellingRecordSeconds(int storyLength) {
        int computed = (int) Math.round(storyLength * 0.6f);
        return Math.max(RETELLING_RECORD_MIN_SECONDS,
                Math.min(computed, RETELLING_RECORD_HARD_MAX_SECONDS));
    }

    /** 复述题内置故事：正文 + 三幕法拆解 + 寓意，供题后复盘对照。 */
    public static final class RetellingBuiltinStory {
        public final String story;
        public final String conflict;
        public final String action;
        public final String outcome;
        public final String moral;

        public RetellingBuiltinStory(
                String story, String conflict, String action, String outcome, String moral) {
            this.story = story;
            this.conflict = conflict;
            this.action = action;
            this.outcome = outcome;
            this.moral = moral;
        }
    }

    // 复述题：内置故事池，接口不可用时兜底，避免复述题卡死。风格对齐《复述训练指南》：
    // 寓言/哲理体、有冲突、有转折、有启发，附三幕法拆解与寓意。
    public static final RetellingBuiltinStory[] RETELLING_BUILTIN_STORIES = {
            new RetellingBuiltinStory(
                    "老农发现自家稻苗发黄，而邻居连夜用抽水机浇灌，田里水汪汪，稻苗水灵。老农心急，也借来抽水机猛灌一通。"
                            + "可第二天，稻苗不但没转绿，反而被泡烂了根。邻居过来说：你田埂没修好，水全渗走了，苗根泡在水里能不烂吗？"
                            + "老农这才明白，自己只看到别人浇水的动作，却漏了人家平日修渠养土的功夫。他痛定思痛，翻田、修埂、按节气引水。"
                            + "第二年秋天，他家稻田也金浪滚滚。欲速则不达，真正的捷径，是把基本功做扎实。",
                    "老农见自家稻苗发黄，邻居用抽水机浇灌后水灵，心急想走捷径。",
                    "老农借抽水机猛灌，反而泡烂苗根；邻居点破他漏了修田埂养土的基本功。",
                    "老农翻田修埂、按节气引水，来年丰收，悟出欲速则不达。",
                    "欲速则不达，把基本功做扎实，才是真正的捷径。"),
            new RetellingBuiltinStory(
                    "小熊在山脚捡到一块会“唱歌”的石头，其实是石缝里卡了只蝉。它把石头捧回家，逢人就炫耀，走路都抬头挺胸，"
                            + "觉得自己马上要成为森林里最亮的星。可没过几天，蝉悄悄飞走了，石头再也不会响。小熊抱着哑石头满山乱转，急得直跺脚。"
                            + "老松鼠看了，慢悠悠说：石头响不响，跟你有什么相干？真本事得长在自己身上。小熊一愣，放下石头，开始每天对着山谷练嗓子。"
                            + "冬去春来，它的歌声真的传遍了整片森林。外物带来的光，说灭就灭；自己练出的本领，才越走越亮。",
                    "小熊捡到会“唱歌”的石头（实为石缝里的蝉），四处炫耀，想靠它成为森林最亮的星。",
                    "蝉飞走、石头不响，小熊满山找；老松鼠点醒它真本事得长在自己身上。",
                    "小熊放下石头每天练嗓，歌声最终传遍整片森林。",
                    "依赖外物得来的光环终会消失，自己练出的本领才靠得住。"),
            new RetellingBuiltinStory(
                    "镇上有两个木匠一起拜师。一个手脚麻利，专挑名贵木料做大件，师傅让做板凳他嫌简单，总想一步登天；"
                            + "另一个木讷老实，每天只肯刨小板凳，一条接一条，刨得光可鉴人。头两年，快木匠的雕花大件常被退货，他怨木料不好、客人不识货；"
                            + "慢木匠却把板凳越做越结实，连老师傅都点名要他做的。三年后，快木匠还在四处返工，慢木匠的小板凳已经成了镇上的抢手货，自己开起了铺子。"
                            + "把不起眼的小事做到极致，本身就是一条越走越宽的大路。",
                    "两个木匠同拜师，一个嫌板凳简单想一步登天，一个只肯刨小板凳。",
                    "快木匠的大件接连被退货，慢木匠把每条板凳刨得光可鉴人、越做越结实。",
                    "三年后快木匠还在返工，慢木匠的小板凳成了镇上抢手货并开了铺子。",
                    "把不起眼的小事做到极致，本身就是一条越走越宽的大路。")
    };

    /** 听力题：内置题目，接口/大模型不可用时兜底，避免听力题卡死。 */
    public static final class ListeningBuiltinQuestion {
        public final String transcript;
        public final String question;
        public final String[] options;
        public final String answer;

        public ListeningBuiltinQuestion(
                String transcript, String question, String[] options, String answer) {
            this.transcript = transcript;
            this.question = question;
            this.options = options;
            this.answer = answer;
        }
    }

    // 听力题：内置题目池。高难度：干扰项均源自原文元素（张冠李戴 / 数字错位 / 顺序颠倒 / 方向反置 / 误解作饵），
    // 原文信息密集（顺序 / 数字对应 / 因果链 / 言外之意），听一遍需真正抓细节才能作答；原文均 ≤ 80 字（TTS 上限）。
    public static final ListeningBuiltinQuestion[] LISTENING_BUILTIN_QUESTIONS = {
            new ListeningBuiltinQuestion(
                    "周一的晨会上，经理先宣布新的考勤制度，随后财务主管说明报销流程调整，最后人事主管公布年度体检安排。"
                            + "会后小李误以为体检安排在下周三，其实定在两周后的周五。",
                    "关于这次晨会，下列说法正确的是？",
                    new String[]{
                            "财务主管最先发言",
                            "体检安排在下周三",
                            "人事主管在最后公布了年度体检安排",
                            "经理宣布的是报销流程调整"},
                    "C"),
            new ListeningBuiltinQuestion(
                    "小区通知，快递柜包裹免费保管两天，超期每天收费一元，而菜鸟驿站可免费保管五天。"
                            + "李女士上周二把包裹放进快递柜，周六才去取，被收取了三元保管费。",
                    "根据材料，下列说法正确的是？",
                    new String[]{
                            "快递柜和菜鸟驿站的免费保管时长相同",
                            "李女士超期两天，被收取两元",
                            "李女士超期三天，共被收取三元保管费",
                            "李女士的包裹存了四天，正好在免费期内"},
                    "C"),
            new ListeningBuiltinQuestion(
                    "海龟性别取决于孵化温度：沙温偏低时孵出的大多是雄性，偏高时多为雌性，中间区间才雌雄平衡。"
                            + "近年全球变暖使海滩沙温上升，环保组织担心某些种群会出现雄少雌多的失衡。",
                    "根据材料，可以推断出？",
                    new String[]{
                            "沙温越高，孵出的海龟越可能是雄性",
                            "全球变暖可能导致海龟种群雌多雄少",
                            "中间区间的沙温最容易孵出雄性",
                            "海龟性别由亲本遗传决定"},
                    "B"),
            new ListeningBuiltinQuestion(
                    "甲：这批货质检报告我已经发你邮箱了，重点看第三项。乙：你又不是不知道，我这周一直在外面跑，"
                            + "邮箱三天没开过了。甲：那这样，晚上我把关键数据直接发你手机。",
                    "乙的言外之意最可能是？",
                    new String[]{
                            "他已经看过质检报告",
                            "他这周无法及时查看邮箱，希望改发其他方式",
                            "他对这批货的质量很有信心",
                            "他要求甲重新做一次质检"},
                    "B"),
            new ListeningBuiltinQuestion(
                    "旅行团报价按成人一人两千、儿童半价、三岁以下免费计。王先生带五岁女儿和两岁儿子报名，"
                            + "又另付了二百元保险费。",
                    "王先生一家此次出行共需支付多少？",
                    new String[]{
                            "三千二百元",
                            "三千四百元",
                            "四千二百元",
                            "二千二百元"},
                    "A"),
            new ListeningBuiltinQuestion(
                    "客服解释，本次促销的满减券与折扣价不能同时使用，且每单最多用一张。"
                            + "张先生发现他选中的两件商品分别参加不同活动，加购后系统提示只能享受其中一件的优惠。",
                    "根据材料，下列说法正确的是？",
                    new String[]{
                            "满减券可以和折扣价同时使用",
                            "每单最多可使用三张满减券",
                            "张先生的两件商品加购后只能享受其中一件的优惠",
                            "满减券的使用没有数量限制"},
                    "C")
    };
}
