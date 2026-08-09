package com.book.mask.util;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import com.book.mask.constant.Const;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    private static final String TAG = "DateUtils";

    // 日期前缀着色：目标剩余天数
    private static final int COLOR_ORANGE_RED = Color.parseColor("#FF5722");  // 橙红色：X 天 >= 10 时数字颜色
    private static final int COLOR_BRIGHT_RED = Color.parseColor("#FF1744");  // 鲜红色：X 天 < 10 时数字颜色（更紧急）
    private static final int COLOR_LIGHT_PURPLE = Color.parseColor("#EAD2EE"); // 浅紫色：其余文字颜色
    private static final int GOAL_TEXT_MAX_LENGTH = 6;                        // 目标内容最多展示 6 个字，超出用…省略

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

    /** 答题计时格式：分钟两位，秒仅显示十秒位。 */
    public static String formatChallengeDuration(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60;
        long tensSeconds = (elapsedSeconds % 60) / 10;
        return String.format(Locale.US, "%02d:%d", minutes, tensSeconds);
    }

    /** 答题记录用完整耗时格式：MM:SS，秒补零。 */
    public static String formatChallengeDurationFull(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
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

    /**
     * 生成悬浮窗日期前缀（带颜色）：
     * 目标替换为实际目标内容（超 6 字省略）；剩余天数 X 默认橙红，X<10 时鲜红；其余文字浅紫。
     */
    public static CharSequence hintDate(String targetDateStr, String motivationTag) {
        Integer daysRemaining = getDaysRemaining(targetDateStr);
        if (daysRemaining == null) {
            return "";
        }

        String goalText = formatGoalText(motivationTag);

        String prefix;
        Integer dayValue = null;
        if (daysRemaining > 0) {
            dayValue = daysRemaining;
            prefix = "距离" + goalText + "只剩 ";
        } else if (daysRemaining == 0) {
            prefix = "今天是" + goalText + "的完成日期！";
        } else {
            dayValue = Math.abs(daysRemaining);
            prefix = goalText + "已过期 ";
        }

        String full;
        int dayStart = -1;
        int dayEnd = -1;
        if (dayValue != null) {
            String dayText = String.valueOf(dayValue);
            full = prefix + dayText + " 天！\n";
            dayStart = prefix.length();
            dayEnd = dayStart + dayText.length();
        } else {
            full = prefix + "\n";
        }

        SpannableString spannable = new SpannableString(full);
        spannable.setSpan(new ForegroundColorSpan(COLOR_LIGHT_PURPLE), 0, full.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (dayValue != null) {
            int dayColor = dayValue < 10 ? COLOR_BRIGHT_RED : COLOR_ORANGE_RED;
            spannable.setSpan(new ForegroundColorSpan(dayColor), dayStart, dayEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Log.d(TAG, "日期提示: " + full.trim());
        return spannable;
    }

    /** 目标内容展示：未设置时回落为“目标”；超过 6 字保留前 6 字并用…省略。 */
    private static String formatGoalText(String motivationTag) {
        if (motivationTag == null || motivationTag.isEmpty() || Const.TARGET_TO_BE_SET.equals(motivationTag)) {
            return "目标";
        }
        return motivationTag.length() > GOAL_TEXT_MAX_LENGTH
                ? motivationTag.substring(0, GOAL_TEXT_MAX_LENGTH) + "…"
                : motivationTag;
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
