import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();
const app = readFileSync(resolve(root, 'frontend/src/App.tsx'), 'utf8');
const css = readFileSync(resolve(root, 'frontend/src/App.css'), 'utf8');

const checks = [
  {
    name: '移除旧的大屏 Hero 结构',
    pass: !app.includes('hero-panel') && !css.includes('.hero-panel') && !css.includes('.hero-copy')
  },
  {
    name: '移除旧的雷达装饰结构',
    pass: !app.includes('radar-orbit') && !css.includes('.radar-orbit')
  },
  {
    name: '首页必须包含可视化画布',
    pass: app.includes('WorkflowCanvas') && app.includes('<canvas') && css.includes('.workflow-canvas')
  },
  {
    name: '首页必须包含任务工作区而不是纯说明区',
    pass: app.includes('workspace-grid') && app.includes('source-panel') && app.includes('operations-panel')
  },
  {
    name: '首页必须包含表格或列表化能力导航',
    pass: app.includes('Table<WorkbenchModule>') && app.includes('module-table')
  },
  {
    name: '样式必须声明桌面视口工作区高度，避免无限向下堆内容',
    pass: css.includes('calc(100vh - 68px)') && css.includes('min-height: 0')
  },
  {
    name: '避免旧 Hero 的视口缩放大标题',
    pass: !css.includes('clamp(34px, 4.8vw, 64px)')
  },
  {
    name: 'React 入口保持深色工作台主题',
    pass: app.includes('theme.darkAlgorithm')
      && app.includes("colorBgBase: '#050914'")
      && app.includes("colorTextBase: '#eaf7ff'")
      && !app.includes('theme.defaultAlgorithm')
      && !css.includes('/* React 白色工作台主题 */')
  }
];

const failed = checks.filter((check) => !check.pass);

if (failed.length > 0) {
  console.error('前端布局契约检查失败：');
  for (const check of failed) {
    console.error(`- ${check.name}`);
  }
  process.exit(1);
}

console.log('前端布局契约检查通过。');
