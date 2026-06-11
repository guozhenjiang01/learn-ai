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
    return '\n'.join(lines)


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
