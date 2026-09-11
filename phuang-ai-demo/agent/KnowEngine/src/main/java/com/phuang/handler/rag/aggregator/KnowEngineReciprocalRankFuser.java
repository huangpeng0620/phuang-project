package com.phuang.handler.rag.aggregator;

import com.phuang.model.dto.KnowEngineDefaultContent;
import dev.langchain4j.rag.content.Content;

import java.util.*;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

/**
 * 倒数排名融合（Reciprocal Rank Fusion，RRF）算法的实现
 * <br>
 * 完整原理说明可参考
 * <a href="https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking">此文档</a>
 */
public class KnowEngineReciprocalRankFuser {

    /**
     * 使用倒数排名融合（RRF）算法将多个内容列表融合为一个内容列表，默认使用 {@code k = 60}
     *
     * @param listsOfContents 待融合的内容列表集合
     * @return 融合并按照融合分数降序排列后的内容列表
     */
    public static List<Content> fuse(Collection<List<KnowEngineDefaultContent>> listsOfContents) {
        return fuse(listsOfContents, 60);
    }

    /**
     * 使用倒数排名融合（RRF）算法将多个内容列表融合为一个内容列表
     * <P>
     *     包装成 KnowEngineDefaultContent 对象是为了通过其文档的 EMBEDDING_ID 进行去重
     * </P>
     * @param listsOfContents 待融合的内容列表集合
     * @param k               排名平滑常量，用于控制各列表中排名差异对融合分数的影响
     *                        根据经验通常取 60，但最优值会随具体应用和数据特征而变化
     *                        {@code k} 越大，不同名次之间的分数差距越小，融合分数越均匀
     *                        {@code k} 越小，各列表中排名靠前的内容影响越大
     *                        {@code k} 必须大于或等于 1
     * @return 融合并按照融合分数降序排列后的内容列表
     */
    public static List<Content> fuse(Collection<List<KnowEngineDefaultContent>> listsOfContents, int k) {
        ensureBetween(k, 1, Integer.MAX_VALUE, "k");
        Map<Content, Double> scores = new LinkedHashMap<>();
        for (List<KnowEngineDefaultContent> singleListOfContent : listsOfContents) {
            for (int i = 0; i < singleListOfContent.size(); i++) {
                Content content = singleListOfContent.get(i);
                double currentScore = scores.getOrDefault(content, 0.0);
                int rank = i + 1;
                double newScore = currentScore + 1.0 / (k + rank);
                scores.put(content, newScore);
            }
        }
        List<Content> fused = new ArrayList<>(scores.keySet());
        fused.sort(Comparator.comparingDouble(scores::get).reversed());
        return fused;
    }
}
