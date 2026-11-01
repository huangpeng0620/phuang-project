package com.phuang.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 *
 * @description CustomTool
 * @author huangpeng
 * @since 2026/10/31
 */

public class CustomTool implements AgentTool {

    /**
     * 工具名称
     * <p>
     *     名称保持稳定, 在同一个 Toolkit 中保持唯一, 尽量使用容易理解的英文名称
     * </p>
     * @return
     */
    @Override
    public String getName() {
        return "custom_tool";
    }

    /**
     * 工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "自定义工具";
    }

    /**
     * 工具参数的 JSON Schema
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "查询")
                ),
                "required", List.of("query")
        );
    }

    /**
     * 工具执行逻辑的入口,天然异步
     * @param param The tool call parameters
     * @return
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String query = (String) param.getInput().get("query");
        return Mono.just(ToolResultBlock.text("结果：" + query));
    }
}