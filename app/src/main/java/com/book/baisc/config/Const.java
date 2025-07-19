package com.book.baisc.config;

public class Const {

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;

    public final static int CASUAL_LIMIT_COUNT = 6;
    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 2秒检查一次

    // 广播Action常量
    public static final String ACTION_UPDATE_CASUAL_COUNT = "com.book.baisc.ACTION_UPDATE_CASUAL_COUNT";


    // 支持的APP枚举
    public enum SupportedApp {
        XHS("小红书", "com.xingin.xhs", "发现"),
        ALIPAY("支付宝", "com.eg.android.AlipayGphone", "股票,行情,持有"),
        ZHIHU("知乎", "com.zhihu.android", "热榜"),
        WECHAT("微信", "com.tencent.mm", "公众号"),
        DOUYIN("抖音", "com.ss.android.ugc.aweme", "推荐"),
        BILIBILI("哔哩哔哩", "tv.danmaku.bili", "推荐");

        private final String appName;
        private final String packageName;
        private final String targetWord;
        
        SupportedApp(String appName, String packageName, String targetWord) {
            this.appName = appName;
            this.packageName = packageName;
            this.targetWord = targetWord;
        }

        public String getAppName() {
            return appName;
        }

        public String getPackageName() {
            return packageName;
        }
        
        public String getTargetWord() {
            return targetWord;
        }
        
        /**
         * 根据包名获取对应的APP
         */
        public static SupportedApp getByPackageName(String packageName) {
            for (SupportedApp app : values()) {
                if (app.packageName.equals(packageName)) {
                    return app;
                }
            }
            return null;
        }
    }


}
