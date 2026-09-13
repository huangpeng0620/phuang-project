package com.phuang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.phuang.model.entity.StaffInfoEntity;

/**
 *
 * @description StaffInfoService
 * @author huangpeng
 * @since 2026/9/13
 */

public interface StaffInfoService extends IService<StaffInfoEntity> {

    StaffInfoEntity getByEmpId(String empId);
}
