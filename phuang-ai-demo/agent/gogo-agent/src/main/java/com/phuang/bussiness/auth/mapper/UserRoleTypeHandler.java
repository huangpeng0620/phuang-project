package com.phuang.bussiness.auth.mapper;

import com.phuang.bussiness.auth.entity.UserRole;
import org.apache.ibatis.type.EnumTypeHandler;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 按枚举名称写入角色，读取时兼容原来大小写不统一的账号。 */
@Component
@MappedTypes(UserRole.class)
public class UserRoleTypeHandler extends EnumTypeHandler<UserRole> {

    public UserRoleTypeHandler() {
        super(UserRole.class);
    }

    @Override
    public UserRole getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readRole(rs.getString(columnName));
    }

    @Override
    public UserRole getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readRole(rs.getString(columnIndex));
    }

    @Override
    public UserRole getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readRole(cs.getString(columnIndex));
    }

    private static UserRole readRole(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UserRole.fromName(value);
        } catch (IllegalArgumentException e) {
            // 历史记录中的未知角色保持无管理员权限，账号本身仍可正常读取。
            return null;
        }
    }
}
