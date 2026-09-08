package com.phuang.service.document.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.phuang.mapper.TableMetaMapper;
import com.phuang.model.entity.KnowledgeDocumentEntity;
import com.phuang.model.entity.TableMeta;
import com.phuang.model.enums.FileType;
import com.phuang.model.enums.KnowledgeBaseType;
import com.phuang.model.exception.BusinessException;
import com.phuang.service.FileProcessService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @description ExcelProcessServiceImpl
 * @author huangpeng
 * @since 2026/9/2
 */
@Service
@Slf4j
public class ExcelProcessServiceImpl implements FileProcessService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private TableMetaMapper tableMetaMapper;

    /**
     * 表名前缀
     */
    private static final String TABLE_PREFIX = "custom_data_query_";

    /**
     * 有效表名正则表达式
     */
    private static final Pattern VALID_TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    @Override
    public boolean supports(FileType fileType, KnowledgeBaseType knowledgeBaseType) {
        if (FileType.EXCEL.equals(fileType) || FileType.CSV.equals(fileType)) {
            return knowledgeBaseType == KnowledgeBaseType.DATA_QUERY;
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String processDocument(KnowledgeDocumentEntity document, String fileMinioUrl, InputStream inputStream) {
        String documentTitle = document.getDocTitle();
        String originalTableName = document.getTableName();
        Long versionId = document.getCurrentVersionId();
        Assert.notNull(versionId, "文档当前版本ID不能为空");
        log.info("开始处理Excel文件: {}, versionId={}", documentTitle, versionId);

        try {
            // 解析excel文件内容
            List<List<String>> excelData = parseExcel(inputStream);
            if (excelData.isEmpty() || excelData.size() < 2) {
                throw new IllegalArgumentException("Excel文件为空或只有表头,没有数据行");
            }
            //获取表头
            List<String> headers = excelData.getFirst();

            //生成表名
            String tableName = generatePhysicalTableName(originalTableName);

            // 生成列信息
            List<ColumnInfo> columns = generateColumnInfo(headers);

            /**
             * 校验表是否已存在
             * 1.表存在
             *  1.1. 元数据校验
             *  1.2. 表结构校验 (前后两次的表结构必须完全一致)
             *  1.3. 清空表数据并重新插入
             *  1.4. 元数据更新
             * 2.表不存在
             *  2.1. 构建建表SQL并执行
             *  2.2. 导入excel数据插入表内
             *  2.3. 生成表元记录并插入
             */
            if (tableMetaMapper.checkTableExists(tableName) > 0) {
                // 元数据校验
                TableMeta existingMeta = tableMetaMapper.selectOne(new LambdaQueryWrapper<TableMeta>().eq(TableMeta::getTableName, tableName));

                // 表结构校验 (前后两次的表结构必须完全一致)
                List<ColumnInfo> existingColumns = parseColumnInfo(existingMeta.getColumnsInfo());
                if (!isSchemaCompatible(existingColumns, columns)) {
                    throw new BusinessException("Excel 表结构与已有表 " + tableName + " 不一致,禁止上传,请保持表头、列名、顺序及类型完全一致;");
                }

                // 清空表数据并重新插入
                log.info("表 {} 已存在且结构一致，执行数据替换", tableName);
                deleteAllData(tableName);
                List<List<String>> dataRows = excelData.subList(1, excelData.size());
                int insertedCount = insertData(tableName, columns, dataRows);
                log.info("表 {} 数据替换完成，新数据 {} 行", tableName, insertedCount);

                // 更新表元数据记录
                existingMeta.setVersionId(versionId);
                existingMeta.setDescription(document.getDescription() != null ? document.getDescription() : "从Excel导入: " + documentTitle);
                tableMetaMapper.updateById(existingMeta);
            } else {
                //构建建表SQL并执行
                String createTableSql = generateCreateTableSql(tableName, document.getDescription(), columns);
                log.info("生成建表SQL: {}", createTableSql);
                tableMetaMapper.executeCreateTable(createTableSql);
                log.info("表 {} 创建成功", tableName);

                //导入excel数据插入表内
                List<List<String>> dataRows = excelData.subList(1, excelData.size());
                int insertedCount = insertData(tableName, columns, dataRows);
                log.info("插入数据 {} 行", insertedCount);

                //生成表元记录并插入
                TableMeta tableMeta = new TableMeta();
                tableMeta.setTableName(tableName);
                tableMeta.setDescription(document.getDescription() != null ? document.getDescription() : "从Excel导入: " + documentTitle);
                tableMeta.setCreateSql(createTableSql);
                tableMeta.setColumnsInfo(JSON.toJSONString(columns));
                tableMeta.setVersionId(versionId);
                int result = tableMetaMapper.insert(tableMeta);
                Assert.isTrue(result == 1, "表元数据保存失败");
                log.info("表元数据保存成功, ID: {}", tableMeta.getId());
            }
        } catch (Exception e) {
            log.error("解析excel异常,fileMinioUrl:{}", fileMinioUrl, e);
            throw new BusinessException(e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
        return fileMinioUrl;
    }

    /**
     * 清空指定表的所有数据
     */
    private void deleteAllData(String tableName) {
        if (!isValidTableName(tableName)) {
            throw new IllegalArgumentException("无效的表名: " + tableName);
        }
        String deleteSql = "DELETE FROM `" + tableName + "`";
        jdbcTemplate.execute(deleteSql);
        log.info("表 {} 旧数据已清空", tableName);
    }

    /**
     * 验证表名是否有效
     */
    private boolean isValidTableName(String tableName) {
        return tableName != null && VALID_TABLE_NAME_PATTERN.matcher(tableName).matches();
    }


    /**
     * 解析已保存的列信息 JSON
     */
    private List<ColumnInfo> parseColumnInfo(String columnsInfoJson) {
        if (columnsInfoJson == null || columnsInfoJson.isBlank()) {
            return Collections.emptyList();
        }
        return JSON.parseArray(columnsInfoJson, ColumnInfo.class);
    }

    /**
     * 判断两次上传的表结构是否一致
     * <p>
     * 要求：列数量、列名、数据类型、顺序完全一致
     */
    private boolean isSchemaCompatible(List<ColumnInfo> existingColumns, List<ColumnInfo> newColumns) {
        if (existingColumns == null || newColumns == null) {
            return existingColumns == newColumns;
        }
        if (existingColumns.size() != newColumns.size()) {
            return false;
        }
        for (int i = 0; i < existingColumns.size(); i++) {
            ColumnInfo a = existingColumns.get(i);
            ColumnInfo b = newColumns.get(i);
            if (a == null || b == null) {
                return false;
            }
            if (!Objects.equals(a.getColumnName(), b.getColumnName())) {
                return false;
            }
            if (!Objects.equals(a.getDataType(), b.getDataType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 插入数据
     */
    private int insertData(String tableName, List<ColumnInfo> columns, List<List<String>> dataRows) {
        int batchSize = 500; // 每批插入500条
        int totalInserted = 0;
        for (int i = 0; i < dataRows.size(); i += batchSize) {
            List<List<String>> batch = dataRows.subList(i, Math.min(i + batchSize, dataRows.size()));
            String insertSql = generateBatchInsertSql(tableName, columns, batch);
            // 使用 JdbcTemplate 执行，绕过 MyBatis-Plus 的BlockAttackInnerInterceptor拦截器
            jdbcTemplate.execute(insertSql);
            totalInserted += batch.size();
        }
        return totalInserted;
    }

    /**
     * 生成批量插入SQL
     */
    private String generateBatchInsertSql(String tableName, List<ColumnInfo> columns, List<List<String>> rows) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (");
        // 列名
        String columnNames = columns.stream().map(c -> "`" + c.getColumnName() + "`")
                .collect(Collectors.joining(", "));
        sql.append(columnNames).append(") VALUES ");
        // 值
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(");
            for (int j = 0; j < columns.size(); j++) {
                if (j > 0) {
                    sql.append(", ");
                }
                String value = j < row.size() ? row.get(j) : "";
                sql.append(escapeSqlValue(value));
            }
            sql.append(")");
        }
        return sql.toString();
    }

    /**
     * 转义SQL值
     */
    private String escapeSqlValue(String value) {
        if (value == null || value.isEmpty()) {
            return "NULL";
        }

        // 转义单引号
        String escaped = value.replace("'", "''");
        // 处理换行符和制表符
        escaped = escaped.replace("\\", "\\\\");
        escaped = escaped.replace("\n", "\\n");
        escaped = escaped.replace("\r", "\\r");
        escaped = escaped.replace("\t", "\\t");

        return "'" + escaped + "'";
    }

    /**
     * 生成建表SQL
     * <P>
     *    例如:  CREATE TABLE IF NOT EXISTS `custom_data_query_car_recall` (
     *              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
     *              `brand` VARCHAR(500) DEFAULT NULL COMMENT 'brand',
     *              `model` VARCHAR(500) DEFAULT NULL COMMENT 'model',
     *              `recall_quantity` VARCHAR(500) DEFAULT NULL COMMENT 'recall_quantity',
     *              `recall_reason` VARCHAR(500) DEFAULT NULL COMMENT 'recall_reason',
     *              `production_date_start` VARCHAR(500) DEFAULT NULL COMMENT 'production_date_start',
     *              `production_date_end` VARCHAR(500) DEFAULT NULL COMMENT 'production_date_end',
     *              `recall_date` VARCHAR(500) DEFAULT NULL COMMENT 'recall_date',
     *              `risk_description` VARCHAR(500) DEFAULT NULL COMMENT 'risk_description',
     *              `solution` VARCHAR(500) DEFAULT NULL COMMENT 'solution',
     *              `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     *              `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
     *              PRIMARY KEY (`id`)
     *            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='car_recall'
     * </P>
     */
    private String generateCreateTableSql(String tableName, String description, List<ColumnInfo> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");
        // 添加自增主键
        sql.append("  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n");
        // 添加Excel列
        for (ColumnInfo column : columns) {
            sql.append("  `").append(column.getColumnName()).append("` ")
                    .append(column.getDataType())
                    .append(" DEFAULT NULL COMMENT '")
                    .append(escapeSqlComment(column.getOriginalHeader()))
                    .append("',\n");
        }

        // 添加创建时间和更新时间
        sql.append("  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n");
        sql.append("  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n");

        // 设置主键
        sql.append("  PRIMARY KEY (`id`)\n");
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='" + description + "'");

        return sql.toString();
    }

    /**
     * 转义SQL注释中的特殊字符
     */
    private String escapeSqlComment(String comment) {
        if (comment == null) {
            return "";
        }
        return comment.replace("'", "\\'").replace("\\", "\\\\");
    }

    /**
     * 生成列信息
     */
    private List<ColumnInfo> generateColumnInfo(List<String> headers) {
        List<ColumnInfo> columns = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            //格式化列名
            String columnName = formatColumnName(header);

            // 处理重复的列名
            String originalName = columnName;
            int suffix = 1;
            while (usedNames.contains(columnName)) {
                columnName = originalName + "_" + suffix++;
            }
            usedNames.add(columnName);

            ColumnInfo column = new ColumnInfo();
            column.setIndex(i);
            column.setOriginalHeader(header);
            column.setColumnName(columnName);
            column.setDataType("VARCHAR(500)"); // 默认使用VARCHAR类型
            columns.add(column);
        }
        return columns;
    }

    /**
     * 格式化列名
     */
    private String formatColumnName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "col";
        }
        // 转换为小写
        String sanitized = name.toLowerCase().trim();
        // 替换非法字符为下划线
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_]", "_");
        // 确保以字母开头
        if (!sanitized.matches("^[a-zA-Z].*")) {
            sanitized = "col_" + sanitized;
        }
        // 限制长度（MySQL列名最大64字符）
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60);
        }
        // 去掉连续和末尾的下划线
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("_+$", "");

        return sanitized;
    }

    /**
     * 列信息内部类
     */
    @Data
    public static class ColumnInfo {

        private int index;

        private String originalHeader;

        private String columnName;

        private String dataType;
    }

    /**
     * 根据原始文件名（逻辑表名）生成 MySQL 物理表名。
     * <p>
     * 生成规则如下：
     * <ol>
     *     <li>去掉最后一个 {@code .} 及其后的文件扩展名；</li>
     *     <li>将名称转换为小写，并把英文字母、数字和下划线之外的字符替换为下划线；</li>
     *     <li>如果清理后的名称不是以英文字母或下划线开头，则添加 {@code t_} 前缀；</li>
     *     <li>移除末尾下划线；清理后为空时使用 {@code table}；原始名称为空白时会生成
     *         {@code table_时间戳}；</li>
     *     <li>将基础名称截断至最多 46 个字符，再添加固定前缀 {@code custom_data_query_}，
     *         保证最终表名不超过 MySQL 的 64 字符限制。</li>
     * </ol>
     * 最终格式为 {@code custom_data_query_<规范化名称>}。例如
     * {@code Sales Report 2026.xlsx} 会生成 {@code custom_data_query_sales_report_2026}
     *
     * 同一非空逻辑表名会稳定生成相同的物理表名，因此可以在不同版本间复用
     *
     * @param originalFilename 原始文件名或逻辑表名，不可为 {@code null}
     * @return 符合 MySQL 命名和长度要求的物理表名
     */
    public String generatePhysicalTableName(String originalFilename) {
        String baseName = originalFilename;
        // 去掉扩展名
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = baseName.substring(0, dotIndex);
        }
        // 清理非法字符
        baseName = formatTableName(baseName);
        // 限制 baseName 长度，确保加上前缀后不超过 MySQL 表名上限 64
        int maxBaseLength = 64 - TABLE_PREFIX.length();
        if (baseName.length() > maxBaseLength) {
            baseName = baseName.substring(0, maxBaseLength);
        }
        baseName = baseName.replaceAll("_+$", "");
        if (baseName.isEmpty()) {
            baseName = "table";
        }
        return TABLE_PREFIX + baseName;
    }

    /**
     * 格式化表名，确保符合MySQL命名规范
     */
    private String formatTableName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "table_" + System.currentTimeMillis();
        }
        // 转换为小写
        String sanitized = name.toLowerCase(Locale.ROOT);
        // 替换非法字符为下划线
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_]", "_");
        // 确保以字母或下划线开头
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "t_" + sanitized;
        }
        // 限制长度（MySQL表名最大64字符）
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60);
        }
        // 去掉末尾的下划线
        sanitized = sanitized.replaceAll("_+$", "");
        return sanitized;
    }

    /**
     * 解析Excel文件
     */
    private List<List<String>> parseExcel(InputStream inputStream) throws IOException {
        List<List<String>> result = Lists.newArrayList();
        EasyExcel.read(inputStream, new ReadListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                List<String> row = Lists.newArrayList();
                // 获取当前行的最大索引
                int maxIndex = data.keySet().stream().max(Integer::compareTo).orElse(-1);
                // 按顺序填充每一列
                for (int i = 0; i <= maxIndex; i++) {
                    String value = data.getOrDefault(i, "");
                    row.add(value != null ? value : "");
                }
                result.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.info("Excel解析完成,共 {} 行", result.size());
            }
            /**
             * EasyExcel 默认将第一行视为表头，不会通过 ReadListener.invoke() 回调返回。所以 parseExcel 返回的数据实际上是从 Excel 的第二行开始的
             * 因此需要设置 headRowNumber(0) 告诉 EasyExcel 从第一行就开始读取数据
             */
        }).headRowNumber(0).sheet().doRead();
        return result;
    }
}
