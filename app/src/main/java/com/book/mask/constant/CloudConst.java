package com.book.mask.constant;

public class CloudConst {

    // 云端接口配置
    public static final String DOMAIN_URL = "https://www.ratetend.com:5001/antiAddict"; // 请替换为实际的地址
    public static final String LLM_PATH_V2 = "/llm/v2";
    public static final String CHALLENGE = "/challenge";
    public static final String ENGLISH_READING = "/reading";
    // 复述题：获取故事
    public static final String RETELLING_STORY = "/challenge/retelling/story";
    // 复述题：官方服务评分网关（自定义 OpenAI 兼容服务走 OpenAiChatClient，不走此路径）
    public static final String RETELLING_SCORE = "/challenge/retelling/score";
    public static final String REPORT_PATH = "/report";
    public static final String GET_CONFIG_PATH = "/getConfig";
    public static final String CONTENT_TYPE = "application/json";

}
