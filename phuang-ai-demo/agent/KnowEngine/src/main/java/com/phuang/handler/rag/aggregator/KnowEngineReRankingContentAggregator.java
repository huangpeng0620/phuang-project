package com.phuang.handler.rag.aggregator;

import com.phuang.model.dto.KnowEngineDefaultContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.DefaultContent;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.rag.content.ContentMetadata.RERANKED_SCORE;
import static java.util.Collections.emptyList;

/**
 * 使用 {@link ScoringModel}（例如 Cohere）对检索内容进行重排序的 {@link ContentAggregator} 实现。
 * <br>
 * {@link ScoringModel} 会根据一个选定的 {@link Query} 对所有 {@link Content} 进行评分。
 * 如果该聚合器接收到多个 {@link Query}
 * （例如使用 {@link ExpandingQueryTransformer} 扩展查询时），
 * 必须通过 {@link #querySelector} 选出一个查询，作为所有内容的重排序依据。
 * 也可以自行实现另一种策略：分别使用检索每组内容时对应的查询进行评分，
 * 再根据评分结果统一重排序，而不是让所有内容共用一个查询。
 * 当多个查询之间差异较大时，这种方式可能获得更好的结果，但调用成本也可能更高。
 * <br>
 * <br>
 * 调用 {@link ScoringModel} 之前，会先按照与 {@link DefaultContentAggregator} 相同的方式
 * 对所有 {@link Content} 进行融合，具体融合规则请参考其 Javadoc。
 * <br>
 * <br>
 * 可选配置参数：
 * <br>
 * - {@link #minScore}：允许返回的最低分数，低于该阈值的内容会被过滤。
 * <br>
 * - {@link #maxResults}：重排序后最多返回的内容数量。
 *
 * @see DefaultContentAggregator
 */
public class KnowEngineReRankingContentAggregator implements ContentAggregator {

    /**
     * 默认查询选择器：输入中只能包含一个查询，并将该查询作为重排序依据；
     * 如果存在多个查询，则无法判断应该使用哪个查询进行评分，直接抛出异常。
     */
    public static final Function<Map<Query, Collection<List<Content>>>, Query> DEFAULT_QUERY_SELECTOR =
            (queryToContents) -> {
                // 默认策略不负责处理多查询场景，调用方必须显式提供查询选择器
                if (queryToContents.size() > 1) {
                    throw illegalArgument(
                            "The 'queryToContents' contains %s queries, making the re-ranking ambiguous. " +
                                    "Because there are multiple queries, it is unclear which one should be " +
                                    "used for re-ranking. Please provide a 'querySelector' in the constructor/builder.",
                                    queryToContents.size()
                    );
                }
                // 单查询场景下直接返回唯一的查询
                return queryToContents.keySet().iterator().next();
            };

    /**
     * 用于计算文本片段与查询之间相关性分数的模型
     */
    private final ScoringModel scoringModel;

    /**
     * 从输入查询中选出一个查询，作为所有候选内容的统一重排序依据
     */
    private final Function<Map<Query, Collection<List<Content>>>, Query> querySelector;

    /**
     * 最低重排序分数；为 {@code null} 时不根据分数过滤内容。
     */
    private final Double minScore;

    /**
     * 最多返回的内容数量；未配置时不限制结果数量
     */
    private final Integer maxResults;

    /**
     * 创建仅指定评分模型的聚合器，使用默认查询选择器，不限制最低分数和返回数量。
     *
     * @param scoringModel 用于内容重排序的评分模型
     */
    public KnowEngineReRankingContentAggregator(ScoringModel scoringModel) {
        this(scoringModel, DEFAULT_QUERY_SELECTOR, null);
    }

