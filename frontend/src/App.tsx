import {
  Alert,
  Badge,
  Button,
  ConfigProvider,
  Divider,
  Drawer,
  Empty,
  Input,
  Layout,
  List,
  Progress,
  Segmented,
  Space,
  Table,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  theme
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  CloudDownloadOutlined,
  CloudServerOutlined,
  CodeOutlined,
  ControlOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  ExportOutlined,
  FileSearchOutlined,
  FundProjectionScreenOutlined,
  LinkOutlined,
  LineChartOutlined,
  PlayCircleOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { useEffect, useMemo, useRef, useState } from 'react';

const { Header, Content } = Layout;
const { Title, Text, Paragraph } = Typography;

type GovernanceCapability = {
  id: string;
  name: string;
  summary: string;
  dataSource: string;
  outputs: string[];
};

type GovernanceReport = {
  success: boolean;
  status: string;
  message: string;
  toolId: string;
  nextActions: string[];
  report: string;
};

type DeployStatus = {
  success: boolean;
  message: string;
  data?: {
    checkedAt: string;
    deploymentState: string;
    deploymentMessage: string;
    serviceState: string;
    serviceReachable: boolean;
    dockerAvailable: boolean;
    containers: Array<{ name: string; status: string; ports: string; expected: boolean }>;
    uptimeSeconds: number;
    memoryUsedMb: number;
    memoryMaxMb: number;
    memoryUsagePercent: number;
    serviceMetrics?: {
      processCpuLoadPercent: number;
      systemCpuLoadPercent: number;
      threadCount: number;
      heapUsagePercent: number;
      diskUsagePercent: number;
      healthLatencyMs: number;
    };
  };
};

type WorkbenchModule = {
  key: string;
  title: string;
  subtitle: string;
  href: string;
  icon: JSX.Element;
  accent: string;
  metrics: string;
  status: 'online' | 'warning' | 'standby';
};

const modules: WorkbenchModule[] = [
  {
    key: 'chat',
    title: 'AI 交互',
    subtitle: '对话、记忆与登录链路',
    href: '/chat.html',
    icon: <ThunderboltOutlined />,
    accent: 'cyan',
    metrics: '对话 / 记忆 / 登录',
    status: 'online'
  },
  {
    key: 'prompt',
    title: 'Prompt 评测',
    subtitle: '模板、优化器、RAG 与知识库',
    href: '/prompt-optimizer.html',
    icon: <ExperimentOutlined />,
    accent: 'violet',
    metrics: '评分 / 短板 / 版本',
    status: 'online'
  },
  {
    key: 'model-evaluation',
    title: '模型测评',
    subtitle: 'Chat / RAG / Prompt 指标趋势',
    href: '/model-evaluation.html',
    icon: <LineChartOutlined />,
    accent: 'cyan',
    metrics: 'Recall / Precision / TopK',
    status: 'online'
  },
  {
    key: 'media',
    title: '内容生成',
    subtitle: '文生图、图生文、文生视频',
    href: '/text-to-image.html',
    icon: <FundProjectionScreenOutlined />,
    accent: 'green',
    metrics: '图像 / 视觉 / 视频',
    status: 'warning'
  },
  {
    key: 'governance',
    title: '治理分析',
    subtitle: '需求巡检、工期统计与版本复盘',
    href: '/document-quality.html',
    icon: <SafetyCertificateOutlined />,
    accent: 'amber',
    metrics: '需求 / 工期 / 复盘',
    status: 'standby'
  },
  {
    key: 'ops',
    title: '实时态势',
    subtitle: '部署、容器、JVM 与日志',
    href: '/service-dashboard.html',
    icon: <CloudServerOutlined />,
    accent: 'rose',
    metrics: '服务 / 容器 / 指标',
    status: 'online'
  }
];

const governanceCapabilities: GovernanceCapability[] = [
  {
    id: 'requirement-review',
    name: '需求巡检',
    summary: '基于导出的需求数据检查文档完整性、任务拆解、计划日期和交付风险。',
    dataSource: '通用需求列表',
    outputs: ['需求文档缺失清单', '任务拆解风险', '交付节奏提示']
  },
  {
    id: 'effort-summary',
    name: '人员工期',
    summary: '基于任务明细按负责人汇总开始日期、结束日期、人天和排期集中风险。',
    dataSource: '通用任务表',
    outputs: ['人员维度工期表', '缺日期告警', '排期集中风险']
  },
  {
    id: 'release-review',
    name: '版本复盘',
    summary: '基于版本发布记录生成上线事项、质量分布、延期原因和复盘结论。',
    dataSource: '通用发布记录',
    outputs: ['版本复盘报告', '质量趋势摘要', '复盘行动项']
  }
];

const governanceOptions = [
  { label: '需求巡检', value: 'requirement-review' },
  { label: '人员工期', value: 'effort-summary' },
  { label: '版本复盘', value: 'release-review' }
];

function App() {
  const [deployStatus, setDeployStatus] = useState<DeployStatus | null>(null);
  const [selectedTool, setSelectedTool] = useState<string>('requirement-review');
  const [demoResult, setDemoResult] = useState<GovernanceReport | null>(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [sourceUrl, setSourceUrl] = useState('');
  const [currentVersion, setCurrentVersion] = useState('v0.1.0');
  const [targetUrl, setTargetUrl] = useState('');
  const [sourceData, setSourceData] = useState('');
  const [collecting, setCollecting] = useState(false);
  const [promptText, setPromptText] = useState('你是开源 AI 应用框架助手，请基于用户目标生成可执行的功能设计和验收标准。');
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  useEffect(() => {
    loadDeployStatus();
    const timer = window.setInterval(loadDeployStatus, 5000);
    return () => window.clearInterval(timer);
  }, []);

  const selectedCapability = useMemo(() => {
    return governanceCapabilities.find((capability) => capability.id === selectedTool);
  }, [selectedTool]);

  const promptScore = useMemo(() => evaluatePrompt(promptText), [promptText]);
  const serviceData = deployStatus?.data;
  const systemLoad = serviceData?.serviceMetrics?.systemCpuLoadPercent ?? 0;
  const diskUsage = serviceData?.serviceMetrics?.diskUsagePercent ?? 0;
  const memoryUsage = serviceData?.memoryUsagePercent ?? 0;
  const onlineModuleCount = modules.filter((module) => module.status === 'online').length;
  const sourceReadyCount = [sourceUrl.trim(), sourceData.trim()].filter(Boolean).length;

  const moduleColumns: ColumnsType<WorkbenchModule> = [
    {
      title: '能力入口',
      key: 'title',
      render: (_, module) => (
        <Space size={10}>
          <span className={`module-symbol accent-${module.accent}`}>{module.icon}</span>
          <span className="module-title-block">
            <strong>{module.title}</strong>
            <Text>{module.subtitle}</Text>
          </span>
        </Space>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 92,
      render: (status: WorkbenchModule['status']) => (
        <Tag color={status === 'online' ? 'success' : status === 'warning' ? 'warning' : 'default'}>
          {status === 'online' ? '在线' : status === 'warning' ? '需检查' : '待采集'}
        </Tag>
      )
    },
    {
      title: '对象',
      dataIndex: 'metrics',
      width: 150,
      responsive: ['lg']
    }
  ];

  function loadDeployStatus() {
    fetch('/api/deploy/status')
      .then((response) => response.json())
      .then((data: DeployStatus) => {
        setDeployStatus(data);
        setLastUpdated(new Date());
      })
      .catch(() => {
        setDeployStatus(null);
        setLastUpdated(new Date());
      });
  }

  function generateGovernanceDemo() {
    setDemoResult(buildGovernanceReport(selectedTool, currentVersion, sourceUrl, sourceData, true));
    setReportOpen(true);
  }

  function openSelectedSource() {
    const value = sourceUrl.trim();
    if (!value) {
      return;
    }
    window.open(value, '_blank', 'noopener,noreferrer');
  }

  function startGovernanceDraft() {
    const normalizedSourceUrl = sourceUrl.trim();
    const normalizedSourceData = sourceData.trim();

    if (!normalizedSourceUrl && !normalizedSourceData) {
      setDemoResult({
        success: false,
        status: 'MISSING_SOURCE',
        message: '请先填写来源说明或粘贴导出数据',
        toolId: selectedTool,
        nextActions: ['来源用于记录报告出处。', '导出通用 CSV/JSON 后粘贴到数据区。', '也可以先生成示例报告确认输出口径。'],
        report: ''
      });
      setReportOpen(true);
      return;
    }

    setCollecting(true);
    window.setTimeout(() => {
      setDemoResult(buildGovernanceReport(selectedTool, currentVersion, normalizedSourceUrl, normalizedSourceData, false));
      setReportOpen(true);
      setCollecting(false);
    }, 180);
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#22d3ee',
          borderRadius: 8,
          colorBgBase: '#050914',
          colorTextBase: '#eaf7ff',
          fontFamily: 'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
        }
      }}
    >
      <Layout className="dashboard-shell">
        <div className="grid-canvas" />
        <Header className="dashboard-header">
          <div className="brand-block">
            <span className="brand-mark">
              <DashboardOutlined />
            </span>
            <span className="brand-copy">
              <Text>Spring AI Command Center</Text>
              <Title level={3}>智能中枢工作台</Title>
            </span>
          </div>
          <Space size={10} className="header-actions">
            <Badge status={serviceData?.serviceReachable ? 'success' : 'warning'} text={serviceData?.serviceReachable ? '服务在线' : '服务待确认'} />
            <Tag icon={<LineChartOutlined />} color="cyan">
              {lastUpdated.toLocaleTimeString('zh-CN', { hour12: false })}
            </Tag>
            <Tooltip title="回到原静态入口">
              <Button icon={<RocketOutlined />} href="/index.html">
                旧版入口
              </Button>
            </Tooltip>
          </Space>
        </Header>

        <Content className="workbench-content">
          <section className="status-strip" aria-label="实时概览">
            <MetricCell icon={<ControlOutlined />} label="功能域" value={`${modules.length} 组`} tone="cyan" />
            <MetricCell icon={<SafetyCertificateOutlined />} label="治理输入" value={`${sourceReadyCount}/2`} tone="amber" />
            <MetricCell icon={<BarChartOutlined />} label="JVM 内存" value={`${memoryUsage.toFixed(1)}%`} tone="green" />
            <MetricCell icon={<CloudServerOutlined />} label="系统负载" value={`${systemLoad.toFixed(1)}%`} tone="rose" />
          </section>

          <section className="workspace-grid">
            <section className="source-panel work-panel">
              <PanelHeader icon={<CloudDownloadOutlined />} title="治理分析" action={<Tag color={sourceReadyCount > 0 ? 'green' : 'blue'}>{sourceReadyCount > 0 ? '已输入' : '待输入'}</Tag>} />
              <Segmented block options={governanceOptions} value={selectedTool} onChange={(value) => setSelectedTool(String(value))} />
              <Space direction="vertical" size={10} className="full-width source-form governance-form">
                <Input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} prefix={<LinkOutlined />} placeholder="来源链接或来源说明，可选" />
                <div className="form-pair">
                  <Input value={currentVersion} onChange={(event) => setCurrentVersion(event.target.value)} placeholder="当前版本，例如 v0.1.0" />
                  <Input value={targetUrl} onChange={(event) => setTargetUrl(event.target.value)} placeholder="输出目标，可选" />
                </div>
                <Input.TextArea
                  className="source-data-input"
                  value={sourceData}
                  onChange={(event) => setSourceData(event.target.value)}
                  placeholder="粘贴导出的 JSON、CSV 或 TSV 数据；也可先用示例报告确认输出口径"
                  autoSize={{ minRows: 3, maxRows: 5 }}
                />
              </Space>
              <div className="action-row governance-actions">
                <Tooltip title="打开来源">
                  <Button icon={<ExportOutlined />} onClick={openSelectedSource}>
                    打开来源
                  </Button>
                </Tooltip>
                <Button type="primary" icon={<CloudDownloadOutlined />} loading={collecting} onClick={startGovernanceDraft}>
                  生成草稿
                </Button>
                <Button icon={<FileSearchOutlined />} onClick={generateGovernanceDemo}>
                  示例报告
                </Button>
              </div>
              <div className="capability-brief">
                {selectedCapability ? (
                  <>
                    <Space className="capability-title">
                      <ApiOutlined />
                      <Title level={5}>{selectedCapability.name}</Title>
                    </Space>
                    <Paragraph ellipsis={{ rows: 2 }}>{selectedCapability.summary}</Paragraph>
                    <div className="chip-line">
                      {selectedCapability.outputs.map((output) => (
                        <span key={output}>
                          <CheckCircleOutlined />
                          {output}
                        </span>
                      ))}
                    </div>
                  </>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="治理能力读取中" />
                )}
              </div>
            </section>

            <section className="operations-panel work-panel">
              <PanelHeader icon={<AppstoreOutlined />} title="AI 执行流" action={<Tag color="cyan">Canvas</Tag>} />
              <WorkflowCanvas activeTool={selectedTool} systemLoad={systemLoad} memoryUsage={memoryUsage} sourceReadyCount={sourceReadyCount} />
              <Divider />
              <div className="prompt-console">
                <div className="prompt-editor">
                  <Text>Prompt 预检</Text>
                  <Input.TextArea value={promptText} onChange={(event) => setPromptText(event.target.value)} autoSize={{ minRows: 4, maxRows: 6 }} />
                </div>
                <div className="prompt-score">
                  <Progress type="dashboard" percent={promptScore.score} strokeColor={promptScore.color} size={104} />
                  <div>
                    <Title level={5}>{promptScore.level}</Title>
                    <Text>{promptScore.reason}</Text>
                  </div>
                </div>
              </div>
            </section>

            <section className="insight-panel work-panel">
              <PanelHeader icon={<CloudServerOutlined />} title="实时态势" action={<Tag color={serviceData?.deploymentState === 'SUCCEEDED' ? 'green' : 'orange'}>{serviceData?.deploymentState || 'SYNC'}</Tag>} />
              <Space direction="vertical" size={12} className="full-width">
                <GaugeLine label="CPU" value={systemLoad} />
                <GaugeLine label="内存" value={memoryUsage} />
                <GaugeLine label="磁盘" value={diskUsage} />
              </Space>
              <Divider />
              <List
                className="container-list"
                size="small"
                dataSource={serviceData?.containers || []}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无容器数据" /> }}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta avatar={<Badge status={item.expected ? 'success' : 'default'} />} title={item.name} description={item.status} />
                  </List.Item>
                )}
              />
              <Alert
                className="auth-alert"
                type={sourceReadyCount > 0 ? 'success' : 'info'}
                showIcon
                message={sourceReadyCount > 0 ? '治理输入已准备' : '等待治理输入'}
                description={sourceReadyCount > 0 ? '当前工作台会基于页面输入生成可复制报告草稿。' : '粘贴通用导出数据，或先生成示例报告确认团队需要的输出结构。'}
              />
            </section>
          </section>

          <section className="module-table-panel">
            <div className="module-table-head">
              <PanelHeader icon={<PlayCircleOutlined />} title="能力入口" action={<Tag color="geekblue">{onlineModuleCount}/{modules.length} 在线</Tag>} />
            </div>
            <Table<WorkbenchModule>
              className="module-table"
              rowKey="key"
              size="middle"
              pagination={false}
              columns={moduleColumns}
              dataSource={modules}
              onRow={(record) => ({
                onClick: () => {
                  window.location.href = record.href;
                }
              })}
            />
          </section>
        </Content>

        <Drawer
          title="治理执行结果"
          width={720}
          open={reportOpen}
          onClose={() => setReportOpen(false)}
          extra={
            <Button icon={<CodeOutlined />} onClick={() => navigator.clipboard.writeText(demoResult?.report || '')}>
              复制
            </Button>
          }
        >
          {demoResult ? (
            <div className="report-drawer">
              <Alert
                type={demoResult.success ? 'success' : demoResult.status === 'AUTH_REQUIRED' || demoResult.status?.startsWith('COLLECTOR_') ? 'warning' : 'info'}
                showIcon
                message={demoResult.message}
                description={(demoResult.nextActions || []).join(' / ')}
              />
              {demoResult.report ? <pre>{demoResult.report}</pre> : <List size="small" dataSource={demoResult.nextActions} renderItem={(item) => <List.Item>{item}</List.Item>} />}
            </div>
          ) : (
            <Empty description="暂无报告" />
          )}
        </Drawer>
      </Layout>
    </ConfigProvider>
  );
}

