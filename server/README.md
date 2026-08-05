> 📖 Full project docs: [English](../README.md) · [中文](../README.zh-CN.md)

# Memento Server

Minimal self-hosted ingest service for the Memento Android app.

## Run

1. Copy `config.example.json` to `config.json`
2. Fill in `deviceToken` and `encryptionSecret` to match the app
3. Start:

```bash
npm start
```

## Endpoints

- `GET /health`
- `POST /ingest`
- `GET /events?limit=20`

## Storage

Events are partitioned by package name and date:

```text
storage/
  raw/
    com.example.app/
      2026-08-05.jsonl
```
