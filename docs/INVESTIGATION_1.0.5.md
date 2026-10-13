# RayNeo AI 1.0.5 官方基线升级与兼容性评估

核验日期：2026-09-21。主线 `enhancer/` 从 `1.0.4 (195)` 升级到 `1.0.5 (201)`；早期独立 `app/` PoC 不在本次升级范围。原始 APK、反编译输出和本地签名产物不提交仓库。

## 2026-10-13 复核

重新读取官方公开下载页，并按网页当前公开签名协议查询 App 生产接口、官网接口。两者均返回 `error_code=1000`、任务 `240 / Android OTA2.5 1.0.5.201`，下载 URL、MD5 和大小均相同。因此当前本地 `1.0.5 (201)` 已是这两个公开渠道返回的最新版本，无更高版本需要迁移。未抓取手机实际请求，账号、设备和灰度条件仍可能影响 App 内查询。

本地原始 APK 的大小、MD5、SHA-256、全部 DEX 哈希和 ZIP 完整性重新验证通过。`libapp.so` 中生产域名与升级路径仍存在；证据中列出的 12 个 smali 类及全部签名与已审核的 1.0.5 样本一致。JADX 单类复核 `K7.b` 确认 `d/e/f/g` 分别对应音频仲裁器、模拟模式、助手运行状态、设备模型；本次有 2 个方法反编译错误，仍以 smali 和 DEX 校验为准。

完整主机回归重新通过，包括控制器 283 项、Paseo WebSocket、Managed Agent REST/SSE、Hook/Manifest、两种音频仲裁器布局各 22 项反射检查和日志持久化。回环服务测试需允许本机端口监听。

2026-10-13 重新执行完整构建成功，新包通过 v3 签名、16 KB 对齐、Manifest 不变量和官方文件保留检查。与本机保存的旧 Turbo 1.0.4 证书一致；本次全新解码的关键 smali 也与既有 ABI 证据一致。产物及 SHA-256 见本文末尾。

本次 ADB 设备列表为空。以下关于保留绑定的判断以同包名、同签名、保留数据覆盖安装为前提；未验证当前手机安装版本、自动重连或镜片上的实际行为。

## 官方检查更新接口

1.0.4、1.0.5 的 Flutter AOT 二进制 `libapp.so` 均包含生产域名 `https://xr-api-p.rayneo.cn` 和路径 `/xrlauncherapi/v1/signApi/gray/upgrade`。本次实际只读请求以下接口成功，并返回同一份新版 APK：

