package com.phuang.service;

import com.phuang.model.dto.ChatParam;
import com.phuang.model.enums.RoleEnum;

public interface UserRoleService {

    public RoleEnum getUserRole(ChatParam chatParam);
}
