package com.book.mask.util;

public class DateUtils {

    /**
     * 格式化剩余时间为MM:SS格式
     */
    public static String formatRemainingTime(long remainingMillis) {
        if (remainingMillis <= 0) {
            return "00:00";
        }

        int totalSeconds = (int) (remainingMillis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }


    /**
     * 获取当前日期字符串 "yyyy-MM-dd"
     */
    public static String getCurrentDate() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
    }

}
