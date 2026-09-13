package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.StaffInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工信息表 Mapper
 */
@Mapper
public interface StaffInfoMapper extends BaseMapper<StaffInfoEntity> {
}
