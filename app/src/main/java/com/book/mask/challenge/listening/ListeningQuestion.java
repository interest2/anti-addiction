package com.book.mask.challenge.listening;

import java.util.ArrayList;
import java.util.List;

/**
 * 听力选择题：听力原文 + 题干 + 选项 + 正确选项字母。
 *
 * <p>「听力原文」默认不展示，答错后可由用户选择是否揭示；一旦揭示，本题不再具备解禁悬浮窗的资格。
 */
public final class ListeningQuestion {

    private final String transcript;
    private final String question;
    private final List<String> options;
    private final String answer;

    public ListeningQuestion(String transcript, String question, List<String> options, String answer) {
        this.transcript = transcript == null ? "" : transcript.trim();
        this.question = question == null ? "" : question.trim();
        this.options = options == null ? new ArrayList<>() : new ArrayList<>(options);
        this.answer = answer == null ? "" : answer.trim().toUpperCase();
    }

    public String getTranscript() {
        return transcript;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public String getAnswer() {
        return answer;
    }

    /** 题目字段是否完整可用：原文/题干/答案非空，选项不少于 2 个，答案落在 A-D 之内。 */
    public boolean isValid() {
        if (transcript.isEmpty() || question.isEmpty() || answer.isEmpty()) {
            return false;
        }
        if (options.size() < 2 || options.size() > 26) {
            return false;
        }
        int answerIndex = answerIndex();
        return answerIndex >= 0 && answerIndex < options.size();
    }

    /** 答案字母对应的选项下标；无效时返回 -1。 */
    public int answerIndex() {
        if (answer.length() != 1) {
            return -1;
        }
        int index = answer.charAt(0) - 'A';
        return index >= 0 && index < options.size() ? index : -1;
    }

    /** 按选项字母前缀拼接选项文本，如 "A. xxx"。 */
    public String optionLabel(int index) {
        if (index < 0 || index >= options.size()) {
            return "";
        }
        return (char) ('A' + index) + ". " + options.get(index);
    }

    public String[] toOptionLabels() {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = optionLabel(i);
        }
        return labels;
    }
}
