package com.phuang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.phuang.mapper.TableMetaMapper;
import com.phuang.model.entity.TableMeta;
import com.phuang.service.KnowEngineTableMetaService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowEngineTableMetaServiceImpl extends ServiceImpl<TableMetaMapper, TableMeta> implements KnowEngineTableMetaService {

    /***
     * 查询所有激活状态下的表结构
     * <P>
     *     DATA_QUERY 同一逻辑表在所有版本中复用同一个物理表,因此只要表元数据存在且未被逻辑删除，就暴露给 Text2SQL
     * </P>
     * @return
     */
    @Override
    public List<TableMeta> listActiveForQuery() {
        List<TableMeta> allMetas = list();
        if (CollectionUtils.isEmpty(allMetas)) {
            return Collections.emptyList();
        }
        return allMetas.stream()
                .filter(meta -> StrUtil.isNotEmpty(meta.getCreateSql()))
                .collect(Collectors.toList());
    }
}
