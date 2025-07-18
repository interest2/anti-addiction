package com.book.baisc.config;

public class Const {

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;

    public final static int CASUAL_LIMIT_COUNT = 6;
    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 2秒检查一次

    // 广播Action常量
    public static final String ACTION_UPDATE_CASUAL_COUNT = "com.book.baisc.ACTION_UPDATE_CASUAL_COUNT";


    // 兼容性保留的常量
    public static final String XHS_PACKAGE = "com.xingin.xhs";

    // 支持的APP枚举
    public enum SupportedApp {
        XHS("com.xingin.xhs", "发现"),
        ALIPAY("com.eg.android.AlipayGphone", "股票,行情,持有");
        
        private final String packageName;
        private final String targetWord;
        
        SupportedApp(String packageName, String targetWord) {
            this.packageName = packageName;
            this.targetWord = targetWord;
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
