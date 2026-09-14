package com.phuang.service;

import com.phuang.model.entity.CarInfoEntity;

import java.util.List;

/**
 * 车型信息服务接口
 */
public interface CarInfoService {

    /**
     * 根据品牌获取车型列表
     */
    List<CarInfoEntity> getCarInfoByBrand(String brand);

}
