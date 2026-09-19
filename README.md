# 课迹 CourseTrace

课迹是一款个人使用的原生 Android 课程与学习记录应用。最低 Android 10（API 29），目标 Android 16（API 36），重点适配小米 OS 3，同时保持标准 Android 行为。

[下载最新版本](https://github.com/green-headed-duck/CourseTrace/releases/latest) · [反馈问题](https://github.com/green-headed-duck/CourseTrace/issues)

## 已实现

- Kotlin + Jetpack Compose Material 3；系统深浅色、动态/自定义主题色、自定义照片背景、横竖屏和宽屏自适应。
- 多学期、单双周/指定周、课程时间与教室；PDF 识别草稿必须预览后才写入。
- 华南理工大学大学城/国际校区第 1—11 节作息模板；PDF 只有“第几节”时自动换算准确时间。
- 今日、周总览、课程编辑/移出课表，以及“下一节课”桌面组件。
- 默认自动应用国家法定节假日：放假日停止排课，调休工作日明确标注；支持更换可信的 HTTPS JSON 来源。国家通知未指定学校补课映射时不会擅自猜测，数据源可用 `FOLLOW_DATE` 明确指定。
- OpenAI 兼容中转接口，默认配置 `https://api.apiyi.com/v1` / `gpt-5.6-luna`；模型名可单独编辑，Key 仅存 Android Keystore。
- ChatGPT Mobile 系统分享与课堂 JSON 专用导入；摘要、问题、错题、痛点、进度、结论和原文分别归档，不必猜测粘贴框。另有为未来/兼容工作区准备的自托管 CourseTrace MCP 中继；ChatGPT Pro 推理与 PDF API 中转互不混用。
- 上课前默认 15 分钟本地提醒、Android 16 临近/上课实时更新样式、点通知直达对应课程；精确提醒无权限时自动降级。
- 早课系统闹钟开关与显式设置操作；最终行为由系统时钟应用确认。
- 课程和 FPGA 等自学项目的资料、课堂原文、错题、痛点、进度与下次资料预测。
- App 私有目录中的真实 Git 仓库，修改后自动提交；大资料只记录 URI 与 SHA-256，不塞进 Git。
- AES-256-GCM 加密备份，可通过系统文件选择器保存到本地、网盘或 WebDAV 文件提供器。
- 默认接入公开 GitHub Release 更新通道，应用内一键检查和下载，无需登录 GitHub；签名清单、APK SHA-256、包名/版本/安装签名四重校验后仍由 Android 系统安装器要求用户确认。
- 生物识别/设备凭据应用锁、锁屏敏感通知保护；无 root、无隐藏 API、无无障碍自动化、无屏幕监听。

## 构建

项目附带 Gradle Wrapper。需要 JDK 17 与 Android SDK 36：

```powershell
$env:JAVA_HOME = '你的 JDK 17 路径'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

本工作区已经生成个人 Release 签名材料，位于被 Git 忽略的 `.secrets/` 与 `keystore.properties`。务必单独离线备份；丢失后无法给已安装版本提供无缝更新。APK 输出在 `app/build/outputs/apk/`。

## 首次安装后的操作

1. 安装签名 Release APK，授予通知权限；在“设置 → 上课提醒”允许精确提醒。
   如需桌面组件，长按手机桌面，选择“小部件/桌面组件 → 课迹 → 下一节课”。Android 不允许应用静默把组件放到桌面。
2. 在“设置 → PDF 识别接口”填入 API Key。默认中转站和模型已经预填，可先点“测试连接”。Key 不会进入仓库或 APK。
3. 从系统文件选择器选课程表 PDF，核对置信度、单双周、时间与教室后再确认导入。
4. 在课程详情中归档课件；文件仍由系统文档提供器管理，课迹只保留持续授权的 URI 和校验值。
5. 个人 Pro 手机端使用“复制课堂指令 → 在 ChatGPT 聊天 → 下课时复制 JSON → 课程记录页的‘粘贴 ChatGPT JSON’”。JSON 不要粘入手动过程、摘要或普通原文框。也可直接通过系统分享把普通聊天原文送回课迹；这些路径不需要 OpenAI API。
6. “设置 → 关于与支持 → GitHub”可直达源码、历史版本下载和问题反馈页面。

## ChatGPT 联动与当前限制

截至 2026-09-17，个人 ChatGPT Pro 可以构建 Apps SDK 应用，但自定义 MCP App 仍不支持 ChatGPT 手机端；Pro 的 MCP 也只开放读取/检索，完整写入能力面向 Business、Enterprise 和 Edu。个人账号当前也不能新建自定义 GPT。因此，手机端“完全无感、自动把每条聊天写进课迹”不能在不绕过系统安全边界的前提下实现。

课迹现在采用可靠的兼容流程：课程详情一键把上下文分享到 ChatGPT；ChatGPT 按课堂指令整理；结束后把结构化 JSON 粘到专用导入框，或用系统分享把普通原文送回课迹。应用明确记录原文完整性，不会谎称拿到了全部聊天。课程详情中的历史记录可以打开，查看摘要、按事件排序的时间线和课堂原文。

源代码生成了专用插件与 Skill，位于 `plugin/coursetrace/coursetrace/`：

- Skill：`skills/coursetrace-learning/`
- MCP 中继：`server/`
- Codex 桌面插件清单：`.codex-plugin/plugin.json`

下面的 MCP 部署步骤用于未来移动端开放、Codex 桌面，或具备完整 MCP 权限的兼容工作区：

1. 在服务器目录执行 `pnpm install && pnpm run build`，生成至少 32 位随机 `COURSETRACE_PAIRING_TOKEN`。
2. 使用 Dockerfile 或 Node 24 启动服务，并通过自己的 HTTPS 域名反向代理 `/mcp` 与 `/v1/device/*`。
3. 在支持完整 MCP 的 ChatGPT 工作区中把 `https://你的域名/mcp` 注册为自定义 App/MCP 端点。此账号动作需要你亲自完成，源码不能替你发布或授权。
4. 在 Android“ChatGPT Mobile 联动”中填同一域名根地址与令牌，点击立即同步。
5. 在兼容客户端中可说：`开始上课`、`记一下这个问题`、`下课`、`分析最近四周高数的痛点`、`开始学习 FPGA`，或让 ChatGPT 识别 PDF 并创建预览草稿。

ChatGPT 无法被动读取其他历史会话。只有 Skill 活跃时追加或由你主动分享的内容才会进入课迹；完整课堂原文会带明确的完整/不完整标记。

## 更新包

默认更新端点是公开仓库根目录的 [`update-manifest.json`](update-manifest.json)，因此普通用户无需填写地址或登录 GitHub。更新清单签名私钥在 `.secrets/update-signing-private.pem`，公钥已固化到应用。生成新 APK 后：

```powershell
.\tools\sign-update-manifest.ps1 `
  -ApkPath .\app\build\outputs\apk\release\app-release.apk `
  -VersionCode 15 -VersionName 0.3.1 `
  -ApkUrl https://github.com/green-headed-duck/CourseTrace/releases/download/v0.3.1/CourseTrace-0.3.1-release.apk
```

将生成的 `update-manifest.json` 提交到仓库 `main` 分支，并把 APK 上传到相应 GitHub Release。后续版本必须沿用同一 Android keystore 和更新清单私钥。设置页仍允许高级用户替换其他 HTTPS 签名清单。

## 安全边界

小米“超级岛”是否展示由 HyperOS 的系统策略和厂商开放资格决定。课迹使用 Android 16 标准实时更新通知作为可工作的实现，不伪造厂商协议、不注入系统界面。系统闹钟、APK 安装、通知与文件访问均保留 Android 的用户确认流程。

## 当前验证版本

`0.3.1`（versionCode 15）默认接入公开 GitHub Release 更新通道，点击即可在应用内检查、下载并校验更新，不需要 GitHub 账号。支持 GitHub 的安全 HTTPS 重定向，同时保留自定义签名清单入口；最终安装仍由 Android 系统确认。0.3.0 的国家节假日自动调整、可替换数据源与离线规则继续保留。
