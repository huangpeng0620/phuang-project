package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.MyCarEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 我的车辆信息 Mapper 接口
 */
@Mapper
public interface MyCarMapper extends BaseMapper<MyCarEntity> {
}
