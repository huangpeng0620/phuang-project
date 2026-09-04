package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.KnowledgeDocumentVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档版本表 Mapper 接口
 */
@Mapper
public interface KnowledgeDocumentVersionMapper extends BaseMapper<KnowledgeDocumentVersionEntity> {
}
