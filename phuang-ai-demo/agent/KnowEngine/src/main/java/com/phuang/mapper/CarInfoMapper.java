package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.CarInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车型信息 Mapper 接口
 */
@Mapper
public interface CarInfoMapper extends BaseMapper<CarInfoEntity> {
}
