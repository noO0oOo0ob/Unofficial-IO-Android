# Unofficial IO Android

> [!WARNING]
> 本项目是非官方、非商业的个人研究项目，仅用于作者自身学习、研究和互操作性验证。作者不支持本人自用之外的复制、分发、部署或其他使用，也不对相关行为及后果承担责任。构建或使用前请完整阅读 [免责声明](DISCLAIMER.md)、[第三方二进制说明](VENDOR_NOTICES.md) 和 [MIT License](LICENSE)。MIT 只覆盖贡献者有权许可的原创内容，不覆盖官方 APK 或其中的第三方材料。

当前主线已实现 [Paseo × RayNeo 眼镜桥接](docs/PASEO_RAYNEO_SOP.md)：普通语音唤醒 → MiniMax ASR → 带上下文的 MiniMax 意图路由 → MiniMax Responses 联网流式回答或 Paseo/Codex。Paseo daemon 保持不变，原版 Paseo App 继续承担完整历史和权限审批。完整实现取舍和问题复盘见 [Paseo × RayNeo 桥接实现与踩坑复盘](docs/PASEO_RAYNEO_IMPLEMENTATION_RETROSPECTIVE.md)。

较早的底座增强、语音入口和 UI 候选调研保留在 [Android 官方底座增强方案 V2](docs/ENHANCEMENT_V2_PLAN.md)、[V3 调查](docs/HOOK_POINT_REVIEW_V3.md) 与 [眼镜 UI 交互选项](docs/GLASSES_UI_INTERACTION_OPTIONS.md)。这些文档是设计证据；当前可操作步骤以 [Paseo × RayNeo 使用 SOP](docs/PASEO_RAYNEO_SOP.md) 为准。

这个仓库现在包含两条验证路径：

1. `app/`：早期独立 PoC，仅用于验证从样本中提取的 CoreConnect 能否在普通 Android App 内运行。
2. `enhancer/`：当前主线。以完整官方 RayNeo AI APK 为底座，保留其 Flutter 页面、资源、原生库和原有启动入口，再注入 Turbo IO 控制台并重打包为一个增强 APK。

增强版不是“一个 App 控制另一个 App”。官方功能与 Turbo IO 功能位于同一个包、同一个进程和同一个 APK 中，不要求 root、解锁 bootloader 或系统签名。

当前运行时严格锁定到以下样本：

- RayNeo AI APK：`1.0.2 (68)`
- 包名：`com.rayneo.venus.pub`
- CoreConnect：`2.0.76-SNAPSHOT`
- 样本 SHA-256：`32c67abbdf3c87eb113f965f984b79eee17b4bf3136f14f535375752ab1a7093`

## 当前实现范围

- Paseo 眼镜模式默认关闭，进程重启后恢复关闭。
- 普通唤醒后保持 Opus 音频上行，独立解码与本地断句；ASR 最终文本通过 type 5 回显。MiniMax 回答期间仍监听音频，本地 VAD 检测到新讲话后立即取消当前回答并收取下一句。首轮 5 秒无语音检测不会误伤连续会话；连续模式只在 120 秒完全无活动后保护性退出。
- 未确定目标时由 MiniMax M3 Responses 结合最近交互、当前会话和当前工作区分类；意图请求关闭 reasoning，避免推理占满输出预算后没有 JSON 正文。选中或新建 Codex 会话后的下一句跳过意图 HTTP，ASR 完成后直接发往该 Codex 会话。普通问答进入 MiniMax Responses，并按需使用服务端 `web_search`。
- 支持列会话、分页、按编号/标题选择、查看详情、最后回复、回答分段、退出助手和二次确认停止。
- 直接使用 Paseo daemon v1 WebSocket RPC，支持可选密码；不改 daemon，也不依赖 Paseo 手机 App 中转。
- 普通模型回答在助手页使用 type 32/type 12；超过一屏后流式刷新始终展示最新文本窗口，完成时停留在最后一段，可说“上一段”回看。Codex 异步执行结果始终使用普通通知，不追加到助手回答流。完整内容仍可在 Paseo App 查看。
- 可选开启高德导航通知镜像：监听手机高德 `com.autonavi.minimap` 的标准导航通知，提取转向文字并通过 business 21/type 2 显示到眼镜；不接入高德 SDK，也不启动第二套导航。
- MiniMax API Key 固定用于 ASR、意图识别和 Responses 回答；中国区接口统一使用 `api.minimax.cn`，当前版本隐藏其他模型配置。Key 使用 Android KeyStore 加密并保存在不参与备份的本机目录。
- 关闭模式取消本轮工作，等待已发出的停止/退出指令结果；OFF 直接执行官方语音方法。
- 支持读取 Paseo 工作区并在眼镜端新建 Codex 会话；不实现 TTS、实时流式 ASR、Paseo Relay/SSH，不重建官方眼镜连接、不改通知总开关。

