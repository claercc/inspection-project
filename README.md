# 工业巡检机器人上位机演示系统

代码整理与检查入口见 [代码阅读指南](docs/CODE_GUIDE.md)。修改源码后在根目录运行 `npm run check`；完整构建与回归命令见指南。

当前交付：**阶段 A，模拟设备的真实通信闭环**。Vue 发起启动/停止，Spring Boot 通过 MQTT 发送命令，独立 edge 进程通过 Modbus TCP 控制独立机器人模拟器，读回状态后经 MQTT 和 SSE 更新 Vue。

2026-10-15 更新：已完成前端监控工作台与职责拆分，并按业务模块创建前后端规划目录。后续从页面按钮逐步接入后端；目录和实现顺序见 [前端优先说明](docs/FRONTEND_FIRST_PLAN.md)。空目录可用 `powershell -ExecutionPolicy Bypass -File scripts/prepare-structure.ps1` 重建。

2026-10-16 更新：温度卡片可「查看来源」，展示实际 Modbus 原值、换算、采集编号和 MQTT 主题，并支持独立订阅核对。先运行 `node scripts/observe-telemetry.mjs --device robot-001`，再打开来源抽屉；详细步骤见 [温度来源核对](docs/DATA_PROVENANCE.md)。本单元 20 项端到端检查及 5 个 TCP 测试通过。

无需机器人、摄像头、厂商账号或付费云 API。运动、温度、电量与点表均为演示模拟；这不代表已接入真实工业设备。摄像机、三点巡检、业务归档与视频按阶段 B/C 推进。

## Docker Compose 启动

前置条件：Docker 引擎运行、Compose v2、可下载 Maven/npm 依赖与容器镜像。

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f server edge robot
```

访问 http://127.0.0.1:8088 。仅 Web 端口绑定本机；Broker、Modbus 和 server API 在容器网络内。默认不依赖 MySQL、Redis、摄像机或媒体服务。

```powershell
docker compose stop robot    # 观察设备离线
docker compose start robot   # 重新读取状态，机器人回到停止状态
docker compose down
```

此配置交付本地模拟演示，公网只读访问与控制鉴权在阶段 C 完成。阶段 A 命令记录保存在进程内存中，重启会清空；它还不是 SQL 历史归档。

## Windows 本地独立进程启动

前置条件：Node.js 20.19+ / 22.12+（本机 Node 24）、Java 17–25、Maven 3.6.3+。推荐 Java 17。没有 Java/Maven 可运行下面的便携工具下载脚本，仅写入 `.tools/`，会校验官方校验和。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -ExecutionPolicy Bypass -File scripts/start.ps1 -SkipBuild
```

访问 http://127.0.0.1:8088 。本地模式使用真实 TCP Aedes MQTT 3.1.1 Broker，Compose 使用 Mosquitto，两者遵循相同协议。五个进程分别运行并可独立停止。日志在 `.run/`，只绑定 loopback。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/stop.ps1
```

`start.ps1` 固定本地端口 1883/1502/8080/8088；`.env` 供 Compose 使用，不会改变本地脚本端口。若已有 Maven/Java，设置 `JAVA_HOME` 或放入 PATH。`.tools/` 中存在便携 Java 17 时会优先使用。

## 验证

```powershell
# Java 构建与真实 TCP 协议集成测试
powershell -ExecutionPolicy Bypass -File scripts/build.ps1

# 自动启动隔离测试栈，从浏览器点击启动/停止，结束后清理测试进程
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

测试栈端口为 18883/11502/18080/18088，不影响演示栈。浏览器优先使用本机 Chrome/Edge；没有时运行 `npx playwright install chromium`。Linux/macOS 可在项目根运行 `mvn verify`、分别在根目录和 web 运行 `npm ci`、在 web 运行 `npm run build`，然后 `JAVA_BIN=/path/to/java npm run verify`。

验证报告与截图在 `docs/verification/`；运行日志在 `.run/verification/`。详情见 [验证记录](docs/VERIFICATION.md) 和 [开发进度](docs/DEVELOPMENT_PROGRESS.md)。Compose 容器运行与本地进程运行分别记录，不混称已验证。

## 阅读与讲解

- [架构与关键实现](docs/ARCHITECTURE.md)
- [Modbus 点表、MQTT 主题与消息](docs/PROTOCOL.md)
- [API 与演示步骤](docs/API_AND_DEMO.md)
- [开发进度与下一阶段](docs/DEVELOPMENT_PROGRESS.md)

版本固定在 Maven POM、npm package/lock 文件和 Compose 中；使用 Eclipse Paho Java 1.2.5、Spring Boot 3.5.16、Vue 3.5.22、Vite 7.1.12。官方参考：[Spring Boot Java 要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Paho Java](https://eclipse.dev/paho/clients/java/)、[Aedes MQTT 3.1.1 支持](https://github.com/moscajs/aedes)、[Vite Node 要求](https://vite.dev/guide/)。
