#!/usr/bin/env python
"""
将本地音频（mp3/m4a/wav/aac 等）批量转换为 16kHz / 单声道 / PCM16 little-endian，
输出到 app/src/full/assets/asr-test/，供 Android「个性化→答题→临时测试」页测试不同长度识别。

依赖：ffmpeg 已加入 PATH（或通过 --ffmpeg 指定）。

用法：
  python tools/package_asr_audio.py D:\\recordings\\a.mp3
  python tools/package_asr_audio.py D:\\recordings\\a.mp3 D:\\recordings\\b.m4a
  python tools/package_asr_audio.py --dir D:\\recordings

转换后的每个 .pcm 文件即为一份独立音频；Android 端会把它按 5/10/20/25/30/31/35/40/45/60 秒
前缀分别识别。
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

SAMPLE_RATE = 16_000
REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = REPO_ROOT / "app" / "src" / "full" / "assets" / "asr-test"
DEFAULT_FFMPEG = "ffmpeg"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="把音频转换为 ASR 测试用的 PCM16 资源")
    parser.add_argument("inputs", nargs="*", help="一个或多个音频文件")
    parser.add_argument(
        "--dir",
        help="批量转换该目录下所有 mp3/m4a/wav/aac/flac/ogg（与 inputs 可叠加）",
    )
    parser.add_argument(
        "--output-dir",
        default=str(DEFAULT_OUTPUT_DIR),
        help="输出目录，默认指向 full flavor 的 assets/asr-test",
    )
    parser.add_argument(
        "--ffmpeg",
        default=DEFAULT_FFMPEG,
        help="ffmpeg 可执行文件路径或命令名",
    )
    return parser.parse_args()


def convert_one(ffmpeg: str, audio: Path, output_dir: Path) -> Path:
    output = output_dir / (audio.stem + ".pcm")
    command = [
        ffmpeg,
        "-nostdin",
        "-v", "error",
        "-y",
        "-i", str(audio),
        "-vn",
        "-ac", "1",
        "-ar", str(SAMPLE_RATE),
        "-f", "s16le",
        str(output),
    ]
    completed = subprocess.run(command, capture_output=True, check=False)
    if completed.returncode != 0:
        error_text = completed.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError("ffmpeg 转换失败：" + audio.name + "：" + (error_text or "未知错误"))
    if not output.is_file():
        raise RuntimeError("未生成输出文件：" + str(output))
    return output


def report(output: Path) -> None:
    data = output.read_bytes()
    if len(data) % 2 != 0:
        print("警告：PCM 字节数为奇数：" + str(output))
    seconds = len(data) / 2 / SAMPLE_RATE
    print(f"  {output.name}\t{len(data)} 字节\t{seconds:.3f} 秒\tSHA-256={hashlib.sha256(data).hexdigest()}")


def main() -> int:
    args = parse_arguments()
    if shutil.which(args.ffmpeg) is None and not Path(args.ffmpeg).is_file():
        print("错误：未找到 ffmpeg。请安装并加入 PATH，或通过 --ffmpeg 指定。", file=sys.stderr)
        return 1

    files: list[Path] = []
    for text in args.inputs:
        path = Path(text).expanduser().resolve()
        if not path.is_file():
            print(f"错误：输入文件不存在：{path}", file=sys.stderr)
            return 1
        files.append(path)
    if args.dir:
        directory = Path(args.dir).expanduser().resolve()
        if not directory.is_dir():
            print(f"错误：目录不存在：{directory}", file=sys.stderr)
            return 1
        supported = {".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg"}
        files.extend(
            p for p in sorted(directory.iterdir())
            if p.is_file() and p.suffix.lower() in supported
        )
    files = list(dict.fromkeys(files))  # 去重且保持顺序
    if not files:
        print("错误：没有输入音频。请传入文件路径或 --dir 目录。", file=sys.stderr)
        return 1

    output_dir = Path(args.output_dir).expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    print("转换到：" + str(output_dir))
    converted = 0
    for audio in files:
        try:
            output = convert_one(args.ffmpeg, audio, output_dir)
            report(output)
            converted += 1
        except Exception as error:
            print(f"失败：{audio.name}：{error}", file=sys.stderr)
    print(f"完成：共转换 {converted}/{len(files)} 份。")
    print("请重新构建 fullDebug 后再到手机端「个性化→答题→临时测试」查看。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
