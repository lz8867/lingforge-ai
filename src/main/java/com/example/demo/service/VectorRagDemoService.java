package com.example.demo.service;

import com.example.demo.service.DocumentQualityEmbeddingClient.EmbeddingResult;
import com.example.demo.service.QdrantVectorStoreClient.DeleteResult;
import com.example.demo.service.QdrantVectorStoreClient.SearchResult;
import com.example.demo.service.QdrantVectorStoreClient.UpsertResult;
import com.example.demo.service.QdrantVectorStoreClient.VectorPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class VectorRagDemoService {

    private static final Logger log = LoggerFactory.getLogger(VectorRagDemoService.class);
    private static final int DEFAULT_TOP_K = 3;
    private static final int TARGET_CHUNK_CHARS = 520;
    private static final String SOURCE_LOCAL_SAMPLE = "local-sample";
    private static final String SOURCE_TYPE_IMPORTED = "imported";

    private final OllamaChatClient ollamaChatClient;
    private final DocumentQualityEmbeddingClient embeddingClient;
    private final QdrantVectorStoreClient vectorStoreClient;
    private final CopyOnWriteArrayList<IndexedDocument> documents;
    private volatile String lastEmbeddingSource = "local-hash";
    private volatile String lastEmbeddingMessage = "尚未执行向量化。";

    @Autowired
    public VectorRagDemoService(
            OllamaChatClient ollamaChatClient,
            DocumentQualityEmbeddingClient embeddingClient,
            QdrantVectorStoreClient vectorStoreClient) {
        this.ollamaChatClient = ollamaChatClient;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
        this.documents = new CopyOnWriteArrayList<>();
        seedDemoDocuments();
    }

    public VectorRagDemoService(OllamaChatClient ollamaChatClient) {
        this(ollamaChatClient, DocumentQualityEmbeddingClient.localOnly(), QdrantVectorStoreClient.disabled());
    }

    public VectorSearchResponse search(String query, int topK) {
        String safeQuery = query == null ? "" : query.trim();
        int boundedTopK = boundTopK(topK);
        log.info("RAG 向量检索开始: queryLength={}, topK={}, provider={}",
                safeQuery.length(), boundedTopK, vectorStoreClient.provider());

        if (safeQuery.isBlank()) {
            log.warn("RAG 向量检索跳过: reason=query blank");
            return new VectorSearchResponse(
                    safeQuery,
                    boundedTopK,
                    List.of(),
                    activeStoreName(),
                    lastEmbeddingSource,
                    "请输入检索问题。"
            );
        }

        EmbeddingResult queryEmbedding = embedText(safeQuery);
        if (vectorStoreClient.enabled()) {
            SearchResult qdrantResult = vectorStoreClient.search(queryEmbedding.vector(), boundedTopK);
            if (qdrantResult.success() && !qdrantResult.hits().isEmpty()) {
                log.info("RAG 向量检索命中 Qdrant: hitCount={}", qdrantResult.hits().size());
                return new VectorSearchResponse(
                        safeQuery,
                        boundedTopK,
                        qdrantResult.hits(),
                        "qdrant",
                        queryEmbedding.source(),
                        qdrantResult.message()
                );
            }
            log.warn("RAG Qdrant 未返回可用命中，回退本地索引: message={}", qdrantResult.message());
        }

        List<SearchHit> matches = documents.stream()
                .map(document -> toSearchHit(document, cosineSimilarity(queryEmbedding.vector(), document.vector())))
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(boundedTopK)
                .toList();

        String message = vectorStoreClient.enabled()
                ? "Qdrant 未返回命中，已回退本地向量索引。"
                : "本地向量索引检索成功。";
        log.info("RAG 本地向量检索完成: resultCount={}, topScore={}",
                matches.size(), matches.isEmpty() ? 0 : matches.get(0).score());
        return new VectorSearchResponse(safeQuery, boundedTopK, matches, "local", queryEmbedding.source(), message);
    }

    public RagAnswerResponse answer(String question, int topK) {
        String safeQuestion = question == null ? "" : question.trim();
        log.info("RAG 回答开始: questionLength={}, topK={}", safeQuestion.length(), topK);

        VectorSearchResponse searchResponse = search(safeQuestion, topK);
        String prompt = buildRagPrompt(safeQuestion, searchResponse.matches());
        return generateAnswer(safeQuestion, searchResponse.matches(), prompt);
    }

    public RagAnswerResponse answerWithKnowledgeContexts(
            String question,
            int topK,
            List<KnowledgeRetrievalHit> contexts) {
        String safeQuestion = question == null ? "" : question.trim();
        int boundedTopK = boundTopK(topK);
        List<SearchHit> searchHits = contexts == null
                ? List.of()
                : contexts.stream()
                .limit(boundedTopK)
                .map(VectorRagDemoService::toSearchHit)
                .toList();
        log.info("Running RAG answer with unified retrieval contexts, questionLength: {}, contextCount: {}",
                safeQuestion.length(), searchHits.size());
        String prompt = buildRagPrompt(safeQuestion, searchHits);
        return generateAnswer(safeQuestion, searchHits, prompt);
    }

    public KnowledgeIngestResponse ingestDocument(String documentName, String content, String source) {
        String safeName = defaultText(documentName, "未命名知识文档");
        String safeSource = defaultText(source, "manual");
        String safeContent = content == null ? "" : content.trim();
        log.info("RAG 知识入库开始: documentName={}, contentLength={}, source={}",
                safeName, safeContent.length(), safeSource);

        if (safeContent.isBlank()) {
            log.warn("RAG 知识入库拒绝: documentName={}, reason=content blank", safeName);
            return new KnowledgeIngestResponse(
                    false,
                    safeName,
                    0,
                    documents.size(),
                    List.of(),
                    activeStoreName(),
                    "请输入需要入库的文档正文。"
            );
        }

        List<DemoDocument> chunks = splitIntoChunks(safeName, safeContent, safeSource);
        removeImportedDocument(safeName);
        List<IndexedDocument> indexedChunks = chunks.stream()
                .map(this::indexDocument)
                .toList();
        documents.addAll(indexedChunks);
        UpsertResult upsertResult = vectorStoreClient.upsert(toVectorPoints(indexedChunks));
        List<SearchHit> hits = indexedChunks.stream()
                .map(chunk -> toSearchHit(chunk, 1.0))
                .toList();
        String activeStore = upsertResult.success() && vectorStoreClient.enabled() ? "qdrant" : "local";
        String message = vectorStoreClient.enabled()
                ? upsertResult.message() + "；本地索引同步完成。"
                : "已写入本地向量索引；如需真实向量库，启动 Qdrant 并设置 RAG_VECTOR_PROVIDER=qdrant。";

        log.info("RAG 知识入库完成: documentName={}, chunkCount={}, totalChunks={}, activeStore={}",
                safeName, indexedChunks.size(), documents.size(), activeStore);
        return new KnowledgeIngestResponse(
                true,
                safeName,
                indexedChunks.size(),
                documents.size(),
                hits,
                activeStore,
                message
        );
    }

    public KnowledgeChunksResponse knowledgeChunks() {
        log.info("RAG Chunk 列表查询: totalChunks={}, importedChunks={}", documents.size(), importedChunkCount());
        List<SearchHit> chunks = documents.stream()
                .map(document -> toSearchHit(document, 1.0))
                .toList();
        return new KnowledgeChunksResponse(documents.size(), importedChunkCount(), chunks);
    }

    public KnowledgeClearResponse clearKnowledgeBase() {
        log.info("RAG 自定义知识清理开始: totalChunks={}, importedChunks={}", documents.size(), importedChunkCount());
        int before = documents.size();
        documents.removeIf(VectorRagDemoService::isImportedDocument);
        int removed = before - documents.size();
        DeleteResult deleteResult = vectorStoreClient.deleteImported();
        String message = vectorStoreClient.enabled()
                ? "已清理本地导入片段。" + deleteResult.message()
                : "已清理本地导入片段，内置样例保留。";
        log.info("RAG 自定义知识清理完成: removed={}, totalChunks={}", removed, documents.size());
        return new KnowledgeClearResponse(removed, documents.size(), message);
    }

    public VectorStoreStatus vectorStoreStatus() {
        QdrantVectorStoreClient.StoreStatus qdrantStatus = vectorStoreClient.status();
        String activeStore = vectorStoreClient.enabled() && qdrantStatus.available() ? "qdrant" : "local";
        String message = "qdrant".equals(activeStore)
                ? "Qdrant 可用，检索会优先使用真实向量库。"
                : "当前使用本地向量索引；配置 RAG_VECTOR_PROVIDER=qdrant 后可接入 Qdrant。";
        log.info("RAG 向量库状态返回: activeStore={}, totalChunks={}, importedChunks={}",
                activeStore, documents.size(), importedChunkCount());
        return new VectorStoreStatus(
                activeStore,
                vectorStoreClient.provider(),
                vectorStoreClient.baseUrl(),
                vectorStoreClient.collection(),
                qdrantStatus.available(),
                documents.size(),
                importedChunkCount(),
                lastEmbeddingSource,
                lastEmbeddingMessage,
                message
        );
    }

    private RagAnswerResponse generateAnswer(String safeQuestion, List<SearchHit> contexts, String prompt) {
        try {
            String answer = ollamaChatClient.generate(prompt);
            log.info("RAG answer demo completed, contextCount: {}", contexts.size());
            return new RagAnswerResponse(true, safeQuestion, answer, contexts, prompt, "RAG回答成功");
        } catch (Exception e) {
            log.error("RAG answer demo failed, contextCount: {}, error: {}", contexts.size(), e.getMessage());
            return new RagAnswerResponse(false, safeQuestion, "AI 回答失败：" + e.getMessage(),
                    contexts, prompt, "大模型回答失败，已保留检索上下文用于排查");
        }
    }

    private void seedDemoDocuments() {
        demoDocuments().stream()
                .map(this::indexDocument)
                .forEach(documents::add);
    }

    private IndexedDocument indexDocument(DemoDocument document) {
        EmbeddingResult embedding = embedText(document.title() + "\n" + document.content());
        return new IndexedDocument(document, embedding.vector());
    }

    private EmbeddingResult embedText(String text) {
        EmbeddingResult embedding = embeddingClient.embed(text);
        lastEmbeddingSource = embedding.source();
        lastEmbeddingMessage = embedding.message();
        return embedding;
    }

    private static SearchHit toSearchHit(KnowledgeRetrievalHit hit) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", hit.sourceType());
        metadata.put("sourceName", hit.sourceName());
        if (hit.metadata() != null) {
            hit.metadata().forEach((key, value) -> metadata.put(key, String.valueOf(value)));
        }
        return new SearchHit(hit.id(), hit.title(), hit.content(), hit.score(), metadata);
    }

    private static SearchHit toSearchHit(IndexedDocument indexedDocument, double score) {
        DemoDocument document = indexedDocument.document();
        return new SearchHit(document.id(), document.title(), document.content(), round(score), document.metadata());
    }

    private static String buildRagPrompt(String question, List<SearchHit> contexts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个 RAG 问答助手。只允许依据【检索上下文】回答。\n");
        prompt.append("如果上下文不足以回答，请明确说无法从资料确认，不要编造。\n\n");
        prompt.append("【检索上下文】\n");
        for (int i = 0; i < contexts.size(); i++) {
            SearchHit hit = contexts.get(i);
            prompt.append(i + 1)
                    .append(". [")
                    .append(hit.id())
                    .append("] ")
                    .append(hit.title())
                    .append("\n")
                    .append(hit.content())
                    .append("\n相似度：")
                    .append(hit.score())
                    .append("\n\n");
        }
        prompt.append("【用户问题】\n").append(question).append("\n\n");
        prompt.append("【回答要求】\n");
        prompt.append("1. 先给出直接答案。\n");
        prompt.append("2. 再列出依据的资料编号。\n");
        prompt.append("3. 如果资料不足，明确说明缺口。");
        return prompt.toString();
    }

    private static List<DemoDocument> splitIntoChunks(String documentName, String content, String source) {
        List<String> chunks = buildChunkTexts(content);
        String docHash = Integer.toHexString((documentName + "\n" + content).hashCode());
        List<DemoDocument> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source", source);
            metadata.put("sourceType", SOURCE_TYPE_IMPORTED);
            metadata.put("documentName", documentName);
            metadata.put("chunkIndex", String.valueOf(i + 1));
            metadata.put("chunkTotal", String.valueOf(chunks.size()));
            metadata.put("vectorStore", "rag-workbench");
            documents.add(new DemoDocument(
                    "rag-" + docHash + "-chunk-" + (i + 1),
                    documentName + " / Chunk " + (i + 1),
                    chunks.get(i),
                    metadata
            ));
        }
        return documents;
    }

    private static List<String> buildChunkTexts(String content) {
        List<String> paragraphs = List.of(content.split("\\R\\s*\\R")).stream()
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
        if (paragraphs.isEmpty()) {
            paragraphs = List.of(content.trim());
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > TARGET_CHUNK_CHARS) {
                flushChunk(chunks, current);
                chunks.addAll(splitLongText(paragraph));
                continue;
            }
            if (!current.isEmpty() && current.length() + paragraph.length() + 2 > TARGET_CHUNK_CHARS) {
                flushChunk(chunks, current);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        flushChunk(chunks, current);
        return chunks.isEmpty() ? List.of(content.trim()) : chunks;
    }

    private static List<String> splitLongText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + TARGET_CHUNK_CHARS);
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    private static void flushChunk(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private void removeImportedDocument(String documentName) {
        documents.removeIf(document -> isImportedDocument(document)
                && documentName.equals(document.document().metadata().get("documentName")));
    }

    private List<VectorPoint> toVectorPoints(List<IndexedDocument> indexedDocuments) {
        return indexedDocuments.stream()
                .map(document -> new VectorPoint(
                        document.document().id(),
                        document.document().title(),
                        document.document().content(),
                        document.vector(),
                        document.document().metadata()
                ))
                .toList();
    }

    private int boundTopK(int topK) {
        int total = Math.max(1, documents.size());
        if (topK <= 0) {
            return Math.min(DEFAULT_TOP_K, total);
        }
        return Math.min(topK, total);
    }

    private int importedChunkCount() {
        return (int) documents.stream().filter(VectorRagDemoService::isImportedDocument).count();
    }

    private String activeStoreName() {
        QdrantVectorStoreClient.StoreStatus status = vectorStoreClient.status();
        return vectorStoreClient.enabled() && status.available() ? "qdrant" : "local";
    }

    private static boolean isImportedDocument(IndexedDocument document) {
        return SOURCE_TYPE_IMPORTED.equals(document.document().metadata().get("sourceType"));
    }

    private static double cosineSimilarity(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String defaultText(String value, String fallback) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .orElse(fallback);
    }

    private static List<DemoDocument> demoDocuments() {
        return List.of(
                new DemoDocument(
                        "vector-store-topk",
                        "向量检索 TopK 排序",
                        "向量检索先把文档切分为 chunk，再用 Embedding 生成向量。查询也会被转换成向量，通过余弦相似度排序，返回 TopK 相关片段。",
                        Map.of("demo", "vector-search", "source", SOURCE_LOCAL_SAMPLE, "sourceType", "sample")
                ),
                new DemoDocument(
                        "rag-grounded-answer",
                        "RAG 可信问答",
                        "RAG 会先进行向量检索，找到与问题相关的资料，再把资料作为检索上下文交给大模型。资料不足时应明确说明无法从资料确认，减少幻觉和编造。",
                        Map.of("demo", "rag-answer", "source", SOURCE_LOCAL_SAMPLE, "sourceType", "sample")
                ),
                new DemoDocument(
                        "prompt-template-guardrails",
                        "Prompt 约束与输出格式",
                        "稳定的 AI 应用需要清楚的角色、任务边界、输出格式和失败处理。结构化提示词能降低格式漂移，方便自动化测试和后续解析。",
                        Map.of("demo", "prompt", "source", SOURCE_LOCAL_SAMPLE, "sourceType", "sample")
                ),
                new DemoDocument(
                        "ollama-local-demo",
                        "本地 Ollama 调用",
                        "本项目通过 OllamaChatClient 调用本地 Ollama generate 接口。RAG demo 在检索完成后，把上下文和问题组装为 prompt，再交给本地模型生成回答。",
                        Map.of("demo", "ollama", "source", SOURCE_LOCAL_SAMPLE, "sourceType", "sample")
                )
        );
    }

    public record VectorSearchResponse(
            String query,
            int topK,
            List<SearchHit> matches,
            String vectorStore,
            String embeddingSource,
            String message) {
        public VectorSearchResponse(String query, int topK, List<SearchHit> matches) {
            this(query, topK, matches, "local", "local-hash", "本地向量索引检索成功。");
        }
    }

    public record RagAnswerResponse(
            boolean success,
            String question,
            String answer,
            List<SearchHit> contexts,
            String prompt,
            String message) {
    }

    public record KnowledgeIngestResponse(
            boolean success,
            String documentName,
            int chunkCount,
            int totalChunks,
            List<SearchHit> chunks,
            String vectorStore,
            String message) {
    }

    public record KnowledgeChunksResponse(int totalChunks, int importedChunks, List<SearchHit> chunks) {
    }

    public record KnowledgeClearResponse(int removedChunkCount, int totalChunks, String message) {
    }

    public record VectorStoreStatus(
            String activeStore,
            String provider,
            String qdrantUrl,
            String qdrantCollection,
            boolean qdrantAvailable,
            int totalChunks,
            int importedChunks,
            String embeddingSource,
            String embeddingMessage,
            String message) {
    }

    public record SearchHit(
            String id,
            String title,
            String content,
            double score,
            Map<String, String> metadata) {
    }

    private record DemoDocument(
            String id,
            String title,
            String content,
            Map<String, String> metadata) {
    }

    private record IndexedDocument(DemoDocument document, double[] vector) {
    }
}
