# RayNeo AI 1.0.2 互操作调查记录

本文记录从 RayNeo AI `1.0.2 (68)` 样本更新独立 PoC、真机排障到路线选择的过程。它既是升级检查表，也是对失败假设的复盘。为避免泄露用户设备信息，文中不记录手机序列号、蓝牙 MAC、账户标识或官方 App 保存的本机身份。

## 目标与约束

目标是在官方尚未提供开发者 SDK 时，验证能否基于用户合法取得的 APK 样本扩展眼镜能力。此次真机调试还要求：

- 使用最新的 `1.0.2 (68)`，不沿用 `1.0.1` 字节码。
- 官方 App 必须保留，不能卸载，也不能清除其登录、绑定和连接状态。
- 第三方 DEX、native library 和完整 APK 只保存在本地，不提交仓库。
- 所有成功状态必须来自可观测证据，不能把“调用未抛异常”当作设备已接受操作。

这组约束决定了独立 App 可以验证加载、发现和协议编码，却不能自然继承官方 App 的私有会话。

## 样本与兼容性锁定

本次样本信息：

| 项目 | 值 |
| --- | --- |
| RayNeo AI | `1.0.2 (68)` |
| 包名 | `com.rayneo.venus.pub` |
| APK SHA-256 | `32c67abbdf3c87eb113f965f984b79eee17b4bf3136f14f535375752ab1a7093` |
| CoreConnect | `2.0.76-SNAPSHOT` |
| ABI | `arm64-v8a` |

三份 DEX 的固定校验值：

| 文件 | SHA-256 |
| --- | --- |
| `classes.dex` | `7777aafca2eb6ad95dc0e4c2858d406d50183142d8c9201739f5d72525e36117` |
| `classes2.dex` | `04a7d400568ea8d66df06133562d329790318f968a378eea524f1459e3e4b35c` |
| `classes3.dex` | `99bcb84be054579256a9e4c0ec2e3c86b5dcf26887a72580c1dbbbaa8db8e85c` |

`1.0.2` 中当前适配依赖的混淆 ABI 仍存在，包括插件入口 `com.rayneo.rayneo_venus_sdk_plugin.j`、管理器 `h` / `h$b`、CoreConnect `I3.v`、消息 `I3.P`、业务枚举 `S3.h` 和扫描过滤器 `S3.F`。类名存在只证明反射入口尚在，字段语义和运行行为仍必须真机验证。

## 三层设备状态

排障中最关键的认识是：“已绑定”“已连接”不是一个状态。

| 层级 | 数据来源 | 含义 | 能否直接发送 CoreConnect 消息 |
| --- | --- | --- | --- |
| Android 系统已配对 | `BluetoothAdapter.getBondedDevices()` | 系统保存了蓝牙配对关系 | 不能 |
| Android 系统 GATT 已连接 | `BluetoothManager.getConnectedDevices(GATT)` | 某个进程持有 BLE GATT 链路 | 不能证明当前 App 可用 |
| 本进程 CoreConnect 已连接 | `I3.v.h()` | 当前进程已有设备对象、认证状态和能力表 | 可以进入发送前置检查 |

官方 App 和独立 PoC 是不同进程，各自拥有私有目录和 CoreConnect 单例。官方 App 中已经绑定并连接，不会让独立 PoC 的 `I3.v.g()` 或 `I3.v.h()` 自动出现同一设备。因此最初看到两个空列表不是系统没有配对，而是 UI 把 CoreConnect 私有状态误标成了通用的“已绑定”和“已连接”。

修正后，页面分别显示“系统已配对”“系统 GATT 已连接”和“Core 已连接”，并从系统配对列表自动选择名称含 `RayNeo` 的设备地址。这个地址只用于辅助发现，不能替代 CoreConnect 设备对象。

## 踩坑与证伪过程

### 1. 初始化成功不等于设备运行时就绪

“初始化内置运行时”成功只表示 DEX 已校验并加载、反射入口可创建、delegate 已注册。它不表示眼镜已发现、已认证或能力协商完成。

因此初始化文案改为“运行时已就绪”，并明确要求继续建立本进程 BLE/CoreConnect 连接。

### 2. `void` 返回制造了发送成功假象

发送入口 `core.t(I3.P)` 返回 `void`。CoreConnect 会在内部捕获部分发送错误并写日志，所以反射调用正常返回只表示消息交给了 dispatcher，不表示消息已发送到眼镜。

