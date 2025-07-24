package com.book.baisc.config;

import java.util.ArrayList;
import java.util.List;

public class Const {

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;

    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 2秒检查一次

    public static final String DEFAULT_HINT_SOURCE = "大模型";
    public static final String CUSTOM_HINT_SOURCE = "自定义";
    public static final String TARGET_TO_BE_SET = "待设置";

    // 云端接口配置
    public static final String DOMAIN_URL = "https://www.ratetend.com:5001/antiAddict"; // 请替换为实际的地址
    public static final String LLM_PATH = "/llm";
    public static final String REPORT_PATH = "/report";
    public static final String LATEST_VERSION_PATH = "/latestAppVersion";
    public static final String CONTENT_TYPE = "application/json";

    // 广播Action常量
    public static final String ACTION_UPDATE_CASUAL_COUNT = "com.book.baisc.ACTION_UPDATE_CASUAL_COUNT";

    /**
     * APP接口，统一SupportedApp和CustomApp的行为
     */
    public interface App {
        String getAppName();
        String getPackageName();
        String getTargetWord();
        int getCasualLimitCount();
    }

    // 支持的APP枚举
    public enum SupportedApp implements App {
        XHS("小红书", "com.xingin.xhs", "发现", 3),
        ZHIHU("知乎", "com.zhihu.android", "热榜", 1),
        DOUYIN("抖音", "com.ss.android.ugc.aweme", "推荐", 1),
        BILIBILI("哔哩哔哩", "tv.danmaku.bili", "推荐", 1),
        ALIPAY("支付宝", "com.eg.android.AlipayGphone", "股票,行情,持有", 1),
        WECHAT("微信", "com.tencent.mm", "公众号", 3),
                ;
        private final String appName;
        private final String packageName;
        private final String targetWord;
        private final int casualLimitCount;
        
        SupportedApp(String appName, String packageName, String targetWord, int casualLimitCount) {
            this.appName = appName;
            this.packageName = packageName;
            this.targetWord = targetWord;
            this.casualLimitCount = casualLimitCount;
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
        
        public int getCasualLimitCount() {
            return casualLimitCount;
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
        
        /**
         * 获取所有支持的APP列表（包括动态添加的）
         */
        public static List<App> getAllApps() {
            List<App> allApps = new ArrayList<>();
            // 添加预定义的APP
            for (SupportedApp app : values()) {
                allApps.add(app);
            }
            // 添加动态添加的APP
            allApps.addAll(CustomAppManager.getInstance().getCustomApps());
            return allApps;
        }
        
        /**
         * 检查包名是否已存在
         */
        public static boolean isPackageNameExists(String packageName) {
            // 检查预定义的APP
            for (SupportedApp app : values()) {
                if (app.packageName.equals(packageName)) {
                    return true;
                }
            }
            // 检查动态添加的APP
            return CustomAppManager.getInstance().isPackageNameExists(packageName);
        }
    }

    /**
     * 自定义APP管理类，用于管理动态添加的APP
     */
    public static class CustomApp implements App {
        private final String appName;
        private final String packageName;
        private final String targetWord;
        private int casualLimitCount; // 改为非final，支持修改
        
        public CustomApp(String appName, String packageName, String targetWord, int casualLimitCount) {
            this.appName = appName;
            this.packageName = packageName;
            this.targetWord = targetWord;
            this.casualLimitCount = casualLimitCount;
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
        
        public int getCasualLimitCount() {
            return casualLimitCount;
        }
        
        public void setCasualLimitCount(int casualLimitCount) {
            this.casualLimitCount = casualLimitCount;
        }
    }
}