    /**
     * 创建可指定查询选择器和最低分数的聚合器，返回数量默认不受限制。
     *
     * @param scoringModel  用于内容重排序的评分模型
     * @param querySelector 多查询场景下用于选择重排序查询的函数；为 {@code null} 时使用默认选择器
     * @param minScore      最低重排序分数；为 {@code null} 时不过滤低分内容
     */
    public KnowEngineReRankingContentAggregator(ScoringModel scoringModel,
                                                Function<Map<Query, Collection<List<Content>>>, Query> querySelector,
                                                Double minScore) {
        this(scoringModel, querySelector, minScore, null);
    }

    /**
     * 创建完整配置的内容聚合器，并对必填参数和可选参数的默认值进行初始化。
     *
     * @param scoringModel  用于内容重排序的评分模型，不能为空
     * @param querySelector 多查询场景下用于选择重排序查询的函数；为 {@code null} 时使用默认选择器
     * @param minScore      最低重排序分数；为 {@code null} 时不过滤低分内容
     * @param maxResults    最多返回的内容数量；为 {@code null} 时不限制结果数量
     */
    public KnowEngineReRankingContentAggregator(ScoringModel scoringModel,
                                                Function<Map<Query, Collection<List<Content>>>, Query> querySelector,
                                                Double minScore,
                                                Integer maxResults) {
        // 校验必填的评分模型，并为未配置的可选项设置默认值
        this.scoringModel = ensureNotNull(scoringModel, "scoringModel");
        this.querySelector = getOrDefault(querySelector, DEFAULT_QUERY_SELECTOR);
        this.minScore = minScore;
        this.maxResults = getOrDefault(maxResults, Integer.MAX_VALUE);
    }

    /**
     * 创建聚合器构建器，用于以链式调用方式配置评分模型、查询选择器及过滤条件。
     *
     * @return 新的聚合器构建器
     */
    public static ReRankingContentAggregatorBuilder builder() {
        return new ReRankingContentAggregatorBuilder();
    }

    /**
     * 聚合并重排序检索内容：先为每个查询融合来自不同数据源的结果，再跨查询进行二次融合，
     * 最后使用选定查询调用评分模型，对内容进行过滤、降序排列和数量截断。
     *
     * @param queryToContents 查询及其对应的多组检索结果
     * @return 完成融合、重排序和过滤后的内容列表
     */
    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        // 没有任何查询结果时，无需执行后续的融合和模型评分
        if (queryToContents.isEmpty()) {
            return emptyList();
        }

        // 选择一个查询，作为所有内容的重排序依据
        Query query = querySelector.apply(queryToContents);

        // 针对每个查询，融合通过该查询从不同数据源检索到的所有内容
        Map<Query, List<Content>> queryToFusedContents = fuse(queryToContents);

        // 转换为基于 EMBEDDING_ID 判断相等的内容对象，确保跨查询融合时可以识别重复片段
        List<List<KnowEngineDefaultContent>> knowEngineDefaultContents = queryToFusedContents.values().stream().map(contents -> {
            return contents.stream().map(content -> {
                return new KnowEngineDefaultContent((DefaultContent) content);
            }).toList();
        }).toList();

        // 没有生成任何待融合的结果列表时直接返回空结果
        if(knowEngineDefaultContents.isEmpty()){
            return emptyList();
        }

        // 将所有查询对应的内容再次进行统一融合
        List<Content> fusedContents = KnowEngineReciprocalRankFuser.fuse(knowEngineDefaultContents);

        // 所有候选内容均为空时，不再调用评分模型
        if (fusedContents.isEmpty()) {
            return fusedContents;
        }