真机日志显示实际错误为 `abilityBeanNotExist`：当前进程缺少设备能力元数据，消息没有发出。此前 UI 却显示成功，这是最危险的一类误判。

当前实现会先查询本进程 CoreConnect 连接表；目标设备不在表中时直接拒绝发送。进一步的可靠性仍需通过 SDK 事件或协议 ACK 确认，不能只依赖同步调用结果。

### 3. 蓝牙 MAC 不是 CoreConnect 设备对象

直接把系统配对地址传给 `h.b(deviceId, "ble", pair)` 仍然返回失败。反编译调用链表明，连接前会先通过 `plugin.y(deviceId)` 查找 `I3.D`。查找范围包括插件发现表、CoreConnect 连接表和 CoreConnect 自有绑定表。

`I3.D` 内部需要由扫描或持久化恢复的 `I3.e0` / `RNPeripheral`，其中包含广播元数据和底层 handle。系统的 `BluetoothDevice` 只有地址与配对状态，无法构造等价对象。

为此，连接前加入最多 5 秒的定向发现。超时时主动停止扫描，避免后台持续耗电。测试中能发现附近其他 BLE 设备，却没有发现已经被官方 App 使用、当时未广播的眼镜，这证明“系统知道 MAC”与“CoreConnect 已发现设备”是两回事。

### 4. 暂停官方 App 也不保证眼镜立即广播

短暂停止官方 App 后重新扫描，眼镜仍未在窗口内出现。可能原因包括眼镜未进入配对/可发现模式、连接释放需要更长时间，或固件广告策略受状态控制。

这个实验只能否定“停止官方 App 后一定立即可发现”，不能证明独立连接永远不可行。后续测试应在用户明确让眼镜进入配对或广播模式后进行，并避免破坏现有绑定。

### 5. Android ID 按签名作用域隔离

反编译确认 CoreConnect 认证使用从 `Settings.Secure.ANDROID_ID` 派生的本机身份。现代 Android 会按设备、用户和应用签名对 Android ID 进行作用域隔离。独立 debug 签名与官方 RayNeo 签名不同，即使位于同一手机，也不会自然得到相同身份。

这意味着复制或重建 `RNPeripheral` 仍不能完整继承官方认证会话，还可能触发重新绑定。基于安全和数据完整性考虑，本次没有尝试复制官方私有数据，也没有伪造绑定记录。

### 6. 系统 GATT 已连接不代表本进程拥有链路

系统查询能看到 GATT 连接，只说明设备正被某个客户端使用。Android 不会把另一个应用创建的 `BluetoothGatt` 对象、回调和会话密钥交给独立 App。将这项状态显示出来有诊断价值，但不能据此开放发送按钮。

### 7. 扫描过滤器不能假设硬件槽可用

测试手机报告 BLE 硬件扫描过滤槽为 `0`。使用硬件过滤的路径不可靠，因此 PoC 启动无过滤扫描，并把目标地址作为 CoreConnect 的 retrieve ID 传入。代价是会收到附近无关 BLE 设备，界面必须明确区分并避免误连。

### 8. UI 值必须在主线程读取

原始按钮回调把 `EditText` 和 `CheckBox` 的读取推迟到 worker 线程。Android View 不是线程安全对象，即使部分设备上看似可用，也存在竞态和线程检查风险。现在点击时先在主线程冻结设备 ID、文本、业务 ID 和配对选项，再把纯数据交给 worker；输入错误仍回显到日志，不让未捕获异常终止 Activity。

### 9. 构建环境也需要显式锁定

本机排障遇到的环境问题包括：

- `adb` 未加入 `PATH`，应使用 `$ANDROID_HOME/platform-tools/adb` 或配置环境变量。
- 仓库没有 `local.properties` 时需显式设置 `ANDROID_HOME`。
- 沙箱环境的默认 `~/.gradle` 不可写，可把 `GRADLE_USER_HOME` 指向临时可写目录。
- 增强脚本曾写死不存在的 Build Tools `36.1.0`，已改为本机安装的 `36.0.0`。
- 首次构建可能需要下载 Gradle 或 Android Platform，并启动 Gradle/ADB daemon；受限环境需明确授权网络和本机 socket。
- 厂商 native library 无法 strip 时会原样打包。这是可接受的构建警告，但会增加 APK 体积。

## 官方链路提供的对照证据

恢复并打开官方 App 后，日志中能观察到同一眼镜完成 GATT 服务发现、认证成功、能力初始化和 CoreConnect 消息流。官方 App 还会在私有 SharedPreferences 中保存带完整 `RNPeripheral` 元数据的绑定设备记录。

