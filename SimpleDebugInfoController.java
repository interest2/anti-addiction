package com.book.mask.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 简化版调试信息接收控制器
 * 使用Map接收JSON数据，避免定义复杂的实体类
 */
@RestController
@RequestMapping("/antiAddict")
public class SimpleDebugInfoController {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleDebugInfoController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 接收调试信息上报 - 使用Map接收
     * POST /antiAddict/runLog
     */
    @PostMapping("/runLog")
    public ResponseEntity<String> receiveDebugInfo(@RequestBody Map<String, Object> debugInfo) {
        try {
            // 获取当前时间
            String currentTime = LocalDateTime.now().format(formatter);
            
            // 格式化打印调试信息
            printFormattedDebugInfo(debugInfo, currentTime);
            
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
    @SuppressWarnings("unchecked")
    private void printFormattedDebugInfo(Map<String, Object> debugInfo, String receiveTime) {
        logger.info("==================================================================================");
        logger.info("📱 调试信息接收时间: {}", receiveTime);
        logger.info("==================================================================================");
        
        // 基本信息
        if (debugInfo.containsKey("timestamp")) {
            logger.info("⏰ 上报时间戳: {}", debugInfo.get("timestamp"));
        }
        if (debugInfo.containsKey("reportTime")) {
            logger.info("📅 上报时间: {}", debugInfo.get("reportTime"));
        }
        
        logger.info("--------------------------------------------------");
        
        // 事件信息
        logger.info("📡 事件信息:");
        if (debugInfo.containsKey("lastEventType")) {
            logger.info("   事件类型: {}", debugInfo.get("lastEventType"));
        }
        if (debugInfo.containsKey("lastEventTimeFormatted")) {
            logger.info("   事件时间: {}", debugInfo.get("lastEventTimeFormatted"));
        }
        if (debugInfo.containsKey("findTextInNodeTimeFormatted")) {
            logger.info("   文本检测时间: {}", debugInfo.get("findTextInNodeTimeFormatted"));
        }
        
        logger.info("--------------------------------------------------");
        
        // 调试时间戳
        logger.info("🔧 调试时间戳:");
        printTimestamp("h0", debugInfo);
        printTimestamp("h1", debugInfo);
        printTimestamp("h7", debugInfo);
        printTimestamp("h8", debugInfo);
        
        logger.info("--------------------------------------------------");
        
        // 界面状态
        logger.info("🖥️ 界面状态:");
        if (debugInfo.containsKey("currentInterface")) {
            logger.info("   当前界面: {}", debugInfo.get("currentInterface"));
        }
        if (debugInfo.containsKey("forceCheck")) {
            logger.info("   强制检查: {}", debugInfo.get("forceCheck"));
        }
        
        logger.info("--------------------------------------------------");
        
        // 当前APP信息
        if (debugInfo.containsKey("currentApp") && debugInfo.get("currentApp") != null) {
            Map<String, Object> currentApp = (Map<String, Object>) debugInfo.get("currentApp");
            logger.info("📱 当前APP信息:");
            if (currentApp.containsKey("appName")) {
                logger.info("   APP名称: {}", currentApp.get("appName"));
            }
            if (currentApp.containsKey("packageName")) {
                logger.info("   包名: {}", currentApp.get("packageName"));
            }
            if (currentApp.containsKey("className")) {
                logger.info("   类名: {}", currentApp.get("className"));
            }
            if (currentApp.containsKey("appState")) {
                logger.info("   APP状态: {}", currentApp.get("appState"));
            }
            if (currentApp.containsKey("hiddenTimestampFormatted")) {
                logger.info("   隐藏时间: {}", currentApp.get("hiddenTimestampFormatted"));
            }
            if (currentApp.containsKey("isManuallyHidden")) {
                logger.info("   手动隐藏: {}", currentApp.get("isManuallyHidden"));
            }
        } else {
            logger.info("📱 当前APP: 无");
        }
        
        logger.info("--------------------------------------------------");
        
        // 悬浮窗状态
        if (debugInfo.containsKey("isFloatingWindowVisible")) {
            boolean isVisible = (Boolean) debugInfo.get("isFloatingWindowVisible");
            logger.info("🪟 悬浮窗状态: {}", isVisible ? "显示" : "隐藏");
        }
        
        // 设备信息
        if (debugInfo.containsKey("deviceInfo")) {
            Map<String, Object> deviceInfo = (Map<String, Object>) debugInfo.get("deviceInfo");
            logger.info("--------------------------------------------------");
            logger.info("📱 设备信息:");
            if (deviceInfo.containsKey("brand")) {
                logger.info("   品牌: {}", deviceInfo.get("brand"));
            }
            if (deviceInfo.containsKey("model")) {
                logger.info("   型号: {}", deviceInfo.get("model"));
            }
            if (deviceInfo.containsKey("androidVersion")) {
                logger.info("   Android版本: {}", deviceInfo.get("androidVersion"));
            }
            if (deviceInfo.containsKey("appVersion")) {
                logger.info("   应用版本: {}", deviceInfo.get("appVersion"));
            }
        }
        
        logger.info("==================================================================================");
        logger.info("📊 调试信息打印完成");
        logger.info("==================================================================================");
    }
    
    /**
     * 打印时间戳信息
     */
    private void printTimestamp(String key, Map<String, Object> debugInfo) {
        if (debugInfo.containsKey(key)) {
            Object value = debugInfo.get(key);
            String formattedKey = key + "Formatted";
            if (debugInfo.containsKey(formattedKey)) {
                Object formattedValue = debugInfo.get(formattedKey);
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
        return ResponseEntity.ok("SimpleDebugInfo接收服务运行正常");
    }
} 