function PanelHeader({ icon, title, action }: { icon: JSX.Element; title: string; action?: JSX.Element }) {
  return (
    <div className="panel-header">
      <Space size={8}>
        <span className="panel-icon">{icon}</span>
        <strong>{title}</strong>
      </Space>
      {action}
    </div>
  );
}

function MetricCell({ icon, label, value, tone }: { icon: JSX.Element; label: string; value: string; tone: string }) {
  return (
    <div className={`metric-cell tone-${tone}`}>
      <span className="metric-cell-icon">{icon}</span>
      <span>
        <Text>{label}</Text>
        <strong>{value}</strong>
      </span>
    </div>
  );
}

function GaugeLine({ label, value }: { label: string; value: number }) {
  return (
    <div className="gauge-line">
      <div>
        <Text>{label}</Text>
        <strong>{Number(value || 0).toFixed(1)}%</strong>
      </div>
      <Progress percent={Math.round(value || 0)} showInfo={false} strokeColor={{ '0%': '#22d3ee', '100%': '#fbbf24' }} />
    </div>
  );
}

function WorkflowCanvas({ activeTool, systemLoad, memoryUsage, sourceReadyCount }: { activeTool: string; systemLoad: number; memoryUsage: number; sourceReadyCount: number }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }

    function draw() {
      const target = canvasRef.current;
      if (!target) {
        return;
      }
      const rect = target.getBoundingClientRect();
      const scale = window.devicePixelRatio || 1;
      target.width = Math.max(1, Math.floor(rect.width * scale));
      target.height = Math.max(1, Math.floor(rect.height * scale));
      const context = target.getContext('2d');
      if (!context) {
        return;
      }
      context.setTransform(scale, 0, 0, scale, 0, 0);
      paintWorkflow(context, rect.width, rect.height, activeTool, systemLoad, memoryUsage, sourceReadyCount);
    }

    const observer = new ResizeObserver(draw);
    observer.observe(canvas);
    draw();
    return () => observer.disconnect();
  }, [activeTool, systemLoad, memoryUsage, sourceReadyCount]);

  return (
    <div className="workflow-canvas-wrap">
      <canvas ref={canvasRef} className="workflow-canvas" aria-label="AI 执行流画布" />
    </div>
  );
}

