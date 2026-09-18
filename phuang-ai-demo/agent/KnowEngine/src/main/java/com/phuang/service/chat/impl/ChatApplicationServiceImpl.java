package com.phuang.service.chat.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.phuang.handler.memory.DatabaseChatMemoryStore;
import com.phuang.handler.rag.PromptHandler;
import com.phuang.handler.rag.aggregator.BgeScoringModel;
import com.phuang.handler.rag.aggregator.KnowEngineHybridContentAggregator;
import com.phuang.handler.rag.aggregator.KnowEngineReRankingContentAggregator;
import com.phuang.handler.rag.aggregator.ProgressAwareContentAggregator;
import com.phuang.handler.rag.retriever.KnowEngineElasticsearchContentRetriever;
import com.phuang.handler.rag.retriever.KnowEngineNeo4jContentRetriever;
import com.phuang.handler.rag.retriever.KnowEngineSqlDatabaseContentRetriever;
import com.phuang.handler.rag.retriever.ProgressAwareContentRetriever;
import com.phuang.handler.rag.router.KnowEngineQueryRouter;
import com.phuang.handler.rag.transformer.KnowEngineQueryTransformer;
import com.phuang.model.dto.ChatParam;
import com.phuang.model.dto.IntentRecognitionResult;
import com.phuang.model.dto.PendingClarification;
import com.phuang.model.entity.MyCarEntity;
import com.phuang.model.entity.TableMeta;
import com.phuang.model.enums.ChatSource;
import com.phuang.model.enums.RoleEnum;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.KnowEngineTableMetaService;
import com.phuang.service.KnowledgeSegmentService;
import com.phuang.service.MyCarService;
import com.phuang.service.UserRoleService;
import com.phuang.service.ai.CommonChatService;
import com.phuang.service.ai.IntentRecognitionService;
import com.phuang.service.ai.KnowEngineChatAiService;
import com.phuang.service.ai.TitleSummaryService;
import com.phuang.service.chat.ChatApplicationService;
import com.phuang.service.chat.ChatConversationService;
import com.phuang.service.chat.ChatMessageService;
import com.phuang.util.DocumentPermissionUtils;
import dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraph;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import dev.langchain4j.store.embedding.filter.Filter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.phuang.config.ElasticSearchConfiguration.INDEX_NAME;
import static com.phuang.model.constant.MetadataKeyConstant.ACCESSIBLE_BY;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @description ChatApplicationServiceImpl
 * @author huangpeng
 * @since 2026/9/6
 */
@Service
@Slf4j
public class ChatApplicationServiceImpl implements ChatApplicationService {

    @Resource
    private ChatModel chatModel;

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private DatabaseChatMemoryStore databaseChatMemoryStore;

    @Resource
    private CommonChatService commonChatService;

    @Resource
    private PromptHandler promptHandler;

    @Resource
    private RestClient restClient;

    @Resource
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    @Resource
    private KnowledgeSegmentService knowledgeSegmentService;

    @Resource
    private DataSource dataSource;

    @Resource
    private MyCarService myCarService;

    @Resource
    private UserRoleService userRoleService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KnowEngineTableMetaService knowEngineTableMetaService;

    @Resource
    private Driver neo4jDriver;

    private static final String CLARIFICATION_KEY_PREFIX = "know-engine:chat-clarification:";

    private static final long CLARIFICATION_TTL_MINUTES = 15;

    private IntentRecognitionService intentRecognitionService;

    /**
     * RAG 对话标题生成专用 model,采用轻量级模型,速度较快
     */
    private OpenAiChatModel titleChatModel;

    /**
     * RAG 对话生成专用 ChatModel，使用更强的模型和较低温度以提升回答质量
     */
    private StreamingChatModel ragChatModel;

    @Value("classpath:prompts/text-to-sql-prompt.txt")
    private org.springframework.core.io.Resource textToSqlPrompt;

    @Value("classpath:prompts/text-to-cypher-prompt.txt")
    private org.springframework.core.io.Resource textToCypherPrompt;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    @Value("classpath:sql/retrieve_tables.sql")
    private org.springframework.core.io.Resource tablesSql;

