package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.UserInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户信息表 Mapper
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfoEntity> {
}