        // 使用选定的查询对融合后的全部内容进行重排序和过滤
        return reRankAndFilter(fusedContents, query);
    }

    /**
     * 以查询为单位进行第一阶段融合：将同一个查询从不同检索数据源获得的多个结果列表，
     * 使用标准 RRF 算法合并为一个有序列表。
     *
     * @param queryToContents 查询及其对应的多组检索结果
     * @return 每个查询及其完成第一阶段融合后的结果列表
     */
    protected Map<Query, List<Content>> fuse(Map<Query, Collection<List<Content>>> queryToContents) {
        Map<Query, List<Content>> fused = new LinkedHashMap<>();
        for (Query query : queryToContents.keySet()) {
            Collection<List<Content>> contents = queryToContents.get(query);
            // 融合同一查询从不同数据源召回的结果，并保留查询与融合结果的对应关系
            fused.put(query, ReciprocalRankFuser.fuse(contents));
        }
        return fused;
    }

    /**
     * 使用评分模型计算所有候选文本与查询的相关性，对低于阈值的内容进行过滤，
     * 再按照相关性分数降序排列、写入重排序分数，并截取指定数量的结果。
     *
     * @param contents 完成 RRF 融合后的候选内容
     * @param query    用作相关性评分依据的查询
     * @return 根据模型分数过滤并重新排序后的内容列表
     */
    protected List<Content> reRankAndFilter(List<Content> contents, Query query) {

        // 提取候选内容中的文本片段，作为评分模型的批量输入
        List<TextSegment> segments = contents.stream()
                .map(Content::textSegment)
                .collect(Collectors.toList());

        // 批量计算每个文本片段与查询的相关性，返回分数顺序与输入片段顺序一致
        List<Double> scores = scoringModel.scoreAll(segments, query.text()).content();

        // 将文本片段与对应分数关联，供后续过滤和排序使用
        Map<TextSegment, Double> segmentToScore = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            segmentToScore.put(segments.get(i), scores.get(i));
        }

        // 依次执行最低分过滤、分数降序排列、分数元数据写入和结果数量截断
        return segmentToScore.entrySet().stream()
                .filter(entry -> minScore == null || entry.getValue() >= minScore)
                .sorted(Map.Entry.<TextSegment, Double>comparingByValue().reversed())
                .map(entry -> Content.from(entry.getKey(), Map.of(RERANKED_SCORE, entry.getValue())))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /** 用于以链式调用方式构建 {@link KnowEngineReRankingContentAggregator}。 */
    public static class ReRankingContentAggregatorBuilder {

        /** 用于内容重排序的评分模型。 */
        private ScoringModel scoringModel;

        /** 多查询场景下用于选择重排序查询的函数。 */
        private Function<Map<Query, Collection<List<Content>>>, Query> querySelector;

        /** 允许返回的最低重排序分数。 */
        private Double minScore;

        /** 重排序后最多返回的内容数量。 */
        private Integer maxResults;

        /** 限制外部直接实例化，统一通过 {@link KnowEngineReRankingContentAggregator#builder()} 创建。 */
        ReRankingContentAggregatorBuilder() {
        }

        /**
         * 设置用于内容重排序的评分模型。
         *
         * @param scoringModel 评分模型
         * @return 当前构建器
         */
        public ReRankingContentAggregatorBuilder scoringModel(ScoringModel scoringModel) {
            this.scoringModel = scoringModel;
            return this;
        }

        /**
         * 设置多查询场景下的重排序查询选择器。
         *
         * @param querySelector 查询选择器
         * @return 当前构建器
         */
        public ReRankingContentAggregatorBuilder querySelector(Function<Map<Query, Collection<List<Content>>>, Query> querySelector) {
            this.querySelector = querySelector;
            return this;
        }

        /**
         * 设置允许返回的最低重排序分数。
         *
         * @param minScore 最低分数
         * @return 当前构建器
         */
        public ReRankingContentAggregatorBuilder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * 设置重排序后最多返回的内容数量。
         *
         * @param maxResults 最大结果数量
         * @return 当前构建器
         */
        public ReRankingContentAggregatorBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * 根据当前配置创建内容聚合器，参数校验和默认值处理由聚合器构造方法完成。
         *
         * @return 配置完成的内容聚合器
         */
        public KnowEngineReRankingContentAggregator build() {
            return new KnowEngineReRankingContentAggregator(this.scoringModel, this.querySelector, this.minScore, this.maxResults);
        }
    }
}
