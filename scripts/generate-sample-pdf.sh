#!/usr/bin/env bash
# 运行 PDF 解析单元测试（会在临时目录生成并验证示例 PDF）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend"
echo "[*] 运行 PdfParseServiceTest …"
mvn -q test -Dtest=PdfParseServiceTest
echo "[✓] PDF 解析测试通过。请上传含文本层的真实 PDF 到平台进行出题。"
