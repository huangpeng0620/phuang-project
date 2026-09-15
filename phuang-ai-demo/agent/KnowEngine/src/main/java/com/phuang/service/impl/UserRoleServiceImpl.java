package com.phuang.service.impl;

import com.phuang.model.dto.ChatParam;
import com.phuang.model.entity.MyCarEntity;
import com.phuang.model.entity.StaffInfoEntity;
import com.phuang.model.enums.ChatSource;
import com.phuang.model.enums.RoleEnum;
import com.phuang.model.enums.StaffStatus;
import com.phuang.service.MyCarService;
import com.phuang.service.StaffInfoService;
import com.phuang.service.UserRoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserRoleServiceImpl implements UserRoleService {

    private static final String STAFF_LOGIN_PREFIX = "staff_";

    @Resource
    private StaffInfoService staffInfoService;

    @Resource
    private MyCarService myCarService;

    @Override
    public RoleEnum getUserRole(ChatParam chatParam) {
        if (chatParam.getChatSource() == ChatSource.STAFF_DING) {
            return RoleEnum.CUSTOMER_SERVICE;
        }

        //再次查询一下车辆，避免水平权限漏洞
        MyCarEntity myCar = myCarService.getCarByUser(chatParam.getIntentRecognitionResult().entities().car_id(), chatParam.getUserId());
        if (Objects.nonNull(myCar)) {
            return RoleEnum.OWNER;
        }

        StaffInfoEntity staffInfo = null;
        String userId = chatParam.getUserId();
        if (Objects.nonNull(userId) && userId.startsWith(STAFF_LOGIN_PREFIX)) {
            String empId = userId.substring(STAFF_LOGIN_PREFIX.length());
            staffInfo = staffInfoService.getByEmpId(empId);
        }
        if (Objects.nonNull(staffInfo) && staffInfo.getStatus() == StaffStatus.ON_JOB) {
            return RoleEnum.CUSTOMER_SERVICE;
        }
        return RoleEnum.VISITOR;
    }
}
