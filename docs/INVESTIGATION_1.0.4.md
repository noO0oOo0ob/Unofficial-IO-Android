# RayNeo AI 1.0.4 导出与 TurboIO 适配记录

日期：2026-09-15。目标版本来自用户通过 USB 连接的 Android 手机。本记录不保存设备序列号、蓝牙地址、账号、token 或 App 私有数据。

## 样本基线

- 包名：`com.rayneo.venus.pub`
- 版本：`1.0.4 (195)`
- minSdk / targetSdk：`29 / 36`
- 安装形态：单一 `base.apk`，`pm path` 未返回 split APK
- APK 大小：`405853526` bytes
- APK SHA-256：`ef2e7dd346ca478e13d0f3f1bf31fa61412e44fb9b4ca9cf0d3e864ac608584b`
- 官方签名证书 SHA-256：`a5bd909475e5c7441e0ef48ff7ee69efbc594808a60b470a4feb6d30b0340883`
- ABI：`arm64-v8a`
- CoreConnect：`2.0.80-SNAPSHOT`

DEX SHA-256：

- `classes.dex`：`98ca2c9e94e6a5ba78726f1d9491e7e261b2dc4291d48c755fc9454bb85e3030`
- `classes2.dex`：`028175c095bee63b16af6c9020552f34bbfbeb4810c9a3cbfdee335ea6c590ae`
- `classes3.dex`：`0a1d561096cb16cbefeb59784542d801a2bfecd71e341e0824b20bbb2f2a9a38`

原始 APK 和 JADX 输出保存在仓库外的 `rayneo-local-samples` 本地目录，不提交和分发。JADX 1.5.5 完整资源反编译产生 63 个方法级错误，no-res 精确类名反编译产生 66 个方法级错误；因此关键签名同时用原始 DEX 的 `dexdump` 复核，不能把反编译 Java 当原源码。

## 1.0.2 到 1.0.4 的关键 ABI 差异

CoreConnect 内部包发生了整体迁移：

- 单例与连接表：`I3.v` → `E3.u`
- 消息模型：`I3.P` → `E3.Q`
- business 枚举：`S3.h` → `P3.h`
- Kotlin Unit：`ca.x` → `Y9.x`
- 语音会话：`N7.b` / `N7.b$b` → `K7.b` / `K7.b$b`
- Kotlin 无参函数接口：`oa.a` → `ka.a`

以下互操作入口仍然存在：

- `com.rayneo.rayneo_venus_sdk_plugin.e.c(String, byte[])` 仍是助手消息分发入口，主 hook 可以继续包裹原方法体。
- `E3.Q(byte[], String, P3.h, String, String)` 保留与旧版同形的 5 参数构造器。
- `E3.u.u(E3.Q, Function2)` 继续提供带完成回调的发送入口；`t` 仍是无可靠完成语义的 fire-and-forget 入口。
- business `13` 仍是 `VOICE_ASSISTANT`，business `21` 仍映射到字节值 `21` 的通知通道；适配器额外校验枚举实际字节，避免未知值静默退回 `RESERVED`。
- `OpusDecoder` 的 `init-uN6NtqM(int,int)`、`decode2Bytes(byte[])` 和 `release()` 仍存在。

## TurboIO 修改

`VendorTransport` 现在使用版本 ABI profile：优先识别 1.0.4，找不到时回退到 1.0.2。profile 同时控制 CoreConnect、消息、business、Unit、语音监听器和函数接口类名。

1.0.4 的共享音频路由器将当前模式从旧版字段 `m` 移到 `n`。旧逻辑若不适配，会把一个固定函数对象误判成“共享音频会话一直运行”，导致 TurboIO 永远无法启用。新版只把 `K7.d.n != null` 解释为活跃共享会话，音频仲裁器 `D7.K.a/b/c/d` 仍分别表示系统中断、翻译、助手和全天智记。

构建脚本已锁定 1.0.4 原 APK 哈希，输出 `RayNeo_AI_1.0.4_195-turbo-debug.apk`。语音 hook 的原方法体仍可逐字节恢复；组装验证只允许原包的 `AndroidManifest.xml`、`classes2.dex` 变化并新增 `classes4.dex`。

## 验证结果与边界

- 主机测试通过：控制器 251 项断言、导航 9 项、模型路由 12 项、通知格式 5 项、Paseo 10 项、Managed Agent 12 项、平台装配 5 项、hook/Manifest 不变量、最新版反射适配 22 项和日志持久化测试。
- 增强 APK 构建、zipalign、本地签名和验证通过。
- 增强 APK SHA-256：`2d492fe3eccac1c1a395104a19a7a445c97ac11fe252e6b626e34aae339f8621`
- 3933 个原包文件保持一致，其中 3623 个为资源、assets 或 native 文件。

尚未完成真机增强包验证。增强包与官方 App 包名相同但签名不同，不能覆盖手机里的官方安装；卸载官方 App 会清除本地登录、绑定和连接状态，因此本轮没有自动安装。构建成功不证明蓝牙握手、Opus 音频、消息 ACK、镜片显示或官方功能回归通过。

真机闭环应在可牺牲数据的测试环境中完成：备份必要数据 → 安装增强包 → 登录并连接眼镜 → 检查 `abi=1.0.4/core-2.0.80` 诊断 → 分别验证普通唤醒、音频帧、发送回调、镜片显示、退出与官方功能回归。
