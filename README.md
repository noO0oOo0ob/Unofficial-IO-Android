# Unofficial IO Android

> [!WARNING]
> 本项目是非官方、非商业的个人研究项目，仅用于作者自身学习、研究和互操作性验证。作者不支持本人自用之外的复制、分发、部署或其他使用，也不对相关行为及后果承担责任。构建或使用前请完整阅读 [免责声明](DISCLAIMER.md)、[第三方二进制说明](VENDOR_NOTICES.md) 和 [MIT License](LICENSE)。MIT 只覆盖贡献者有权许可的原创内容，不覆盖官方 APK 或其中的第三方材料。

这个仓库现在包含两条验证路径：

1. `app/`：早期独立 PoC，仅用于验证从样本中提取的 CoreConnect 能否在普通 Android App 内运行。
2. `enhancer/`：当前主线。以完整官方 RayNeo AI APK 为底座，保留其 Flutter 页面、资源、原生库和原有启动入口，再注入 Turbo IO 控制台并重打包为一个增强 APK。

增强版不是“一个 App 控制另一个 App”。官方功能与 Turbo IO 功能位于同一个包、同一个进程和同一个 APK 中，不要求 root、解锁 bootloader 或系统签名。

当前运行时严格锁定到以下样本：

- RayNeo AI APK：`1.0.2 (68)`
- 包名：`com.rayneo.venus.pub`
- CoreConnect：`2.0.76-SNAPSHOT`
- 样本 SHA-256：`32c67abbdf3c87eb113f965f984b79eee17b4bf3136f14f535375752ab1a7093`

## 初版能力

- 初始化内置 RayNeo 连接运行时
- 兼容扫描附近 BLE 设备，由用户确认 RayNeo 眼镜广播
- 查询 Android 系统已配对设备、系统 GATT 连接和本进程 CoreConnect 连接
- 通过 BLE 连接、断开设备
- 接收并打印 SDK 事件
- 向任意业务通道发送原始二进制消息
- 按 iOS Turbo-IO 的 `AssistantMsg` 协议发送 ASR 文本（business id `13`、type `5`）
- 以普通通知卡片向眼镜推送指定标题和正文（business id `21`、type `2`），可选先开启通知总开关

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
shasum -a 256 /Users/sheng/Downloads/RayNeo_AI_1.0.2.apk
```

### 2. 构建、对齐和签名

在仓库根目录执行：

```bash
./enhancer/build-enhanced-apk.sh /Users/sheng/Downloads/RayNeo_AI_1.0.2.apk
```

脚本依次完成：

1. 校验输入 APK 的 SHA-256，防止对不兼容版本进行修改。
2. 使用 `apktool --no-src` 解包并原样保留官方 DEX。
3. 修改 Manifest，加入 `Turbo IO` 启动入口和网络安全配置。
4. 用 `javac` 编译 `enhancer/src/`，再由 `d8` 生成注入用的 `classes4.dex`。
5. 重新构建 APK，并执行 `zipalign`。
6. 使用本机 Android debug key 签名并运行 `apksigner verify`。
7. 输出最终 APK 的 SHA-256。

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

### 4. 首次运行和眼镜消息测试

1. 先进入 `RayNeo AI`，完成必要权限、账号和眼镜连接流程。
2. 打开 `Turbo IO`，点击“初始化官方 CoreConnect”。
3. 点击“已连接”确认设备；只连接一副眼镜时，发送操作可以把设备 ID 留空。
4. 在“眼镜消息推送”中填写标题和正文。首次测试保留“发送前开启眼镜通知总开关”勾选。
5. 点击“推送通知到眼镜”。日志出现“已提交到 CoreConnect，UID=...”表示数据已交给设备通信层，最终展示结果仍以眼镜实测为准。

### 5. ADB 调试

查看增强入口日志和 Android 运行时异常：

```bash
adb logcat -c
adb logcat | grep -E 'Turbo IO|AndroidRuntime|CoreConnect|rayneo'
```

需要让手机访问电脑上运行的 Codex Bridge 时，假设本地端口为 `8787`：

```bash
adb reverse tcp:8787 tcp:8787
adb reverse --list
```

然后在 Turbo IO 中填写 `http://127.0.0.1:8787`。测试结束后可以移除端口映射：

```bash
adb reverse --remove tcp:8787
```

如需重新安装并保留应用数据，再次执行 `adb install --no-incremental -r ...` 即可；只有签名不一致时才需要卸载。

## 运行原理简述

增强包保留官方 `RayNeo AI` 启动入口，同时增加 `Turbo IO` 启动入口。Turbo IO 控制台直接复用官方包内 CoreConnect，并实现与 iOS 工程相同的 Codex Bridge 端点：

- `GET /v1/state`
- `POST /v1/message`
- `POST /v1/stop`

真机通过 `adb reverse` 调试电脑本地 bridge 时，可以填写 `http://127.0.0.1:<端口>`；其他地址要求 HTTPS。令牌使用 Android Keystore 加密保存。

## 当前边界

这是一份兼容性 PoC，不是官方 SDK。设备发现、连接和消息 ABI 来自对指定 APK 的互操作分析，因此官方 App 升级后可能失效。增强包必须重新签名，不能覆盖官方签名版本；依赖官方证书指纹的登录、支付、推送或服务端校验也可能拒绝重签包。

独立 PoC 可以与官方 App 并存，并能读取 Android 系统配对和 GATT 状态，但不能继承官方 App 私有目录中的 CoreConnect 设备对象、能力表或绑定身份。系统显示眼镜已连接不等于独立进程已完成 CoreConnect 握手；发送前必须以本进程连接列表为准。不同签名的应用还会获得不同作用域的 Android ID，因此不能把官方绑定会话当作可跨应用复用的系统连接。

小米 10 的蓝牙控制器报告硬件扫描过滤槽为 0，因此初版使用无硬件过滤扫描。页面会列出附近其他 BLE 设备；不要连接无法确认属于自己的设备。

代码可构建和进行协议单测，不等于已经在真实眼镜上验证。运行前请确保你有权分析和使用相关二进制；第三方二进制不会被声明为本项目原创或重新许可。详见 [VENDOR_NOTICES.md](VENDOR_NOTICES.md)。

## 许可与声明

本仓库原创内容采用 [MIT License](LICENSE)。项目的非商业研究定位、支持范围、风险和责任限制见 [DISCLAIMER.md](DISCLAIMER.md)；第三方材料的权属与许可边界见 [VENDOR_NOTICES.md](VENDOR_NOTICES.md)。

本次 `1.0.2` 适配中遇到的状态误判、设备发现、签名身份和部署路线问题，连同证据与取舍，记录在 [docs/INVESTIGATION_1.0.2.md](docs/INVESTIGATION_1.0.2.md)。
