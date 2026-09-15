package com.phuang.service;

import com.phuang.model.entity.MyCarEntity;

import java.util.List;

/**
 * 我的车辆信息服务接口
 */
public interface MyCarService {

    /**
     * 根据用户ID获取车辆信息
     * @param userId
     * @return
     */
    List<MyCarEntity> getCarByUserId(String userId);

    MyCarEntity getCarByUser(String carId,String userId);

}
