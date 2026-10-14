package com.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.ai.AiClient;
import com.rag.config.RagProperties;
import com.rag.entity.KnowledgeChunk;
import com.rag.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RAG 检索模块。针对 2C2G 服务器做了取舍：
 * 不引入独立向量数据库，向量以 LONGBLOB 存 MySQL，
 * 全量加载进 JVM 内存做暴力余弦 Top-K（万级 chunk 规模下单次检索毫秒级）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    public record Hit(String docTitle, String content, double score) {
    }

    private record CachedChunk(String docTitle, String content, float[] vector) {
    }

    private final KnowledgeChunkMapper chunkMapper;
    private final AiClient aiClient;
    private final RagProperties props;

    /** 内存向量缓存，知识库变更后失效重建 */
    private volatile List<CachedChunk> cache;

    /** 知识文档入库：切片 → 调 API 向量化 → 存储 */
    public synchronized int ingest(String title, String content) {
        List<String> pieces = chunk(content);
        List<float[]> vectors = aiClient.embed(pieces);
        for (int i = 0; i < pieces.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocTitle(title);
            chunk.setChunkIndex(i);
            chunk.setContent(pieces.get(i));
            chunk.setEmbedding(toBytes(vectors.get(i)));
            chunkMapper.insert(chunk);
        }
        invalidate();
        return pieces.size();
    }

    /** 为历史无向量的切片补齐向量（种子数据导入后执行一次） */
    public synchronized int embedAll() {
        List<KnowledgeChunk> missing = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>().isNull(KnowledgeChunk::getEmbedding));
        int count = 0;
        for (int start = 0; start < missing.size(); start += 16) {
            List<KnowledgeChunk> batch = missing.subList(start, Math.min(start + 16, missing.size()));
            List<float[]> vectors = aiClient.embed(batch.stream().map(KnowledgeChunk::getContent).toList());
            for (int i = 0; i < batch.size(); i++) {
                KnowledgeChunk chunk = batch.get(i);
                chunk.setEmbedding(toBytes(vectors.get(i)));
                chunkMapper.updateById(chunk);
                count++;
            }
        }
        invalidate();
        return count;
    }

    /** 检索 Top-K 相关知识切片 */
    public List<Hit> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<CachedChunk> chunks = loadCache();
        if (chunks.isEmpty()) {
            return List.of();
        }
        float[] queryVector = aiClient.embed(query);
        List<Hit> hits = new ArrayList<>();
        for (CachedChunk chunk : chunks) {
            double score = cosine(queryVector, chunk.vector());
            if (score >= props.getMinScore()) {
                hits.add(new Hit(chunk.docTitle(), chunk.content(), score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > props.getTopK() ? hits.subList(0, props.getTopK()) : hits;
    }

    /** 知识库统计：标题 → [切片数, 已向量化数] */
    public List<KnowledgeChunk> allChunks() {
        return chunkMapper.selectList(null);
    }

    public synchronized void invalidate() {
        cache = null;
    }

    private synchronized List<CachedChunk> loadCache() {
        List<CachedChunk> local = cache;
        if (local != null) {
            return local;
        }
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>().isNotNull(KnowledgeChunk::getEmbedding));
        local = chunks.stream()
                .map(c -> new CachedChunk(c.getDocTitle(), c.getContent(), toFloats(c.getEmbedding())))
                .toList();
        cache = local;
        log.info("知识向量缓存已加载：{} 条切片", local.size());
        return local;
    }

    /** 按固定窗口切片（带重叠），中文友好 */
    private List<String> chunk(String content) {
        String normalized = content.replaceAll("\\r\\n", "\n").trim();
        List<String> pieces = new ArrayList<>();
        int size = props.getChunkSize();
        int step = Math.max(size - props.getChunkOverlap(), 1);
        for (int start = 0; start < normalized.length() && pieces.size() < 50; start += step) {
            int end = Math.min(start + size, normalized.length());
            pieces.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
        return pieces;
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * vector.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private static float[] toFloats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
