package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识片段表 Mapper 接口
 */
@Mapper
public interface KnowledgeSegmentMapper extends BaseMapper<KnowledgeSegmentEntity> {
}
