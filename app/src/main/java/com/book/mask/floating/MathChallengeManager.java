package com.book.mask.floating;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

import com.book.mask.R;
import com.book.mask.config.Const;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.SettingsManager;
import com.book.mask.config.CustomApp;
import com.book.mask.util.ArithmeticUtils;

/**
 * 数学题验证管理器
 * 负责生成数学题、显示验证界面、处理用户输入等功能
 */
public class MathChallengeManager {
    
    private static final String TAG = "MathChallenge";
    
    private Context context;
    private View floatingView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private Handler handler;
    private FloatService accessibilityService;
    private CustomApp currentApp; // 当前APP（统一使用CustomApp）
    private SettingsManager settingsManager;
    
    // 数学题相关
    private Random random = new Random();
    private int currentAnswer = 0;
    private boolean isMathChallengeActive = false;
    
    // 回调接口
    public interface OnMathChallengeListener {
        void onAnswerCorrect();
        void onChallengeCancel();
    }
    
    private OnMathChallengeListener listener;
    
    public MathChallengeManager(Context context, View floatingView, 
                               WindowManager windowManager, WindowManager.LayoutParams layoutParams,
                               Handler handler, FloatService accessibilityService) {
        this.context = context;
        this.floatingView = floatingView;
        this.windowManager = windowManager;
        this.layoutParams = layoutParams;
        this.handler = handler;
        this.accessibilityService = accessibilityService;
        this.settingsManager = new SettingsManager(context);
        
        initializeComponents();
    }
    
    /**
     * 设置当前APP
     */
    public void setCurrentApp(CustomApp app) {
        this.currentApp = app;
    }
    

    
    public void setOnMathChallengeListener(OnMathChallengeListener listener) {
        this.listener = listener;
    }
    
    public OnMathChallengeListener getOnMathChallengeListener() {
        return listener;
    }
    
    public boolean isMathChallengeActive() {
        return isMathChallengeActive;
    }
    
    /**
     * 初始化数学题相关组件
     */
    private void initializeComponents() {
        if (floatingView == null) return;
        
        Button submitButton = floatingView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = floatingView.findViewById(R.id.btn_cancel_close);
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);

        // 提交答案按钮
        submitButton.setOnClickListener(v -> handleSubmitAnswer());
        
        // 取消按钮
        cancelButton.setOnClickListener(v -> {
            Log.d(TAG, "用户取消关闭");
            
            // 针对微信APP的特殊处理：点击取消直接当作答题通过
            if (CustomAppManager.WECHAT_PACKAGE.equals(currentApp.getPackageName())) {
                Log.d(TAG, "微信APP取消按钮被点击，直接当作答题通过");
                hideMathChallenge();
                if (listener != null) {
                    listener.onAnswerCorrect();
                }
            } else {
                // 其他APP正常处理
                hideMathChallenge();
                if (listener != null) {
                    listener.onChallengeCancel();
                }
            }
        });
        
