package com.phuang.handler.splitter;

import com.google.common.collect.Maps;
import com.phuang.util.SnowflakeIdGenerator;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.phuang.model.constant.MetadataKeyConstant.*;

/**
 * Markdown文档分割器，基于标题层级进行文档分段
 * 支持保留元数据、父子分段关系等高级特性
 *
 * @author andyflury （https://github.com/langchain4j/langchain4j/issues/574 ）
 * @author phuang
 */
@Slf4j
public class MarkdownHeaderParentTextSplitter implements DocumentSplitter {

    /**
     * 定义 Markdown 标题符号到元数据字段名的映射
     */
    private static final Map<String, String> DEFAULT_HEADERS_TO_SPLIT = Maps.newHashMap();

    static {
        DEFAULT_HEADERS_TO_SPLIT.put("#", "title");
        DEFAULT_HEADERS_TO_SPLIT.put("##", "subtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("###", "subsubtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("####", "subsubsubtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("#####", "subsubsubsubtitle");
        DEFAULT_HEADERS_TO_SPLIT.put("######", "subsubsubsubsubtitle");
    }

    /**
     * 需要分割的标题列表,默认使用 DEFAULT_HEADERS_TO_SPLIT
     * <P>
     *     按标题标记长度倒序排列
     * </P>
     */
    private List<Map.Entry<String, String>> headersToSplitOn;

    /**
     * 是否跳过最后的相邻块合并步骤, 默认 true
     * <P>
     *     例如:
     *      # 用户手册
     *     ## 安装
     *     这里是安装步骤。
     *
     *     1.如果 returnEachLine = true; 则分成两个分块:
     *              分片1: # 用户手册
     *              分片2：## 安装 + 安装步骤
     *
     *     2.如果 returnEachLine = false; 则分成一个分块:
     *          分片1：
     *              # 用户手册
     *             ## 安装
     *             这里是安装步骤
     * </P>
     */
    private boolean returnEachLine;

    /**
     * 是否从分片正文中删除 Markdown 标题, 默认 false
     */
    private boolean stripHeaders;

    /**
     * 分片最大字符数,0 表示不限制
     */
    private int chunkSize;

    /**
     * 相邻分片之间的重叠字符数
     */
    private int overlap;

    /**
     * 构造函数
     *
     * @param headersToSplitOn 标题分割映射表，key为标题标记（如"#"、"##"），value为元数据中的键名
     * @param returnEachLine   是否按行返回结果，false时会聚合相同元数据的行
     * @param stripHeaders     是否在结果中移除标题行
     */
    public MarkdownHeaderParentTextSplitter(Map<String, String> headersToSplitOn, boolean returnEachLine, boolean stripHeaders) {
        this(headersToSplitOn, returnEachLine, stripHeaders, 0, 0);
    }

    public MarkdownHeaderParentTextSplitter(int chunkSize, int overlap) {
        this(DEFAULT_HEADERS_TO_SPLIT, true, false, chunkSize, overlap);
    }

    /**
     * 构造函数（通过标题级别指定分割层级）
     *
     * @param titleLevel     标题级别（1-6），表示按1到titleLevel级标题进行分割
     * @param returnEachLine 是否按行返回结果，false时会聚合相同元数据的行
     * @param stripHeaders   是否在结果中移除标题行
     * @param chunkSize      每个分片的最大字符数，超出则按chunkSize再次切割，0表示不限制
     * @param overlap        相邻分片之间的重叠字符数
     */
    public MarkdownHeaderParentTextSplitter(int titleLevel, boolean returnEachLine, boolean stripHeaders, int chunkSize, int overlap) {
        this(buildHeadersMap(titleLevel), returnEachLine, stripHeaders, chunkSize, overlap);
    }

    /**
     * 根据标题级别生成标题分割映射表
     *
     * @param titleLevel 标题级别（1-6）
     * @return 标题分割映射表
     */
    private static Map<String, String> buildHeadersMap(int titleLevel) {
        if (titleLevel < 1 || titleLevel > 6) {
            throw new IllegalArgumentException("titleLevel must be between 1 and 6, but got: " + titleLevel);
        }
        String[] names = {"title", "subtitle", "subsubtitle", "subsubsubtitle", "subsubsubsubtitle", "subsubsubsubsubtitle"};
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i <= titleLevel; i++) {
            String key = "#".repeat(i);
            headers.put(key, names[i - 1]);
        }
        return headers;
    }

