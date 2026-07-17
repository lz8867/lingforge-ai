package com.example.demo.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaGenerationPageTest {

    @Test
    void imageToTextPageShouldRenderModelFailureInResultPanel() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/image-to-text.html"));

        assertTrue(page.contains("renderDescriptionResult(data)"));
        assertTrue(page.contains("data.fallback === true"));
        assertTrue(page.contains("本地基础摘要"));
        assertTrue(page.contains("actionSuggestion"));
        assertTrue(page.contains("copy-description-button"));
        assertTrue(page.contains("renderModelAvailabilitySection"));
        assertTrue(page.contains("图片理解模型可用性"));
        assertFalse(page.contains("alert(`生成失败：${data.message || '未知错误'}`)"));
    }

    @Test
    void imageToTextResultShouldAutoScrollAndAvoidClippingGeneratedDescription() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/image-to-text.html"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("scrollDescriptionResultIntoView()"));
        assertTrue(page.contains("analysisViewport.scrollIntoView"));
        assertTrue(page.contains("document.querySelector('.media-preview-panel')"));
        assertTrue(page.contains("id=\"description-result\""));
        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] #description-content"));
        assertTrue(css.contains("max-height: none"));
        assertTrue(css.contains("overflow: visible"));
        int clippedRule = css.indexOf("body[data-app-page=\"image-to-text\"] #description-content {\n    max-height: min(520px, calc(100vh - var(--app-header-height) - 260px));");
        int expandedRule = css.indexOf("body[data-app-page=\"image-to-text\"] #description-content {\n    max-height: none;\n    overflow: visible;");
        assertTrue(expandedRule > clippedRule);
    }

    @Test
    void imageToTextResultShouldKeepPreviewVisibleBesideGeneratedText() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] .media-preview-panel:has(#description-result:not(.hidden)):has(#image-preview:not(.hidden))"));
        assertTrue(css.contains("grid-template-columns: minmax(220px, 320px) minmax(0, 1fr)"));
        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] .media-preview-panel:has(#description-result:not(.hidden)) #image-preview"));
        assertTrue(css.contains("position: sticky"));
        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] .media-preview-panel:has(#description-result:not(.hidden)) #preview-image"));
    }

    @Test
    void imageToTextSuccessShouldFormatQualityDescriptionAndKeepRawCopyText() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/image-to-text.html"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("formatDescriptionText(data.description || '')"));
        assertTrue(page.contains("descriptionContent.dataset.copyText = data.description || '';"));
        assertTrue(page.contains("description-content--quality"));
        assertTrue(page.contains("description-section-title"));
        assertTrue(page.contains("description-copy-block"));
        assertTrue(page.contains("descriptionContent.dataset.copyText || descriptionContent.textContent"));
        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] .description-content--quality"));
        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] .description-section-title"));
        assertTrue(css.contains("body[data-app-page=\"image-to-text\"] .description-copy-block"));
    }

    @Test
    void textToImagePageShouldRenderModelFailureInResultPanel() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/text-to-image.html"));

        assertTrue(page.contains("renderImageGenerationFailure(data)"));
        assertTrue(page.contains("当前图片生成通道未就绪"));
        assertTrue(page.contains("toggleImageFailureDetail"));
        assertTrue(page.contains("actionSuggestion"));
        assertTrue(page.contains("imageModelsTried"));
        assertTrue(page.contains("renderModelAvailabilitySection"));
        assertTrue(page.contains("图片模型可用性"));
        assertFalse(page.contains("alert(`生成失败：${data.message || '未知错误'}`)"));
    }

    void textToImageValidationShouldUseFriendlyPanel() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/text-to-image.html"));

        assertTrue(page.contains("请先输入图片描述"));
        assertTrue(page.contains("未收到可用图片数据"));
    }

    @Test
    void textToVideoPageShouldUseCustomerReadableFailurePanelInsteadOfAlertingRawErrors() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/text-to-video.html"));

        assertTrue(page.contains("renderVideoGenerationFailure"));
        assertTrue(page.contains("当前视频生成通道未就绪"));
        assertTrue(page.contains("toggleVideoFailureDetail"));
        assertTrue(page.contains("actionSuggestion"));
        assertTrue(page.contains("renderModelAvailabilitySection"));
        assertTrue(page.contains("视频模型可用性"));
        assertTrue(page.contains("video-error-detail"));
        assertTrue(page.contains("clearVideoPreview()"));
        assertTrue(page.contains("videoPreview.removeAttribute('src')"));
        assertFalse(page.contains("alert(`生成失败：${errorMessage}`)"));
        assertFalse(page.contains("alert(`生成失败：${error.message}`)"));
        assertFalse(page.contains("alert(`查询失败：${error.message}`)"));
    }

    @Test
    void textToVideoPageShouldGateGenerationBehindCapabilityState() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/text-to-video.html"));

        assertTrue(page.contains("id=\"video-capability-state\""));
        assertTrue(page.contains("refreshVideoCapability"));
        assertTrue(page.contains("/api/image/generate/video/capability"));
        assertTrue(page.contains("data-video-action=\"generate\" disabled"));
        assertTrue(page.contains("当前没有可用视频生成通道"));
        assertTrue(page.contains("id=\"video-empty-state\""));
        assertTrue(page.contains("videoCapability.canGenerate !== true"));
        assertTrue(page.contains("renderVideoCapabilityGate"));
        assertTrue(page.contains("Wan2.1-T2V-1.3B"));
        assertTrue(page.contains("832x480"));
        assertFalse(page.contains("视频生成准备就绪"));
        assertFalse(page.contains("默认先检查本地免费视频通道，不可用时回退云端视频模型。"));
    }

    @Test
    void wanVideoBridgeShouldWrapOfficialWanGenerateCommand() throws Exception {
        String script = Files.readString(Path.of("scripts/wan_video_server.py"));

        assertTrue(script.contains("ThreadingHTTPServer"));
        assertTrue(script.contains("/api/text-to-video"));
        assertTrue(script.contains("/api/text-to-video/status/"));
        assertTrue(script.contains("--save_file"));
        assertTrue(script.contains("t2v-1.3B"));
        assertTrue(script.contains("832*480"));
        assertFalse(script.contains("WAN_DRY_RUN"));
    }

    @Test
    void imageToTextPageShouldRenderFailurePanelAndToggleDetails() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/image-to-text.html"));

        assertTrue(page.contains("renderDescriptionFailure"));
        assertTrue(page.contains("当前图生文通道未就绪"));
        assertTrue(page.contains("toggleDescriptionFailureDetail"));
        assertTrue(page.contains("查看技术详情"));
        assertFalse(page.contains("alert('请上传图片文件')"));
        assertFalse(page.contains("alert('图片大小不能超过 10MB')"));
        assertFalse(page.contains("alert('请先上传图片')"));
    }

    @Test
    void ragDemoPageShouldKeepWorkbenchHeroAndInputsCompact() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/rag-demo.html"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));

        assertTrue(page.contains("知识库入库"));
        assertTrue(page.contains("检索调试"));
        assertTrue(page.contains("引用证据"));
        assertTrue(page.contains("向量库状态"));
        assertTrue(css.contains("body[data-app-page=\"rag-demo\"] .app-workbench-hero"));
        assertTrue(css.contains("body[data-app-page=\"rag-demo\"] main > section:not(.app-workbench-hero)"));
        assertTrue(css.contains("body[data-app-page=\"rag-demo\"] #vector-query"));
        assertTrue(css.contains("body[data-app-page=\"rag-demo\"] #rag-question"));
    }

    @Test
    void modelingDemoPageShouldProvideImageToThreeDWorkbench() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/modeling-demo.html"));
        String home = Files.readString(Path.of("src/main/resources/static/index.html"));
        String renderer = Files.readString(Path.of("src/main/resources/static/js/app-layout.js"));
        String css = Files.readString(Path.of("src/main/resources/static/css/app-layout.css"));
        String officialViewer = Files.readString(Path.of("src/main/resources/static/triposplat-viewer.html"));

        assertTrue(page.contains("<title>图像建模 Demo - Spring AI Demo</title>"));
        assertTrue(page.contains("data-app-page=\"modeling-demo\""));
        assertTrue(page.contains("id=\"model-source-image\""));
        assertTrue(page.contains("id=\"model-prompt\""));
        assertTrue(page.contains("id=\"modeling-canvas\""));
        assertTrue(page.contains("id=\"modeling-progress\""));
        assertTrue(page.contains("startModelingDemo()"));
        assertTrue(page.contains("selectModelingSample("));
        assertTrue(page.contains("downloadModelingGlb()"));
        assertTrue(page.contains("downloadModelingSplat()"));
        assertTrue(page.contains("downloadModelingManifest()"));
        assertTrue(page.contains("submitModelingJob"));
        assertTrue(page.contains("pollModelingJob"));
        assertTrue(page.contains("renderRemoteModelingJob"));
        assertTrue(page.contains("renderRemoteTripoSplatPly"));
        assertTrue(page.contains("applyRemoteModelingPollingState"));
        assertTrue(page.contains("formatRemotePollingStatusDetail"));
        assertTrue(page.contains("modelingRemoteStatusProgress"));
        assertTrue(page.contains("REMOTE_MODELING_MAX_POLLS"));
        assertTrue(page.contains("remoteModelingStepCount"));
        assertTrue(page.contains("isLocalTripoSplatService"));
        assertTrue(page.contains("quality === 'quality' ? 20 : quality === 'balanced' ? 10 : 4"));
        assertTrue(page.contains("本机 CPU 会使用可用画质采样"));
        assertFalse(page.contains("quality === 'quality' ? 4 : quality === 'balanced' ? 2 : 1"));
        assertFalse(page.contains("throw new Error('后端建模任务轮询超时。');"));
        assertTrue(page.contains("parsePlyGaussianPointCloud"));
        assertTrue(page.contains("binary_little_endian"));
        assertTrue(page.contains("new THREE.Points"));
        assertTrue(page.contains("真实点云预览"));
        assertTrue(page.contains("id=\"modeling-splat-viewer\""));
        assertTrue(page.contains("showOfficialTripoSplatViewer"));
        assertTrue(page.contains("frame.classList.add('is-official-viewer')"));
        assertTrue(page.contains("classList.remove('is-official-viewer')"));
        assertTrue(page.contains(".modeling-viewer-frame.is-official-viewer #modeling-canvas"));
        assertTrue(page.contains("const officialViewerOpened = showOfficialTripoSplatViewer(findRemoteOutput('splat', 'splat.splat') || findRemoteOutput('ply', 'splat.ply'));"));
        assertFalse(page.contains("const remoteRendered = await renderRemoteTripoSplatPly(job);\n        let officialViewerOpened = false;"));
        assertTrue(page.contains("tripoSplatViewerBootTimeout"));
        assertTrue(page.contains("triposplat-viewer.html?splat="));
        assertTrue(page.contains("handleTripoSplatViewerMessage"));
        assertTrue(page.contains("window.addEventListener('message', handleTripoSplatViewerMessage)"));
        assertTrue(officialViewer.contains("SparkRenderer"));
        assertTrue(officialViewer.contains("SplatMesh"));
        assertTrue(officialViewer.contains("const assetURL = params.get(\"splat\") || params.get(\"ply\")"));
        assertTrue(officialViewer.contains("camera.position.set(0, 0.12, 1.55)"));
        assertTrue(officialViewer.contains("frameSplatScene"));
        assertTrue(officialViewer.contains("splatRoot.position.sub(center)"));
        assertTrue(officialViewer.contains("2.05 / maxAxis"));
        assertTrue(officialViewer.contains("maxAxis * fitScale * 0.98"));
        assertTrue(officialViewer.contains("splat.rotation.y = Math.PI / 2"));
        assertTrue(officialViewer.contains("splatRoot.rotation.x = Math.PI"));
        assertTrue(officialViewer.contains("renderer.domElement.style.filter"));
        assertTrue(officialViewer.contains("brightness(1.68)"));
        assertFalse(officialViewer.contains("/js/app-layout.js"));
        assertFalse(officialViewer.contains("/css/app-layout.css"));
        assertTrue(officialViewer.contains("/js/tech-theme.js"));
        assertFalse(officialViewer.contains("cdn.jsdelivr.net/npm/flowbite"));
        assertTrue(officialViewer.contains("normalizeErrorMessage"));
        assertTrue(officialViewer.contains("triposplat-viewer-error"));
        assertTrue(officialViewer.contains("window.addEventListener('error'"));
        assertTrue(officialViewer.contains("window.addEventListener('unhandledrejection'"));
        assertTrue(page.contains("/api/modeling/jobs"));
        assertTrue(page.contains("TripoSplat 服务未就绪"));
        assertTrue(page.contains("本地联调资产"));
        assertTrue(page.contains("buildGeneratedGlb"));
        assertTrue(page.contains("extractUploadedImageSilhouette"));
        assertTrue(page.contains("createImageDrivenShape"));
        assertTrue(page.contains("currentImageShapePoints"));
        assertTrue(page.contains("radius: Math.hypot(point.x - center.x, point.y - center.y)\n            }))\n            .sort"));
        assertTrue(page.contains("createPreviewMaterial"));
        assertTrue(page.contains("addPreviewOutline"));
        assertTrue(page.contains("fitModelPreviewBounds"));
        assertTrue(page.contains("side: THREE.DoubleSide"));
        assertTrue(page.contains("texture.anisotropy"));
        assertTrue(page.contains("预览轮廓增强"));
        assertTrue(page.contains("preserveDrawingBuffer: true"));
        assertTrue(page.contains("buildCharacterReliefPreview"));
        assertTrue(page.contains("createCharacterSubjectTexture"));
        assertTrue(page.contains("createCharacterContextTexture"));
        assertTrue(page.contains("人物纹理浮雕预览"));
        assertTrue(page.contains("alphaTest: completed ? 0.025 : 0.045"));
        assertFalse(page.contains("inactiveAlpha = completed ? 0.03 : 0.14"));
        assertTrue(page.contains("refineForegroundMaskForComplexBackground"));
        assertTrue(page.contains("coverage > 0.86"));
        assertTrue(page.contains("复杂背景前景已收敛"));
        assertTrue(page.contains("localContrastAt"));
        assertTrue(page.contains("currentImageMaskBounds"));
        assertTrue(page.contains("isolateCenteredForegroundComponent"));
        assertTrue(page.contains("findMaskBounds"));
        assertTrue(page.contains("drawImage(preview, crop.x, crop.y, crop.width, crop.height"));
        assertTrue(page.contains("buildCharacterSubjectAlpha"));
        assertTrue(page.contains("const subjectSuppression"));
        assertFalse(page.contains("inactiveAlpha = completed ? 0 : 0.14"));
        assertTrue(page.contains("中心连通主体"));
        assertTrue(page.contains("上传图轮廓"));
        assertTrue(page.contains("modelingGeneratedAssets"));
        assertTrue(page.contains("提交建模任务"));
        assertTrue(page.contains("重新生成资产"));
        assertTrue(page.contains("modeling-generation-state"));
        assertTrue(page.contains("modeling-completion-badge"));
        assertTrue(page.contains("modeling-result-summary"));
        assertTrue(page.contains("modeling-compare-toggle"));
        assertTrue(page.contains("setModelingResultSummary"));
        assertTrue(page.contains("本地预览资产已就绪"));
        assertTrue(page.contains("真实推理未配置"));
        assertTrue(page.contains("下载 GLB / SPLAT / Manifest"));
        assertTrue(page.contains("modeling-service-state[data-state=\"local-complete\"]"));
        assertFalse(page.contains("Local Fallback"));
        assertFalse(page.contains("兜底"));
        assertFalse(page.contains("回落"));
        assertTrue(page.contains("setGenerationVisualState"));
        assertTrue(page.contains("toggleGeneratedComparison"));
        assertTrue(page.contains("focusGeneratedAssetView"));
        assertTrue(page.contains("生成资产正面已锁定"));
        assertTrue(page.contains("autoRotate = false"));
        assertTrue(page.contains("modelGroup.rotation.set(0, 0, 0)"));
        assertFalse(page.contains("addGeneratedReliefLayers"));
        assertFalse(page.contains("modeling-contour-layer"));
        assertTrue(page.contains("轮廓浮雕资产"));
        assertFalse(page.contains("addGeneratedDepthShell"));
        assertFalse(page.contains("new THREE.BoxGeometry(railThickness"));
        assertTrue(page.contains("three.module.min.js"));
        assertTrue(page.contains("OrbitControls"));
        assertTrue(page.contains("ExtrudeGeometry"));
        assertTrue(page.contains("modelingPipelineStages"));
        assertTrue(page.contains("图像建模流水线"));
        assertTrue(page.contains("GLB 资产包"));
        assertFalse(page.contains("GLB 资产包 · 演示占位"));
        assertFalse(page.contains("不冒充已生成可生产使用的 GLB"));
        assertFalse(page.contains("占位"));

        assertTrue(home.contains("href=\"/modeling-demo.html\""));
        assertTrue(home.contains("data-app-link=\"modeling-demo\""));
        assertTrue(renderer.contains("{ id: \"modeling-demo\", label: \"图像建模\", href: \"/modeling-demo.html\", page: \"modeling-demo\" }"));
        assertTrue(renderer.contains("\"modeling-demo\": \"fa-cubes\""));
        assertTrue(renderer.contains("\"modeling-demo\": \"上传参考图并演示图像到 3D 资产的本地建模流程。\""));

        assertTrue(css.contains("body[data-app-page=\"modeling-demo\"] .modeling-demo-shell"));
        assertTrue(css.contains("body[data-app-page=\"modeling-demo\"] .modeling-stage-grid"));
        assertTrue(css.contains("body[data-app-page=\"modeling-demo\"] .modeling-viewer-frame"));
        assertTrue(css.contains("body[data-app-page=\"modeling-demo\"] #modeling-canvas"));
        assertTrue(css.contains("@media (max-width: 768px)"));
    }

    @Test
    void modelingDemoCharacterReliefShouldPreserveComplexReferenceContext() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/modeling-demo.html"));

        assertTrue(page.contains("function shouldPreserveCharacterReferenceContext()"));
        assertTrue(page.contains("function characterReferenceCrop(imageWidth, imageHeight, maskSize)"));
        assertTrue(page.contains("function buildCharacterSubjectAlpha(mask, size)"));
        assertTrue(page.contains("function createCharacterContextTexture(crop, textureResult, completed)"));
        assertTrue(page.contains("const contextPreserved = shouldPreserveCharacterReferenceContext();"));
        assertTrue(page.contains("const subjectSuppression"));
        assertTrue(page.contains("const vignette"));
        assertTrue(page.contains("alphaTest: completed ? 0.025 : 0.045"));
        assertTrue(page.contains("复杂人物图已启用上下文保真纹理"));
        assertTrue(page.contains("referenceContextPreserved: shouldPreserveCharacterReferenceContext()"));
    }

    @Test
    void modelingDemoCharacterReliefShouldRenderAsCleanLayeredAsset() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/modeling-demo.html"));

        assertTrue(page.contains("function createCharacterSubjectTexture(completed)"));
        assertTrue(page.contains("function createCharacterSubjectMaskCanvas(mask, size, crop, width, height)"));
        assertTrue(page.contains("function createCharacterDepthTexture(subjectCanvas, completed)"));
        assertTrue(page.contains("function addCharacterDepthLayers(targetGroup, textureResult, width, height, completed)"));
        assertTrue(page.contains("function addCharacterContactShadow(targetGroup)"));
        assertTrue(page.contains("addCharacterDepthLayers(relief, textureResult, width, height, completed);"));
        assertTrue(page.contains("addCharacterContactShadow(relief);"));
        assertTrue(page.contains("relief.rotation.y = completed ? -0.065 : -0.025;"));
        assertTrue(page.contains("fitModelPreviewBounds(3.12);"));
        assertTrue(page.contains("const size = 192;"));
        assertTrue(page.contains("const maxHorizontalGap = Math.max(10, Math.round(size * 0.1));"));
        assertTrue(page.contains("const featherRadius = Math.max(2, Math.round(size / 48));"));
        assertTrue(page.contains("context.imageSmoothingQuality = 'high';"));
        assertTrue(page.contains("context.filter = 'blur(1.2px)';"));
        assertFalse(page.contains("const plateGeometry = new THREE.ExtrudeGeometry(createImageDrivenShape(),"));
        assertFalse(page.contains("function addCleanCharacterReliefEdge"));
        assertFalse(page.contains("人物浅浮雕体"));
        assertFalse(page.contains("addGeneratedReliefLayers"));
        assertFalse(page.contains("modeling-contour-layer"));
        assertFalse(page.contains("const count = completed ? 680 : 72;"));
        assertFalse(page.contains("context.strokeRect(3, 3, canvas.width - 6, canvas.height - 6);"));
    }

    @Test
    void modelingDemoCharacterReliefShouldUseDisplacedSurfaceAndExportEmbeddedTexture() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/modeling-demo.html"));

        assertTrue(page.contains("<link rel=\"icon\" href=\"data:,\">"));
        assertTrue(page.contains("function getCharacterReliefProfile(completed)"));
        assertTrue(page.contains("function createCharacterReliefGeometry(width, height, textureResult, completed)"));
        assertTrue(page.contains("function applyCharacterSurfaceDepth(geometry, subjectCanvas, profile)"));
        assertTrue(page.contains("function smoothCharacterReliefDepth(geometry, segmentsX, segmentsY, passes)"));
        assertTrue(page.contains("new THREE.PlaneGeometry(width, height, profile.segmentsX, profile.segmentsY)"));
        assertTrue(page.contains("smoothCharacterReliefDepth(geometry, profile.segmentsX, profile.segmentsY, profile.smoothingPasses);"));
        assertTrue(page.contains("geometry.computeVertexNormals();"));
        assertTrue(page.contains("const frontMaterial = new THREE.MeshStandardMaterial({"));
        assertTrue(page.contains("emissive: 0xffffff,"));
        assertTrue(page.contains("emissiveIntensity: completed ? 0.28 : 0.12"));
        assertTrue(page.contains("metalness: 0,"));
        assertTrue(page.contains("new THREE.AmbientLight(0xffffff, 0.86)"));
        assertTrue(page.contains("new THREE.DirectionalLight(0xffffff, 1.35)"));
        assertTrue(page.contains("new THREE.PointLight(0x22d3ee, 1.1, 12)"));
        assertTrue(page.contains("id=\"modeling-mode-badge\">Balanced</span>"));
        assertTrue(page.contains("<option value=\"balanced\" selected>平衡预览 · 10步</option>"));
        assertFalse(page.contains("<option value=\"speed\" selected>"));
        assertTrue(page.contains("front.userData.exportTextureCanvas = textureResult.canvas;"));
        assertTrue(page.contains("front.userData.isCharacterReliefSurface = true;"));
        assertTrue(page.contains("function canvasToPngBytes(canvas)"));
        assertTrue(page.contains("attributes.TEXCOORD_0 = uvAccessor;"));
        assertTrue(page.contains("baseColorTexture: { index: baseColorTextureIndex }"));
        assertTrue(page.contains("mimeType: 'image/png'"));
        assertTrue(page.contains("function appendCharacterReliefSplatRows(rows)"));
        assertTrue(page.contains("结构化点采样 · 本地生成"));
        assertTrue(page.contains("reliefSurface: currentKind === 'character'"));
        assertFalse(page.contains("const frontGeometry = new THREE.PlaneGeometry(width, height, 1, 1);"));
    }

    @Test
    void modelingDemoShouldUseFocusedProfessionalWorkspaceHierarchy() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/modeling-demo.html"));

        assertTrue(page.contains("--modeling-surface:"));
        assertTrue(page.contains("class=\"modeling-control-section modeling-control-section--source\""));
        assertTrue(page.contains("class=\"modeling-control-section modeling-control-section--intent\""));
        assertTrue(page.contains("class=\"modeling-control-section modeling-control-section--quality\""));
        assertTrue(page.contains("class=\"modeling-stage-index\""));
        assertTrue(page.contains("class=\"modeling-viewer-toolbar\""));
        assertTrue(page.contains("class=\"modeling-inspector-header\""));
        assertTrue(page.contains("class=\"modeling-log-drawer\""));
        assertTrue(page.contains("<summary><span><i class=\"fa fa-terminal\"></i> 任务日志</span>"));
        assertTrue(page.contains("@media (prefers-reduced-motion: reduce)"));
        assertTrue(page.contains(":focus-visible"));
        assertTrue(page.contains("@media (max-width: 1180px)"));
        assertFalse(page.contains("transform: translateY(-1px)"));
    }

    @Test
    void modelingDemoDesktopLayoutShouldAvoidVerticalCompression() throws Exception {
        String page = Files.readString(Path.of("src/main/resources/static/modeling-demo.html"));

        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] main.modeling-demo-shell.app-workbench"));
        assertTrue(page.contains("@media (min-width: 1181px) and (max-width: 1439px)"));
        assertTrue(page.contains("grid-template-columns: minmax(300px, 340px) minmax(0, 1fr)"));
        assertTrue(page.contains("grid-template-rows: minmax(0, 1fr) 148px"));
        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] .modeling-asset-panel"));
        assertTrue(page.contains("grid-column: 1 / -1"));
        assertTrue(page.contains("@media (min-width: 1440px)"));
        assertTrue(page.contains("grid-template-rows: auto 34px minmax(0, 1fr)"));
        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] .modeling-stage-grid.app-content-shell"));
        assertTrue(page.contains("height: 34px !important"));
        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] .modeling-workbench.app-content-shell"));
        assertTrue(page.contains("grid-template-columns: minmax(288px, 356px) minmax(540px, 1fr) minmax(286px, 342px)"));
        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] .modeling-control-panel"));
        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] .modeling-asset-panel"));
        assertTrue(page.contains("scrollbar-gutter: stable"));
        assertTrue(page.contains("body[data-app-layout=\"active\"][data-app-page=\"modeling-demo\"] .modeling-viewer-frame"));
        assertTrue(page.contains("min-height: 0 !important"));
    }
}