function paintWorkflow(
  context: CanvasRenderingContext2D,
  width: number,
  height: number,
  activeTool: string,
  systemLoad: number,
  memoryUsage: number,
  sourceReadyCount: number
) {
  context.clearRect(0, 0, width, height);
  const activeCapability = governanceCapabilities.find((item) => item.id === activeTool);
  const nodes = [
    { label: '来源', detail: `${sourceReadyCount}/2`, x: 0.12, y: 0.32, color: '#22d3ee' },
    { label: '分析', detail: activeCapability?.name || '治理', x: 0.36, y: 0.2, color: '#a78bfa' },
    { label: '评测', detail: `${memoryUsage.toFixed(0)}%`, x: 0.62, y: 0.38, color: '#34d399' },
    { label: '输出', detail: `${systemLoad.toFixed(0)}%`, x: 0.84, y: 0.22, color: '#fbbf24' }
  ];

  context.fillStyle = '#07111f';
  context.fillRect(0, 0, width, height);

  for (let i = 0; i < nodes.length - 1; i += 1) {
    const from = nodes[i];
    const to = nodes[i + 1];
    const startX = from.x * width;
    const startY = from.y * height;
    const endX = to.x * width;
    const endY = to.y * height;
    const gradient = context.createLinearGradient(startX, startY, endX, endY);
    gradient.addColorStop(0, `${from.color}dd`);
    gradient.addColorStop(1, `${to.color}88`);
    context.strokeStyle = gradient;
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(startX, startY);
    context.bezierCurveTo(startX + width * 0.12, startY + height * 0.32, endX - width * 0.12, endY - height * 0.28, endX, endY);
    context.stroke();
  }

  nodes.forEach((node, index) => {
    const x = node.x * width;
    const y = node.y * height;
    context.shadowColor = node.color;
    context.shadowBlur = 22;
    context.fillStyle = node.color;
    context.beginPath();
    context.arc(x, y, 10 + index * 1.2, 0, Math.PI * 2);
    context.fill();
    context.shadowBlur = 0;
    context.fillStyle = 'rgba(5, 9, 20, 0.9)';
    roundedRect(context, x - 46, y + 18, 92, 46, 8);
    context.fill();
    context.strokeStyle = `${node.color}88`;
    context.stroke();
    context.fillStyle = '#eaf7ff';
    context.font = '700 13px system-ui';
    context.textAlign = 'center';
    context.fillText(node.label, x, y + 38);
    context.fillStyle = '#9fb5c9';
    context.font = '12px system-ui';
    context.fillText(node.detail, x, y + 56);
  });
}

function roundedRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  context.beginPath();
  context.moveTo(x + radius, y);
  context.lineTo(x + width - radius, y);
  context.quadraticCurveTo(x + width, y, x + width, y + radius);
  context.lineTo(x + width, y + height - radius);
  context.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
  context.lineTo(x + radius, y + height);
  context.quadraticCurveTo(x, y + height, x, y + height - radius);
  context.lineTo(x, y + radius);
  context.quadraticCurveTo(x, y, x + radius, y);
  context.closePath();
}

function evaluatePrompt(value: string) {
  const checks = [
    { text: '目标清晰', done: value.length > 30 },
    { text: '包含约束边界', done: /不得|不要|仅|必须|边界/.test(value) },
    { text: '包含输出格式', done: /JSON|表格|格式|结构化/.test(value) },
    { text: '包含评判或成功标准', done: /标准|评分|质量|命中|验收/.test(value) }
  ];
  const score = Math.max(35, Math.round((checks.filter((item) => item.done).length / checks.length) * 100));
  return {
    score,
    color: score > 80 ? '#34d399' : score > 60 ? '#22d3ee' : '#fbbf24',
    level: score > 80 ? '结构良好' : score > 60 ? '可用但需增强' : '信息不足',
    reason: '正式评分可进入 Prompt 优化器进行模型评测。',
    actions: checks
  };
}

