(function () {
    const categories = [
        {
            id: "ai",
            label: "AI 交互",
            icon: "fa-comments",
            pages: [
                { id: "home", label: "首页", href: "/index.html#home", page: "index", hash: "home" },
                { id: "chat", label: "对话", href: "/chat.html", page: "chat" },
                { id: "memory", label: "记忆", href: "/memory-manager.html", page: "memory-manager" }
            ]
        },
        {
            id: "prompt",
            label: "Prompt 与知识",
            icon: "fa-sliders",
            pages: [
                { id: "prompt-demo", label: "模板", href: "/prompt-demo.html", page: "prompt-demo" },
                { id: "prompt-optimizer", label: "优化器", href: "/prompt-optimizer.html", page: "prompt-optimizer" },
                { id: "rag", label: "RAG", href: "/rag-demo.html", page: "rag-demo" },
                { id: "mineru", label: "文档解析", href: "/mineru-demo.html", page: "mineru-demo" },
                { id: "document-summary", label: "文档摘要", href: "/document-summary.html", page: "document-summary" },
                { id: "knowledge", label: "知识库", href: "/index.html#knowledge", page: "index", hash: "knowledge" }
            ]
        },
        {
            id: "media",
            label: "内容生成",
            icon: "fa-magic",
            pages: [
                { id: "text-to-image", label: "文生图", href: "/text-to-image.html", page: "text-to-image" },
                { id: "image-to-text", label: "图生文", href: "/image-to-text.html", page: "image-to-text" },
                { id: "text-to-video", label: "文生视频", href: "/text-to-video.html", page: "text-to-video" },
                { id: "modeling-demo", label: "图像建模", href: "/modeling-demo.html", page: "modeling-demo" }
            ]
        },
        {
            id: "governance",
            label: "治理工具",
            icon: "fa-sitemap",
            pages: [
                { id: "model-evaluation", label: "模型测评", href: "/model-evaluation.html", page: "model-evaluation" },
                { id: "document-quality", label: "文档质量", href: "/document-quality.html", page: "document-quality" }
            ]
        },
        {
            id: "ops",
            label: "运维管理",
            icon: "fa-terminal",
            pages: [
                { id: "digital-twin", label: "能力治理", href: "/digital-twin.html", page: "digital-twin" },
                { id: "skill-monitor", label: "Skill 监控", href: "/skill-monitor.html", page: "skill-monitor" },
                { id: "deploy", label: "部署", href: "/index.html#deploy", page: "index", hash: "deploy" },
                { id: "logs", label: "日志", href: "/index.html#logs", page: "index", hash: "logs" },
                { id: "service-dashboard", label: "运行大屏", href: "/service-dashboard.html", page: "service-dashboard" }
            ]
        }
    ];

    const accountActions = [
        {
            id: "login",
            label: "登录",
            href: "/login.html",
            page: "login",
            categoryId: "account",
            categoryLabel: "账号",
            categoryIcon: "fa-user-circle-o"
        }
    ];
    const pageIcons = {
        home: "fa-home",
        chat: "fa-commenting",
        memory: "fa-database",
        login: "fa-sign-in",
        "prompt-demo": "fa-flask",
        "prompt-optimizer": "fa-magic",
        rag: "fa-search",
        mineru: "fa-file-text-o",
        "document-summary": "fa-list-alt",
        knowledge: "fa-book",
        "text-to-image": "fa-image",
        "image-to-text": "fa-file-text-o",
        "text-to-video": "fa-video-camera",
        "modeling-demo": "fa-cubes",
        "model-evaluation": "fa-line-chart",
        "document-quality": "fa-check-square-o",
        "skill-monitor": "fa-puzzle-piece",
        "digital-twin": "fa-connectdevelop",
        deploy: "fa-cogs",
        logs: "fa-terminal",
        "service-dashboard": "fa-line-chart"
    };
    const pageSummaries = {
        home: "按功能域查看 AI 对话、Prompt、内容生成、治理和运维工具入口。",
        chat: "面向连续问答的 AI 对话工作台，保留输入区和对话历史。",
        memory: "集中维护用户记忆、偏好、上下文和历史消息。",
        login: "通过短信验证码进入 AI 工具台。",
        "prompt-demo": "用模板、变量和测试结果快速调试提示词。",
        "prompt-optimizer": "评测 Prompt 质量、定位短板，并沉淀优化版本。",
        rag: "演示向量检索、上下文命中和 RAG 最终回答。",
        mineru: "把 PDF、DOCX、Markdown 等文档解析成 Markdown、结构块和 JSON。",
        "document-summary": "提取 PDF、DOCX 文档摘要、核心要点和关键词。",
        knowledge: "维护知识条目、分类、标签和 AI 辅助生成内容。",
        "text-to-image": "左侧输入生成参数，右侧查看图片生成结果。",
        "image-to-text": "上传图片并生成结构化图像描述。",
        "text-to-video": "配置视频生成 Prompt、模型参数和预览结果。",
        "modeling-demo": "上传参考图并演示图像到 3D 资产的本地建模流程。",
        "model-evaluation": "汇总 chat、rag、prompt 场景的 Recall、Precision、F1 与 TopK 命中趋势。",
        "document-quality": "评测文档内容质量，输出规则问题、六维评分、量化指标和整改建议。",
        "skill-monitor": "跟踪本地自定义 skill 清单、使用事件、成功率和接入状态。",
        "digital-twin": "定位 AI 能力故障、业务影响和下一步处置动作。",
        deploy: "触发构建、部署、重启，并在同一工作台查看部署结果。",
        logs: "查看 Docker 运行日志和部署过程输出。",
        "service-dashboard": "汇总服务健康度、JVM、容器和运行日志。"
    };
    const expandedCategoryIds = new Set();

    function onReady(callback) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
            return;
        }
        callback();
    }

    function normalizePath() {
        const path = window.location.pathname || "/index.html";
        if (path === "/" || path.endsWith("/")) {
            return "/index.html";
        }
        return path;
    }

    function allPages() {
        return categories.flatMap(function (category) {
            return category.pages.map(function (page) {
                return {
                    ...page,
                    categoryId: category.id,
                    categoryLabel: category.label,
                    categoryIcon: category.icon
                };
            });
        }).concat(accountActions);
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#039;");
    }

    function pageIcon(page) {
        return pageIcons[page.id] || page.categoryIcon || "fa-circle-o";
    }

    function categoryFor(current) {
        return categories.find(function (category) {
            return category.id === current.categoryId;
        });
    }

    function pageSummary(current) {
        return pageSummaries[current.id] || "按当前业务域组织的功能工作区。";
    }

    function resolveCurrentPage() {
        const path = normalizePath();
        const hash = (window.location.hash || "#home").replace("#", "") || "home";
        const routedHash = hash === "deploy-result" ? "deploy" : hash;
        const pages = allPages();

        if (path.endsWith("/index.html")) {
            return pages.find(function (page) {
                return page.page === "index" && page.hash === routedHash;
            }) || pages.find(function (page) {
                return page.id === "home";
            });
        }

        return pages.find(function (page) {
            return path.endsWith("/" + page.page + ".html");
        }) || pages.find(function (page) {
            return page.id === "home";
        });
    }

    function ensureHeader() {
        let header = document.querySelector("header");
        if (!header) {
            header = document.createElement("header");
            const firstCanvas = document.querySelector(".tech-grid-canvas");
            if (firstCanvas && firstCanvas.nextSibling) {
                document.body.insertBefore(header, firstCanvas.nextSibling);
            } else {
                document.body.prepend(header);
            }
        }
        header.classList.add("app-header");
        return header;
    }

    function ensureSidebar() {
        let sidebar = document.querySelector(".app-sidebar");
        if (!sidebar) {
            sidebar = document.createElement("aside");
            const header = ensureHeader();
            if (header.nextSibling) {
                document.body.insertBefore(sidebar, header.nextSibling);
            } else {
                document.body.appendChild(sidebar);
            }
        }
        sidebar.id = "app-sidebar";
        sidebar.className = "app-sidebar";
        sidebar.setAttribute("aria-label", "功能侧边导航");
        sidebar.setAttribute("data-flowbite-component", "sidebar");
        sidebar.setAttribute("data-drawer-placement", "left");
        return sidebar;
    }

    function ensureSidebarBackdrop() {
        let backdrop = document.querySelector(".app-sidebar-backdrop");
        if (!backdrop) {
            backdrop = document.createElement("button");
            backdrop.type = "button";
            backdrop.className = "app-sidebar-backdrop";
            backdrop.setAttribute("aria-label", "关闭功能导航");
            backdrop.setAttribute("data-app-sidebar-close", "true");
            const sidebar = ensureSidebar();
            document.body.insertBefore(backdrop, sidebar.nextSibling);
        }
        return backdrop;
    }

    function sidebarLink(page, current) {
        const active = page.id === current.id ? " is-active" : "";
        const title = escapeHtml((page.categoryLabel ? page.categoryLabel + " / " : "") + page.label);
        return [
            "<a class=\"app-sidebar-link" + active + "\" href=\"" + page.href + "\" data-app-link=\"" + page.id + "\" title=\"" + title + "\" aria-label=\"" + title + "\">",
            "<span class=\"app-sidebar-link-icon\"><i class=\"fa " + pageIcon(page) + "\"></i></span>",
            "<span class=\"app-sidebar-link-text\">" + escapeHtml(page.label) + "</span>",
            "</a>"
        ].join("");
    }

    function renderHeader(current) {
        const header = ensureHeader();
        header.className = "app-header app-topbar";

        header.innerHTML = [
            "<button type=\"button\" class=\"app-sidebar-toggle\" data-app-sidebar-toggle data-drawer-target=\"app-sidebar\" data-drawer-toggle=\"app-sidebar\" aria-controls=\"app-sidebar\" aria-label=\"切换功能导航\" aria-expanded=\"false\" aria-pressed=\"false\" title=\"切换功能导航\">",
            "<i class=\"fa fa-bars\"></i>",
            "</button>",
            "<div class=\"app-brand\">",
            "<div class=\"app-brand-title\"><i class=\"fa fa-robot\"></i><span>Spring AI Demo</span></div>",
            "<div class=\"app-brand-subtitle\">按功能域组织的 AI 工具台</div>",
            "</div>",
            "<div class=\"app-topbar-right\">",
            "<nav class=\"app-topbar-account\" aria-label=\"账号操作\">",
            renderAccountActions(current),
            "</nav>",
            "<div class=\"app-topbar-current\" aria-label=\"当前功能位置\">",
            "<span class=\"app-topbar-kicker\"><i class=\"fa " + current.categoryIcon + "\"></i>" + escapeHtml(current.categoryLabel) + "</span>",
            "<strong>" + escapeHtml(current.label) + "</strong>",
            "</div>",
            "</div>"
        ].join("");
    }

    function renderAccountActions(current) {
        return accountActions.map(function (action) {
            const active = action.id === current.id ? " is-active" : "";
            return [
                "<a class=\"app-topbar-account-link" + active + "\" href=\"" + action.href + "\" data-app-account-action=\"" + action.id + "\" aria-label=\"" + escapeHtml(action.label) + "\">",
                "<i class=\"fa " + pageIcon(action) + "\"></i>",
                "<span>" + escapeHtml(action.label) + "</span>",
                "</a>"
            ].join("");
        }).join("");
    }

    function renderSidebar(current) {
        const sidebar = ensureSidebar();
        ensureSidebarBackdrop();
        expandedCategoryIds.add(current.categoryId);
        const groups = categories.map(function (category) {
            const expanded = expandedCategoryIds.has(category.id);
            const expandedClass = expanded ? " is-expanded" : "";
            const linksId = "app-sidebar-links-" + category.id;
            const categoryLabel = escapeHtml(category.label);
            return [
                "<section class=\"app-sidebar-group" + expandedClass + "\" data-app-category-group=\"" + category.id + "\">",
                "<button type=\"button\" class=\"app-sidebar-label\" data-app-sidebar-group-toggle=\"" + category.id + "\" aria-expanded=\"" + expanded + "\" aria-controls=\"" + linksId + "\" title=\"" + categoryLabel + "\" aria-label=\"" + categoryLabel + "\">",
                "<span class=\"app-sidebar-label-main\"><i class=\"fa " + category.icon + "\"></i><span>" + categoryLabel + "</span></span>",
                "<i class=\"fa fa-chevron-down app-sidebar-caret\"></i>",
                "</button>",
                "<div id=\"" + linksId + "\" class=\"app-sidebar-links\">",
                category.pages.map(function (page) {
                    return sidebarLink({
                        ...page,
                        categoryIcon: category.icon,
                        categoryLabel: category.label
                    }, current);
                }).join(""),
                "</div>",
                "</section>"
            ].join("");
        }).join("");

        sidebar.innerHTML = [
            "<div class=\"app-sidebar-head\">",
            "<div class=\"app-sidebar-mark\"><i class=\"fa fa-robot\"></i></div>",
            "<div><strong>AI 工具台</strong><span>Flowbite Workbench</span></div>",
            "<button type=\"button\" class=\"app-sidebar-close\" data-app-sidebar-close=\"true\" aria-label=\"关闭功能导航\"><i class=\"fa fa-times\"></i></button>",
            "</div>",
            "<nav class=\"app-sidebar-nav\" aria-label=\"Flowbite 功能侧栏\">",
            groups,
            "</nav>"
        ].join("");
    }

    function workbenchSwitcher(current) {
        const category = categoryFor(current);
        if (!category) {
            return "";
        }

        return category.pages.map(function (page) {
            const active = page.id === current.id ? " is-active" : "";
            return [
                "<a class=\"app-workbench-chip" + active + "\" href=\"" + page.href + "\" data-app-link=\"" + page.id + "\">",
                "<i class=\"fa " + pageIcon({ ...page, categoryIcon: category.icon }) + "\"></i>",
                "<span>" + escapeHtml(page.label) + "</span>",
                "</a>"
            ].join("");
        }).join("");
    }

    function renderWorkbenchHero(main, current) {
        let hero = Array.from(main.children).find(function (element) {
            return element.classList.contains("app-workbench-hero");
        });
        if (!hero) {
            hero = document.createElement("section");
            hero.className = "app-workbench-hero";
            main.prepend(hero);
        }

        hero.innerHTML = [
            "<div class=\"app-workbench-heading\">",
            "<span class=\"app-workbench-trail\"><i class=\"fa " + current.categoryIcon + "\"></i>" + escapeHtml(current.categoryLabel) + "</span>",
            "<h1><i class=\"fa " + pageIcon(current) + "\"></i><span>" + escapeHtml(current.label) + "</span></h1>",
            "<p>" + escapeHtml(pageSummary(current)) + "</p>",
            "</div>",
            "<nav class=\"app-workbench-switcher\" aria-label=\"当前功能域入口切换\">",
            workbenchSwitcher(current),
            "</nav>"
        ].join("");
    }

    function decorateContentShells(main) {
        Array.from(main.children).forEach(function (element) {
            if (element.classList.contains("app-workbench-hero")) {
                return;
            }
            if (element.matches("script, style")) {
                return;
            }
            element.classList.add("app-content-shell");
            if (element.matches("section")) {
                element.classList.add("app-workbench-panel");
            }
        });
    }

    function decorateLegacyHeadings(main) {
        main.querySelectorAll(".text-center.mb-8").forEach(function (heading) {
            heading.classList.add("app-legacy-heading");
        });
    }

    function decorateWorkbenchPanels(main) {
        main.querySelectorAll([
            ".bg-white.rounded-lg.shadow-lg",
            ".bg-white.rounded-lg.shadow-md",
            ".panel",
            ".status-panel",
            ".runtime-panel",
            ".media-control-panel",
            ".media-preview-panel",
            ".login-container",
            ".sidebar",
            ".editor-container",
            ".variables-container",
            ".result-container",
            ".user-input",
            ".filter-bar",
            ".stat-card",
            ".status-kpi",
            ".memory-card"
        ].join(",")).forEach(function (panel) {
            panel.classList.add("app-workbench-panel");
        });
    }

    function normalizeFooterLinks() {
        const targets = [
            { href: "/index.html", label: "首页", icon: "fa-home" },
            { href: "/prompt-demo.html", label: "提示词工程", icon: "fa-flask" },
            { href: "/service-dashboard.html", label: "运行大屏", icon: "fa-dashboard" }
        ];
        document.querySelectorAll("footer a[href='#']").forEach(function (link, index) {
            const target = targets[index % targets.length];
            link.href = target.href;
            link.setAttribute("aria-label", target.label);
            link.setAttribute("title", target.label);
            link.innerHTML = "<i class=\"fa " + target.icon + " text-xl\"></i>";
        });
    }

    function decorateWorkbench(current) {
        const main = ensureMain();
        main.classList.add("app-workbench");
        main.setAttribute("data-app-workbench", current.categoryId);
        main.setAttribute("data-app-workbench-page", current.id);
        renderWorkbenchHero(main, current);
        decorateContentShells(main);
        decorateLegacyHeadings(main);
        decorateWorkbenchPanels(main);
    }

    function ensureMain() {
        let main = document.querySelector("main");
        if (!main) {
            main = document.createElement("main");
            const firstScript = document.querySelector("script");
            const movable = Array.from(document.body.children).filter(function (element) {
                return !element.matches("header, script, .tech-grid-canvas, .app-context, .app-sidebar, .app-sidebar-backdrop");
            });
            document.body.insertBefore(main, firstScript || null);
            movable.forEach(function (element) {
                main.appendChild(element);
            });
        }
        main.classList.add("app-page-layout");
        return main;
    }

    function renderContext(current) {
        let context = document.querySelector(".app-context");
        if (!context) {
            context = document.createElement("div");
            context.className = "app-context";
            document.body.appendChild(context);
        }
        context.innerHTML = [
            "<span>归属</span>",
            "<strong>" + current.categoryLabel + "</strong>",
            "<span>/</span>",
            "<strong>" + current.label + "</strong>"
        ].join("");
    }

    function applyBodyMetadata(current) {
        document.documentElement.setAttribute("data-app-layout", "active");
        document.body.setAttribute("data-app-layout", "active");
        document.body.setAttribute("data-app-page", current.page);
        document.body.setAttribute("data-app-section", current.id);
        document.body.setAttribute("data-app-category", current.categoryId);
    }

    function updateActiveNavigation(current) {
        document.querySelectorAll("[data-app-link]").forEach(function (link) {
            link.classList.toggle("is-active", link.getAttribute("data-app-link") === current.id);
        });
    }

    function switchIndexTabIfNeeded(current) {
        if (current.page !== "index" || typeof window.switchTab !== "function") {
            return;
        }
        window.switchTab(current.hash || "home", null);
    }

    function hydrateFlowbite() {
        if (typeof window.initFlowbite !== "function") {
            return;
        }
        window.requestAnimationFrame(function () {
            window.initFlowbite();
        });
    }

    function updateSidebarToggleState() {
        const toggle = document.querySelector("[data-app-sidebar-toggle]");
        if (!toggle) {
            return;
        }

        const open = document.body.classList.contains("app-sidebar-open");
        const label = open ? "关闭功能导航" : "打开功能导航";

        toggle.setAttribute("aria-label", label);
        toggle.setAttribute("title", label);
        toggle.setAttribute("aria-expanded", String(open));
    }

    function syncLayout() {
        const current = resolveCurrentPage();
        applyBodyMetadata(current);
        renderHeader(current);
        renderSidebar(current);
        ensureMain();
        renderContext(current);
        switchIndexTabIfNeeded(current);
        decorateWorkbench(current);
        normalizeFooterLinks();
        updateActiveNavigation(current);
        updateSidebarToggleState();
        hydrateFlowbite();
    }

    function closeSidebar() {
        document.body.classList.remove("app-sidebar-open");
        updateSidebarToggleState();
    }

    function toggleSidebar() {
        document.body.classList.toggle("app-sidebar-open");
        updateSidebarToggleState();
    }

    function isCompactNavigation() {
        return window.matchMedia("(max-width: 1180px)").matches;
    }

    function toggleSidebarGroup(button) {
        const categoryId = button.getAttribute("data-app-sidebar-group-toggle");
        const group = button.closest(".app-sidebar-group");
        if (!categoryId || !group) {
            return;
        }

        const expanded = !group.classList.contains("is-expanded");
        group.classList.toggle("is-expanded", expanded);
        button.setAttribute("aria-expanded", String(expanded));
        if (expanded) {
            expandedCategoryIds.add(categoryId);
            return;
        }
        expandedCategoryIds.delete(categoryId);
    }

    function handleShellActions(event) {
        if (event.target.closest("[data-app-sidebar-toggle]")) {
            event.preventDefault();
            toggleSidebar();
            return;
        }

        const groupToggle = event.target.closest("[data-app-sidebar-group-toggle]");
        if (groupToggle) {
            event.preventDefault();
            toggleSidebarGroup(groupToggle);
            return;
        }

        if (event.target.closest("[data-app-sidebar-close]")) {
            event.preventDefault();
            closeSidebar();
            return;
        }

        if (isCompactNavigation() && event.target.closest("a[data-app-link]")) {
            closeSidebar();
        }
    }

    function closeSidebarOnEscape(event) {
        if (event.key === "Escape") {
            closeSidebar();
        }
    }

    function closeSidebarOnDesktop() {
        if (!isCompactNavigation()) {
            closeSidebar();
        }
        updateSidebarToggleState();
    }

    function interceptSamePageIndexLinks(event) {
        const link = event.target.closest("a[data-app-link]");
        if (!link) {
            return;
        }

        const url = new URL(link.href, window.location.origin);
        if (!normalizePath().endsWith("/index.html") || !url.pathname.endsWith("/index.html") || !url.hash) {
            return;
        }

        event.preventDefault();
        window.location.hash = url.hash;
        syncLayout();
    }

    onReady(function () {
        syncLayout();
        document.addEventListener("click", handleShellActions);
        document.addEventListener("click", interceptSamePageIndexLinks);
        document.addEventListener("keydown", closeSidebarOnEscape);
        window.addEventListener("hashchange", syncLayout);
        window.addEventListener("resize", closeSidebarOnDesktop);
    });
}());
