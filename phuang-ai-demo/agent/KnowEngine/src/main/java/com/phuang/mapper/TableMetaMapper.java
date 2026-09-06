package com.phuang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.phuang.model.entity.TableMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 表元数据 Mapper 接口
 */
@Mapper
public interface TableMetaMapper extends BaseMapper<TableMeta> {

    /**
     * 检查表是否存在
     * <P>
     *     information_schema.tables 是 MySQL 提供的系统元数据视图，用于查询当前数据库实例中所有表和视图的信息
     * </P>
     * @param tableName 表名
     * @return 存在返回1，不存在返回0
     */
    @Select("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = #{tableName} AND table_schema = DATABASE()")
    int checkTableExists(@Param("tableName") String tableName);

    /**
     * 执行动态SQL（建表）
     *
     * @param sql 建表SQL语句
     */
    @Update("${sql}")
    void executeCreateTable(@Param("sql") String sql);

}