    /**
     * 构造函数（支持 chunkSize 和 overlap）
     *
     * @param headersToSplitOn 标题分割映射表，key为标题标记（如"#"、"##"），value为元数据中的键名
     * @param returnEachLine   是否按行返回结果，false时会聚合相同元数据的行
     * @param stripHeaders     是否在结果中移除标题行
     * @param chunkSize        每个分片的最大字符数，超出则按chunkSize再次切割，0表示不限制
     * @param overlap          相邻分片之间的重叠字符数
     */
    public MarkdownHeaderParentTextSplitter(Map<String, String> headersToSplitOn, boolean returnEachLine, boolean stripHeaders, int chunkSize, int overlap) {
        // 按标题标记长度倒序排列，确保优先匹配更长的标记（如"###"优先于"##"）
        this.headersToSplitOn = headersToSplitOn.entrySet().stream().sorted(Comparator.comparingInt(e -> -e.getKey().length())).collect(Collectors.toList());
        this.returnEachLine = returnEachLine;
        this.stripHeaders = stripHeaders;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<TextSegment> split(Document document) {
        log.info("开始解析Markdown文档...");
        // 删除文档中所有空行再用换行符拼回文本
        String text = Arrays.stream(document.text().split("\n"))
                .filter(line -> !line.trim().isEmpty()).collect(Collectors.joining("\n"));
        List<DocumentWithMetadata> segments = splitWithMetadata(text, document.metadata().toMap());
        return segments.stream().map(segment -> new TextSegment(segment.getContent(), Metadata.from(segment.getMetadata()))).collect(Collectors.toList());
    }

    /**
     * 简化版分割方法，不保留元数据
     *
     * @param text 待分割的文本
     * @return 分割后的文本片段列表
     */
    public List<TextSegment> splitText(String text) {
        // 移除文本中所有空行
        String filteredText = Arrays.stream(text.split("\n"))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.joining("\n"));
        List<TextSegment> result = new ArrayList<>();
        List<DocumentWithMetadata> segments = splitWithMetadata(filteredText, new HashMap<>());
        for (DocumentWithMetadata segment : segments) {
            result.add(new TextSegment(segment.getContent(), Metadata.from(segment.getMetadata())));
        }
        return result;
    }

