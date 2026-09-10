# Unofficial IO Android

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

## 构建独立 PoC

独立 PoC 依赖从用户本地合法取得的样本 APK 提取 DEX 和原生库；这些厂商二进制不会提交或随仓库分发，文件位置与校验值见 [VENDOR_NOTICES.md](VENDOR_NOTICES.md)。仅克隆公开仓库时，优先使用下面的官方底座增强包构建流程。

```bash
./gradlew testDebugUnitTest :app:assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 构建官方底座增强包

首次构建需要把 Apktool 3.0.3 放到 `tools/apktool.jar`：

```bash
mkdir -p tools
curl -fL https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar -o tools/apktool.jar
```

```bash
./enhancer/build-enhanced-apk.sh /Users/sheng/Downloads/RayNeo_AI_1.0.2.apk
```

脚本会校验固定样本、以 `apktool --no-src` 保留官方 DEX、编译新增 Java 代码为 `classes4.dex`，然后重新对齐并使用本机 Android debug key 签名。产物位于 `enhancer/build/output/RayNeo_AI_1.0.2-turbo-debug.apk`。

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

本次 `1.0.2` 适配中遇到的状态误判、设备发现、签名身份和部署路线问题，连同证据与取舍，记录在 [docs/INVESTIGATION_1.0.2.md](docs/INVESTIGATION_1.0.2.md)。
