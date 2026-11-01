package com.phuang.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 *
 * @description SensitiveTool
 * @author huangpeng
 * @since 2026/11/1
 */

public class SensitiveTool {

    @Tool(name = "delete_file", description = "删除指定文件，参数 path 是目标路径。")
    public String deleteFile(@ToolParam(name = "path", description = "要删除的文件路径") String path) {
        return "删除完成：" + path;
    }
}