function buildGovernanceReport(toolId: string, version: string, sourceUrl: string, sourceData: string, demo: boolean): GovernanceReport {
  const capability = governanceCapabilities.find((item) => item.id === toolId) || governanceCapabilities[0];
  const source = sourceUrl.trim() || '页面手动输入';
  const dataLines = sourceData.trim().split(/\n+/).filter(Boolean).length;
  const normalizedVersion = version.trim() || '未标版本';
  const report = [
    `【${capability.name}｜${demo ? '示例报告' : '报告草稿'}】`,
    `版本：${normalizedVersion}`,
    `来源：${source}`,
    `数据行数：${demo ? '示例数据' : `${dataLines} 行输入`}`,
    '',
    '一、检查范围',
    `- 数据来源类型：${capability.dataSource}`,
    `- 输出目标：${capability.outputs.join('、')}`,
    '',
    '二、主要发现',
    '- 需要确认需求、任务或发布记录是否覆盖当前版本范围。',
    '- 需要补齐缺失负责人、计划日期、验收口径和风险说明。',
    '- 需要把高风险事项沉淀为可跟踪的行动项。',
    '',
    '三、下一步',
    '- 使用真实导出数据替换示例输入。',
    '- 复核字段映射和统计口径。',
    '- 将确认后的报告发布到团队协作空间。'
  ].join('\n');

  return {
    success: true,
    status: demo ? 'DEMO_READY' : 'DRAFT_READY',
    message: demo ? '已生成通用示例报告。' : '已基于页面输入生成报告草稿。',
    toolId,
    nextActions: ['复核来源和字段映射。', '确认统计口径。', '必要时补充团队特有规则。'],
    report
  };
}

export default App;
