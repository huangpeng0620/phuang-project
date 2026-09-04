package com.phuang.controller;

import com.phuang.converter.CustomListOutputConverter;
import com.phuang.model.Book;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 *
 * @description ChatClientController
 * @author huangpeng
 * @since 2026/3/31
 */
@RestController
@RequestMapping("/structure")
public class StructureOutputController implements InitializingBean {

    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    private ChatClient chatClient;

    /**
     * 格式化输出Bean对象(方式一)
     * @param topic
     * @return
     */
    @GetMapping("/call/toBean1")
    public Book toBean1(@RequestParam("topic") String topic) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template("请给我推荐一本{topic}有关的书，输出格式：{format}")
                .build();
        //定义转换器
        BeanOutputConverter<Book> converter = new BeanOutputConverter<>(Book.class);
        String resp = chatClient.prompt(promptTemplate.create(Map.of("topic", topic, "format", converter.getFormat())))
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
        return converter.convert(resp);
    }

    /**
     * 格式化输出Bean对象(方式二)
     * @param topic
     * @return
     */
    @GetMapping("/call/toBean2")
    public Book toBean2(@RequestParam("topic") String topic) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template("请给我推荐一本{topic}有关的书")
                .build();
        /**
         * 追踪源码可发现,entity 方法的底层原理也是请求前创建了转换器构建提示词后请求LLM后进行格式化转换,原理等同于方式一,只不过这种写法简单
         */
        return chatClient.prompt(promptTemplate.create(Map.of("topic", topic)))
                .call()
                .entity(Book.class);
    }

    /**
     * 格式化输出Bean对象集合
     * @param topic
     * @return
     */
    @RequestMapping("/call/toBeanList1")
    public List<Book> toBeanList1(@RequestParam("topic") String topic) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template("请给我推荐几本{topic}有关的书")
                .build();
        return chatClient.prompt(promptTemplate.create(Map.of("topic", topic)))
                .call()
                .entity(new ParameterizedTypeReference<List<Book>>() {
                });
    }

    @RequestMapping("/call/toBeanList2")
    public List<Book> toBeanList2(@RequestParam("topic") String topic) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template("请给我推荐几本{topic}有关的书，输出格式：{format}")
                .build();
        CustomListOutputConverter<Book> converter = new CustomListOutputConverter<>(Book.class);
        String resp = chatClient.prompt(promptTemplate.create(Map.of("topic", topic, "format", converter.getFormat())))
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
        return converter.convert(resp);
    }

    /**
     * 格式化输出Map
     * 注意: 提示词中需要指定K,V
     * @param topic
     * @return
     */
    @RequestMapping("/call/toBeanMap")
    public Map<String, Object> toBeanMap(@RequestParam("topic") String topic) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template("请给我推荐几本{topic}有关的书,以书名作为key，书的信息作为value")
                .build();
        return chatClient.prompt(promptTemplate.create(Map.of("topic", topic)))
                .call()
                .entity(new MapOutputConverter());
    }

    /**
     * 初始化 chatclient
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        chatClient = ChatClient.builder(deepSeekR1ChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.7)
                        .build())
                .build();
    }
}
