package com.phuang.controller;

import com.alibaba.cloud.ai.memory.mem0.advisor.Mem0ChatMemoryAdvisor;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.alibaba.cloud.ai.memory.mem0.advisor.Mem0ChatMemoryAdvisor.USER_ID;

@Lazy
@RestController
@RequestMapping("/longTermMemory")
public class LongTermMemoryController implements InitializingBean {

/*    private static final PromptTemplate MEM0_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
            请直接回答用户的问题，不要提及、引用或暗示长期记忆、历史记录或其他信息来源。
            Pay attention: Please directly answer the user's question, and do not mention, refer to, or imply long-term memory, historical records, or other information sources.
            {query}
            """);*/
    private static final PromptTemplate MEM0_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
                     ---------------------
                     USER_INPUT_MESSAGE:
                     {query}
                     ---------------------
                     Use the long term conversation memory from the LONG_TERM_MEMORY section to provide accurate answers.
            
                     LONG_TERM_MEMORY is a dictionary containing the search results, typically under a "results" type, and potentially "relations" type if graph store is enabled.
                     Example:
                     ```text
                     \\[
                     	 \\{
                     	  "type": "results", e.g.: vector store
                           "id": "...", e.g.: memory id
                           "memory": "...", e.g.: memory text
                           "hash": "...",  e.g.: memory hash value
                           "metadata": "...", e.g.: user custom dict
                           "score": 0.3,   e.g.: relevance score: the higher the score, the more relevant.
                           "created_at": "...", e.g.: created time
                           "updated_at": null, e.g.: updated time
                           "user_id": "...",
                           "agent_id": "...",
                           "run_id": "...",
                           "role": "..."
                         \\},
                     	\\{
                     	  "type": "relations", e.g.: graph store
                           "source": "...", e.g.: graph store source
                           "relationship": "...", e.g.: value is loves means hobby
                           "destination": "...",
                           "target": "..."
                         \\}
                     ]
                     ```
                   Pay attention:
                        Please directly answer the user's question, and do not mention, refer to, or imply long-term memory, historical records, 
                   or other information sources.
            
                     ---------------------
                     LONG_TERM_MEMORY:
                     {long_term_memory}
                     ---------------------
            """);


    @Resource(name = "deepSeekR1ChatModel")
    private ChatModel deepSeekR1ChatModel;

    @Resource
    private VectorStore mem0MemoryStore;

    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message,
                       @RequestParam("userId") String userId) {
        return chatClient.prompt(message)
                .advisors(req -> req.params(Map.of(USER_ID, userId)))
                .call().content();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Mem0ChatMemoryAdvisor mem0ChatMemoryAdvisor = Mem0ChatMemoryAdvisor.builder(mem0MemoryStore)
                .systemPromptTemplate(MEM0_SYSTEM_PROMPT_TEMPLATE)
                .build();
        this.chatClient = ChatClient.builder(deepSeekR1ChatModel)
                .defaultAdvisors(mem0ChatMemoryAdvisor)
                .build();
    }
}
