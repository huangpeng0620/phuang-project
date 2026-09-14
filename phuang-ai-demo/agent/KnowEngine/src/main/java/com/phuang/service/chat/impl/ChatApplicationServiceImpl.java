package com.phuang.service.chat.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.phuang.handler.converter.CarInfoConverter;
import com.phuang.handler.converter.MyCarConverter;
import com.phuang.handler.memory.DatabaseChatMemoryStore;
import com.phuang.handler.rag.PromptHandler;
import com.phuang.handler.rag.aggregator.BgeScoringModel;
import com.phuang.handler.rag.aggregator.KnowEngineHybridContentAggregator;
import com.phuang.handler.rag.aggregator.KnowEngineReRankingContentAggregator;
import com.phuang.handler.rag.aggregator.ProgressAwareContentAggregator;
import com.phuang.handler.rag.retriever.KnowEngineElasticsearchContentRetriever;
import com.phuang.handler.rag.retriever.KnowEngineSqlDatabaseContentRetriever;
import com.phuang.handler.rag.retriever.ProgressAwareContentRetriever;
import com.phuang.handler.rag.router.KnowEngineQueryRouter;
import com.phuang.handler.rag.transformer.KnowEngineQueryTransformer;
import com.phuang.model.dto.ChatParam;
import com.phuang.model.entity.CarInfoEntity;
import com.phuang.model.entity.MyCarEntity;
import com.phuang.model.enums.ChatSource;
import com.phuang.model.enums.KnowEngineIntent;
import com.phuang.model.enums.RoleEnum;
import com.phuang.service.CarInfoService;
import com.phuang.service.KnowledgeSegmentService;
import com.phuang.service.MyCarService;
import com.phuang.service.ai.CommonChatService;
import com.phuang.service.ai.IntentRecognitionService;
import com.phuang.service.ai.KnowEngineChatAiService;
import com.phuang.service.ai.TitleSummaryService;
import com.phuang.service.chat.ChatApplicationService;
import com.phuang.service.chat.ChatConversationService;
import com.phuang.service.chat.ChatMessageService;
import com.phuang.util.DocumentPermissionUtils;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.phuang.config.ElasticSearchConfiguration.INDEX_NAME;
import static com.phuang.model.constant.MetadataKeyConstant.ACCESSIBLE_BY;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

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
    private CarInfoService carInfoService;

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

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

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
        String messageId = chatMessageService.saveUserMessage(conversationId, content);
        String aiMessageId = chatMessageService.saveAssistantMessage(conversationId);

        //进入流式对话
        return Flux.just("[PROGRESS]:正在识别您的意图...")
                .concatWith(Mono.fromCallable(() -> {
                            // 调用LLM意图识别
                            return intentRecognitionService.chat(conversationId, content);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(intentRecognitionResult -> {

                            // 意图识别完成后清除缓存，避免意图识别的AI响应污染后续RAG对话的历史记忆
                            databaseChatMemoryStore.evictCache(finalConversationId);

                            if (!intentRecognitionResult.related()) {
                                //使用通用大模型进行对话
                                StringBuilder contentBuilder = new StringBuilder();
                                return Flux.concat(Flux.just("[PROGRESS]:正在为您生成回答..."),
                                        commonChatService.streamChat(userId, content)
                                                .doOnNext(token -> contentBuilder.append(token))
                                                .doOnComplete(() -> chatMessageService.updateContent(aiMessageId, contentBuilder.toString())));
                            }
                            // 进入RAG流程（进度由内部组件发出）
                            return ragChat(ChatParam.builder()
                                    .userId(userId)
                                    .conversationId(finalConversationId)
                                    .messageId(messageId)
                                    .content(content)
                                    .assistantMessageId(aiMessageId)
                                    .intentRecognitionResult(intentRecognitionResult)
                                    .chatSource(chatSource)
                                    .build());
                        }))
                .doOnError(e -> log.error("流式对话异常,conversationId:{}", finalConversationId, e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }

    /**
     * 进入RAG流式对话
     * <p>
     *      1. 根据意图识别结果，判断是否需要车辆信息
     *      2. 如果车辆信息不完善，则返回车辆信息不完善提示
     *      3. 根据意图识别结果，判断是否需要车辆信息
     * </p>
     * @param chatParam
     * @return
     */
    public Flux<String> ragChat(ChatParam chatParam) {
        KnowEngineIntent intent = KnowEngineIntent.getIntent(chatParam.getIntentRecognitionResult());
        /**
         * 只有用户通过网页端访问时，才需要车辆信息
         */
        if(chatParam.getChatSource() == ChatSource.USER_WEB){
            // 如果是维保服务、技术支持，则需要车辆信息
            if (intent == KnowEngineIntent.CAR_MAINTENANCE || intent == KnowEngineIntent.CAR_TECH_SUPPORT) {
                if (chatParam.getIntentRecognitionResult().entities().car_id() == null) {
                    List<MyCarEntity> myCars = myCarService.getCarByUserId(chatParam.getUserId());
                    if (CollectionUtils.isEmpty(myCars)) {
                        return Flux.just("[WARN]:您还没有添加车辆信息，请先添加车辆信息");
                    } else if (myCars.size() >= 1) {
                        return Flux.just("[CARD]:请先选择车辆")
                                .concatWith(Flux.just("[CARD_CHOICE_MYCAR]:" + JSON.toJSONString(MyCarConverter.INSTANCE.toVOList(myCars))));
                    }
                }
            }

            // 如果是营销政策，则需要车辆信息
            if (intent == KnowEngineIntent.CAR_MARKETING) {
                if (chatParam.getIntentRecognitionResult().entities().car_model() == null) {
                    List<CarInfoEntity> carInfoList = carInfoService.getCarInfoByBrand(null);
                    return Flux.just("[CARD]:请先选择您要咨询的车辆")
                            .concatWith(Flux.just("[CARD_CHOICE_CAR]:" + JSON.toJSONString(CarInfoConverter.INSTANCE.toVOList(carInfoList))));
                }
            }
        }

        return doChat(chatParam);
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
                    KnowEngineQueryTransformer knowEngineQueryTransformer = new KnowEngineQueryTransformer(chatModel, chatParam.getMessageId(), processCallback);

                    // 构造查询路由器
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

                    ProgressAwareContentRetriever sqlRetriever = null;
                    try {
                        sqlRetriever = ProgressAwareContentRetriever.builder().delegate(KnowEngineSqlDatabaseContentRetriever.builder()
                                        .dataSource(dataSource)
                                        .promptTemplate(new PromptTemplate(textToSqlPrompt.getContentAsString(StandardCharsets.UTF_8)))
                                        .databaseStructure(null)
                                        .chatModel(chatModel)
                                        .fallbackRetriever(embeddingRetriever)
                                        .build())
                                .progressCallback(processCallback)
                                .build();
                    } catch (IOException e) {
                        log.error("Error creating SQL retriever", e);
                    }

                    KnowEngineQueryRouter knowEngineQueryRouter = new KnowEngineQueryRouter(Arrays.asList(embeddingRetriever, fullTextRetriever, sqlRetriever),
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
     * 构造权限过滤器
     *
     * @param chatParam
     * @return
     */
    private Filter buildFilter(ChatParam chatParam) {
        // 默认权限过滤器：允许访客权限
        Filter permissionFilter = metadataKey(ACCESSIBLE_BY).isEqualTo(RoleEnum.VISITOR.name());

        // 根据用户角色获取权限 todo
        //RoleEnum roleEnum = userRoleService.getUserRole(chatParam);

        // 获取该文档支持的所有权限
        String[] permissions = DocumentPermissionUtils.getDocumentAccessiblePermission(null);
        for (String permission : permissions) {
            // 非访客权限时，将权限用or连接，表示支持多种权限
            if (!RoleEnum.VISITOR.name().equals(permission)) {
                permissionFilter = permissionFilter.or(metadataKey(ACCESSIBLE_BY).isEqualTo(permission));
            }
        }
        return permissionFilter;
    }
}
