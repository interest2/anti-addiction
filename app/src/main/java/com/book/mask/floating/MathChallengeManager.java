package com.book.mask.floating;

import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Random;

import com.book.mask.R;
import com.book.mask.config.Const;
import com.book.mask.config.CustomAppManager;
import com.book.mask.setting.RelaxManager;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.config.CustomApp;
import com.book.mask.util.ArithmeticUtils;
import com.book.mask.util.ContentUtils;

import org.json.JSONObject;

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
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    
    // 用于跟踪是否正在选择文字，避免打断用户的选择操作
    private boolean isTextSelecting = false;

    static HashMap<String, String> challenge = new HashMap<>();

    static {
        String q = "小红书社区组织挖红薯：\n" +
                "如果每位主包挖 12 个，地里还剩 75 个；\n" +
                "如果每位主包挖 29 个，就还缺 180 个。\n" +
                "问共有多少位主包参加了挖红薯？";
        String a = "15";
        challenge.put("question", q);
        challenge.put("answer", a);
    }

    // 数学题相关
    private String currentAnswer = "";
    private static boolean isMathChallengeActive = false;
    private boolean componentsInitialized = false;
    // type 0：算术题；1：应用题；2：英文阅读
    private int currentType = 0;
    private static final int TYPE_ARITHMETIC = 0;
    private static final int TYPE_WORD = 1;
    private static final int TYPE_ENGLISH = 2;

    // 英文阅读题缓存
    static HashMap<String, String> englishChallenge = new HashMap<>();

    static {
        String eq = "Supermarkets were still a California phenomenon; District food shopping was done in small groceries, in red-fronted outlets of the Great Atlantic & Pacific Tea Company, in open markets, or on pavements." +
                "\n\n" +
                "A.Supermarkets were prevalent in California, while food shopping in other districts was typically done in small groceries, open markets, or on pavements.\n" +
                "B.California exclusively used open markets for food shopping, with supermarkets being a rare phenomenon.\n" +
                "C.Supermarkets were the dominant form of food shopping in all districts, with no use of small groceries or open markets.";
        String ea = "A";
        englishChallenge.put("question", eq);
        englishChallenge.put("answer", ea);
    }

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
        this.relaxManager = new RelaxManager(context);
        this.appSettingsManager = new AppSettingsManager(context);
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
    private boolean ensureComponentsInitialized() {
        if (componentsInitialized) {
            return true;
        }
        if (floatingView == null) {
            return false;
        }

        ViewStub challengeStub = floatingView.findViewById(R.id.math_challenge_stub);
        if (challengeStub != null) {
            challengeStub.inflate();
        }
        
        Button submitButton = floatingView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = floatingView.findViewById(R.id.btn_cancel_close);
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);
        if (submitButton == null || cancelButton == null || answerEdit == null) {
            Log.e(TAG, "数学题布局懒加载失败");
            return false;
        }

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
        componentsInitialized = true;
        return true;
    }
    
    /**
     * 显示数学题验证界面
     */
    public void showMathChallenge() {
        if (!ensureComponentsInitialized()) {
            return;
        }

        // 根据用户设置决定题型
        String questionType = appSettingsManager.getMathQuestionType();
        if ("arithmetic_only".equals(questionType)) {
            // 纯算术题
            currentType = TYPE_ARITHMETIC;
        } else if ("english_reading".equals(questionType)) {
            // 英文阅读
            currentType = TYPE_ENGLISH;
        } else {
            // 混合型：按配置比例生成应用题，其余为算术题
            currentType = new Random().nextInt(100) < Const.MIXED_WORD_PROBLEM_PERCENT
                    ? TYPE_WORD : TYPE_ARITHMETIC;
        }
        
        View mathLayout = floatingView.findViewById(R.id.math_challenge_layout);
        TextView questionText = floatingView.findViewById(R.id.tv_math_question);
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);
        TextView resultText = floatingView.findViewById(R.id.tv_math_result);
        android.widget.ScrollView scrollView = floatingView.findViewById(R.id.sv_math_question);
        
        // 确保题目文字可以选中、复制和翻译
        questionText.setTextIsSelectable(true);
        questionText.setLongClickable(true);
        questionText.setFocusable(true);
        questionText.setFocusableInTouchMode(true);
        
        // 确保ScrollView不会拦截文本选择事件
        if (scrollView != null) {
            scrollView.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        
        // 设置自定义文本选择回调，确保菜单能正常显示
        questionText.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                // 标记为正在选择文字
                isTextSelecting = true;
                return true; // 返回true以显示默认菜单（包括复制、全选等）
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                return false; // 使用默认菜单
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                return false; // 让系统处理默认操作
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
                // 文本选择菜单关闭时，延迟取消标记
                handler.postDelayed(() -> {
                    isTextSelecting = false;
                }, 300);
            }
        });
        
        // 根据题型调整UI样式和位置
        if (currentType == TYPE_ENGLISH) {
            // 英文阅读：浅灰色背景、黑字、常规不加粗、位置在关闭按钮下方、宽度占满屏幕
            mathLayout.setBackgroundColor(0xFFF5F5F5); // 浅灰色背景，有利于阅读
            questionText.setTextColor(0xFF000000); // 黑色文字
            questionText.setTypeface(null, android.graphics.Typeface.NORMAL); // 常规不加粗
            
            // 调整位置和宽度：顶部正好低于关闭按钮，宽度占满屏幕
            // 关闭按钮：marginTop=50dp, height=40dp，所以阅读区topMargin=90dp
            android.widget.RelativeLayout.LayoutParams layoutParams = 
                (android.widget.RelativeLayout.LayoutParams) mathLayout.getLayoutParams();
            layoutParams.removeRule(android.widget.RelativeLayout.BELOW);
            layoutParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            int topMarginPx = (int) (90 * context.getResources().getDisplayMetrics().density); // 90dp转px，正好低于关闭按钮
            layoutParams.topMargin = topMarginPx;
            layoutParams.leftMargin = 0; // 左边距为0，占满屏幕
            layoutParams.rightMargin = 0; // 右边距为0，占满屏幕
            mathLayout.setLayoutParams(layoutParams);
            
            // 设置ScrollView最大高度，为阅读题提供更大的显示空间
            if (scrollView != null) {
                int maxHeightPx = (int) (500 * context.getResources().getDisplayMetrics().density); // 500dp转px
                android.widget.LinearLayout.LayoutParams scrollParams = 
                    (android.widget.LinearLayout.LayoutParams) scrollView.getLayoutParams();
                scrollParams.height = maxHeightPx;
                scrollView.setLayoutParams(scrollParams);
            }
            
            // 设置字体大小
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        } else {
            // 其他题型：恢复默认样式
            mathLayout.setBackgroundColor(0xFF333333); // 深灰色背景
            questionText.setTextColor(0xFFFFFFFF); // 白色文字
            questionText.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
            
            // 恢复默认位置和边距
            android.widget.RelativeLayout.LayoutParams layoutParams = 
                (android.widget.RelativeLayout.LayoutParams) mathLayout.getLayoutParams();
            layoutParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            layoutParams.addRule(android.widget.RelativeLayout.BELOW, R.id.top_info_layout);
            layoutParams.topMargin = (int) (10 * context.getResources().getDisplayMetrics().density); // 10dp转px
            layoutParams.leftMargin = (int) (20 * context.getResources().getDisplayMetrics().density); // 20dp转px
            layoutParams.rightMargin = (int) (20 * context.getResources().getDisplayMetrics().density); // 20dp转px
            mathLayout.setLayoutParams(layoutParams);
            
            // 恢复ScrollView默认最大高度
            if (scrollView != null) {
                int maxHeightPx = (int) (300 * context.getResources().getDisplayMetrics().density); // 300dp转px
                android.widget.LinearLayout.LayoutParams scrollParams = 
                    (android.widget.LinearLayout.LayoutParams) scrollView.getLayoutParams();
                scrollParams.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT;
                scrollView.setLayoutParams(scrollParams);
            }
            
            // 根据type动态设置字体大小（应用题使用较小字体）
            int fontSize = currentType == TYPE_WORD ? 16 : 20;
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        }

        /**
         * 获取题目
         */
        String question = unifyGetQuestion();
        currentAnswer = unifyGetAnswer(question);

        /**
         * 原流程
         */
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
                    if (isMathChallengeActive && answerEdit != null && questionText != null) {
                        // 如果正在选择文字，不要强制让EditText获得焦点
                        if (isTextSelecting) {
                            // 检查是否还有文字被选中
                            try {
                                int selectionStart = questionText.getSelectionStart();
                                int selectionEnd = questionText.getSelectionEnd();
                                if (selectionStart < 0 || selectionEnd < 0 || selectionStart == selectionEnd) {
                                    // 没有文字被选中，取消选择标记
                                    isTextSelecting = false;
                                }
                            } catch (Exception e) {
                                isTextSelecting = false;
                            }
                        }
                        
                        // 如果不在选择文字状态，且EditText失去焦点，则重新获得焦点
                        if (!isTextSelecting && !answerEdit.hasFocus()) {
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
     * 封装了不同类型题的获取问题、答案
     * @return
     */
    private String unifyGetQuestion() {
        // 普通算术题
        if(currentType == TYPE_ARITHMETIC){
            return generateMathQuestion();
        // 英文阅读
        }else if(currentType == TYPE_ENGLISH){
            // 先尝试从缓存获取
            String cachedQuestion = englishChallenge.get("question");
            String cachedAnswer = englishChallenge.get("answer");

            // 异步获取新的远程题目，为下次使用做准备
            fetchLatestEnglishReading();

            if (cachedQuestion != null && cachedAnswer != null) {
                Log.d(TAG, "使用缓存的英文阅读题");
                return cachedQuestion;
            }

            // 缓存中没有，使用本地题目作为备选
            Log.w(TAG, "缓存中没有英文阅读题，使用本地算术题作为备选");
            return generateMathQuestion();
        // 情景逻辑题
        }else{
            // 先尝试从缓存获取
            String cachedQuestion = challenge.get("question");
            String cachedAnswer = challenge.get("answer");

            // 异步获取新的远程题目，为下次使用做准备
            fetchLatestChallenge();

            if (cachedQuestion != null && cachedAnswer != null) {
                Log.d(TAG, "使用缓存的远程题目");
                return cachedQuestion;
            }

            // 缓存中没有，使用本地题目作为备选
            Log.w(TAG, "缓存中没有远程题目，使用本地算术题作为备选");
            return generateMathQuestion();
        }
    }

    private String unifyGetAnswer(String question) {
        if(currentType == TYPE_ARITHMETIC){
            return String.valueOf(ArithmeticUtils.getMathAnswer(question));
        }else if(currentType == TYPE_ENGLISH){
            /*缓存获取英文阅读答案*/
            return englishChallenge.get("answer");
        }else{
            /*缓存获取答案*/
            return challenge.get("answer");
        }
    }

    /**
     * 异步获取远程题目并缓存
     */
    public void fetchLatestChallenge() {
        Log.d(TAG, "开始获取最新远程题目");
        
        new Thread(() -> {
            try {
                String remoteChallenge = httpObtainChallenge(currentType);
                if (remoteChallenge != null) {
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(remoteChallenge);
                    String remoteQuestion = (String) jsonResponse.get("question");
                    String remoteAnswer = (String) jsonResponse.get("answer");
                    
                    // 缓存题目和答案
                    challenge.put("question", remoteQuestion);
                    challenge.put("answer", remoteAnswer);
                    
                    Log.d(TAG, "远程题目获取成功并已缓存");
                } else {
                    Log.w(TAG, "获取远程题目失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "获取远程题目时发生异常", e);
            }
        }).start();
    }

    /**
     * http 获取题目
     */
    private String httpObtainChallenge(int type) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

            JSONObject reqJson = new JSONObject();
            reqJson.put("type", type);
            reqJson.put("devId", androidId);

            String response = ContentUtils.doHttpPost(Const.DOMAIN_URL + Const.CHALLENGE,
                    reqJson.toString(), java.util.Collections.singletonMap("Accept", "application/json"));
            return ContentUtils.parseRespJson(response);

        } catch (Exception e) {
            Log.e(TAG, "HTTP请求异常", e);
            return null;
        }
    }

    /**
     * 异步获取英文阅读题目并缓存
     */
    public void fetchLatestEnglishReading() {
        Log.d(TAG, "开始获取最新英文阅读题目");
        
        new Thread(() -> {
            try {
                String remoteChallenge = httpObtainEnglishReading();
                if (remoteChallenge != null) {
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(remoteChallenge);
                    String remoteQuestion = (String) jsonResponse.get("question");
                    String remoteAnswer = (String) jsonResponse.get("answer");
                    
                    // 缓存题目和答案
                    englishChallenge.put("question", remoteQuestion);
                    englishChallenge.put("answer", remoteAnswer);
                    
                    Log.d(TAG, "英文阅读题获取成功并已缓存");
                } else {
                    Log.w(TAG, "获取英文阅读题失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "获取英文阅读题时发生异常", e);
            }
        }).start();
    }

    /**
     * http 获取英文阅读题目
     */
    private String httpObtainEnglishReading() {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            int readingLength = appSettingsManager.getEnglishReadingLength();
            // 确保阅读字数至少为最小值200
            readingLength = Math.max(readingLength, Const.ENGLISH_READING_LENGTH_MIN);

            JSONObject reqJson = new JSONObject();
            reqJson.put("devId", androidId);
            reqJson.put("length", readingLength);

            String response = ContentUtils.doHttpPost(Const.DOMAIN_URL + Const.ENGLISH_READING,
                    reqJson.toString(), java.util.Collections.singletonMap("Accept", "application/json"));
            return ContentUtils.parseRespJson(response);

        } catch (Exception e) {
            Log.e(TAG, "HTTP请求英文阅读题异常", e);
            return null;
        }
    }

    /**
     * 隐藏数学题验证界面
     */
    public void hideMathChallenge() {
        if (floatingView == null || !componentsInitialized) return;
        
        View mathLayout = floatingView.findViewById(R.id.math_challenge_layout);
        EditText answerEdit = floatingView.findViewById(R.id.et_math_answer);
        
        // 隐藏输入法
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(answerEdit.getWindowToken(), 0);
        }
        
        // 清除EditText焦点
        answerEdit.clearFocus();
        
        // 恢复默认样式和位置（为下次显示做准备）
        mathLayout.setBackgroundColor(0xFF333333); // 恢复深灰色背景
        TextView questionText = floatingView.findViewById(R.id.tv_math_question);
        if (questionText != null) {
            questionText.setTextColor(0xFFFFFFFF); // 恢复白色文字
            questionText.setTypeface(null, android.graphics.Typeface.BOLD); // 恢复加粗
        }
        
        // 恢复ScrollView默认最大高度
        android.widget.ScrollView scrollView = floatingView.findViewById(R.id.sv_math_question);
        if (scrollView != null) {
            android.widget.LinearLayout.LayoutParams scrollParams = 
                (android.widget.LinearLayout.LayoutParams) scrollView.getLayoutParams();
            scrollParams.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT;
            scrollView.setLayoutParams(scrollParams);
        }
        
        // 恢复默认位置和边距
        android.widget.RelativeLayout.LayoutParams mathLayoutParams = 
            (android.widget.RelativeLayout.LayoutParams) mathLayout.getLayoutParams();
        mathLayoutParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        mathLayoutParams.addRule(android.widget.RelativeLayout.BELOW, R.id.top_info_layout);
        mathLayoutParams.topMargin = (int) (10 * context.getResources().getDisplayMetrics().density); // 10dp转px
        mathLayoutParams.leftMargin = (int) (20 * context.getResources().getDisplayMetrics().density); // 20dp转px
        mathLayoutParams.rightMargin = (int) (20 * context.getResources().getDisplayMetrics().density); // 20dp转px
        mathLayout.setLayoutParams(mathLayoutParams);
        
        // 隐藏数学题区域
        mathLayout.setVisibility(View.GONE);
        isMathChallengeActive = false;
        isTextSelecting = false; // 重置文字选择状态
        
        // 通知AccessibilityService数学题验证结束
        if (accessibilityService != null) {
            accessibilityService.onMathChallengeEnd();
        }
        
        // 重新设置悬浮窗为不可获得焦点，避免影响其他应用
        // 保持与showFloatingWindow中的标志位设置一致
        this.layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(floatingView, this.layoutParams);
        
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
            if (currentAnswer.equalsIgnoreCase(userAnswer)) {
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
                Log.d(TAG, "数学题回答错误: " + userAnswer + " (正确答案: " + currentAnswer + ")");
                resultText.setText("❌ 答案错误，请重新计算");
                resultText.setTextColor(context.getResources().getColor(android.R.color.holo_red_light));
                resultText.setVisibility(View.VISIBLE);
                
                // 清空输入框
                answerEdit.setText("");
                
                // 1 秒后生成新题目，保持输入法显示
                handler.postDelayed(() -> {
                    // 生成新题目，但不重新初始化悬浮窗参数
                    TextView questionText = floatingView.findViewById(R.id.tv_math_question);
                    String question = unifyGetQuestion();
                    currentAnswer = unifyGetAnswer(question);
                    questionText.setText(question);

                    // 确保题目文字可以选中、复制和翻译
                    questionText.setTextIsSelectable(true);
                    questionText.setLongClickable(true);
                    questionText.setFocusable(true);
                    questionText.setFocusableInTouchMode(true);
                    
                    // 重新设置自定义文本选择回调
                    questionText.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                        @Override
                        public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                            isTextSelecting = true;
                            return true;
                        }

                        @Override
                        public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                            return false;
                        }

                        @Override
                        public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                            return false;
                        }

                        @Override
                        public void onDestroyActionMode(android.view.ActionMode mode) {
                            handler.postDelayed(() -> {
                                isTextSelecting = false;
                            }, 300);
                        }
                    });

                    // 根据题型设置样式（英文阅读保持白底黑字、常规不加粗）
                    if (currentType == TYPE_ENGLISH) {
                        questionText.setTextColor(0xFF000000); // 黑色文字
                        questionText.setTypeface(null, android.graphics.Typeface.NORMAL); // 常规不加粗
                        questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    } else {
                        questionText.setTextColor(0xFFFFFFFF); // 白色文字
                        questionText.setTypeface(null, android.graphics.Typeface.BOLD); // 加粗
                        int fontSize = currentType == TYPE_WORD ? 16 : 20;
                        questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
                    }

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
        String difficultyMode = appSettingsManager.getMathDifficultyMode();

        if ("custom".equals(difficultyMode)) {
            // 使用自定义难度设置
            int additionDigits = appSettingsManager.getMathAdditionDigits();
            int subtractionDigits = appSettingsManager.getMathSubtractionDigits();
            int multiplierDigits = appSettingsManager.getMathMultiplicationMultiplierDigits();
            int multiplicandDigits = appSettingsManager.getMathMultiplicationMultiplicandDigits();

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
