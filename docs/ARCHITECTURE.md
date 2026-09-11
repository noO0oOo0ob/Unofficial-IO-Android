# 初版技术架构

## 打包与加载

```text
Unofficial IO APK
├── 主应用 DEX：UI、协议编码、版本锁定适配层
├── assets/vendor：RayNeo AI 1.0.2 的三份原始 DEX
└── lib/arm64-v8a：CoreConnect 最小 native 依赖闭包
```

应用首次初始化时，把三份内置 DEX 复制到本应用私有 `code_cache`，逐份校验 SHA-256，并在交给 `DexClassLoader` 前标记为只读。类加载器的父级是主应用 ClassLoader，native 搜索路径只指向本 APK 的 `nativeLibraryDir`。整个过程中不查询 `com.rayneo.venus.pub`，也不从其他安装包读取代码。

## 设备控制链路

```text
MainActivity
  -> BundledRayNeoRuntime（稳定的自有 API）
    -> 版本锁定的反射 ABI
      -> RayNeo plugin delegate / CoreConnect
        -> Android Bluetooth BLE
          -> RayNeo 眼镜
```

初始化适配层复用原插件内部的 discovery、connection、message、analytics 和 file-transfer delegate，使 CoreConnect 产生的事件继续被转换为 Map 并进入插件已有事件队列。主应用定时排空该队列并显示事件。独立 App 不共享官方 App 的私有绑定数据库或进程内连接；它会从 Android 系统配对列表识别 RayNeo 蓝牙地址，再建立自己的 CoreConnect 连接。

当前暴露的动作包括扫描附近 BLE 设备、查询 Android 系统配对和 GATT 状态、查询本进程 CoreConnect 连接、BLE 连接/断开，以及按业务 ID 发消息。独立 App 无法读取官方 App 的私有绑定数据库或复用其进程内 CoreConnect 单例；对于已经在系统层配对的 RayNeo，页面会自动填入蓝牙地址，再由本进程建立连接。扫描不下发 Android 硬件过滤器，因此兼容硬件扫描过滤槽为 0 的手机，但 CoreConnect 也会把其他 BLE 广播转换成发现事件；用户必须确认设备名和设备 ID 后再连接。这里控制的是眼镜，不是官方手机 App。

CoreConnect 的认证还使用按应用签名作用域生成的本机身份。独立 debug 签名与官方签名不同，因此 Android 系统配对、官方 GATT 已连接和官方 CoreConnect 已认证是三个不同状态。独立 App 不会把系统 GATT 状态冒充为本进程连接，也不会在能力表缺失时报告消息发送成功。

## 消息收发

发送路径把 payload、deviceId、business enum、随机 messageId 和 channel 名组装为 CoreConnect 的传输消息，再交给 core dispatcher。接收路径由原始 message delegate 解析并写入事件队列，主应用读取后显示原始 Map。

与 iOS Turbo-IO 对齐的语音助手上行消息使用 protobuf wire format：

- `version = 1`
- `type = 5`（ASR text）
- `message = {"final":true,"text":"..."}`
- CoreConnect business id = `13`（voice assistant）

协议编码由主应用独立实现，不依赖生成的 protobuf 类，并有 golden-byte 单测。

## 初版限制

- 只支持 `arm64-v8a` 和 Android 10（API 29）以上。
- 反射类名和字段名与 RayNeo AI `1.0.2 (68)` 强绑定，升级必须重新审计。
- Companion Device Manager 后台服务不能从动态 DEX 直接注册进 Manifest，本版定位为前台连接 PoC。
- 独立 PoC 尚未完成真机眼镜握手；已从官方 App 日志确认同一眼镜可完成 GATT、认证、能力协商和消息链路，这不能替代独立进程验证。
- APK 内的原始 DEX 远大于真正需要的类。后续应在真机链路跑通后裁剪为可维护的 clean-room 兼容层。

完整调查过程、失败假设和路线权衡见 [1.0.2 调查记录](INVESTIGATION_1.0.2.md)。
