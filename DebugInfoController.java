package com.book.mask.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 调试信息接收控制器
 * 接收Android端上报的调试信息并格式化打印
 */
@RestController
@RequestMapping("/antiAddict")
public class DebugInfoController {
    
    private static final Logger logger = LoggerFactory.getLogger(DebugInfoController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 接收调试信息上报
     * POST /antiAddict/runLog
     */
    @PostMapping("/runLog")
    public ResponseEntity<String> receiveDebugInfo(@RequestBody String jsonData) {
        try {
            // 解析JSON数据
            JsonNode jsonNode = objectMapper.readTree(jsonData);
            
            // 获取当前时间
            String currentTime = LocalDateTime.now().format(formatter);
            
            // 格式化打印调试信息
            printFormattedDebugInfo(jsonNode, currentTime);
            
            // 返回成功响应
            return ResponseEntity.ok("调试信息接收成功");
            
        } catch (Exception e) {
            logger.error("接收调试信息失败", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("调试信息格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 格式化打印调试信息
     */
    private void printFormattedDebugInfo(JsonNode jsonNode, String receiveTime) {
        logger.info("==================================================================================");
        logger.info("📱 调试信息接收时间: {}", receiveTime);
        logger.info("==================================================================================");
        
        // 基本信息
        if (jsonNode.has("timestamp")) {
            logger.info("⏰ 上报时间戳: {}", jsonNode.get("timestamp").asText());
        }
        if (jsonNode.has("reportTime")) {
            logger.info("📅 上报时间: {}", jsonNode.get("reportTime").asText());
        }
        
        logger.info("--------------------------------------------------");
        
        // 事件信息
        logger.info("📡 事件信息:");
        if (jsonNode.has("lastEventType")) {
            logger.info("   事件类型: {}", jsonNode.get("lastEventType").asText());
        }
        if (jsonNode.has("lastEventTimeFormatted")) {
            logger.info("   事件时间: {}", jsonNode.get("lastEventTimeFormatted").asText());
        }
        if (jsonNode.has("findTextInNodeTimeFormatted")) {
            logger.info("   文本检测时间: {}", jsonNode.get("findTextInNodeTimeFormatted").asText());
        }
        
        logger.info("--------------------------------------------------");
        
        // 调试时间戳
        logger.info("🔧 调试时间戳:");
        printTimestamp("h0", jsonNode);
        printTimestamp("h1", jsonNode);
        printTimestamp("h7", jsonNode);
        printTimestamp("h8", jsonNode);
        
        logger.info("--------------------------------------------------");
        
        // 界面状态
        logger.info("🖥️ 界面状态:");
        if (jsonNode.has("currentInterface")) {
            logger.info("   当前界面: {}", jsonNode.get("currentInterface").asText());
        }
        if (jsonNode.has("forceCheck")) {
            logger.info("   强制检查: {}", jsonNode.get("forceCheck").asText());
        }
        
        logger.info("--------------------------------------------------");
        
        // 当前APP信息
        if (jsonNode.has("currentApp") && !jsonNode.get("currentApp").isNull()) {
            JsonNode currentApp = jsonNode.get("currentApp");
            logger.info("📱 当前APP信息:");
            if (currentApp.has("appName")) {
                logger.info("   APP名称: {}", currentApp.get("appName").asText());
            }
            if (currentApp.has("packageName")) {
                logger.info("   包名: {}", currentApp.get("packageName").asText());
            }
            if (currentApp.has("className")) {
                logger.info("   类名: {}", currentApp.get("className").asText());
            }
            if (currentApp.has("appState")) {
                logger.info("   APP状态: {}", currentApp.get("appState").asText());
            }
            if (currentApp.has("hiddenTimestampFormatted")) {
                logger.info("   隐藏时间: {}", currentApp.get("hiddenTimestampFormatted").asText());
            }
            if (currentApp.has("isManuallyHidden")) {
                logger.info("   手动隐藏: {}", currentApp.get("isManuallyHidden").asText());
            }
        } else {
            logger.info("📱 当前APP: 无");
        }
        
        logger.info("--------------------------------------------------");
        
        // 悬浮窗状态
        if (jsonNode.has("isFloatingWindowVisible")) {
            logger.info("🪟 悬浮窗状态: {}", 
                jsonNode.get("isFloatingWindowVisible").asBoolean() ? "显示" : "隐藏");
        }
        
        // 设备信息
        if (jsonNode.has("deviceInfo")) {
            JsonNode deviceInfo = jsonNode.get("deviceInfo");
            logger.info("--------------------------------------------------");
            logger.info("📱 设备信息:");
            if (deviceInfo.has("brand")) {
                logger.info("   品牌: {}", deviceInfo.get("brand").asText());
            }
            if (deviceInfo.has("model")) {
                logger.info("   型号: {}", deviceInfo.get("model").asText());
            }
            if (deviceInfo.has("androidVersion")) {
                logger.info("   Android版本: {}", deviceInfo.get("androidVersion").asText());
            }
            if (deviceInfo.has("appVersion")) {
                logger.info("   应用版本: {}", deviceInfo.get("appVersion").asText());
            }
        }
        
        logger.info("==================================================================================");
        logger.info("📊 调试信息打印完成");
        logger.info("==================================================================================");
    }
    
    /**
     * 打印时间戳信息
     */
    private void printTimestamp(String key, JsonNode jsonNode) {
        if (jsonNode.has(key)) {
            String value = jsonNode.get(key).asText();
            String formattedKey = key + "Formatted";
            if (jsonNode.has(formattedKey)) {
                String formattedValue = jsonNode.get(formattedKey).asText();
                logger.info("   {}: {} ({})", key, formattedValue, value);
            } else {
                logger.info("   {}: {}", key, value);
            }
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("DebugInfo接收服务运行正常");
    }
} 