# 云服务器部署指南（CentOS 8）

适用于 **腾讯云 / 阿里云 / 华为云** 等任意云厂商的 **CentOS 8** 虚拟机。本项目使用 **Docker + docker-compose** 一键启动前后端。

> CentOS 8 已停止维护（EOL），若 `dnf install` 报源不可用，请先完成 [§2 修复软件源](#2-修复-centos-8-软件源可选)。

## 1. 架构

```
公网 IP :80
  └── frontend 容器 (Nginx)  → 静态页面 + 反代 /api
        └── backend 容器 (8080，仅容器内访问)
              └── 卷 ./data → SQLite + PDF 上传
```

## 2. 修复 CentOS 8 软件源（可选）

若出现 `Failed to download metadata` 或找不到包，切换到 vault 源：

```bash
sudo sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*
sudo sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*
sudo dnf clean all && sudo dnf makecache
```

## 3. 安全组与防火墙

### 3.1 云控制台安全组

| 端口 | 说明 |
|------|------|
| 22 | SSH |
| 80 | HTTP（访问站点） |
| 443 | HTTPS（配置证书后） |

**不要**对公网开放 8080。

### 3.2 firewalld

```bash
sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

## 4. 安装 Docker（Docker CE 官方源）

CentOS 8 默认源通常**没有** `docker-compose-plugin`，推荐安装 **Docker CE + 独立 compose 二进制**。

```bash
# 基础工具
sudo dnf install -y yum-utils git curl

# 添加 Docker 官方仓库
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# 安装 Docker
sudo dnf install -y docker-ce docker-ce-cli containerd.io

# 启动并开机自启
sudo systemctl enable --now docker

# 当前用户免 sudo（root 可跳过）
sudo usermod -aG docker "$USER"
```

非 root 用户需**退出 SSH 重新登录**后执行 `docker ps`。

### 4.1 安装 docker-compose

```bash
sudo curl -L "https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

docker --version
docker-compose --version
```

> 全文使用 **`docker-compose`**（带连字符），不要用 `docker compose`。

## 5. 部署应用

```bash
# 克隆（替换为你的仓库地址）
git clone https://github.com/Leovany/ai-learning-platform.git
cd ai-learning-platform

# 配置大模型密钥
cp .env.example .env
vi .env    # 填入 ZHIPU_API_KEY、DEEPSEEK_API_KEY 等

# 构建并启动（首次较慢，需下载 Maven/Node 依赖）
docker-compose up -d --build

# 查看状态
docker-compose ps
docker-compose logs -f
```

浏览器访问：**http://你的公网IP**

服务器上自检：

```bash
curl -s http://127.0.0.1/api/ping
curl -s http://127.0.0.1/api/llm/config
```

## 6. 环境变量（`.env`）

```env
LLM_PROVIDER=auto
LLM_MODEL=glm-4.7-flash
ZHIPU_API_KEY=你的智谱密钥
DEEPSEEK_API_KEY=你的DeepSeek密钥
```

## 7. 数据持久化

`docker-compose.yml` 挂载 `./data`，重建容器不丢数据：

```bash
# 备份
tar czvf backup-data-$(date +%F).tar.gz data/
```

## 8. 常用运维

```bash
cd ~/ai-learning-platform

git pull
docker-compose up -d --build

docker-compose logs -f backend
docker-compose down
```

## 9. HTTPS（可选）

1. 域名 A 记录指向服务器公网 IP  
2. 使用云厂商免费 SSL 证书，或 certbot  
3. 宿主机 Nginx/Caddy 将 443 反代到 `127.0.0.1:80`

## 10. 常见问题

| 现象 | 处理 |
|------|------|
| `dnf` 无法下载 | 按 §2 切换 vault 源 |
| 没有 `docker-compose-plugin` | 正常，用 §4.1 独立二进制 |
| 构建 OOM | 升级到 **2G+ 内存**，或本地构建镜像后推送到云镜像仓库 |
| 外网无法访问 | 安全组放行 80 + `firewall-cmd --list-all` |
| 上传/卷权限问题 | `sudo chcon -Rt svirt_sandbox_file_t data/`（SELinux Enforcing 时） |
| `docker info` 显示 podman | 卸载 podman 冲突包，改装 §4 的 docker-ce |

## 11. 推荐配置

| 项目 | 建议 |
|------|------|
| 系统 | CentOS 8.x（或迁移至 Rocky Linux 8 / AlmaLinux 8） |
| CPU/内存 | 2 核 2G 起 |
| 磁盘 | 20G+（含 PDF 与数据库） |
| 带宽 | 3 Mbps 起 |
