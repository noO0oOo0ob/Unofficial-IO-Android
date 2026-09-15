# Paseo × RayNeo 眼镜桥接使用 SOP

## 交付范围

本版本在 RayNeo AI 1.0.2 增强包内直接实现 Paseo daemon 客户端，**不修改 Paseo daemon**。增强包和原版 Paseo 手机 App 是同一 daemon 的两个平级客户端：

- 眼镜侧负责短交互：带上下文的 MiniMax 意图识别、联网流式问答、列会话、查看、选择、列工作区、新建 Codex 会话、发送 query 和停止任务。
- Paseo App 负责完整历史、权限审批、长内容和高级操作。
- 眼镜退出助手页后，已经提交的任务继续在电脑执行；完成结果改用眼镜普通通知提醒。

当前仅支持直连 `ws://` 或 `wss://` 的 Paseo daemon，不实现 Paseo Relay/SSH 传输。手机和电脑在同一可信局域网时使用电脑局域网 IP；跨网络建议走可信 VPN 或 HTTPS/WSS 反向代理，不要把无密码 daemon 暴露到公网。

## 一、构建 APK

在工程根目录执行：

```bash
cd /Users/USER/opensource/Unofficial-IO-Android
./enhancer/test/run.sh
./enhancer/build-enhanced-apk.sh \
  /Users/USER/opensource/rayneo-local-samples/RayNeo_AI_1.0.2.apk
```

输入 APK 必须是 RayNeo AI 1.0.2（68），SHA-256：

```text
32c67abbdf3c87eb113f965f984b79eee17b4bf3136f14f535375752ab1a7093
```

输出文件：

```text
enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk
```

## 二、安装到手机

先检查设备：

```bash
adb devices
```

如果手机上已安装的是同一 debug key 签名的旧增强包，可保留数据升级：

```bash
adb install --no-incremental -r \
  /Users/USER/opensource/Unofficial-IO-Android/enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk
```

如果安装的是官方签名版本，签名不同，必须卸载后重装。**卸载会清除官方 App 的本地数据和绑定状态**：

```bash
adb uninstall com.rayneo.venus.pub
adb install --no-incremental \
  /Users/USER/opensource/Unofficial-IO-Android/enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk
```

安装后桌面会有 `RayNeo AI` 和 `Turbo IO` 两个入口，它们属于同一个增强 APK。

## 三、准备 Paseo daemon

让 daemon 监听局域网可访问地址并设置密码，例如：

```bash
PASEO_PASSWORD='你的强密码' \
  paseo daemon start --listen 0.0.0.0:6767 \
  --hostnames '电脑局域网IP'
```

然后在 Turbo IO 填写 `ws://电脑局域网IP:6767/ws` 和同一密码。当前这台电脑填写 `ws://HOST_LAN_IP:6767/ws`。不要填写 `localhost` 或 `127.0.0.1`，它们在手机上指向手机自己。

先用 Paseo App、Desktop 或 CLI 创建至少一个会话。例如：

```bash
paseo run --provider codex/gpt-5.5 \
  --cwd /Users/USER/opensource/Unofficial-IO-Android \
  "检查当前工程状态，等待我的后续指令"
```

## 四、首次配置

1. 打开 `RayNeo AI`，重新完成账号、权限、眼镜绑定和连接。
2. 在官方设置中允许眼镜通知，否则退出助手页后的完成提醒可能不可见。
3. 打开 `Turbo IO`，保存 MiniMax API Key。该 Key 固定用于 ASR、意图识别以及 MiniMax Responses 联网流式回答。
4. 当前版本不显示其他聊天模型配置；后续会单独设计多 Provider 的长期方案。
5. 进入“设置 → 管理源”，添加一个 Paseo 源并填写名称、WebSocket URL；daemon 有密码时一并输入。点“保存并测试连接”，看到连接成功提示。
6. 可继续添加“公司电脑”“家里电脑”等源。每个源独立保存密码、Client ID、最近会话和工作区；在源列表点“切换到此源”即可切换。切换、编辑或删除前必须先关闭 TurboIO 接管模式。
7. 点“开启接管模式”。TurboIO 接管模式只在当前 App 进程有效，进程重启后默认关闭；Paseo 是其中的代码任务与会话能力。

旧版单一 Paseo 配置会自动迁移为名为“我的电脑”的第一个源。MiniMax Key 与各 Paseo 源密码均由 AndroidKeyStore 加密保存，不写入 APK、诊断记录或系统备份。

## 五、可说的提示语

