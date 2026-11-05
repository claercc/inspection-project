# 代码阅读与检查

更新：2026-11-03。已整理模拟链路的职责与格式，并为主要函数补充中文用途、参数、返回值和调用说明。

## 从哪里读

前端从 `web/src/features/device/DevicePage.vue` 开始。设备相关组件在同目录的 `components/`，页面状态和订阅生命周期在 `useDevice.js`，网络请求在 `api.js`。页面展示指标、趋势、控制和命令记录，连接与诊断按需打开。状态文字与显示格式仍分别在 `config/deviceStatus.js` 和 `utils/format.js`。摄像头页面已经启用，持久化采集记录入口保持禁用。

摄像头页面从 `web/src/features/camera/CameraPage.vue` 开始，接口封装在同目录的 `api.js`。后端 `camera/CameraController` 提供信息、预览帧和抓拍接口，`CameraService` 只编排图像获取，`CameraDriver` 隔离具体设备实现。当前注入的是 `SimulatedCameraDriver`，生成的画面始终带模拟标识。

后端从 `device/DeviceController` 进入 `device/DeviceService`，后者管理设备状态、控制准入和命令记录。`integration/EdgeGateway` 负责 MQTT 生命周期和发布；`integration/EdgeMessageHandler` 解析接收消息并调用设备服务。配置集中在 `application.yml`，继续兼容 DEVICE_ID / MQTT_URL 环境变量。SSE 由 `DeviceEventStream` 广播。现有 `device/domain/` 中的校验、命令历史和采样规则继续复用。

edge 从 `EdgeApplication` 开始，它装配设备驱动、MQTT 和单线程执行器。`DeviceCommandHandler` 处理校验、去重、写入和读回；`RobotDriver` 描述它需要的设备操作；`ModbusRobotDriver` 实现寄存器读取、换算与原始数据证据。模拟器点表的名称统一定义在 `common` 的 `RobotRegisters`。

## 保留的关键规则

- START 需要新鲜在线状态，且没有等待反馈的命令；STOP 可以跟在等待反馈的 START 后面，同一时间只接受一条等待反馈的 STOP。
- 命令发布顺序与接受顺序一致。等待 MQTT PUBACK 时释放状态锁，使设备结果能够先到达。
- 写入前失败或设备明确拒绝写入记为 FAILED；写入回复丢失或写入后读回失败记为 RESULT_UNKNOWN。
- 迟到或重复采样不覆盖最新状态，也不刷新采样存活时间。命令的迟到确认仍可更新 RESULT_UNKNOWN，已确认终态不被重复回执覆盖。
- 命令记录与去重账本仍在进程内存中，重启会清空；设备与测量值仍由软件模拟。

## 函数注释约定

Java 方法使用 Javadoc，前端与 Node 脚本使用 JSDoc，PowerShell 函数使用帮助注释。说明包括用途与调用时机、参数含义与单位、默认值、返回数据及可能为空的情况；关键对外函数提供简短调用示例。

异步函数分别说明 HTTP/MQTT 提交结束与设备执行确认的含义。Java 的 void 方法和构造器不添加无效的 `@return` 标签，在正文说明副作用；JS 异步无数据返回使用 `Promise<void>`。错误会被内部处理的函数说明提示或状态更新方式，确实向外抛出或拒绝 Promise 的场景才列异常。

阅读例子可从 `web/src/features/device/api.js`、`web/src/utils/format.js`、`common/.../ModbusClient.java` 和 `server/.../CommandHistory.java` 开始。前端返回的设备快照、命令记录和页面会话对象也有 JSDoc 字段说明，可供编辑器悬停查看。

## 日常命令

在项目根目录运行：

```powershell
npm run format        # 格式化 Java、Vue、JS、CSS、POM 和配置
npm run check         # 格式检查 + JavaScript/Vue 静态检查
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

`build.ps1` 执行 Java Checkstyle、测试、打包、前端格式/静态检查与生产构建。Checkstyle 要求控制语句使用大括号、一行一条语句、每个变量独立声明，并检查通配导入和未使用导入。格式化统一使用仓库固定版本的 Prettier，Java/XML 使用相应插件；编辑器保存与命令行采用同一配置。

VS Code 打开项目后可按 `.vscode/extensions.json` 安装推荐扩展，项目已配置保存时格式化和 ESLint 修复。通过 `npm run check` 不代表协议和控制行为已验证；行为验证由 Java 测试与独立进程端到端测试承担。