这组证据说明硬件、系统蓝牙和官方 `1.0.2` 链路本身正常，同时把故障范围收敛到独立 App 的进程、设备对象和签名身份边界。它不能作为独立 PoC 已经握手成功的证据。

## 路线权衡

### 路线 A：独立 App 并存

优点是保留官方 App、部署简单、故障隔离清晰，适合协议编码、动态加载和可观测性实验。缺点是无法继承官方私有绑定对象、进程内连接、能力表和签名作用域身份。

在“官方 App 不卸载、不清数据”的约束下，这条路线不能把官方认证会话当作系统公共资源。它仍可能在眼镜主动进入可发现/配对模式后建立一套属于自己的连接，但可能涉及重新绑定，必须谨慎验证。

### 路线 B：以官方 APK 为底座的增强包

把控制台注入官方包，可在相同包名和进程中复用 CoreConnect 组件，技术上最接近目标。但任何修改都需要重新签名，而 Android 不允许不同签名的 APK 覆盖官方安装。卸载官方版本再装增强包会丢失或迁移失败私有状态，登录、推送和服务端证书校验也可能拒绝重签名。

因此该路线与“保留当前官方 App”冲突。仓库保留构建工具用于受控研究，不把它描述成无损升级方案。

### 路线 C：向官方进程注入运行时代码

root、LSPosed 或 Frida 类方案可以让扩展逻辑进入官方进程，并保留官方签名安装。代价是需要更高设备权限，安全面和维护成本显著增加，系统升级、反调试和完整性校验都可能使方案失效。这不是普通 APK 能力，也不属于当前无 root PoC 的默认交付路径。

### 当前决定

保持官方 App 与绑定状态不变；独立 PoC 只报告其真实可见的三层状态，并阻止未连接时的假发送成功。增强包与运行时注入继续作为明确标注风险的研究路线，不在用户主设备上自动执行。

## 已知无效做法

- 只升级 DEX 哈希，不重新审计混淆 ABI。
- 把官方 App 的“已绑定”理解成 Android 全局可复用会话。
- 仅凭系统配对 MAC 直接调用 CoreConnect 连接。
- 仅凭系统 GATT 已连接判断独立 App 可以发送。
- 以反射方法未抛异常作为消息发送成功。
- 手工构造或复制 `RNPeripheral` 后忽略签名作用域身份。
- 用 debug 签名增强 APK直接覆盖官方签名 APK。
- 为排障卸载官方 App 或清除数据，破坏用户现有绑定。

## 复现与升级检查表

提取新版本后，至少执行以下检查：

1. 用包管理器确认 `versionName`、`versionCode` 和安装包路径。
2. 计算完整 APK、每份 DEX 和最小 native 依赖闭包的 SHA-256。
3. 对比官方证书指纹，记录变化但不要提交用户设备信息。
4. 用 `apkanalyzer` 确认反射类、方法签名、字段和 CoreConnect 版本。
5. 运行协议单测和 debug APK 构建。
6. 真机分别检查系统配对、系统 GATT、本进程 CoreConnect 三层状态。
7. 观察 discovery、authentication、ability 初始化和消息 ACK/错误事件。
8. 只在本进程连接和能力信息齐备后允许发送。
9. 确认官方 App、登录和绑定仍然完整，再结束测试。

通用构建命令：

```bash
export ANDROID_HOME=/path/to/Android/sdk
export GRADLE_USER_HOME=/path/to/writable/gradle-home
./gradlew testDebugUnitTest :app:assembleDebug
```

通用 ADB 检查命令：

```bash
$ANDROID_HOME/platform-tools/adb devices -l
$ANDROID_HOME/platform-tools/adb shell dumpsys package com.rayneo.venus.pub
$ANDROID_HOME/platform-tools/adb logcat
```

日志导出和提交前必须去除设备地址、序列号、账户信息、token、本机身份及其他个人数据。

## 后续工作

- 在眼镜由用户明确置于可发现模式时，验证独立进程的完整发现、认证和能力协商。
- 将发送结果绑定到 CoreConnect 回调或协议 ACK，形成可验证的消息状态机。
- 为三层状态和错误分类补 UI 自动化或 instrumentation 测试。
- 继续缩小厂商字节码依赖，逐步形成可审计、可维护的 clean-room 兼容层。
- 每次官方 APK 更新都重新执行本页检查表，禁止仅修改版本字符串后发布。