| 目的 | 推荐说法 | 近似说法示例 |
|---|---|---|
| 列出会话 | “列出会话” | “显示会话列表”“看看有哪些任务” |
| 翻会话页 | “下一页” | “更多会话”“上一页” |
| 看详情 | “查看第二个详情” | “看看第三个” |
| 选择并续聊 | “继续第二个” | “选择第一个”“基于登录问题排查继续” |
| 列出工作区 | “列出工作区” | “有哪些项目”“显示工程列表” |
| 新建 Codex 会话 | “新建会话” | 随后说“选择第一个工作区”；选中后自动创建并选择 |
| 查询当前选择 | “当前会话” | “现在聊的是哪个任务” |
| 重看回复 | “查看最后回复” | “重新显示上一条回复” |
| 阅读长回复 | “下一段” | “上一段”“继续显示” |
| 停止任务 | “停止当前任务” | 随后必须再说“确认停止”；也可说“取消操作” |
| 退出助手 | “退出” | “退下”“关闭助手”“结束语音助手”“再见” |
| MiniMax 联网问答 | 天气、新闻、百科、写作、翻译、闲聊 | “今天天气怎么样”“介绍一下你自己” |
| 发送 Codex query | 编程、工程文件和测试任务 | “检查一下测试为什么失败”“继续修改并跑完整构建” |

没有确定目标时，ASR 文本会调用 MiniMax M3 Responses 意图模型，请求包含最近 10 条眼镜交互、当前会话、当前工作区和待完成操作。该请求关闭 reasoning，只生成短 JSON 路由结果，避免推理文本占满输出额度。选择会话或新建会话后，下一句完全跳过意图模型，ASR 完成即发送到该 Codex 会话。Codex 自身的完整历史仍由 Paseo/Codex 保存，不会每轮复制给路由模型。

## 六、正常使用流程

推荐完整流程：

```text
普通方式唤醒眼镜助手
→ 说“列出会话”
→ 眼镜显示 1～5 个会话
→ 触发下一轮，说“查看第二个详情”或“继续第二个”
→ 普通问题由 MiniMax Responses 回答；需要实时信息时自动使用 web_search
→ 回答通过 type 32 增量刷新，完成后发送最终消息
→ 代码任务先选择会话，再说实际 query
→ 眼镜显示“已发送”，Paseo daemon 在电脑执行
→ Codex 完成结果始终作为普通消息发送，不追加到当前 AI 助手回答流
→ 完整历史和长内容去 Paseo App 查看
→ 完成后说“退出”或“退下”，主动关闭眼镜助手
```

首句开始后，手机保持眼镜音频上行：回答生成和展示期间可直接讲话。本地 VAD 确认新语音后会立刻取消正在进行的 MiniMax 请求，结束旧回答并收取新一句。模型请求、流式增量、页面展示和新语音都会刷新 120 秒无活动保护；不再在回答完成 10 秒后自动关页。可说“退出”或“退下”主动结束。眼镜 type 11 和再次普通唤醒仍作为兼容入口。

每个 Codex query 发往 Paseo 前会附加眼镜消息约束：结论优先、最多 4 个短要点、尽量不超过 350 个中文字符、避免表格和长代码。这个前缀会出现在 Paseo 会话历史中。MiniMax 前台回答使用更短的眼镜约束，并且不会进入 Paseo。

如果没有现成会话，可说“新建会话”，从工作区列表选择编号后自动创建。若任务需要权限，仍去 Paseo App 完成批准；之后可在眼镜说“查看最后回复”。完整工具调用继续在 Paseo App 查看。

## 七、排障

连接失败时依次检查：

```bash
paseo daemon status
nc -vz HOST_LAN_IP 6767
adb shell am force-stop com.rayneo.venus.pub
adb logcat -c
adb logcat | grep -E 'TurboIO|AndroidRuntime|CoreConnect|rayneo'
```

- `Paseo WebSocket rejected: HTTP/1.1 401/403`：密码错误，或 daemon 的 Host/网络策略拒绝连接。
- `Connection refused`：daemon 未启动、端口错误、没有监听 `0.0.0.0`，或手机与电脑不在同一可达局域网。
- 能测试 Paseo、眼镜不能收音：先确认官方 App 已连眼镜，并结束全天智记、通话、翻译等占用音频的功能。
- query 已发送但没有通知：检查官方通知总开关；也可直接打开 Paseo App 看任务状态。
- 助手页回答样式异常：完整回答的 type 32 已标记 `answer.isFinal=true`，随后发送 type 12 完成事件；仍需以本次 APK 的真机镜片验收为准。

## 八、当前边界

- 不修改 Paseo daemon；协议按当前 Paseo 源码的 v1 `hello/session` 消息实现。
- 可在眼镜端从 Paseo 已登记的工作区中选择并创建 Codex 会话；新增工程、worktree/分支高级设置仍在 Paseo App/Desktop 完成。
- ASR 是说完后整句回显，不是逐字实时字幕。
- MiniMax 回答是 SSE 流式接收并以节流后的最新文本窗口刷新眼镜；长回答完成时停留在最后一段，可说“上一段”回看。这是回答流式，不代表 ASR 实时。
- MiniMax 中国区 ASR、意图和 Responses 统一调用 `api.minimax.cn`。请求会注入手机当前日期、时间和时区；天气、新闻、价格、行情等时效问题会明确要求先调用 `web_search`。
- 主机测试、APK 签名校验和连接层 ACK 不等于镜片实际显示；type 32 连续回答、type 11 下一轮、通知降级仍需本次构建的真机验收。
- 增强包锁定 RayNeo AI 1.0.2（68）；官方 App 升级后必须重新审计和适配。
