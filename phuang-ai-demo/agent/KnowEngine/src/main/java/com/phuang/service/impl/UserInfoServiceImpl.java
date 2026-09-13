package com.phuang.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.UserInfoMapper;
import com.phuang.model.entity.UserInfoEntity;
import com.phuang.service.UserInfoService;
import org.springframework.stereotype.Service;

/**
 * 客户信息表 Service 实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfoEntity> implements UserInfoService {
}
