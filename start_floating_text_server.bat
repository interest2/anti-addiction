@echo off
chcp 65001 > nul
echo 🌟 启动悬浮窗动态文字测试服务器
echo ================================
echo.

REM 检查Python是否安装
python --version > nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到Python，请先安装Python
    echo 📥 下载地址: https://www.python.org/downloads/
    pause
    exit /b 1
)

REM 检查服务器文件是否存在
if not exist "floating_text_test_server.py" (
    echo ❌ 错误: 未找到服务器文件 floating_text_test_server.py
    echo 📂 请确保文件在当前目录中
    pause
    exit /b 1
)

echo ✅ Python环境检查通过
echo 🚀 正在启动服务器...
echo.

REM 启动Python服务器
python floating_text_test_server.py

echo.
echo 🛑 服务器已停止
pause 