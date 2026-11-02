/** 导航表示当前能力清单；规划项保持禁用，不代表已经存在对应页面或后端接口。 */
export const navigation = [
  { id: 'monitoring', label: '实时监控', mark: '01', implemented: true },
  { id: 'inspections', label: '巡检任务', mark: '02', implemented: false },
  { id: 'alarms', label: '告警中心', mark: '03', implemented: false },
  { id: 'history', label: '历史报告', mark: '04', implemented: false },
  { id: 'diagnostics', label: '通信诊断', mark: '05', implemented: true },
  { id: 'devices', label: '设备管理', mark: '06', implemented: false },
];

export const featureRoadmap = [
  {
    order: '01',
    name: '启动 / 停止机器人',
    status: '已接通',
    description: '已有真实 MQTT → edge → Modbus TCP → 状态读回链路。',
    next: '保留现有接口，逐步整理后端分层。',
  },
  {
    order: '02',
    name: '新建巡检任务',
    status: '推荐下一步',
    description: '先做点位选择、任务名称、校验与确认，再接创建任务接口。',
    next: 'inspection：api → application → domain → infrastructure。',
  },
  {
    order: '03',
    name: '启动 / 取消任务',
    status: '待实现',
    description: '先明确任务状态与按钮可用条件，再接 edge 执行与到达反馈。',
    next: '任务状态机与目标点演示点表。',
  },
  {
    order: '04',
    name: '获取现场截图',
    status: '待实现',
    description: '先设计图片查看与失败提示，再接独立软件摄像机和归档。',
    next: 'camera / file 模块与 edge 摄像机适配。',
  },
  {
    order: '05',
    name: '查看 / 确认告警',
    status: '待实现',
    description: '展示测量值、阈值、截图与处理记录，确认不代表异常消失。',
    next: 'alarm 模块、规则与业务持久化。',
  },
  {
    order: '06',
    name: '查看任务报告',
    status: '待实现',
    description: '汇总各点采集、图片、异常和执行事件。',
    next: '历史查询、文件关联、MySQL 与迁移。',
  },
];