    /**
     * 核心分割逻辑(保留元数据)
     *
     * @param text         待分割的文本
     * @param baseMetadata 基础元数据，会被传递到每个分段中
     * @return 带有元数据的文档片段列表
     */
    private List<DocumentWithMetadata> splitWithMetadata(String text, Map<String, Object> baseMetadata) {
        List<String> lines = Arrays.asList(text.split("\n"));
        /**
         * currentContent 当前正在累积的正文
         * currentMetadata 当前正文对应的 metadata
         * linesWithMetadata 保存已经完成的 "正文 + 元数据" 分块,已经收集完成的分块结果
         * initialMetadata 最新标题路径对应的 metadata
         */
        List<String> currentContent = new ArrayList<>();
        List<Line> linesWithMetadata = new ArrayList<>();
        Map<String, Object> currentMetadata = new HashMap<>(baseMetadata);
        Map<String, Object> initialMetadata = new HashMap<>(baseMetadata);
        // 标题栈,用来维护当前标题路径
        List<Header> headerStack = new ArrayList<>();

        boolean inCodeBlock = false;  // 是否在代码块中
        String openingFence = "";     // 代码块的开始标记
        for (String line : lines) {
            String strippedLine = line.trim();
            /**
             * Markdown 支持两种常见的围栏代码块:
             *  1. ```java 代码```
             *  2. ~~~java 代码~~~
             * 下面逻辑的核心目的是避免把代码块里面以 # 开头的代码误判成 Markdown 标题
             */
            if (!inCodeBlock) {
                if (strippedLine.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    openingFence = "```";
                } else if (strippedLine.startsWith("~~~")) {
                    inCodeBlock = !inCodeBlock;
                    openingFence = "~~~";
                }
            } else {
                if (strippedLine.startsWith(openingFence)) {
                    inCodeBlock = false;
                    openingFence = "";
                }
            }
            // 代码块内的内容直接添加，不做标题检测
            if (inCodeBlock) {
                currentContent.add(strippedLine);
                continue;
            }

            /**
             * 检测并处理标题行
             * <P>
             *     下面 interrupted {} 中的类似于标签（label），它不是关键字，名字可以随便取
             *     {} 内可以通过 break interrupted; 直接退出代码块
             * </P>
             */
            interrupted:
            {
                for (Map.Entry<String, String> header : headersToSplitOn) {
                    String sep = header.getKey();    // 标题标记，如 "#"、"##"
                    String name = header.getValue(); // 元数据中的键名, 如 "title"、"subtitle"
                    /**
                     * 判断是否为有效的标题行
                     * <P>
                     *     前提: Markdown 标题都是以 sep 开头的,但是标题内容和 sep 之间会存在空格
                     *     假设: sep = "##"
                     *     strippedLine 需要以 "##" 开头,但 "###" 也满足这种情况,因此还需要增加需要满足 strippedLine是空标题行或者 sep 是空格的条件
                     * </P>
                     */
                    if (strippedLine.startsWith(sep) && (strippedLine.length() == sep.length() || strippedLine.charAt(sep.length()) == ' ')) {
                        if (name != null) {
                            // 计算当前标题级别（统计#的个数）
                            int currentHeaderLevel = (int) sep.chars().filter(ch -> ch == '#').count();
                            /**
                             * 用于维护当前标题的父子层级关系
                             * 【核心规则】：遇到新标题时,删除旧的同级标题和旧的子标题,只保留仍然有效的父标题
                             *  例如: ## 安装
                             *       ### Windows安装
                             *       ## 使用
                             *  1. 读取到 ## 使用 前，标题栈是： 安装（2级）、Windows安装（3级）
                             *  2. 新标题“使用”是 2 级，所以执行：旧标题级别 >= 新标题级别
                             *                          2.1 Windows安装：3 >= 2  删除
                             *                          2.2 安装：2 >= 2         删除
                             *  3. 然后加入新的“使用”, 标题栈变更: 使用（2级）
                             */
                            while (!headerStack.isEmpty() && headerStack.get(headerStack.size() - 1).getLevel() >= currentHeaderLevel) {
                                Header poppedHeader = headerStack.remove(headerStack.size() - 1);
                                initialMetadata.remove(poppedHeader.getName());
                            }

                            // 将当前标题加入栈，并更新元数据
                            Header headerType = new Header(currentHeaderLevel, name, strippedLine.substring(sep.length()).trim());
                            headerStack.add(headerType);
                            initialMetadata.put(name, headerType.getData());
                            initialMetadata.put(HEADER_LEVEL, currentHeaderLevel);
                            // 为当前分段生成唯一ID, 用于后续建立父子关系
                            initialMetadata.put(CHUNK_ID, SnowflakeIdGenerator.getInstance().nextIdStr());
                        }

                        // 遇到新标题时，保存之前累积的内容
                        if (!currentContent.isEmpty()) {
                            linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
                            currentContent.clear();
                        }

                        // 根据stripHeaders配置决定是否保留标题行
                        if (!stripHeaders) {
                            currentContent.add(strippedLine);
                        }
                        break interrupted;
                    }
                }

                // 处理非标题行
                if (!strippedLine.isEmpty()) {
                    currentContent.add(strippedLine);
                } else if (!currentContent.isEmpty()) {
                    // 遇到空行时，保存当前累积的内容
                    linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
                    currentContent.clear();
                }
            }
            // 更新当前元数据为最新的标题信息
            currentMetadata = new HashMap<>(initialMetadata);
        }

        // 处理最后累积的内容
        if (!currentContent.isEmpty()) {
            linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata));
        }

        // 根据配置决定返回方式
        List<DocumentWithMetadata> segments;
        if (!returnEachLine) {
            // 聚合模式：将相同元数据的行合并
            segments = aggregateLinesToChunks(linesWithMetadata);
        } else {
            // 逐行模式：保持每行独立
            segments = linesWithMetadata.stream()
                    .map(line -> new DocumentWithMetadata(line.getContent(), line.getMetadata()))
                    .collect(Collectors.toList());
        }

        // 如果设置了 chunkSize，对超出大小的分片进行二次切割
        if (chunkSize > 0) {
            segments = splitByChunkSize(segments);
        }
        return segments;
    }

    /**
     * 聚合行为分块
     * 将具有相同元数据的行合并为一个分块，并处理父子关系
     *
     * @param lines 待聚合的行列表
     * @return 聚合后的文档片段列表
     */
    private List<DocumentWithMetadata> aggregateLinesToChunks(List<Line> lines) {
        List<Line> aggregatedChunks = new ArrayList<>();
        for (Line line : lines) {
            // 情况1：元数据相同，直接合并到上一个分块
            if (!aggregatedChunks.isEmpty() && aggregatedChunks.get(aggregatedChunks.size() - 1).getMetadata().equals(line.getMetadata())) {
                Line last = aggregatedChunks.get(aggregatedChunks.size() - 1);
                last.setContent(last.getContent() + "  \n" + line.getContent());
            }
            // 情况2：元数据不同但上一行以标题结尾且未剥离标题，则也合并
            // 这样可以将标题和其下的第一段内容合并在一起
            else if (!aggregatedChunks.isEmpty() && !aggregatedChunks.get(aggregatedChunks.size() - 1).getMetadata().equals(line.getMetadata())
                    && aggregatedChunks.get(aggregatedChunks.size() - 1).getMetadata().size() < line.getMetadata().size()
                    && aggregatedChunks.get(aggregatedChunks.size() - 1).getContent().split("\n")[aggregatedChunks.get(aggregatedChunks.size() - 1).getContent().split("\n").length - 1].startsWith("#") && !stripHeaders) {

                Line last = aggregatedChunks.get(aggregatedChunks.size() - 1);
                last.setContent(last.getContent() + "  \n" + line.getContent());
            }
            // 情况3：创建新分块
            else {
                aggregatedChunks.add(line);
            }
        }
        return aggregatedChunks.stream()
                .map(chunk -> new DocumentWithMetadata(chunk.getContent(), chunk.getMetadata()))
                .collect(Collectors.toList());
    }

    /**
     * 对超出 chunkSize 的分片进行二次切割
     * <p>
     * 切割规则：
     * - 未超出 chunkSize 的分片保持不变
     * - 超出 chunkSize 的分片：保留完整分片（标记为跳过embedding），同时生成拆分后的多个分片
     *
     * @param segments 原始分片列表
     * @return 切割后的分片列表
     */
    private List<DocumentWithMetadata> splitByChunkSize(List<DocumentWithMetadata> segments) {
        List<DocumentWithMetadata> result = new ArrayList<>();
        for (DocumentWithMetadata segment : segments) {
            String content = segment.getContent();
            if (content.length() <= chunkSize) {
                // 未超出 chunkSize，保持原分片不变
                result.add(segment);
            } else {
                /**
                 * 超出 chunkSize,则通过【父子分块】进行二次切割,通常存在两种做法:
                 *  1.父分块维护在关系型数据库、子分块维护在向量数据库、子分块通过其元数据内的 parentChunkId 字段即可获取其父分块
                 *  2.父子分块都维护在向量数据库,但父分块不做向量化
                 * 这里采用了第二种方式:父分块内保留完整分片,并通过其元数据内的 skipEmbedding 标记其需要跳过 embedding
                 */
                //父分块
                Map<String, Object> fullMetadata = new HashMap<>(segment.getMetadata());
                String parentChunkId = SnowflakeIdGenerator.getInstance().nextIdStr();
                fullMetadata.put(CHUNK_ID, parentChunkId);
                fullMetadata.put(SKIP_EMBEDDING, 1);
                result.add(new DocumentWithMetadata(content, fullMetadata));
                //拆分子分块
                int start = 0;
                while (start < content.length()) {
                    int end = Math.min(start + chunkSize, content.length());
                    String subContent = content.substring(start, end);
                    // 复制元数据并进行更新
                    Map<String, Object> subMetadata = new HashMap<>(segment.getMetadata());
                    subMetadata.put(CHUNK_ID, SnowflakeIdGenerator.getInstance().nextIdStr());
                    subMetadata.put(PARENT_CHUNK_ID, parentChunkId);
                    result.add(new DocumentWithMetadata(subContent, subMetadata));
                    if (end == content.length()) {
                        break;
                    }
                    // 下一片的起始位置 = 当前片的结束位置 - overlap
                    start = end - Math.min(overlap, end);
                }
            }
        }
        return result;
    }

    /**
     * 内部类：表示带有元数据的文本行
     */
    @Setter
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Line {
        /**
         * 文本内容
         */
        private String content;
        /**
         * 元数据信息
         */
        private Map<String, Object> metadata;
    }

    /**
     * 内部类：表示Markdown标题
     */
    @Setter
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Header {

        /**
         * 标题级别（1-6）
         */
        private int level;

        /**
         * 元数据中的键名
         */
        private String name;

        /**
         * 标题文本内容（不含#标记）
         */
        private String data;
    }

    /**
     * 内部类：携带元数据的文档片段
     */
    private static class DocumentWithMetadata {

        private final String content;

        private final Map<String, Object> metadata;

        public DocumentWithMetadata(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = new HashMap<>(metadata);
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
