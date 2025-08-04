package com.book.mask.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 超简化版调试信息接收控制器
 * 直接接收JSON字符串并格式化打印
 */
@RestController
@RequestMapping("/antiAddict")
public class UltraSimpleDebugController {
    
    private static final Logger logger = LoggerFactory.getLogger(UltraSimpleDebugController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 接收调试信息上报 - 直接接收JSON字符串
     * POST /antiAddict/runLog
     */
    @PostMapping("/runLog")
    public ResponseEntity<String> receiveDebugInfo(@RequestBody String jsonData) {
        try {
            // 获取当前时间
            String currentTime = LocalDateTime.now().format(formatter);
            
            // 解析JSON并格式化打印
            JsonNode jsonNode = objectMapper.readTree(jsonData);
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
        
        // 打印完整的JSON（格式化）
        try {
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            logger.info("📋 完整JSON数据:");
            logger.info(prettyJson);
        } catch (Exception e) {
            logger.error("格式化JSON失败", e);
        }
        
        logger.info("==================================================================================");
        logger.info("📊 调试信息打印完成");
        logger.info("==================================================================================");
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UltraSimpleDebug接收服务运行正常");
    }
} 