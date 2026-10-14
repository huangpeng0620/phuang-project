package com.sprouttrail.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sprouttrail.auth.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** 使用 MyBatis-Plus API 操作用户表。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
