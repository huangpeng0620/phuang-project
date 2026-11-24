package com.phuang.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 *
 * @description ArticleTools
 * @author huangpeng
 * @since 2026/11/18
 */

public class ArticleTools {

    @Tool(name = "publish_article", description = "发布一篇文章")
    public String publishArticle(@ToolParam(name = "title", description = "文章标题") String title) {
        // 只有 ConfirmResult.confirmed=true 后，代码才会执行到这里。
        System.out.println("真正执行 publish_article：" + title);
        return "文章发布成功：" + title;
    }
}
