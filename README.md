# Memento

Memento 是一个自用的安卓通知采集项目。它会把手机通知整理后上传到服务器，再交给后端的 AI 做语义分析、总结和记忆整理。

## 项目组成

- `app/`：安卓客户端，负责通知采集、过滤、加密和上传
- `server/`：Node 服务端，负责接收并保存清洗后的通知数据

## 主要能力

- 采集安卓通知
- 白名单 / 黑名单过滤
- 通知加密上传
- 按包名分目录、按天分文件保存
- 可选保活与开机恢复

## 当前状态

- 安卓包名：`com.lecheeel.memento`
- 服务端：HTTP 接收，适合内网和自用环境
- 目标场景：个人知识整理、记忆归档、思路回溯

## 运行说明

1. 安装安卓应用
2. 授予通知访问权限
3. 配置服务器地址与密钥
4. 在设置页选择需要采集的应用

## 说明

本项目当前以自用为主，默认不追求公开分发的复杂权限收敛。

---

# Memento

Memento is a personal Android notification capture project. It collects mobile notifications, uploads them to a server, and lets the backend AI handle semantic analysis, summarization, and memory organization.

## Components

- `app/`: Android client for capture, filtering, encryption, and upload
- `server/`: Node server for receiving and storing cleaned notification data

## Features

- Android notification capture
- Whitelist / blacklist filtering
- Encrypted upload
- Per-package folders and per-day files
- Optional keep-alive and boot recovery

## Current Status

- Android package name: `com.lecheeel.memento`
- Server: HTTP ingestion, suitable for LAN and personal use
- Use case: personal memory archive, thought tracking, and recall

## Run Steps

1. Install the Android app
2. Grant notification access
3. Configure server URL and secret
4. Choose which apps to capture in settings

## Note

This project is currently optimized for personal use rather than public distribution.
