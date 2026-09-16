package com.phuang.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 等待用户补充的信息，短期保存在 Redis 中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingClarification {

    /** 状态所属用户。 */
    private String userId;

    /** 已经合并过上下文的原问题。 */
    private String originalContent;

    /** 当前需要用户补充的字段。 */
    private String clarificationField;

    /** 车辆选择场景的候选 carId，顺序与文本编号一致。 */
    private List<String> candidateCarIds;
}