        // 回车键提交答案
        answerEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                submitButton.performClick();
                return true;
            }
            return false;
        });
        
        // EditText点击时确保显示输入法
        answerEdit.setOnClickListener(v -> {
            answerEdit.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(answerEdit, InputMethodManager.SHOW_FORCED);
            }
        });
        
        // 添加焦点变化监听器
        answerEdit.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(TAG, "EditText焦点状态变化: " + hasFocus);
            if (hasFocus && isMathChallengeActive) {
                // 获得焦点时，确保输入法显示
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(answerEdit, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }
    
    /**
     * 显示数学题验证界面
     */
    public void showMathChallenge() {
        if (floatingView == null) return;
        
        LinearLayout mathLayout = floatingView.findViewById(R.id.math_challenge_layout);
        TextView questionText = floatingView.findViewById(R.id.tv_math_question);
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);
        TextView resultText = floatingView.findViewById(R.id.tv_math_result);
        
        // 生成新的数学题
        String question = generateMathQuestion();
        currentAnswer = ArithmeticUtils.getMathAnswer(question);

        questionText.setText(question);
        
        // 清空输入框和结果
        answerEdit.setText("");
        resultText.setText("");
        resultText.setVisibility(View.GONE);
        
        // 显示数学题区域
        mathLayout.setVisibility(View.VISIBLE);
        isMathChallengeActive = true;
        
        // 通知AccessibilityService数学题验证开始
        if (accessibilityService != null) {
            accessibilityService.onMathChallengeStart();
        }
        
        // 关键：在数学题验证期间，完全允许悬浮窗获得焦点
        // 这样输入法就不会被意外隐藏了
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        windowManager.updateViewLayout(floatingView, layoutParams);
        
        // 让EditText获得焦点
        answerEdit.setFocusable(true);
        answerEdit.setFocusableInTouchMode(true);
        answerEdit.requestFocus();
        
        // 延迟显示输入法，确保界面已准备好
        handler.postDelayed(() -> {
            // 再次确保焦点在EditText上
            answerEdit.requestFocus();
            
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(answerEdit, InputMethodManager.SHOW_FORCED);
            }
            
            // 定期检查并保持EditText焦点（防止焦点丢失）
            Runnable focusKeeper = new Runnable() {
                @Override
                public void run() {
                    if (isMathChallengeActive && answerEdit != null) {
                        if (!answerEdit.hasFocus()) {
                            Log.d(TAG, "检测到EditText失去焦点，重新获得焦点");
                            answerEdit.requestFocus();
                            
                            // 重新显示输入法
                            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(answerEdit, InputMethodManager.SHOW_IMPLICIT);
                            }
                        }
                        
                        // 继续检查
                        handler.postDelayed(this, 1000); // 每秒检查一次
                    }
                }
            };
            handler.postDelayed(focusKeeper, 1000); // 1秒后开始检查
            
            Log.d(TAG, "输入法显示完成，开始焦点保持机制");
        }, 300); // 300ms后显示输入法
        
        Log.d(TAG, "显示数学题验证界面，输入法已请求显示");
    }
    
    /**
     * 隐藏数学题验证界面
     */
    public void hideMathChallenge() {
        if (floatingView == null) return;
        
        LinearLayout mathLayout = floatingView.findViewById(R.id.math_challenge_layout);
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);
        
        // 隐藏输入法
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(answerEdit.getWindowToken(), 0);
        }
        
        // 清除EditText焦点
        answerEdit.clearFocus();
        
        // 隐藏数学题区域
        mathLayout.setVisibility(View.GONE);
        isMathChallengeActive = false;
        
        // 通知AccessibilityService数学题验证结束
        if (accessibilityService != null) {
            accessibilityService.onMathChallengeEnd();
        }
        
        // 重新设置悬浮窗为不可获得焦点，避免影响其他应用
        // 保持与showFloatingWindow中的标志位设置一致
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(floatingView, layoutParams);
        
        Log.d(TAG, "隐藏数学题验证界面，输入法已隐藏");
    }

    /**
     * 处理提交答案
     */
    private void handleSubmitAnswer() {
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);
        TextView resultText = floatingView.findViewById(R.id.tv_math_result);
        
        String userAnswer = answerEdit.getText().toString().trim();
        if (TextUtils.isEmpty(userAnswer)) {
            resultText.setText("⚠️ 请输入答案");
            resultText.setVisibility(View.VISIBLE);
            return;
        }
        
        try {
            int answer = Integer.parseInt(userAnswer);
            if (answer == currentAnswer) {
                // 答案正确
                Log.d(TAG, "数学题回答正确");
                resultText.setText("✅ 答案正确！");
                resultText.setTextColor(context.getResources().getColor(android.R.color.holo_green_light));
                resultText.setVisibility(View.VISIBLE);
                
                // 延迟通知答案正确，让用户看到正确提示
                handler.postDelayed(() -> {
                    // 先隐藏输入法
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(answerEdit.getWindowToken(), 0);
                    }
                    
                    if (listener != null) {
                        listener.onAnswerCorrect();
                    }
                }, 1000);
                
            } else {
                // 答案错误
                Log.d(TAG, "数学题回答错误: " + answer + " (正确答案: " + currentAnswer + ")");
                resultText.setText("❌ 答案错误，请重新计算");
                resultText.setTextColor(context.getResources().getColor(android.R.color.holo_red_light));
                resultText.setVisibility(View.VISIBLE);
                
                // 清空输入框
                answerEdit.setText("");
                
                // 1 秒后生成新题目，保持输入法显示
                handler.postDelayed(() -> {
                    // 生成新题目，但不重新初始化悬浮窗参数
                    TextView questionText = floatingView.findViewById(R.id.tv_math_question);
                    String question = generateMathQuestion();
                    currentAnswer = ArithmeticUtils.getMathAnswer(question);
                    questionText.setText(question);
                    
                    // 清空输入框但保持焦点
                    answerEdit.setText("");
                    answerEdit.requestFocus();
                    
                    // 隐藏结果提示
                    resultText.setVisibility(View.GONE);
                    
                    Log.d(TAG, "生成新数学题，保持输入法显示");
                }, 1000);
            }
        } catch (NumberFormatException e) {
            resultText.setText("⚠️ 请输入有效数字");
            resultText.setVisibility(View.VISIBLE);
        }
    }


    /**
     * 根据设置获取数学题参数
     */
    private String generateMathQuestion() {
        String difficultyMode = settingsManager.getMathDifficultyMode();

        if ("custom".equals(difficultyMode)) {
            // 使用自定义难度设置
            int additionDigits = settingsManager.getMathAdditionDigits();
            int subtractionDigits = settingsManager.getMathSubtractionDigits();
            int multiplierDigits = settingsManager.getMathMultiplicationMultiplierDigits();
            int multiplicandDigits = settingsManager.getMathMultiplicationMultiplicandDigits();

            return ArithmeticUtils.customArithmetic(additionDigits, subtractionDigits, multiplierDigits, multiplicandDigits);
        } else {
            // 使用默认难度
            return ArithmeticUtils.customArithmetic(
                    Const.ADD_LEN_DEFAULT,
                    Const.SUB_LEN_DEFAULT,
                    Const.MUL_FIRST_LEN_DEFAULT,
                    Const.MUL_SECOND_LEN_DEFAULT);
        }
    }
}
