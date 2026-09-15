package com.phuang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.MyCarMapper;
import com.phuang.model.entity.MyCarEntity;
import com.phuang.service.MyCarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 我的车辆信息服务实现类
 */
@Service
@Slf4j
public class MyCarServiceImpl extends ServiceImpl<MyCarMapper, MyCarEntity> implements MyCarService {

    @Override
    public List<MyCarEntity> getCarByUserId(String userId) {
        return this.list(new LambdaQueryWrapper<MyCarEntity>()
                .eq(MyCarEntity::getUserId, userId));
    }

    @Override
    public MyCarEntity getCarByUser(String carId, String userId) {
        return this.getOne(new LambdaQueryWrapper<MyCarEntity>()
                .eq(MyCarEntity::getCarId, carId)
                .eq(MyCarEntity::getUserId, userId));
    }


}