## 环境准备

推荐在 macOS 或 Linux 上构建。当前脚本按以下工具版本和目录验证：

- JDK 17 或更高版本，命令行中可使用 `java`、`javac` 和 `jar`
- Node.js 18 或更高版本
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0（需要 `d8`、`zipalign` 和 `apksigner`）
- Android Platform Tools（需要 `adb`）
- Apktool 3.0.3
- 本人合法取得的 RayNeo AI `1.0.2 (68)` 原始 APK
- Android 10（API 29）或更高版本的测试手机

先检查基础命令：

```bash
java -version
javac -version
node --version
adb version
```

设置 Android SDK 路径；下面的写法会在变量未配置时使用 macOS 的默认位置：

```bash
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
```

Linux 或自定义安装目录请把默认路径替换为实际 SDK 路径。

确认脚本依赖存在：

```bash
test -f "$ANDROID_HOME/platforms/android-36/android.jar"
test -x "$ANDROID_HOME/build-tools/36.0.0/d8"
test -x "$ANDROID_HOME/build-tools/36.0.0/zipalign"
test -x "$ANDROID_HOME/build-tools/36.0.0/apksigner"
```

首次构建下载 Apktool 3.0.3：

```bash
mkdir -p tools
curl -fL https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar \
  -o tools/apktool.jar
```

构建脚本默认使用 `~/.android/debug.keystore`。如果文件不存在，可生成仅供本地调试的密钥：

```bash
mkdir -p ~/.android
keytool -genkeypair -v \
  -keystore ~/.android/debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -dname "CN=Android Debug,O=Android,C=US" \
  -keyalg RSA -keysize 2048 -validity 10000
```

也可以通过 `ANDROID_DEBUG_KEYSTORE` 指定其他兼容的调试密钥库。

## 构建独立 PoC（非主线）

独立 PoC 依赖从用户本地合法取得的样本 APK 提取 DEX 和原生库；这些厂商二进制不会提交或随仓库分发，文件位置与校验值见 [VENDOR_NOTICES.md](VENDOR_NOTICES.md)。仅克隆公开仓库时，优先使用下面的官方底座增强包构建流程。

```bash
./gradlew testDebugUnitTest :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 构建官方底座增强包（推荐）

### 1. 准备底座 APK

把本人合法取得的 `RayNeo_AI_1.0.2.apk` 放在本机目录中。仓库不会提供、下载或分发该文件。脚本只接受 SHA-256 为 `32c67abbdf3c87eb113f965f984b79eee17b4bf3136f14f535375752ab1a7093` 的固定样本，可先自行核对：

```bash
shasum -a 256 /Users/USER/Downloads/RayNeo_AI_1.0.2.apk
```

### 2. 构建、对齐和签名

在仓库根目录执行：

```bash
./enhancer/build-enhanced-apk.sh /Users/USER/Downloads/RayNeo_AI_1.0.2.apk
```

脚本依次完成：

1. 校验输入 APK 哈希，解包并只在 `classes2.dex` 的语音入口增加路由包装；原方法体完整保留。
2. 编译限定的演示类，生成 `classes4.dex`；Manifest 新增控制页入口和独立的高德导航通知监听组件。
3. 以原 APK 为模板，仅替换 Manifest、`classes2.dex`，新增 `classes4.dex`。官方资源、assets、原生库和其他 DEX 逐字节保留。
4. 执行 16 KB 原生库对齐、本地签名、签名验证与文件差异验证，生成 `verification.json`。

构建产物位于：

```text
enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk
```

每次构建都会清理 `enhancer/build/`。不要把 APK、签名密钥或从官方 APK 提取出的内容提交到仓库。

### 3. 安装到测试手机

打开手机的开发者选项和 USB 调试，连接后确认授权：

```bash
adb devices
```

增强包沿用官方包名 `com.rayneo.venus.pub`，但使用本地 debug key 重新签名，因此不能直接覆盖官方签名版本。

如果手机上已经安装的是本项目用同一把 debug key 构建的旧版本，可保留数据升级：

```bash
adb install --no-incremental -r \
  enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk
```

如果手机上是官方签名版本，必须先自行备份必要数据，再卸载官方 App。下面的卸载命令会清除该 App 的本地数据：

```bash
adb uninstall com.rayneo.venus.pub
adb install --no-incremental \
  enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk
