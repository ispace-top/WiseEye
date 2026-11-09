# 慧眼 WiseEye

将闲置 Android 手机变成**标准 IP 摄像头**，接入 EasyNVR 等平台，实现零额外硬件投入的家庭监控。

> 作者：[爱思培斯 ispace-top](https://github.com/ispace-top) · 主页 [ispace.top](https://ispace.top)

## 核心能力

| 能力 | 说明 |
|------|------|
| 🎥 视频采集 | CameraX 采集 + MediaCodec H.264 硬编码（零拷贝） |
| 🎙️ 音频采集 | AudioRecord + MediaCodec AAC 硬编码 |
| 📡 RTSP 推流 | 自研 RTSP 服务端（RTP over TCP，RFC 6184/3640），Digest 鉴权 |
| 🔍 ONVIF | WS-Discovery 自动发现 + Device/Media/PTZ 服务 + WS-Security 鉴权 |
| 📹 RTMP 推流 | 自研轻量 RTMP 客户端（跨网场景） |
| 🔄 变焦 | 多摄光学/数字变焦（Strategy/Factory/Adapter 设计模式） |
| 🔋 长时值守 | 前台服务 + 三锁 + 看门狗 + 开机自启 + 关屏推流 |

## 技术栈

- Kotlin / CameraX / MediaCodec
- 全自研流媒体协议栈：RTSP（RTP/RTCP/SDP）、ONVIF（SOAP/WS-Discovery/WS-Security）、RTMP（握手/AMF0/FLV）
- minSdk 24 / targetSdk 34

## 快速开始

```bash
# 构建
./gradlew assembleDebug

# 安装（替换为你的设备）
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 接入 EasyNVR

1. 打开 App → 点击「开始推流」，界面显示 RTSP 地址（默认 `rtsp://admin:admin123@手机IP:8554/live`）；
2. EasyNVR → 设备管理 → 添加设备 → 选 ONVIF（自动搜索发现）或 RTSP（手动填地址）；
3. 默认账号 `admin` / 密码 `admin123`（可在 App 设置页修改）。

## 目录结构

```
app/src/main/java/top/ispace/wiseeye/
├── stream/    # 采集/编码/服务/复用器
├── rtsp/      # 自研 RTSP 服务端
├── onvif/     # 自研 ONVIF 服务
├── rtmp/      # 自研 RTMP 推流
├── keepalive/ # 锁管理/开机自启
├── data/      # 配置存储
└── util/      # 工具类
```

## 许可证

MIT License
