# 从前端按钮逐步接入后端

更新：2026-10-15。按用户最新要求，先做有完整视觉和交互边界的前端，再按按钮逐个完成业务链路。未实现功能只创建目录，不生成占位页面、Controller、Service 或实体。

## 已落地的前端结构

```text
web/src/
├─ App.vue                         页面入口，只组合布局与视图
├─ main.js                         Vue 挂载与全局样式
├─ layouts/ConsoleLayout.vue        导航、顶部栏、功能路线弹窗
├─ views/
│  ├─ monitoring/MonitoringView.vue 监控页面组合与展示交互
│  ├─ inspections/                 空：巡检任务
│  ├─ alarms/                      空：告警
│  ├─ history/                     空：历史报告
│  ├─ diagnostics/                 空：未来独立诊断页，当前诊断在首页
│  ├─ devices/                     空：设备管理
│  └─ settings/                    空：配置页面
├─ api/device.js                   HTTP / SSE 地址、发送、消息解析
├─ composables/useDeviceConsole.js  会话状态、订阅生命周期、控制动作
├─ config/workspace.js              导航能力清单与按钮实现路线
├─ assets/styles/console.css        视觉变量、布局、组件状态、响应式
├─ assets/images/                  空：未来实际使用的图片
├─ components/common/              空：后续提取真实复用组件
├─ components/monitoring/           空：后续拆分监控面板
├─ components/charts/              空：后续复用图表
├─ router/                         空：有多个真实页面后再接路由
├─ stores/                         空：需要跨页面共享状态后再选择状态库
└─ utils/                          空：出现复用工具时再添加
```

当前单页面保持 Vue 3 + JavaScript + Vite，未新增依赖。composable 是管理有状态逻辑的函数，本项目用它管理 SSE 和按钮动作；参考 [Vue 官方说明](https://vuejs.org/guide/reusability/composables.html)。视图不直接调用 fetch 或 EventSource。

## 后端结构：模块内分层

```text
server/src/main/java/demo/inspection/server/
├─ ServerApplication.java
├─ device/
│  ├─ DeviceController.java         原有运行代码，暂时保留
│  ├─ api/                         空：HTTP、请求/响应 DTO、异常映射
│  ├─ application/                 空：查询/控制用例、权限、事务
│  ├─ domain/                      空：设备状态、规则、所需接口
│  └─ infrastructure/              空：MQTT、Redis、SQL 具体适配
├─ inspection/{api,application,domain,infrastructure}/  空：任务
├─ alarm/{api,application,domain,infrastructure}/       空：告警
├─ file/{api,application,domain,infrastructure}/        空：文件
├─ camera/{api,application,domain,infrastructure}/      空：摄像机
├─ access/{api,application,domain,infrastructure}/      空：身份与权限
├─ audit/{api,application,domain,infrastructure}/       空：操作审计
├─ diagnostics/{api,application,domain,infrastructure}/ 空：通信诊断
└─ integration/ConsoleService.java 原有过渡实现，待逐用例提取
```

大括号表示实际存在的四个子目录，不是一个大括号名称。资源目录 `db/migration/`、`config/` 为空。edge 已预留 application/domain/infrastructure(mqtt/modbus/camera)/config；摄像机模拟器和部署 media/environments 也只建目录。

这些目录表达后续代码的归属，**不代表后端已完成分层迁移**。已有 Java 代码与 MQTT/Modbus 行为本轮未改动。后续逐按钮调整，避免目录变动与功能变动同时扩大。

接口层 → 应用层 → 领域规则；基础设施实现业务层所需接口。领域规则不依赖 HTTP、Paho 或 SDK。MySQL 保存业务事实，Redis 保存可重建最新状态。需要持久化时再明确 ORM、迁移、事务与唯一键，不提前生成空实现。

Git 不跟踪空目录。目录清单由 `scripts/prepare-structure.ps1` 保存；在新检出中运行即可恢复，不放 `.gitkeep` 或假业务文件：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/prepare-structure.ps1
```

## 本轮按钮能力

| 按钮 / 展示 | 当前行为 | 数据边界 |
| --- | --- | --- |
| 启动 / 停止机器人 | 已接原有后端，反馈以读回为准 | POST /api/commands + SSE |
| 重连实时订阅 | 关闭旧 EventSource，重新订阅 | 不重发设备命令，不重连设备 |
| 指令详情 | 查看完整 commandId、结果和关联事件 | 来自已有 SSE；未上报阶段不推断 |
| 功能路线 | 展示当前能力与后续按钮实现顺序 | 本地静态规划配置，非业务记录 |
| 通信诊断导航 | 滚动到已有诊断面板 | 没有虚构新的诊断页面 |
| 新建巡检任务及其余规划导航 | 禁用并标注规划中/待实现 | 没有创建任务或请求未实现接口 |
| 温度趋势 | 当前会话最多 60 个真实接收采样 | 无固定模拟曲线，无数据库历史；离线降低透明度 |
| 温度查看来源（单元 3） | 锁定采样、原始寄存器与换算、实际主题、独立核对说明 | 已接 edge 原始 FC03 数据；来源元数据不是硬件认证 |

## 推荐逐按钮实现顺序

下列 API 是**设计建议，尚未创建**。

| 顺序 | 前端按钮与第一小步 | 后续后端用例与边界 |
| --- | --- | --- |
| 1 | 新建巡检任务：表单、点位选择、校验、配置预览 | POST /api/inspection-tasks；inspection 模块创建任务并保存，接入后才提示创建成功 |
| 2 | 启动 / 取消任务：状态展示与按钮条件 | POST /api/inspection-tasks/{id}/start 或 /cancel；先补目标点控制与到达反馈，再做 edge 任务执行 |
| 3 | 获取截图：图片查看、加载/失败状态 | POST /api/cameras/{id}/snapshots；独立 HTTP 模拟摄像机、驱动与文件归档 |
| 4 | 查看 / 确认告警：证据详情与处理输入 | alarm 模块；阈值判断、唯一事件、确认记录，确认不等于异常消失 |
| 5 | 查看任务报告：结果、图片与事件列表 | GET /api/inspection-tasks/{id}/report；查询已持久化事实，不先做复杂 PDF |
| 6 | 登录与受控操作：只读和操作状态 | access / audit；公网前落实权限与操作审计 |

每个按钮仍按“前端可见交互 → 请求/响应约定 → 后端分层用例 → 必要存储/设备动作 → 反馈 → 验证”拆成若干学习单元。需要模拟预览时明确标注预览，不把本地草稿当作后端已创建的任务。

## 本次验收与下一步

Vue 生产构建与端到端回归结果见 `VERIFICATION.md` 的前端单元记录。当前演示使用已有本地服务 http://127.0.0.1:8088 ，刷新查看新页面。

2026-10-16 已按用户反馈先完成温度来源核对，见 DATA_PROVENANCE.md 与 LEARNING_PROGRESS.md。当前停止并等待阅读反馈；之后再决定完善场景模型或进入“新建巡检任务”的前端表单。本表保留长期按钮路线，不代表自动开始下一项。
