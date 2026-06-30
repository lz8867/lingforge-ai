(function () {
    const THEME_ATTRIBUTE = "data-tech-theme";
    const CANVAS_CLASS = "tech-grid-canvas";
    const reduceMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");

    function onReady(callback) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
            return;
        }
        callback();
    }

    function clamp(value, min, max) {
        return Math.min(Math.max(value, min), max);
    }

    function createNode(width, height) {
        const speed = 0.12 + Math.random() * 0.22;
        const angle = Math.random() * Math.PI * 2;

        return {
            x: Math.random() * width,
            y: Math.random() * height,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            radius: 1.1 + Math.random() * 1.9,
            pulse: Math.random() * Math.PI * 2
        };
    }

    function installCanvasRenderer() {
        if (document.querySelector("." + CANVAS_CLASS)) {
            return;
        }

        const canvas = document.createElement("canvas");
        canvas.className = CANVAS_CLASS;
        canvas.setAttribute("aria-hidden", "true");
        document.body.prepend(canvas);

        const context = canvas.getContext("2d", { alpha: true });
        if (!context) {
            return;
        }

        let width = 0;
        let height = 0;
        let pixelRatio = 1;
        let nodes = [];
        let animationFrame = 0;

        function resize() {
            pixelRatio = clamp(window.devicePixelRatio || 1, 1, 2);
            width = Math.max(document.documentElement.clientWidth, window.innerWidth || 0);
            height = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);
            canvas.width = Math.floor(width * pixelRatio);
            canvas.height = Math.floor(height * pixelRatio);
            canvas.style.width = width + "px";
            canvas.style.height = height + "px";
            context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);

            const nodeCount = clamp(Math.round((width * height) / 24000), 34, 92);
            nodes = Array.from({ length: nodeCount }, function () {
                return createNode(width, height);
            });
        }

        function drawGrid() {
            context.save();
            context.globalAlpha = 0.22;
            context.strokeStyle = "rgba(34, 211, 238, 0.28)";
            context.lineWidth = 1;

            for (let x = 0; x <= width; x += 96) {
                context.beginPath();
                context.moveTo(x + 0.5, 0);
                context.lineTo(x + 0.5, height);
                context.stroke();
            }

            for (let y = 0; y <= height; y += 96) {
                context.beginPath();
                context.moveTo(0, y + 0.5);
                context.lineTo(width, y + 0.5);
                context.stroke();
            }

            context.restore();
        }

        function moveNodes() {
            if (reduceMotionQuery.matches) {
                return;
            }

            nodes.forEach(function (node) {
                node.x += node.vx;
                node.y += node.vy;
                node.pulse += 0.018;

                if (node.x < -20 || node.x > width + 20) {
                    node.vx *= -1;
                }
                if (node.y < -20 || node.y > height + 20) {
                    node.vy *= -1;
                }
            });
        }

        function drawConnections() {
            const maxDistance = width < 720 ? 118 : 148;

            for (let i = 0; i < nodes.length; i += 1) {
                for (let j = i + 1; j < nodes.length; j += 1) {
                    const first = nodes[i];
                    const second = nodes[j];
                    const dx = first.x - second.x;
                    const dy = first.y - second.y;
                    const distance = Math.sqrt(dx * dx + dy * dy);

                    if (distance > maxDistance) {
                        continue;
                    }

                    const alpha = (1 - distance / maxDistance) * 0.34;
                    context.strokeStyle = "rgba(34, 211, 238, " + alpha.toFixed(3) + ")";
                    context.lineWidth = 1;
                    context.beginPath();
                    context.moveTo(first.x, first.y);
                    context.lineTo(second.x, second.y);
                    context.stroke();
                }
            }
        }

        function drawNodes(time) {
            nodes.forEach(function (node, index) {
                const pulse = reduceMotionQuery.matches ? 0.45 : Math.sin(time * 0.0014 + node.pulse) * 0.45;
                const radius = node.radius + pulse;
                const isAccent = index % 5 === 0;

                context.fillStyle = isAccent ? "rgba(251, 191, 36, 0.88)" : "rgba(94, 234, 212, 0.88)";
                context.shadowColor = isAccent ? "rgba(251, 191, 36, 0.42)" : "rgba(34, 211, 238, 0.48)";
                context.shadowBlur = 14;
                context.beginPath();
                context.arc(node.x, node.y, Math.max(0.8, radius), 0, Math.PI * 2);
                context.fill();
            });
            context.shadowBlur = 0;
        }

        function draw(time) {
            context.clearRect(0, 0, width, height);
            drawGrid();
            moveNodes();
            drawConnections();
            drawNodes(time || 0);
        }

        function render(time) {
            draw(time);
            if (!reduceMotionQuery.matches) {
                animationFrame = window.requestAnimationFrame(render);
            }
        }

        function restartRenderer() {
            window.cancelAnimationFrame(animationFrame);
            resize();
            render(0);
        }

        let resizeTimer = 0;
        window.addEventListener("resize", function () {
            window.clearTimeout(resizeTimer);
            resizeTimer = window.setTimeout(restartRenderer, 120);
        });

        if (typeof reduceMotionQuery.addEventListener === "function") {
            reduceMotionQuery.addEventListener("change", restartRenderer);
        }

        restartRenderer();
    }

    onReady(function () {
        document.body.setAttribute(THEME_ATTRIBUTE, "active");
        document.body.classList.add("tech-page-ready");
        installCanvasRenderer();
    });
}());
