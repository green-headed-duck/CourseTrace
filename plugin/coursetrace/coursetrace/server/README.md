# CourseTrace MCP relay

This small self-hosted service links ChatGPT tools to the paired Android app. It does not use an OpenAI API key. ChatGPT performs the reasoning; the relay only stores a limited phone snapshot and an ordered change queue until the phone synchronizes.

## Run locally

Install Node.js 24, then:

```powershell
npm install
npm run build
$env:COURSETRACE_PAIRING_TOKEN = (New-Guid).Guid + (New-Guid).Guid
npm start
```

Use a different 32+ character random token in production. Do not commit it. The service exposes `/mcp`, `/v1/device/snapshot`, `/v1/device/changes`, `/v1/device/ack`, and unauthenticated `/health`.

## Registration and plan boundary

Deploy behind HTTPS, register `https://your-domain.example/mcp` as a custom ChatGPT app/MCP endpoint in a compatible workspace, and use the same bearer pairing token in the Android app. Registration must be performed in the user's own account; source code cannot perform that account action automatically.

As of 2026-09-17, custom MCP apps are web-only and personal Pro accounts have read/fetch rather than full write MCP access. This relay is therefore ready for future support, Codex desktop, or a compatible Business/Enterprise/Edu workspace. The Android app's system-share workflow is the supported personal-Pro mobile path today.

The relay state is intentionally not canonical. The Android private Git repository is canonical; keep the relay volume encrypted at rest and apply a short backup retention policy.
