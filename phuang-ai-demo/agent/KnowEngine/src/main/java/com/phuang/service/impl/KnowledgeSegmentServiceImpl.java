package com.phuang.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.KnowledgeSegmentMapper;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import com.phuang.service.KnowledgeSegmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 知识片段表 Service 实现
 *
 * @author huangpeng
 * @since 2026/8/29
 */
@Service
@Slf4j
public class KnowledgeSegmentServiceImpl
        extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegmentEntity>
        implements KnowledgeSegmentService {
}
