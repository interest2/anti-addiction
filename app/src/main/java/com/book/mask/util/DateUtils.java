package com.book.mask.util;

import android.util.Log;

import com.book.mask.constant.Const;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    private static final String TAG = "DateUtils";

    // 时间格式化器
    public static final SimpleDateFormat timeFormatter =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    /**
     * 格式化时间戳为可读格式
     */
    public static String formatTime(long timestamp) {
        if (timestamp == 0) return "未设置";
        return timeFormatter.format(new Date(timestamp));
    }

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

    /**
     * 纳秒转毫秒（double，保留小数用于耗时诊断）
     */
    public static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    /**
     * 格式化毫秒耗时为三位小数字符串，用于诊断日志
     */
    public static String formatMillis(double millis) {
        return String.format(Locale.getDefault(), "%.3f", millis);
    }

    public static String hintDate(String targetDateStr) {
        Integer daysRemaining = getDaysRemaining(targetDateStr);
        if (daysRemaining == null) {
            return "";
        }

        String dateHint;
        if (daysRemaining > 0) {
            dateHint = "距离目标只剩 " + daysRemaining + " 天！";
        } else if (daysRemaining == 0) {
            dateHint = "今天是目标日期！";
        } else {
            dateHint = "目标日期已过期 " + Math.abs(daysRemaining) + " 天！";
        }
        Log.d(TAG, "日期提示: " + dateHint);
        return dateHint + "\n";
    }

    public static String countdownDate(String targetDateStr) {
        Integer daysRemaining = getDaysRemaining(targetDateStr);
        return daysRemaining == null ? "倒计时 -- 天" : "倒计时 " + daysRemaining + " 天";
    }

    private static Integer getDaysRemaining(String targetDateStr) {
        if (Const.TARGET_TO_BE_SET.equals(targetDateStr) || targetDateStr.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date targetDate = sdf.parse(targetDateStr);
            if (targetDate == null) {
                return null;
            }

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar targetCalendar = Calendar.getInstance();
            targetCalendar.setTime(targetDate);
            targetCalendar.set(Calendar.HOUR_OF_DAY, 0);
            targetCalendar.set(Calendar.MINUTE, 0);
            targetCalendar.set(Calendar.SECOND, 0);
            targetCalendar.set(Calendar.MILLISECOND, 0);

            long timeDiff = targetCalendar.getTimeInMillis() - today.getTimeInMillis();
            return (int) (timeDiff / (24 * 60 * 60 * 1000));
        } catch (Exception e) {
            Log.e(TAG, "计算剩余天数失败", e);
            return null;
        }
    }

}
