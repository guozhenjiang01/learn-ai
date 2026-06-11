#!/usr/bin/env python3
"""用 pyte 虚拟终端渲染原始 terminal log → 人类可读的干净文本。

用法: python3 terminal-render.py <raw_log_path>
输出: 干净文本到 stdout
"""

import sys
import os

# pyte 安装在用户目录下
sys.path.insert(0, os.path.expanduser('~/.local/lib/python3.12/site-packages'))
sys.path.insert(0, os.path.expanduser('~/.local/lib/python3.11/site-packages'))

try:
    import pyte
except ImportError:
    print("ERROR: pyte not installed", file=sys.stderr)
    sys.exit(1)


def render_terminal_log(filepath: str) -> str:
    """读取 raw terminal log，用 pyte 模拟终端渲染，返回干净文本。"""
    with open(filepath, 'rb') as f:
        raw_bytes = f.read()

    if len(raw_bytes) == 0:
        return ""

    # 解码：终端输出以 UTF-8 为主，用 replace 处理异常字节
    text = raw_bytes.decode('utf-8', errors='replace')

    # 估算需要的行数：每 5000 字节约 1 行，至少 2000 行
    estimated_lines = max(2000, len(raw_bytes) // 80 + 100)
    # 上限 20000 行防止异常大文件 OOM
    rows = min(estimated_lines, 20000)
    cols = 120  # Hermes 终端标准宽度

    screen = pyte.Screen(cols, rows)
    stream = pyte.Stream(screen)
    stream.feed(text)

    # 提取非空行
    lines = [line.rstrip() for line in screen.display if line.strip()]

    # 裁剪掉 Hermes 启动横幅和系统信息，从用户第一条输入开始
    lines = trim_banner(lines)

    return '\n'.join(lines)


def trim_banner(lines):
    """找到第一条用户输入，裁掉前面的系统横幅。"""
    # 找到 "Welcome to Hermes Agent" 行
    welcome_idx = None
    for i, line in enumerate(lines):
        if 'Welcome to Hermes Agent' in line:
            welcome_idx = i
            break
    if welcome_idx is None:
        return lines  # 无横幅，原样返回

    # 从 welcome 之后找第一个 '●' 提示符（可能跟用户输入在同一行）
    prompt_idx = None
    for i in range(welcome_idx + 1, len(lines)):
        if lines[i].strip().startswith('●'):
            prompt_idx = i
            break

    if prompt_idx is None:
        # 没找到 ●，尝试从 welcome 后第二条分隔线之后开始
        sep_count = 0
        for i in range(welcome_idx + 1, len(lines)):
            if lines[i].startswith('──') or lines[i].startswith('━━'):
                sep_count += 1
                if sep_count >= 2:
                    # 分隔线之后的行就是用户输入
                    if i + 1 < len(lines):
                        return lines[i + 1:]
                    break
        return lines  # 回退

    # ● 和用户输入在同一行 → 从该行开始
    # ● 单独一行（旧格式）→ 从下一行开始
    if lines[prompt_idx].strip() == '●':
        start = prompt_idx + 1
    else:
        start = prompt_idx
    if start >= len(lines):
        return lines
    return lines[start:]


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: terminal-render.py <raw_log_file>", file=sys.stderr)
        sys.exit(2)

    filepath = sys.argv[1]
    if not os.path.isfile(filepath):
        print(f"ERROR: file not found: {filepath}", file=sys.stderr)
        sys.exit(3)

    try:
        result = render_terminal_log(filepath)
        print(result)
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(4)
