import { readdirSync, readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';

const root = process.cwd();
const staticDir = resolve(root, 'src/main/resources/static');
const css = readFileSync(resolve(staticDir, 'css/app-layout.css'), 'utf8');
const layoutJs = readFileSync(resolve(staticDir, 'js/app-layout.js'), 'utf8');

const htmlFiles = readdirSync(staticDir)
  .filter((file) => file.endsWith('.html'))
  .sort();

const expectedPages = [
  'chat',
  'cicd-deploy',
  'digital-twin',
  'document-quality',
  'image-to-text',
  'index',
  'login',
  'memory-manager',
  'mineru-demo',
  'modeling-demo',
  'model-evaluation',
  'prompt-demo',
  'prompt-optimizer',
  'rag-demo',
  'service-dashboard',
  'skill-monitor',
  'text-to-image',
  'text-to-video'
];

const pageContracts = {
  'chat.html': ['chat-workbench', 'chat-context-panel', 'chat-history-panel', 'chat-compose-panel'],
  'rag-demo.html': ['rag-workbench', 'rag-query-panel', 'rag-answer-panel', 'rag-trace-panel'],
  'prompt-demo.html': ['prompt-workbench', 'prompt-template-panel', 'prompt-editor-panel', 'prompt-result-panel'],
  'prompt-optimizer.html': ['prompt-optimizer-workbench', 'prompt-input-column', 'evaluation-column', 'prompt-output-column'],
  'memory-manager.html': ['memory-workbench', 'memory-insight-panel', 'memory-filter-panel', 'memory-list'],
  'mineru-demo.html': [
    'mineru-workbench',
    'mineru-upload-panel',
    'mineru-preview-panel',
    'mineru-block-list',
    'mineru-markdown-preview',
    'mineru-json-preview',
    "fetch('/api/mineru/parse'"
  ],
  'modeling-demo.html': [
    'modeling-workbench',
    'modeling-control-panel',
    'modeling-control-scroll',
    'modeling-submit-bar',
    'modeling-viewer-panel',
    'modeling-asset-panel',
    'modeling-service-state',
    'modeling-generation-state',
    'modeling-completion-badge',
    'modeling-compare-toggle',
    "fetch('/api/modeling/capability'",
    'initModelingCapability',
    'setGenerationVisualState',
    'applyRemoteModelingPollingState',
    'tripoSplatViewerBootTimeout',
    'toggleGeneratedComparison',
    'addGeneratedReliefLayers',
    'modeling-contour-layer',
    '轮廓浮雕资产',
    '当前仅展示本地预览',
    '生成完成'
  ],
  'model-evaluation.html': [
    'model-evaluation-workbench',
    'model-evaluation-summary-strip',
    'model-evaluation-chart-panel',
    'model-evaluation-runs-panel',
    'modelEvaluationSceneFilter',
    'renderModelEvaluationTrend'
  ],
  'text-to-image.html': ['media-workbench', 'media-control-panel', 'media-preview-panel'],
  'image-to-text.html': ['media-workbench', 'media-control-panel', 'media-preview-panel'],
  'text-to-video.html': ['media-workbench', 'media-control-panel', 'media-preview-panel'],
  'index.html': [
    'app-capability-map',
    'app-capability-flow',
    'app-capability-node',
    'app-home-ops-brief',
    'app-home-ops-grid',
    'deploy-workbench',
    'logs-panel-shell',
    'knowledge-rag-shell',
    'knowledge-query-panel',
    'knowledge-answer-panel',
    'knowledge-evidence-list',
    'knowledge-source-list',
    'askKnowledgeQuestion()'
  ],
  'service-dashboard.html': ['runtime-dashboard-workbench', 'runtime-metrics-canvas-wrap', 'runtime-log-panel'],
  'cicd-deploy.html': ['deployment-status-page', "window.location.replace('/index.html#deploy-result')", '打开部署工作台'],
  'digital-twin.html': ['twin-workbench', 'twin-status-strip', 'twin-main-grid', 'twin-topology-stage', 'twin-topology-canvas'],
  'document-quality.html': ['dq-shell', 'dq-command-strip', 'dq-main-grid', 'dq-score-panel', 'dq-report-stage', 'dq-report-tabs'],
  'skill-monitor.html': ['skill-monitor-workbench', 'skill-status-strip', 'skill-main-grid', 'skill-inventory', 'skill-detail'],
  'login.html': ['login-workbench', 'login-signal-panel', 'login-form-panel', 'login-container', 'phone-input', 'login-button']
};

const compatibilityRedirectPages = {
  'cicd-deploy': '/index.html#deploy-result'
};
const standaloneViewerFiles = new Set([
  'triposplat-viewer.html'
]);

const selectorContracts = [
  'body[data-app-page="chat"] .chat-workbench',
  'body[data-app-page="rag-demo"] .rag-workbench',
  'body[data-app-page="prompt-demo"] .prompt-workbench',
  'body[data-app-page="prompt-optimizer"] .prompt-optimizer-workbench',
  'body[data-app-page="mineru-demo"] .mineru-workbench',
  'body[data-app-category="media"] .media-workbench',
  'body[data-app-page="memory-manager"] .memory-workbench',
  'body[data-app-page="login"] .login-workbench',
  'body[data-app-page="index"] .app-capability-map',
  'body[data-app-page="skill-monitor"] .skill-monitor-workbench'
];

const failures = [];

function includesAll(source, tokens, scope) {
  for (const token of tokens) {
    if (!source.includes(token)) {
      failures.push(`${scope} 缺少结构标记：${token}`);
    }
  }
}

function cssRuleBody(selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = css.match(new RegExp(`${escapedSelector}\\s*\\{([\\s\\S]*?)\\n\\}`));
  return match ? match[1] : '';
}

for (const page of expectedPages) {
  if (!htmlFiles.includes(`${page}.html`)) {
    failures.push(`缺少静态页面：${page}.html`);
  }
  if (!layoutJs.includes(`page: "${page}"`) && page !== 'index' && !compatibilityRedirectPages[page]) {
    failures.push(`app-layout.js 未登记页面：${page}`);
  }
}

for (const file of htmlFiles) {
  const html = readFileSync(resolve(staticDir, file), 'utf8');
  if (!standaloneViewerFiles.has(file)) {
    includesAll(html, [
      '/css/tech-theme.css',
      '/css/app-layout.css',
      '/js/tech-theme.js',
      '/js/app-layout.js'
    ], file);
  }
  if (html.includes('20260611-noshellscroll') || html.includes('20260611-ops1')) {
    failures.push(`${file} 仍引用旧版 app-layout 静态资源版本号`);
  }
  includesAll(html, pageContracts[file] || [], file);
}

for (const selector of selectorContracts) {
  if (!css.includes(selector)) {
    failures.push(`app-layout.css 缺少页面级布局规则：${selector}`);
  }
}

if (!css.includes('.app-workbench-heading p') || !css.includes('display: none;')) {
  failures.push('app-layout.css 未隐藏工作台 Hero 的解释文案，首屏仍会被无效提示占用');
}

const promptOptimizerHtml = readFileSync(resolve(staticDir, 'prompt-optimizer.html'), 'utf8');
const promptOptimizerMainRule = cssRuleBody('body[data-app-page="prompt-optimizer"] main.app-shell');
const promptOptimizerWorkbenchRule = cssRuleBody('body[data-app-layout="active"][data-app-page="prompt-optimizer"] main.app-workbench');
const promptOptimizerToolbarRule = cssRuleBody('body[data-app-page="prompt-optimizer"] .toolbar');
const promptOptimizerWorkflowRule = cssRuleBody('body[data-app-page="prompt-optimizer"] .prompt-workflow-strip');
const promptOptimizerToolGridRule = cssRuleBody('body[data-app-page="prompt-optimizer"] .tool-grid');

if (promptOptimizerHtml.includes('tool-grid mt-5 prompt-optimizer-workbench')) {
  failures.push('prompt-optimizer.html 工作区不能继续使用 mt-5 叠加垂直间距');
}
if (!promptOptimizerMainRule.includes('--prompt-optimizer-row-gap: 8px;')
    || !promptOptimizerMainRule.includes('padding-top: 8px !important;')
    || !promptOptimizerWorkbenchRule.includes('gap: var(--prompt-optimizer-row-gap) !important;')) {
  failures.push('Prompt 优化器顶部区域必须使用单一 8px 行距变量，避免标题、流程条、工作区间距叠加');
}
if (!promptOptimizerToolbarRule.includes('margin: 0 !important;')
    || !promptOptimizerWorkflowRule.includes('margin: 0 !important;')
    || !promptOptimizerToolGridRule.includes('margin-top: 0 !important;')) {
  failures.push('Prompt 优化器标题、流程条、工作区不能再通过多个 margin 叠加出大空白');
}

for (const mediaFile of ['text-to-image.html', 'image-to-text.html']) {
  const html = readFileSync(resolve(staticDir, mediaFile), 'utf8');
  if (html.includes('max-w-4xl')) {
    failures.push(`${mediaFile} 仍使用 max-w-4xl 窄容器，媒体工作台会被压缩`);
  }
}

for (const file of htmlFiles) {
  const html = readFileSync(resolve(staticDir, file), 'utf8');
  if (html.includes('text-6xl')) {
    failures.push(`${file} 仍使用 text-6xl 巨型装饰图标`);
  }
  if (html.includes('rounded-lg shadow-lg p-8')) {
    failures.push(`${file} 仍使用旧式大 padding 面板 rounded-lg shadow-lg p-8`);
  }
}

const indexHtml = readFileSync(resolve(staticDir, 'index.html'), 'utf8');
if (indexHtml.includes('app-feature-card card-hover')) {
  failures.push('index.html 仍使用业务卡片墙 app-feature-card card-hover');
}
if (indexHtml.includes('app-feature-groups') || indexHtml.includes('app-feature-group') || indexHtml.includes('app-feature-table')) {
  failures.push('index.html 仍使用分组卡片/功能表格外观，首页入口必须改为可扫描命令矩阵');
}
if (indexHtml.includes('app-command-center') || indexHtml.includes('app-command-rail') || indexHtml.includes('app-command-inspector')) {
  failures.push('index.html 首页禁止继续使用伪工作台/卡片面板结构，应使用能力地图导航');
}
if (!indexHtml.includes('<a class="app-capability-node"') && !indexHtml.includes('<a class="app-capability-node ')) {
  failures.push('index.html 首页入口必须使用能力地图节点链接');
}
if (!indexHtml.includes('app-capability-flow') || !indexHtml.includes('app-home-ops-brief')) {
  failures.push('index.html 首页必须使用能力流转图 + 运行知识动态，不允许卡片墙或重复索引');
}
if (indexHtml.includes('app-capability-index')) {
  failures.push('index.html 首页不能再保留重复的能力快捷索引');
}
if (css.includes('grid-template-rows: 34px minmax(0, 1fr)')) {
  failures.push('app-layout.css 首页命令矩阵禁止使用 1fr 拉伸单行，会导致首行被撑成大空白');
}
if (!css.includes('.app-capability-flow') || !css.includes('.app-home-ops-brief')) {
  failures.push('app-layout.css 首页必须包含能力地图和运行知识动态布局样式');
}
if (css.includes('.app-capability-index') || css.includes('height: min(720px, var(--app-compact-panel));')
    || css.includes('grid-template-rows: auto minmax(0, 1fr) auto auto auto')) {
  failures.push('app-layout.css 首页不能使用重复索引或固定高度压缩能力地图');
}
for (const token of [
  'data-app-link="chat"',
  'data-app-link="memory"',
  'data-app-link="model-evaluation"',
  'data-app-link="prompt-demo"',
  'data-app-link="prompt-optimizer"',
  'data-app-link="rag"',
  'data-app-link="mineru"',
  'data-app-link="text-to-image"',
  'data-app-link="image-to-text"',
  'data-app-link="text-to-video"',
  'data-app-link="document-quality"',
  'data-app-link="digital-twin"',
  'data-app-link="skill-monitor"',
  'data-app-link="deploy"',
  'data-app-link="logs"',
  'data-app-link="service-dashboard"'
]) {
  if (!indexHtml.includes(token)) {
    failures.push(`index.html 首页入口缺少统一链接钩子：${token}`);
  }
}

const documentQualityHtml = readFileSync(resolve(staticDir, 'document-quality.html'), 'utf8');
if (documentQualityHtml.includes('dq-page-heading')) {
  failures.push('document-quality.html 仍保留占首屏空间的 dq-page-heading');
}
if (documentQualityHtml.includes('等待评测输入。报告会展示') || documentQualityHtml.includes('40 / 50 / 10 加权')) {
  failures.push('document-quality.html 仍存在大段解释或无效加权提示文案');
}
for (const token of [
  "switchQualityReportTab('issues')",
  "switchQualityReportTab('scores')",
  "switchQualityReportTab('evidence')",
  "switchQualityReportTab('fields')",
  "openQualityDetailModal('fields')"
]) {
  if (!documentQualityHtml.includes(token)) {
    failures.push(`document-quality.html 缺少有效报告入口：${token}`);
  }
}

if (indexHtml.includes('知识库管理') || indexHtml.includes('知识库列表') || indexHtml.includes('点击上方按钮新建知识库')) {
  failures.push('index.html 知识库页仍是 CRUD 管理页文案，必须改为检索、证据、答案的 RAG 工作流');
}
if (indexHtml.includes('id="knowledge-list"')) {
  failures.push('index.html 知识库页仍保留 knowledge-list 旧列表容器，应改为证据命中和知识覆盖面板');
}
for (const token of ['.knowledge-rag-shell', '.knowledge-main-grid', '.knowledge-evidence-list', '.knowledge-source-list']) {
  if (!css.includes(token)) {
    failures.push(`app-layout.css 缺少知识库 RAG 布局样式：${token}`);
  }
}

if (failures.length > 0) {
  console.error('静态页面布局契约检查失败：');
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log(`静态页面布局契约检查通过：${htmlFiles.length} 个页面。`);
