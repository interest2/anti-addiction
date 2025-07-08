#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
悬浮窗动态文字内容测试服务器
提供HTTP接口返回动态文字内容用于测试
"""

import json
import socket
import threading
import time
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

class FloatingTextHandler(BaseHTTPRequestHandler):
    
    def do_OPTIONS(self):
        """处理CORS预检请求"""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def do_GET(self):
        """处理GET请求，返回简单的API信息"""
        response = {
            "name": "悬浮窗动态文字API",
            "version": "1.0.0",
            "endpoints": {
                "/api/floating-text": "获取动态文字内容 (POST)"
            },
            "timestamp": datetime.now().isoformat()
        }
        
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))
    
    def do_POST(self):
        """处理POST请求"""
        try:
            # 解析URL路径
            path = urlparse(self.path).path
            
            if path == '/api/floating-text':
                self.handle_floating_text_request()
            else:
                self.send_error(404, "API接口不存在")
                
        except Exception as e:
            print(f"❌ 处理POST请求时发生错误: {e}")
            self.send_error(500, f"服务器内部错误: {str(e)}")
    
    def handle_floating_text_request(self):
        """处理悬浮窗文字请求"""
        try:
            # 读取请求体
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
            if content_length > 0:
                request_data = json.loads(post_data.decode('utf-8'))
                print(f"📨 收到请求: {request_data}")
            else:
                request_data = {}
            
            # 生成动态文字内容
            text_content = self.generate_dynamic_text()
            
            # 构建响应
            response = {
                "success": True,
                "text": text_content,
                "timestamp": datetime.now().isoformat(),
                "message": "文字内容获取成功"
            }
            
            print(f"📤 返回文字内容: {text_content}")
            
            # 发送响应
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))
            
        except json.JSONDecodeError:
            print("❌ 请求数据不是有效的JSON格式")
            self.send_error(400, "请求数据格式错误")
        except Exception as e:
            print(f"❌ 处理悬浮窗文字请求时发生错误: {e}")
            self.send_error(500, f"处理请求失败: {str(e)}")
    
    def generate_dynamic_text(self):
        """生成动态文字内容"""
        current_time = datetime.now()
        
        # 根据时间生成不同的文字内容
        hour = current_time.hour
        minute = current_time.minute
        
        if hour < 6:
            base_text = "🌙 夜深了，注意休息哦"
        elif hour < 12:
            base_text = "🌅 早上好！新的一天开始了"
        elif hour < 18:
            base_text = "☀️ 下午好！适度使用手机"
        else:
            base_text = "🌆 晚上好！放松一下吧"
        
        # 添加时间信息
        time_text = f"当前时间: {current_time.strftime('%H:%M:%S')}"
        
        # 添加一些随机的提示语
        tips = [
            "记得保护眼睛👀",
            "适当休息很重要💪",
            "保持良好作息😊",
            "多喝水有益健康💧",
            "户外活动更健康🌿"
        ]
        
        import random
        tip = random.choice(tips)
        
        # 组合最终文字
        final_text = f"{base_text}\n{time_text}\n{tip}"
        
        return final_text
    
    def log_message(self, format, *args):
        """自定义日志格式"""
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {format % args}")

def find_free_port(start_port=8003):
    """查找可用的端口"""
    for port in range(start_port, start_port + 10):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(('', port))
            sock.close()
            return port
        except OSError:
            continue
    return None

def start_server():
    """启动HTTP服务器"""
    port = find_free_port(8003)
    if port is None:
        print("❌ 无法找到可用端口 (8003-8012)")
        return
    
    server_address = ('', port)
    httpd = HTTPServer(server_address, FloatingTextHandler)
    
    print(f"🚀 悬浮窗动态文字测试服务器启动成功!")
    print(f"🌐 服务地址: http://localhost:{port}")
    print(f"📱 Android地址: http://10.0.2.2:{port}")
    print(f"📡 API接口: http://10.0.2.2:{port}/api/floating-text")
    print(f"⏰ 启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 50)
    print("📝 接口说明:")
    print("  POST /api/floating-text - 获取动态文字内容")
    print("  GET  /                 - 获取API信息")
    print("=" * 50)
    print("💡 提示: 按 Ctrl+C 停止服务器")
    print()
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 服务器停止中...")
        httpd.server_close()
        print("✅ 服务器已停止")

if __name__ == "__main__":
    print("🌟 悬浮窗动态文字内容测试服务器")
    print("=" * 50)
    
    try:
        start_server()
    except Exception as e:
        print(f"❌ 启动失败: {e}")
        input("按回车键退出...") 