package com.example.demo.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BrowserSmokeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(45);

    @TempDir
    Path tempDir;

    @Test
    void browserShouldCompleteDocumentQualityWorkflow() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ui.smoke"), "Run with -Dui.smoke=true to execute Chrome UI smoke");

        String baseUrl = System.getProperty("ui.baseUrl", "http://127.0.0.1:8080");
        ChromeSession chrome = startChrome();
        try {
            runDocumentQualityWorkflow(chrome, baseUrl);
        } finally {
            chrome.close();
        }
    }

    private void runDocumentQualityWorkflow(ChromeSession chrome, String baseUrl) throws Exception {
        try (CdpClient page = chrome.openPage()) {
            page.blockExternalCdn();
            page.navigate(baseUrl + "/document-quality.html");
            page.waitForTrue("document quality form", "document.readyState !== 'loading' && !!document.querySelector('#fill-sample-button')");

            page.evaluate("document.querySelector('#fill-sample-button').click(); true");
            page.evaluate("document.querySelector('#evaluate-button').click(); true");
            page.waitForTrue(
                    "quality evaluation result",
                    "!!document.querySelector('[data-export-format=\"html\"]') && document.querySelector('[data-export-format=\"html\"]').disabled === false"
            );

            String reportText = page.evaluateString("document.querySelector('#quality-result').innerText");
            assertTrue(reportText.contains("综合得分"), reportText);
            assertTrue(reportText.contains("优先处理问题") || reportText.contains("整改建议"), reportText);
            assertTrue(reportText.contains("字段覆盖"), reportText);

            page.evaluate("""
                    Array.from(document.querySelectorAll('.dq-report-actions button'))
                        .find(button => button.textContent.includes('查看抽取字段'))
                        .click();
                    true;
                    """);
            page.waitForTrue(
                    "field extraction modal",
                    "!document.querySelector('#quality-detail-modal').classList.contains('hidden')"
                            + " && document.querySelector('#quality-detail-content').innerText.includes('结构化字段')"
                            + " && document.querySelector('#quality-detail-content').innerText.includes('AI Skill 编排')"
                            + " && document.querySelector('#quality-detail-content').innerText.includes('接口契约')"
            );
            page.evaluate("document.querySelector('#quality-detail-modal .dq-modal-footer button').click(); true");

            page.evaluate("""
                    window.__documentQualityDownload = null;
                    HTMLAnchorElement.prototype.click = function () {
                        window.__documentQualityDownload = {download: this.download || '', href: this.href || ''};
                    };
                    document.querySelector('[data-export-format="html"]').click();
                    true;
                    """);
            page.waitForTrue(
                    "quality report download",
                    "window.__documentQualityDownload && window.__documentQualityDownload.download.endsWith('.html')"
            );

            page.evaluate("""
                    Array.from(document.querySelectorAll('.dq-export-button'))
                        .find(button => button.textContent.trim() === '历史')
                        .click();
                    true;
                    """);
            page.waitForTrue(
                    "quality history render",
                    "document.querySelector('#quality-result').innerText.includes('最近评测历史')"
                            + " || document.querySelector('#quality-result').innerText.includes('暂无历史记录')"
            );
        }
    }

    private ChromeSession startChrome() throws Exception {
        String chromeBinary = chromeBinary();
        int port = Integer.getInteger("ui.chromePort", 9333);
        Path userDataDir = tempDir.resolve("chrome-profile");
        Files.createDirectories(userDataDir);

        Process process = new ProcessBuilder(List.of(
                chromeBinary,
                "--headless=new",
                "--remote-debugging-address=127.0.0.1",
                "--remote-debugging-port=" + port,
                "--user-data-dir=" + userDataDir,
                "--disable-gpu",
                "--no-first-run",
                "--no-default-browser-check",
                "about:blank"
        ))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        ChromeSession session = new ChromeSession(process, port);
        session.waitUntilReady();
        return session;
    }

    private String chromeBinary() {
        String configured = System.getProperty("chrome.bin", "");
        if (!configured.isBlank()) {
            return configured;
        }
        List<String> candidates = List.of(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
        );
        return candidates.stream()
                .filter(path -> new File(path).isFile())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Chrome or Edge executable not found"));
    }

    private static final class ChromeSession implements AutoCloseable {
        private final Process process;
        private final int port;
        private final HttpClient httpClient = HttpClient.newHttpClient();

        private ChromeSession(Process process, int port) {
            this.process = process;
            this.port = port;
        }

        private void waitUntilReady() throws Exception {
            long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
            Exception lastFailure = null;
            while (System.nanoTime() < deadline) {
                try {
                    httpJson("GET", "/json/version");
                    return;
                } catch (Exception e) {
                    lastFailure = e;
                    Thread.sleep(150);
                }
            }
            throw new IllegalStateException("Chrome DevTools endpoint did not become ready", lastFailure);
        }

        private CdpClient openPage() throws Exception {
            JsonNode target = httpJson("PUT", "/json/new?about:blank");
            String webSocketUrl = target.path("webSocketDebuggerUrl").asText();
            if (webSocketUrl.isBlank()) {
                throw new IllegalStateException("Chrome target did not expose webSocketDebuggerUrl: " + target);
            }
            return CdpClient.connect(webSocketUrl);
        }

        private JsonNode httpJson(String method, String path) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5));
            HttpRequest request = "PUT".equals(method)
                    ? builder.PUT(HttpRequest.BodyPublishers.noBody()).build()
                    : builder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Chrome DevTools HTTP " + response.statusCode() + ": " + response.body());
            }
            return JSON.readTree(response.body());
        }

        @Override
        public void close() {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private static final class CdpClient implements WebSocket.Listener, AutoCloseable {
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private final AtomicInteger ids = new AtomicInteger();
        private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private final StringBuilder partialMessage = new StringBuilder();
        private WebSocket webSocket;

        private static CdpClient connect(String webSocketUrl) throws Exception {
            CdpClient client = new CdpClient();
            client.webSocket = client.httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(webSocketUrl), client)
                    .get(10, TimeUnit.SECONDS);
            client.send("Page.enable", JSON.createObjectNode());
            client.send("Runtime.enable", JSON.createObjectNode());
            client.send("Network.enable", JSON.createObjectNode());
            return client;
        }

        private void blockExternalCdn() throws Exception {
            ObjectNode params = JSON.createObjectNode();
            ArrayNode urls = JSON.createArrayNode();
            urls.add("https://cdn.tailwindcss.com/*");
            urls.add("https://cdn.jsdelivr.net/*");
            params.set("urls", urls);
            send("Network.setBlockedURLs", params);
        }

        private void navigate(String url) throws Exception {
            ObjectNode params = JSON.createObjectNode();
            params.put("url", url);
            send("Page.navigate", params);
        }

        private JsonNode evaluate(String expression) throws Exception {
            ObjectNode params = JSON.createObjectNode();
            params.put("expression", expression);
            params.put("returnByValue", true);
            params.put("awaitPromise", true);
            return send("Runtime.evaluate", params).path("result").path("result");
        }

        private String evaluateString(String expression) throws Exception {
            return evaluate(expression).path("value").asText();
        }

        private void waitForTrue(String description, String expression) throws Exception {
            long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
            String lastValue = "";
            Exception lastFailure = null;
            while (System.nanoTime() < deadline) {
                try {
                    JsonNode result = evaluate("Boolean(" + expression + ")");
                    lastValue = result.toString();
                    if (result.path("value").asBoolean(false)) {
                        return;
                    }
                } catch (Exception e) {
                    lastFailure = e;
                }
                Thread.sleep(150);
            }
            if (lastFailure != null) {
                fail("Timed out waiting for " + description
                        + "; last failure: " + lastFailure.getMessage()
                        + "; debug: " + debugSnapshot());
            }
            fail("Timed out waiting for " + description
                    + "; last value: " + lastValue
                    + "; debug: " + debugSnapshot());
        }

        private String debugSnapshot() {
            try {
                return evaluateString("""
                        JSON.stringify({
                            url: location.href,
                            readyState: document.readyState,
                            qualityText: document.querySelector('#quality-result')?.innerText?.slice(0, 500) || '',
                            qualityExportDisabled: document.querySelector('[data-export-format="html"]')?.disabled ?? null,
                            documentContentLength: document.querySelector('#document-content')?.value?.length ?? null,
                            evaluateDisabled: document.querySelector('#evaluate-button')?.disabled ?? null,
                            masterText: document.querySelector('#collection-result')?.innerText?.slice(0, 500) || '',
                            sourceUrlLength: document.querySelector('#source-url')?.value?.length ?? null,
                            sourceDataLength: document.querySelector('#source-data')?.value?.length ?? null
                        })
                        """);
            } catch (Exception e) {
                return "debug unavailable: " + e.getMessage();
            }
        }

        private JsonNode send(String method, ObjectNode params) throws Exception {
            int id = ids.incrementAndGet();
            ObjectNode message = JSON.createObjectNode();
            message.put("id", id);
            message.put("method", method);
            message.set("params", params == null ? JSON.createObjectNode() : params);
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pending.put(id, future);
            webSocket.sendText(JSON.writeValueAsString(message), true).join();
            JsonNode response = future.get(10, TimeUnit.SECONDS);
            if (response.has("error")) {
                throw new IllegalStateException(method + " failed: " + response.path("error"));
            }
            return response;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialMessage.append(data);
            if (last) {
                String message = partialMessage.toString();
                partialMessage.setLength(0);
                try {
                    JsonNode node = JSON.readTree(message);
                    if (node.has("id")) {
                        CompletableFuture<JsonNode> future = pending.remove(node.get("id").asInt());
                        if (future != null) {
                            future.complete(node);
                        }
                    }
                } catch (IOException e) {
                    pending.values().forEach(future -> future.completeExceptionally(e));
                    pending.clear();
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            pending.values().forEach(future -> future.completeExceptionally(error));
            pending.clear();
        }

        @Override
        public void close() {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }
}
