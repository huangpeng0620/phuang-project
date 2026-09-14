package com.phuang.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.CarInfoMapper;
import com.phuang.model.entity.CarInfoEntity;
import com.phuang.service.CarInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 车型信息服务实现类
 */
@Service
@Slf4j
public class CarInfoServiceImpl extends ServiceImpl<CarInfoMapper, CarInfoEntity> implements CarInfoService {

    @Override
    public List<CarInfoEntity> getCarInfoByBrand(String brand) {
        return this.list(new LambdaQueryWrapper<CarInfoEntity>()
                .eq(CarInfoEntity::getBrand, brand));
    }
}