    @PostConstruct
    public void init() {
        ragChatModel = OpenAiStreamingChatModel.builder()
                .apiKey(chatModelApiKey)
                .baseUrl(chatModelBaseUrl)
                .modelName("qwen3.6-plus")
                .logRequests(true)
                .logRequests(true)
                .temperature(0.2)
                .topP(0.9)
                .customParameters(Map.of("enable_thinking", false))
                .build();
        titleChatModel = OpenAiChatModel.builder()
                .apiKey(chatModelApiKey)
                .modelName("qwen3.5-flash")
                .temperature(0.7)
                .baseUrl(chatModelBaseUrl)
                .customParameters(Map.of("enable_thinking", false))
                .build();
        intentRecognitionService = AiServices.builder(IntentRecognitionService.class).chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(10)
                        .chatMemoryStore(databaseChatMemoryStore).build())
                .build();
        log.info("成功初始化 ragChatModel、titleChatModel");
    }

    /**
     * 流式会话接口
     * @return
     */
    @Override
    public Flux<String> chat(String userId, String content, String conversationId, ChatSource chatSource) {
        if (StrUtil.isNotEmpty(conversationId) && chatConversationService.checkUserConversation(conversationId, userId)) {
            throw new BusinessException("会话任务异常");
        }
        final String finalConversationId;
        if (StrUtil.isEmpty(conversationId)) {
            //临时会话标题
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            //创建新用户会话
            finalConversationId = chatConversationService.createConversation(userId, tempTitle);
            log.info("创建新会话: conversationId:{}, tempTitle:{}", finalConversationId, tempTitle);

            /**
             * 异步采用虚拟线程调用 LLM 生成摘要标题,完成后回写到数据库
             * <P>
             *     目前只在会话首次进行标题生成,后期可以进一步优化为【可根据后续用户问题进行持续更新】
             * </P>
             */
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    TitleSummaryService titleSummaryService = AiServices.builder(TitleSummaryService.class)
                            .chatModel(titleChatModel)
                            .build();
                    String aiTitle = titleSummaryService.generateTitle(content);
                    chatConversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("异步标题更新完成: conversationId:{}, title:{}", finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.error("异步标题生成失败, 保留临时标题: conversationId:{}", finalConversationId, e);
                }
            });
        } else {
            finalConversationId = conversationId;
        }
        //保存消息记录
        String messageId = chatMessageService.saveUserMessage(finalConversationId, content);
        String aiMessageId = chatMessageService.saveAssistantMessage(finalConversationId);

        PendingClarification pending = getPendingClarification(finalConversationId);
        String question = content;
        if (pending != null && userId.equals(pending.getUserId())) {
            if ("取消".equals(content.trim())) {
                clearPendingClarification(finalConversationId);
                return textResponse(aiMessageId, "已取消上一个问题，请重新输入您想咨询的问题。")
                        .concatWith(Mono.just("[DONE]:" + finalConversationId));
            }

            if ("car_id".equals(pending.getClarificationField()) && !CollectionUtils.isEmpty(pending.getCandidateCarIds())) {
                MyCarEntity selectedCar = resolveCarByIndex(content, pending.getCandidateCarIds(),
                        myCarService.getCarByUserId(userId));
                if (selectedCar == null) {
                    return textResponse(aiMessageId, "没有识别到您选择的车辆，请回复列表中的有效序号。")
                            .concatWith(Mono.just("[DONE]:" + finalConversationId));
                }
                question = withVehicleContext(pending.getOriginalContent(), selectedCar);
            } else {
                question = pending.getOriginalContent() + "\n\n用户补充信息：" + content.trim();
            }
            clearPendingClarification(finalConversationId);
        }

        return processQuestion(userId, question, finalConversationId, messageId, aiMessageId, chatSource)
                .doOnError(e -> log.error("流式对话异常,conversationId:{}", finalConversationId, e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }

    /**
     * 对完整问题执行意图识别，并统一分流到澄清、普通对话或 RAG
     */
    private Flux<String> processQuestion(String userId,
                                         String content,
                                         String conversationId,
                                         String messageId,
                                         String assistantMessageId,
                                         ChatSource chatSource) {
        return Flux.just("[PROGRESS]:正在识别您的意图...")
                .concatWith(Mono.fromCallable(() -> intentRecognitionService.chat(conversationId, content))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(intentResult -> {
                            databaseChatMemoryStore.evictCache(conversationId);
                            ChatParam chatParam = ChatParam.builder()
                                    .userId(userId)
                                    .conversationId(conversationId)
                                    .messageId(messageId)
                                    .content(content)
                                    .assistantMessageId(assistantMessageId)
                                    .intentRecognitionResult(intentResult)
                                    .chatSource(chatSource)
                                    .build();
                            if (intentResult.needClarification() && StrUtil.isNotBlank(intentResult.clarificationQuestion())) {
                                return clarify(chatParam);
                            }
                            if (!intentResult.related()) {
                                StringBuilder contentBuilder = new StringBuilder();
                                return Flux.concat(Flux.just("[PROGRESS]:正在为您生成回答..."),
                                        commonChatService.streamChat(userId, content)
                                                .doOnNext(contentBuilder::append)
                                                .doOnComplete(() -> chatMessageService.updateContent(assistantMessageId, contentBuilder.toString())));
                            }
                            return doChat(chatParam);
                        }));
    }

    /**
     * 用户问题澄清分支
     *     <P>
     *       通用澄清场景仅返回普通文本, 只有 car_id 场景会附加用户车辆编号列表
     *     </P>
     * @param chatParam
     * @return
     */
    private Flux<String> clarify(ChatParam chatParam) {
        IntentRecognitionResult intentResult = chatParam.getIntentRecognitionResult();
        List<MyCarEntity> myCars = null;

        if (chatParam.getChatSource() == ChatSource.USER_WEB && "car_id".equals(intentResult.clarificationField())) {
            myCars = myCarService.getCarByUserId(chatParam.getUserId());
            if (CollectionUtils.isEmpty(myCars)) {
                return textResponse(chatParam.getAssistantMessageId(), "您还没有添加车辆信息，请先添加车辆信息。");
            }
            if (myCars.size() == 1) {
                chatParam.setContent(withVehicleContext(chatParam.getContent(), myCars.getFirst()));
                return doChat(chatParam);
            }
        }

        PendingClarification pending = PendingClarification.builder()
                .userId(chatParam.getUserId())
                .originalContent(chatParam.getContent())
                .clarificationField(StrUtil.blankToDefault(intentResult.clarificationField(), "other"))
                .candidateCarIds(myCars == null ? null : myCars.stream().map(MyCarEntity::getCarId).toList())
                .build();
        if (!savePendingClarification(chatParam.getConversationId(), pending)) {
            return textResponse(chatParam.getAssistantMessageId(), "暂时无法记录补充信息，请稍后重新提问。");
        }

        String question = intentResult.clarificationQuestion();
        if (!CollectionUtils.isEmpty(myCars)) {
            question += "\n\n" + buildVehicleChoiceText(myCars);
        }
        return textResponse(chatParam.getAssistantMessageId(), question);
    }

    /**
     * 根据用户回复的编号解析车辆，并再次确认车辆仍属于当前用户。
     */
    MyCarEntity resolveCarByIndex(String answer, List<String> candidateCarIds, List<MyCarEntity> currentCars) {
        if (StrUtil.isBlank(answer) || CollectionUtils.isEmpty(candidateCarIds) || CollectionUtils.isEmpty(currentCars)) {
            return null;
        }
        try {
            int index = Integer.parseInt(answer.trim()) - 1;
            if (index < 0 || index >= candidateCarIds.size()) {
                return null;
            }
            String selectedCarId = candidateCarIds.get(index);
            return currentCars.stream()
                    .filter(car -> selectedCarId.equals(car.getCarId()))
                    .findFirst()
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 生成普通 Markdown 文本格式的车辆编号列表
     */
    private String buildVehicleChoiceText(List<MyCarEntity> cars) {
        StringBuilder text = new StringBuilder("请选择车辆并回复序号：\n\n");
        for (int i = 0; i < cars.size(); i++) {
            MyCarEntity car = cars.get(i);
            text.append(i + 1).append(". ").append(vehicleName(car));
            if (StrUtil.isNotBlank(car.getPlateNumber())) {
                text.append("（").append(car.getPlateNumber()).append("）");
            }
            text.append('\n');
        }
        return text.append("\n如需放弃，请回复“取消”。").toString();
    }

    /**
     * 将已确认的车辆信息追加到原问题，供下一次意图识别和 RAG 使用
     */
    private String withVehicleContext(String originalContent, MyCarEntity car) {
        StringBuilder content = new StringBuilder(originalContent)
                .append("\n\n已确认车辆：").append(vehicleName(car))
                .append("；车辆ID：").append(car.getCarId());
        if (StrUtil.isNotBlank(car.getCarInfoId())) {
            content.append("；车型ID：").append(car.getCarInfoId());
        }
        return content.toString();
    }

    private String vehicleName(MyCarEntity car) {
        if (StrUtil.isNotBlank(car.getFullName())) {
            return car.getFullName();
        }
        if (StrUtil.isNotBlank(car.getNickname())) {
            return car.getNickname();
        }
        return "车辆 " + car.getCarId();
    }

    private Flux<String> textResponse(String assistantMessageId, String content) {
        chatMessageService.updateContent(assistantMessageId, content);
        return Flux.just(content);
    }

    private PendingClarification getPendingClarification(String conversationId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(CLARIFICATION_KEY_PREFIX + conversationId);
            return StrUtil.isBlank(json) ? null : JSON.parseObject(json, PendingClarification.class);
        } catch (Exception e) {
            log.error("读取澄清状态失败, conversationId:{}", conversationId, e);
            return null;
        }
    }

    private boolean savePendingClarification(String conversationId, PendingClarification pending) {
        try {
            stringRedisTemplate.opsForValue().set(CLARIFICATION_KEY_PREFIX + conversationId,
                    JSON.toJSONString(pending), CLARIFICATION_TTL_MINUTES, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            log.error("保存澄清状态失败, conversationId:{}", conversationId, e);
            return false;
        }
    }

    private void clearPendingClarification(String conversationId) {
        try {
            stringRedisTemplate.delete(CLARIFICATION_KEY_PREFIX + conversationId);
        } catch (Exception e) {
            log.error("清除澄清状态失败, conversationId:{}", conversationId, e);
        }
    }

    /**
     * 流式对话
     * <p>
     * 使用 Flux.create() 将 RAG 管道各环节的进度消息与 LLM 流式输出桥接到同一个 Flux 中，
     * 确保进度消息在对应的 LLM token 之前到达前端。
     * <p>
     * 进度推送环节：
     * <ol>
     *   <li>问题改写 — 由 {@link KnowEngineQueryTransformer} 发送</li>
     *   <li>问题路由 — 由 {@link KnowEngineQueryRouter} 发送</li>
     *   <li>排序筛选 — 由 {@link ProgressAwareContentAggregator} 发送</li>
     *   <li>生成回答 — 由 {@link ProgressAwareContentAggregator} 在聚合完成后发送</li>
     * </ol>
     *
     * @param chatParam 对话参数
     */
    public Flux<String> doChat(ChatParam chatParam) {
        return Flux.<String>create(sink -> {
                    Consumer<String> processCallback = sink::next;

                    //构造权限过滤
                    Filter accessibleByFilter = buildFilter(chatParam);

                    //构建查询改写器
                    KnowEngineQueryTransformer knowEngineQueryTransformer = KnowEngineQueryTransformer.builder()
                            .chatModel(chatModel)
                            .chatMessageId(chatParam.getMessageId())
                            .progressCallback(processCallback)
                            .build();

                    // 构建向量检索器
                    ProgressAwareContentRetriever embeddingRetriever = ProgressAwareContentRetriever.builder()
                            .delegate(KnowEngineElasticsearchContentRetriever.builder()
                                    .configuration(ElasticsearchConfigurationKnn.builder().build())
                                    .maxResults(5)
                                    .minScore(0.5)
                                    .embeddingModel(openAiEmbeddingModel)
                                    .restClient(restClient)
                                    .indexName(INDEX_NAME)
                                    .knowledgeSegmentService(knowledgeSegmentService)
                                    .filter(accessibleByFilter)
                                    .build())
                            .progressCallback(processCallback)
                            .build();

                    // 构建全文检索器
                    ProgressAwareContentRetriever fullTextRetriever = ProgressAwareContentRetriever.builder()
                            .delegate(KnowEngineElasticsearchContentRetriever.builder()
                                    .configuration(ElasticsearchConfigurationFullText.builder().build())
                                    .restClient(restClient)
                                    .embeddingModel(openAiEmbeddingModel)
                                    .knowledgeSegmentService(knowledgeSegmentService)
                                    .indexName(INDEX_NAME)
                                    .filter(accessibleByFilter)
                                    .maxResults(5)
                                    .build())
                            .progressCallback(processCallback)
                            .build();

                    //构建sql数据检索器
                    ProgressAwareContentRetriever sqlRetriever = null;
                    try {
                        //构建表结构数据
                        String databaseStructure = buildDatabaseStructure();
                        sqlRetriever = ProgressAwareContentRetriever.builder()
                                .delegate(KnowEngineSqlDatabaseContentRetriever.builder()
                                        .dataSource(dataSource)
                                        .promptTemplate(new PromptTemplate(textToSqlPrompt.getContentAsString(StandardCharsets.UTF_8)))
                                        .databaseStructure(databaseStructure)
                                        .chatModel(chatModel)
                                        .fallbackRetriever(embeddingRetriever)
                                        .userId(chatParam.getUserId())
                                        .build())
                                .progressCallback(processCallback)
                                .build();
                    } catch (IOException e) {
                        log.error("Error creating SQL retriever", e);
                    }

                    //构建图数据库检索器
                    ProgressAwareContentRetriever neo4jRetriever = null;
                    try {
                        neo4jRetriever = new ProgressAwareContentRetriever(
                                KnowEngineNeo4jContentRetriever.builder()
                                        .graph(Neo4jGraph.builder()
                                                .driver(neo4jDriver)
                                                .build())
                                        .chatModel(chatModel)
                                        .promptTemplate(new PromptTemplate(textToCypherPrompt.getContentAsString(UTF_8)))
                                        .fallbackRetriever(embeddingRetriever)
                                        .userId(chatParam.getUserId())
                                        .build(), processCallback);
                    } catch (IOException e) {
                        log.warn("Error creating Neo4j retriever", e);
                    }

                    // 构建查询路由器
                    KnowEngineQueryRouter knowEngineQueryRouter = new KnowEngineQueryRouter(Lists.newArrayList(embeddingRetriever, fullTextRetriever, sqlRetriever, neo4jRetriever),
                            chatModel, processCallback);

                    //构造融合重排序器(ProgressAwareContentAggregator -> KnowEngineHybridContentAggregator -> KnowEngineReRankingContentAggregator)
                    ProgressAwareContentAggregator knowEngineReRankingContentAggregator = ProgressAwareContentAggregator.builder()
                            .assistantMessageId(chatParam.getAssistantMessageId())
                            .progressCallback(processCallback)
                            .chatMessageService(chatMessageService)
                            .delegate(KnowEngineHybridContentAggregator.builder()
                                    .unstructuredAggregator(KnowEngineReRankingContentAggregator.builder()
                                            .scoringModel(BgeScoringModel.getInstance())
                                            .minScore(0.6)
                                            .maxResults(5)
                                            .querySelector(queryToContents -> queryToContents.keySet().iterator().next())
                                            .build())
                                    .build())
                            .build();

                    //构造上下文融合器
                    ContentInjector contentInjector = new DefaultContentInjector();

                    // 组装 RAG 流程编排器
                    RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                            .queryTransformer(knowEngineQueryTransformer)
                            .queryRouter(knowEngineQueryRouter)
                            .contentAggregator(knowEngineReRankingContentAggregator)
                            .contentInjector(contentInjector)
                            .build();

                    //获取上下文融合提示词
                    String prompt = promptHandler.getPrompt(chatParam.getIntentRecognitionResult());

                    KnowEngineChatAiService knowEngineChatAiService = AiServices.builder(KnowEngineChatAiService.class)
                            .streamingChatModel(ragChatModel)
                            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                                    .id(memoryId)
                                    .maxMessages(10)
                                    .chatMemoryStore(databaseChatMemoryStore)
                                    .build())
                            .systemMessage(prompt)
                            .retrievalAugmentor(retrievalAugmentor)
                            .build();

                    //订阅 LLM 流式输出，桥接到 sink
                    AtomicBoolean firstToken = new AtomicBoolean(Boolean.TRUE);
                    StringBuilder contentBuilder = new StringBuilder();
                    Disposable disposable = knowEngineChatAiService.streamChat(chatParam.getConversationId(), chatParam.getContent())
                            .doOnNext(token -> {
                                // 首个 token 到达时，如果之前没有发出"正在生成回答"，则补发
                                // （正常情况下由 ProgressAwareContentAggregator 已发出，此处为兜底）
                                if (firstToken.compareAndSet(true, false)) {
                                    // 标记已开始接收 token
                                }
                                contentBuilder.append(token);
                            })
                            .doOnComplete(() -> chatMessageService.updateContent(chatParam.getAssistantMessageId(), contentBuilder.toString()))
                            .subscribe(sink::next, sink::error, sink::complete);

                    // 取消时同步取消内部订阅
                    sink.onCancel(disposable::dispose);
                }).subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.parallel());
    }

    /**
     * 构建数据库结构描述
     * <p>
     *   数据库结构的来源:
     *     1.当前系统已存在的系统业务表结构(通过 classpath:sql/retrieve_tables.sql 维护)
     *     2.用户自定义上传的用于数据检索的动态表结构(通过 table_meta 元数据表维护 )
     *  将两者合并作为 Text2SQL Prompt 的 databaseStructure 参数，使 LLM 感知所有可查询的表
     * </p>
     */
    private String buildDatabaseStructure() throws IOException {
        StringBuilder sb = new StringBuilder();
        // 静态表结构
        sb.append(tablesSql.getContentAsString(UTF_8));
        // 从 table_meta 读取当前激活版本对应的动态表结构
        List<TableMeta> tableMetas = knowEngineTableMetaService.listActiveForQuery();
        if (CollectionUtil.isNotEmpty(tableMetas)) {
            sb.append("\n\n");
            String dynamicSql = tableMetas.stream()
                    .filter(meta -> StrUtil.isNotEmpty(meta.getCreateSql()))
                    .map(TableMeta::getCreateSql)
                    .collect(Collectors.joining("\n\n"));
            sb.append(dynamicSql);
        }
        return sb.toString();
    }

    /**
     * 构造权限过滤器
     *
     * @param chatParam
     * @return
     */
    private Filter buildFilter(ChatParam chatParam) {
        // 默认权限过滤器：允许访客权限
        Filter permissionFilter = metadataKey(ACCESSIBLE_BY).isEqualTo(RoleEnum.VISITOR.name());

        // 根据用户角色获取权限
        RoleEnum roleEnum = userRoleService.getUserRole(chatParam);

        // 获取该文档支持的所有权限
        String[] permissions = DocumentPermissionUtils.getDocumentAccessiblePermission(roleEnum);
        for (String permission : permissions) {
            // 非访客权限时，将权限用or连接，表示支持多种权限
            if (!RoleEnum.VISITOR.name().equals(permission)) {
                permissionFilter = permissionFilter.or(metadataKey(ACCESSIBLE_BY).isEqualTo(permission));
            }
        }
        return permissionFilter;
    }
}
