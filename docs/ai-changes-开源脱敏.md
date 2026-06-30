# 开源脱敏变更记录

## 背景

用户准备将当前工程整理为个人 GitHub 开源项目，需要移除不适合公开仓库的内部平台痕迹、本地产物和真实样例信息。

## 设计与测试策略

- 采用测试先行方式，新增开源脱敏守护测试，扫描源码、测试、前端源码、脚本和公开入口文件，阻止内部域名、内部平台名、真实公司样例和高风险示例凭据再次进入开源候选文件。
- 对已经从页面入口卸载的内部采集后端链路，选择移除后端 API、服务模型和测试，而不是继续保留不可公开的采集实现。
- 对 React 工作台保留治理分析体验，但改为通用本地报告草稿，不再调用内部采集接口。
- 对本地产物和历史过程文件，通过 `.gitignore` 阻止进入首个公开仓库。

## 变更内容

- 新增 `OpenSourceSanitizationTest`，作为开源候选文件脱敏守护。
- 移除已卸载治理采集后端相关控制器、服务、请求/响应模型和测试。
- React/Vite 工作台改为通用治理分析草稿生成，不再依赖私有采集 API。
- 将测试中的真实公司主体、内部 skill 名、高风险手机号/凭据样例替换为虚构通用样例。
- 更新数字孪生治理节点元数据，改为通用治理入口。
- 新增根目录 `.gitignore`，排除构建产物、日志、本地虚拟环境、生成媒体、运行缓存、私密配置和历史 AI 过程记录。
- 补齐 TripoSplat 查看器的统一主题与布局资源引用，并同步测试约束。
- 进一步移除历史兼容治理页、导航登记、页面契约和残留样式，避免公开仓库保留已下线内部入口痕迹。
- 将生成型文档评审产物目录加入忽略规则，避免二进制文档和渲染过程文件进入首次公开提交。

## 验证结果

- `mvn -q -Dtest=OpenSourceSanitizationTest test` 通过。
- `npm run build` 通过。
- `mvn -q -Dtest=OpenSourceSanitizationTest,ReactDashboardFrontendTest,SkillMonitorServiceTest,DocumentQualityServiceTest,DocumentSummaryServiceTest,DigitalTwinServiceTest test` 通过。
- `npm run test:static-layout` 通过。
- `npm run test:layout` 通过。
- `mvn -q test` 通过。
- 使用 Vite 本地服务验证 `/react-dashboard/`，桌面视口 `1440x900` 与移动视口 `390x844` 均无横向溢出，页面展示通用治理草稿入口，未出现旧内部 API 文案。