```

安装完成后，桌面会同时出现官方 `RayNeo AI` 入口和新增的 `Turbo IO` 入口。

两个入口使用独立任务，可通过最近任务或桌面 `Turbo IO` 图标返回控制页，无须杀进程。控制页的“查看诊断记录（重启后保留）”保存有大小上限的本机事件记录，不含 Key、录音或识别正文；进程重启后模式仍默认关闭。任务切换改动待真机验收。

### 4. 首次运行和 Paseo 眼镜模式

1. 先进入 `RayNeo AI`，完成权限、账号和眼镜连接，保持只连接一副眼镜。
2. 打开 `Turbo IO`，保存 MiniMax API Key。
3. 填入 Paseo WebSocket URL 和可选 daemon 密码，先点“测试 Paseo 连接”。USB 首测可执行 `adb reverse tcp:6767 tcp:6767` 后填写 `ws://127.0.0.1:6767/ws`。
4. 在 Paseo App/Desktop/CLI 中先创建会话，在官方 App 中开启需要的眼镜通知。
5. 点击“开启 Paseo 眼镜模式”。可直接提问；也可说“列出会话”后选择并续聊，或说“新建会话”后选择工作区。
6. 点击“关闭 Paseo 眼镜模式”，等状态确认为“已关闭”，再验证官方语音和其他功能。

高德导航提示可独立于 Paseo 模式使用：在 Turbo IO 控制页勾选“把手机高德导航通知转发到眼镜”，按提示进入系统“通知使用权”页面并允许 `Turbo IO 高德导航`，同时保持官方 RayNeo 的眼镜通知总开关开启。该功能默认关闭，只处理高德 Android 包名；通知未暴露标准文字或手机系统限制后台监听时不会转发。

完整命令、正常流程与排障见 [Paseo × RayNeo 使用 SOP](docs/PASEO_RAYNEO_SOP.md)。主机自动测试：`enhancer/test/run.sh`。这不等于真机 Opus、蓝牙投递、助手页面显示或官方功能回归通过。

### 5. ADB 调试

查看增强入口日志和 Android 运行时异常：

```bash
adb logcat -c
adb logcat | grep -E 'Turbo IO|AndroidRuntime|CoreConnect|rayneo'
```

需要让手机访问电脑上运行的 Paseo daemon 时，默认端口为 `6767`：

```bash
adb reverse tcp:6767 tcp:6767
adb reverse --list
```

然后在 Turbo IO 中填写 `ws://127.0.0.1:6767/ws`。测试结束后可以移除端口映射：

```bash
adb reverse --remove tcp:6767
```

如需重新安装并保留应用数据，再次执行 `adb install --no-incremental -r ...` 即可；只有签名不一致时才需要卸载。

## 运行原理简述

增强包保留官方 `RayNeo AI` 启动入口，同时增加 `Turbo IO` 启动入口。Turbo IO 复用同进程内官方 CoreConnect 与眼镜通信，另用独立 WebSocket 客户端直连原有 Paseo daemon。它实现 hello、会话目录、timeline、发送、等待完成和取消等现有 RPC，不注册或修改 daemon 接口。MiniMax Key 与可选 daemon 密码使用 AndroidKeyStore 加密保存。

## 当前边界

这是一份兼容性 PoC，不是官方 SDK。设备发现、连接和消息 ABI 来自对指定 APK 的互操作分析，因此官方 App 升级后可能失效。增强包必须重新签名，不能覆盖官方签名版本；依赖官方证书指纹的登录、支付、推送或服务端校验也可能拒绝重签包。

独立 PoC 可以与官方 App 并存，并能读取 Android 系统配对和 GATT 状态，但不能继承官方 App 私有目录中的 CoreConnect 设备对象、能力表或绑定身份。系统显示眼镜已连接不等于独立进程已完成 CoreConnect 握手；发送前必须以本进程连接列表为准。不同签名的应用还会获得不同作用域的 Android ID，因此不能把官方绑定会话当作可跨应用复用的系统连接。

小米 10 的蓝牙控制器报告硬件扫描过滤槽为 0，因此初版使用无硬件过滤扫描。页面会列出附近其他 BLE 设备；不要连接无法确认属于自己的设备。

代码可构建和进行协议单测，不等于已经在真实眼镜上验证。运行前请确保你有权分析和使用相关二进制；第三方二进制不会被声明为本项目原创或重新许可。详见 [VENDOR_NOTICES.md](VENDOR_NOTICES.md)。

## 许可与声明

本仓库原创内容采用 [MIT License](LICENSE)。项目的非商业研究定位、支持范围、风险和责任限制见 [DISCLAIMER.md](DISCLAIMER.md)；第三方材料的权属与许可边界见 [VENDOR_NOTICES.md](VENDOR_NOTICES.md)。

本次 `1.0.2` 适配中遇到的状态误判、设备发现、签名身份和部署路线问题，连同证据与取舍，记录在 [docs/INVESTIGATION_1.0.2.md](docs/INVESTIGATION_1.0.2.md)。
