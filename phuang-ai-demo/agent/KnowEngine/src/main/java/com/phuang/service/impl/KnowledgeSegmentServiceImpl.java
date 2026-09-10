package com.phuang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.KnowledgeSegmentMapper;
import com.phuang.model.entity.KnowledgeSegmentEntity;
import com.phuang.service.KnowledgeSegmentService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 知识片段表 Service 实现
 *
 * @author huangpeng
 * @since 2026/8/29
 */
@Service
@Slf4j
public class KnowledgeSegmentServiceImpl extends ServiceImpl<KnowledgeSegmentMapper, KnowledgeSegmentEntity> implements KnowledgeSegmentService {

    private static final String REDIS_KEY_PREFIX = "know-engine:knowledge-segment:text:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 通过 chunkId 获取文档
     * @param chunkId
     * @return
     */
    @Override
    public String getTextByChunkId(Serializable chunkId) {
        String cacheKey = REDIS_KEY_PREFIX + chunkId;
        String textCache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (Objects.nonNull(textCache)) {
            if (textCache.isEmpty()) {
                return null;
            }
            return textCache;
        }
        KnowledgeSegmentEntity segmentEntity = super.getOne(new LambdaQueryWrapper<KnowledgeSegmentEntity>()
                .eq(KnowledgeSegmentEntity::getChunkId, chunkId));
        if (Objects.nonNull(segmentEntity)) {
            stringRedisTemplate.opsForValue().set(cacheKey, segmentEntity.getText(), 30, TimeUnit.SECONDS);
            return segmentEntity.getText();
        } else {
            // 缓存空值，避免缓存击穿，重复查询数据库
            stringRedisTemplate.opsForValue().set(cacheKey, "");
        }
        return null;
    }
}
