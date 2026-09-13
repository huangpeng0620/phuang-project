package com.phuang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.StaffInfoMapper;
import com.phuang.model.entity.StaffInfoEntity;
import com.phuang.service.StaffInfoService;
import org.springframework.stereotype.Service;

/**
 * 员工信息表 Service 实现类
 */
@Service
public class StaffInfoServiceImpl extends ServiceImpl<StaffInfoMapper, StaffInfoEntity> implements StaffInfoService {

    /**
     * 根据工号查询员工信息
     * @param empId
     * @return
     */
    @Override
    public StaffInfoEntity getByEmpId(String empId) {
        return this.getOne(new LambdaQueryWrapper<StaffInfoEntity>().eq(StaffInfoEntity::getEmpId, empId));
    }
}