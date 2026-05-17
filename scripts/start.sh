#!/usr/bin/env bash
# 一键启动 Docker 部署（需在项目根目录执行）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "[!] 未找到 .env，正在从 .env.example 复制…"
  cp .env.example .env
  echo "    请编辑 .env 填入 ZHIPU_API_KEY 或 DEEPSEEK_API_KEY 后重新运行本脚本。"
  exit 1
fi

if docker compose version &>/dev/null; then
  COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE="docker-compose"
else
  echo "[!] 未安装 Docker Compose，请先安装 Docker。"
  exit 1
fi

echo "[*] 构建并启动服务…"
$COMPOSE up -d --build

echo ""
echo "[✓] 启动完成"
echo "    前端: http://localhost"
echo "    API:  http://localhost/api/ping"
echo "    查看日志: $COMPOSE logs -f"
