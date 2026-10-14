package com.sprouttrail.image.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sprouttrail.image.model.ImageGenerationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 使用 MyBatis-Plus 通用 Mapper 操作文生图记录。
 */
@Mapper
public interface ImageGenerationRecordMapper extends BaseMapper<ImageGenerationRecord> {
}