- App 生产域名：`GET https://xr-api-p.rayneo.cn/xrlauncherapi/v1/signApi/gray/upgrade`
- 官网下载页使用：`GET https://xrlauncher-o.api.leiniao.com/xrlauncherapi/v1/signApi/gray/upgrade`
- 官网入口：[雷鸟 AI 眼镜下载](https://rayneo.cn/commonPage/venus/appDownload/index_m.html)，来自[官方支持页](https://rayneo.cn/support.html)。海外页面使用 `/xrlauncherhwapi/v1/signApi/gray/upgrade`。

查询复用了官方公开网页的参数与签名协议，不使用用户登录信息：`packageName=com.rayneo.venus.pub`、`packageType=app`、`deviceType=android`，`versionCode` 和 `appVersion` 均为网页默认的 `00.00.00.0001`；请求头使用 `xtcl-app`、`xtcl-nonce`、`xtcl-timestamp`、`xtcl-authorization`。不在仓库保存签名密钥或完整鉴权请求头。

这里的“最新”是查询时官方公开下载渠道实际返回的版本；未抓取手机上的更新请求，不能据此承诺账号、设备或灰度渠道得到完全相同的结果。App 内路径及域名来自二进制，GET、签名和查询参数来自官网实现及成功请求，不能把两者误写成已完成 App 运行时抓包。

返回任务 `240 / Android OTA2.5 1.0.5.201`，`versionName=1.0.5`、服务端 `versionCode=100050201`。APK Manifest 的 Android `versionCode=201`，两者不是同一编码。完整公开响应及静态 ABI 核验结果保存在 [official-1.0.5-upgrade.json](evidence/official-1.0.5-upgrade.json)。更新内容为全天智记、实时字幕翻译、OTA/配对连接体验优化及问题修复。

## APK 校验

| 项目 | 结果 |
| --- | --- |
| 包名 | `com.rayneo.venus.pub` |
| Android 版本 | `1.0.5 (201)`，此前为 `1.0.4 (195)` |
| minSdk / targetSdk / ABI | `29 / 36 / arm64-v8a` |
| 大小 | `406035635` bytes |
| MD5 | `fb44c02b50dceb632b7b7440496c7669`，匹配官方接口 |
| SHA-256 | `770ba0793d31609aa1e4477db2f8a7aec2c8acc4d9c6ab43d57b0dfc720d3ab3` |
| 官方证书 SHA-256 | `a5bd909475e5c7441e0ef48ff7ee69efbc594808a60b470a4feb6d30b0340883`，与旧官方样本相同 |

构建脚本锁定新版 SHA-256，保持官方包名、版本号及原有组件，仅增加 Turbo 组件与语音入口包装。CoreConnect Java 层有变化；不能因为 ABI 或 `libconnect_core.so` 相同就认定整个 SDK 版本相同，因此诊断标签改为共享 ABI `1.0.4-1.0.5/E3.u`，不再给新版误标旧 SDK 版本。

## Turbo 兼容性

| Turbo 依赖 | 新版核验 |
| --- | --- |
| 语音 Hook | `com.rayneo.rayneo_venus_sdk_plugin.e.c(String, byte[])` 和监听器列表 `e.c` 保留 |
| 连接表/发送 | `E3.u.h()`、静态单例 `h`、Context `i` 保留；`u(E3.Q, Function2)` smali 方法体与 1.0.4 相同 |
| 消息模型 | `E3.Q` smali 相同，五参数构造器保留 |
| business 编码 | `P3.h` 与 `P3.h$a.a(byte)` 保留；枚举值未变，仍使用 business 13/21 |
| 语音实例 | `K7.b$b.a → K7.b`；`e/f/g` 仍分别提供 mock/running/model |
| 音频占用 | `K7.b.d` 类型从 `D7.K` 改为 `D7.L`，`a/b/c/d` 布尔状态及 `d(boolean)` 复位方法保持语义；适配器访问实际对象，不硬编码仲裁器类名 |
| 共享音频 | `K7.d.n` 仍为共享模式对象；普通唤醒仍清除历史 system-interruption 标志 |
| Opus | `OpusDecoder` smali 完全相同；初始化、解码、释放签名均保留 |

JADX 1.5.5 本轮有 68 个方法级反编译错误，关键入口同时以 smali 核验；文件查找以 `.class` 描述符为准，避免 macOS 不区分大小写导致 `u.smali` / `u.1.smali` 混淆。

结论：静态检查未发现需要改写 Turbo 接管、协议、Agent 平台或存储逻辑的不兼容。本次运行时代码只更新 ABI 诊断标签，新增回归覆盖 1.0.5 的仲裁器类名变化。66 个 native 库有 10 个变化，`libconnect_core.so`、`libopus.so`、`libopusenc.so` 相同，但 `libapp.so`、`libbusiness.so`、`libbusiness_jni.so` 等变化意味着仍需真机验收；方法签名与构建成功不等于设备行为完全兼容。

## 是否需要重新绑定眼镜

**如果手机上仍是同一把本地 debug key 签名的 Turbo 1.0.4，预期可以覆盖安装保留数据，不需要主动解绑或重新绑定。** 新包仍使用该包名和本机原有 keystore，Android 版本码从 195 升到 201。`adb install -r` 保留应用数据，见 [Android ADB 文档](https://developer.android.com/tools/adb)；更新需满足签名兼容，见 [Android 签名文档](https://developer.android.com/studio/publish/app-signing)。

- 官方账号、本地绑定记录、系统蓝牙配对通常随保留数据更新继续使用；进程重启后的自动重连不等于重新绑定。
- Turbo 的 SharedPreferences 名称、KeyStore alias、MiniMax/Managed Agent 密文文件路径均未修改，预期保留原配置。
- 接管模式原本就不跨进程保存，更新后需重新手动开启。
- 若手机安装的是官方签名版，或另一把密钥签名的增强版，不能直接覆盖；不要为绕过安装失败直接卸载。卸载会删除本地数据并影响加密配置，届时可能需要重新登录、配置和绑定。
- 官方 Flutter 层及配对相关代码确有更新，未完成全部数据迁移逻辑逆向，也未验证手机现有绑定状态；不能保证绝对不会要求重新认证或配对。
- App 内直接下载的是官方签名 APK，不是 Turbo 增强包；后续官方更新也应重新做兼容性检查与增强包构建。

建议安装顺序：关闭 Turbo 接管 → 同签名覆盖安装 → 打开官方入口确认账号/眼镜自动连接 → 打开 Turbo 检查配置并启用 → 测试唤醒、连续语音、消息/通知显示 → 关闭接管检查官方语音、翻译和全天智记。无需先升级眼镜固件或主动解除绑定。

```bash
adb install --no-incremental -r enhancer/build/output/RayNeo_AI_1.0.5_201-turbo-debug.apk
```

## 验证边界

主机回归通过：控制器 283 项断言、导航 9 项、模型路由 12 项、通知格式 5 项、Paseo 10 项、Managed Agent 16 项、平台装配 5 项、Hook/Manifest 不变量、1.0.4/1.0.5 两种仲裁器布局各 22 项反射适配检查、日志持久化测试。

ADB 本次未发现已连接设备。尚未安装到手机，也未证明实际蓝牙重连、绑定保留、音频帧、消息 ACK、镜片显示或官方功能回归。

增强 APK 于 2026-10-13 重新构建，并完成 16 KB 对齐、v3 签名验证和文件差异验证：

- 产物：`enhancer/build/output/RayNeo_AI_1.0.5_201-turbo-debug.apk`
- SHA-256：`975fef105c24188d106eb7fdbefb9ad85139f87b9e0d27c64a30025edf5c925c`
- 新旧本地 Turbo 包证书 SHA-256 均为 `50c30ceed3b6fa090379750c02a1d2dd2d26d5fef49803bfe9ea92063c4b5cab`。这是对本机保存的旧包的核验，不是对当前手机安装包的核验。
- 仅修改 `AndroidManifest.xml`、`classes2.dex` 并新增 `classes4.dex`；3939 个官方文件逐字节一致，其中资源、assets、native 共 3629 个。原语音 Hook 方法体可完整恢复。
- 2026-09-21 首次构建在组装阶段因磁盘不足中断；清理本轮生成的重复解包资源后，从已成功编译的中间包继续完成组装、对齐、签名与验证。没有修改签名密钥或官方资源来解决该问题。
- 旧增强包保留在仓库外 `../rayneo-local-samples/turbo-backup-1.0.4/`。保留 APK 不等于已备份应用私有数据，也不保证 Android 允许直接降级覆盖。
