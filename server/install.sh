#!/usr/bin/env bash
#
# Memento Server 一键安装脚本
# ============================
# 用法:
#   sudo bash install.sh            # 默认端口 49033
#   sudo bash install.sh 8080       # 指定端口
#
# 功能:
#   1. 安装代码到 /opt/memento/server
#   2. 生成配对密钥 (deviceToken + encryptionSecret)，或保留已有配置
#   3. 创建 systemd 服务 (memento.service) 并设置开机自启
#   4. 启动并自检
#
# 幂等: 重复执行不会覆盖已有 config.json 和数据
# ============================

set -euo pipefail

PORT="${1:-49033}"
INSTALL_DIR="/opt/memento"
UNIT_NAME="memento.service"
UNIT_FILE="/etc/systemd/system/${UNIT_NAME}"
APP_OWNER="${SUDO_USER:-$(whoami)}"

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # 脚本所在目录（server/）
REPO_ROOT="$(cd "${SRC_DIR}/.." && pwd)"

echo "==> [1/6] 环境检查"
command -v node >/dev/null 2>&1 || { echo "错误: 需要 Node.js >= 18"; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "错误: 需要 openssl"; exit 1; }
NODE_BIN="$(command -v node)"
echo "    node: $(${NODE_BIN} --version) ($(dirname ${NODE_BIN}))"

echo "==> [2/6] 安装代码到 ${INSTALL_DIR}"
mkdir -p "${INSTALL_DIR}"
if [ -d "${INSTALL_DIR}/server" ]; then
  echo "    已存在旧安装，仅更新代码（保留配置和数据）"
  cp -r "${SRC_DIR}"/. "${INSTALL_DIR}/server/" 2>/dev/null || true
  rm -rf "${INSTALL_DIR}/server/storage"
else
  mkdir -p "${INSTALL_DIR}/server"
  cp -r "${SRC_DIR}"/. "${INSTALL_DIR}/server/"
  rm -rf "${INSTALL_DIR}/server/storage"
fi
chown -R "${APP_OWNER}":"${APP_OWNER}" "${INSTALL_DIR}"

echo "==> [3/6] 生成/保留配置 (${INSTALL_DIR}/server/config.json)"
CONFIG="${INSTALL_DIR}/server/config.json"
if [ -f "${CONFIG}" ]; then
  echo "    已存在 config.json，保留密钥并更新端口为 ${PORT}"
  "${NODE_BIN}" -e '
    const fs = require("fs");
    const [port, cfgPath] = process.argv.slice(1);
    const c = JSON.parse(fs.readFileSync(cfgPath, "utf8"));
    c.port = Number(port);
    fs.writeFileSync(cfgPath, JSON.stringify(c, null, 2));
  ' "${PORT}" "${CONFIG}"
else
  TOKEN="$(openssl rand -hex 24)"
  SECRET="$(openssl rand -hex 32)"
  "${NODE_BIN}" -e '
    const fs = require("fs");
    const [port, token, secret] = process.argv.slice(1);
    const base = JSON.parse(fs.readFileSync("config.example.json", "utf8"));
    base.port = Number(port);
    base.deviceToken = token;
    base.encryptionSecret = secret;
    fs.writeFileSync("config.json", JSON.stringify(base, null, 2));
  ' "${PORT}" "${TOKEN}" "${SECRET}"
  chmod 600 "${CONFIG}"
  echo "    已生成新密钥（见文末输出，请填入安卓端）"
fi
chown "${APP_OWNER}":"${APP_OWNER}" "${CONFIG}"

echo "==> [4/6] 写入 systemd 单元 ${UNIT_FILE}"
cat > "${UNIT_FILE}" <<EOF
[Unit]
Description=Memento notification ingest server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=${INSTALL_DIR}/server
ExecStart=${NODE_BIN} index.mjs
Restart=always
RestartSec=3
User=${APP_OWNER}
Group=${APP_OWNER}
Environment=NODE_ENV=production
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
chown root:root "${UNIT_FILE}"

echo "==> [5/6] 启用并启动服务"
systemctl daemon-reload
systemctl enable "${UNIT_NAME}" >/dev/null 2>&1 || true
systemctl restart "${UNIT_NAME}"
sleep 1

echo "==> [6/6] 自检"
if systemctl is-active --quiet "${UNIT_NAME}"; then
  echo "    服务: active ✅"
else
  echo "    服务: 启动失败 ❌  (journalctl -u ${UNIT_NAME} -n 50 查看日志)"
  exit 1
fi
HEALTH="$(curl -s --max-time 5 "http://127.0.0.1:${PORT}/health" || true)"
echo "    /health: ${HEALTH}"

echo ""
echo "=============================================="
echo " ✅ Memento Server 安装完成"
echo "=============================================="
echo " 服务单元 : ${UNIT_NAME}"
echo " 安装目录 : ${INSTALL_DIR}/server"
echo " 监听地址 : 0.0.0.0:${PORT}"
echo " 数据目录 : ${INSTALL_DIR}/server/storage/clean"
echo ""
echo " 常用命令:"
echo "   systemctl status ${UNIT_NAME}     # 状态"
echo "   journalctl -u ${UNIT_NAME} -f     # 日志"
echo "   systemctl restart ${UNIT_NAME}    # 重启"
echo ""
if [ -f "${CONFIG}" ]; then
  TOKEN_NOW="$(grep -o '"deviceToken": "[^"]*"' "${CONFIG}" | cut -d'"' -f4)"
  SECRET_NOW="$(grep -o '"encryptionSecret": "[^"]*"' "${CONFIG}" | cut -d'"' -f4)"
  echo " 安卓端配对信息（请填入 App）:"
  echo "   服务器地址 : http://<服务器IP>:${PORT}"
  echo "   deviceToken: ${TOKEN_NOW}"
  echo "   encryptionSecret: ${SECRET_NOW}"
  echo ""
  echo " ⚠️  密钥仅本次显示，请妥善保存；泄露请删除 ${CONFIG} 后重跑本脚本"
fi
echo "=============================================="
