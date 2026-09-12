package com.phuang.service.chat.impl;

import cn.hutool.core.util.StrUtil;
import com.phuang.handler.memory.DatabaseChatMemoryStore;
import com.phuang.handler.rag.PromptHandler;
import com.phuang.handler.rag.aggregator.BgeScoringModel;
import com.phuang.handler.rag.aggregator.KnowEngineReRankingContentAggregator;
import com.phuang.handler.rag.retriever.KnowEngineElasticsearchContentRetriever;
import com.phuang.handler.rag.retriever.KnowEngineSqlDatabaseContentRetriever;
import com.phuang.handler.rag.router.KnowEngineQueryRouter;
import com.phuang.handler.rag.transformer.KnowEngineQueryTransformer;
import com.phuang.model.dto.ChatParam;
import com.phuang.model.dto.IntentRecognitionResult;
import com.phuang.model.enums.ChatSource;
import com.phuang.model.enums.RoleEnum;
import com.phuang.service.KnowledgeSegmentService;
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
import reactor.core.publisher.Flux;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
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

        IntentRecognitionResult recognitionResult = intentRecognitionService.chat(conversationId, content);
        if (!recognitionResult.related()) {
            //使用通用大模型进行对话
            return commonChatService.streamChat(userId, content)
                    .concatWith(Flux.just("[DONE]:" + finalConversationId));
        } else {
            //rag流程

        }
        return null;
    }

    public Flux<String> doChat(ChatParam chatParam) {

        Consumer<String> processCallback = new Consumer<String>() {
            @Override
            public void accept(String s) {
            }
        };

        //构造权限过滤
        Filter accessibleByFilter = buildFilter(chatParam);

        //构建查询改写器
        KnowEngineQueryTransformer knowEngineQueryTransformer = new KnowEngineQueryTransformer(chatModel, chatParam.getMessageId(), processCallback);

        // 构造查询路由器
        KnowEngineElasticsearchContentRetriever embeddingRetriever = KnowEngineElasticsearchContentRetriever.builder()
                .configuration(ElasticsearchConfigurationKnn.builder().build())
                .maxResults(5)
                .minScore(0.5)
                .embeddingModel(openAiEmbeddingModel)
                .restClient(restClient)
                .indexName(INDEX_NAME)
                .knowledgeSegmentService(knowledgeSegmentService)
                .filter(accessibleByFilter)
                .build();

        KnowEngineElasticsearchContentRetriever fullTextRetriever = KnowEngineElasticsearchContentRetriever.builder()
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .restClient(restClient)
                .embeddingModel(openAiEmbeddingModel)
                .knowledgeSegmentService(knowledgeSegmentService)
                .indexName(INDEX_NAME)
                .filter(accessibleByFilter)
                .maxResults(5)
                .build();

        KnowEngineSqlDatabaseContentRetriever sqlRetriever = null;
        try {
            sqlRetriever = KnowEngineSqlDatabaseContentRetriever.builder()
                    .dataSource(dataSource)
                    .promptTemplate(new PromptTemplate(textToSqlPrompt.getContentAsString(StandardCharsets.UTF_8)))
                    .databaseStructure(null)
                    .chatModel(chatModel)
                    .fallbackRetriever(embeddingRetriever)
                    .build();
        } catch (IOException e) {
            log.error("Error creating SQL retriever", e);
        }

        KnowEngineQueryRouter knowEngineQueryRouter = new KnowEngineQueryRouter(Arrays.asList(embeddingRetriever, fullTextRetriever, sqlRetriever),
                chatModel, processCallback);

        //构造融合重排序器
        KnowEngineReRankingContentAggregator knowEngineReRankingContentAggregator = KnowEngineReRankingContentAggregator.builder()
                .scoringModel(BgeScoringModel.getInstance())
                .minScore(0.6)
                .maxResults(5)
                .querySelector(queryToContents -> queryToContents.keySet().iterator().next())
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

        return knowEngineChatAiService.streamChat(chatParam.getConversationId(), chatParam.getContent());
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
