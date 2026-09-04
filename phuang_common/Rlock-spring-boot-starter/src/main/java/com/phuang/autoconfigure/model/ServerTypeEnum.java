package com.phuang.autoconfigure.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * huangpeng
 * 2023/8/15 19:34
 */
@AllArgsConstructor
@Getter
public enum ServerTypeEnum {

    SINGLE_SERVER(1, "SINGLE_SERVER"),
    CLUSTER_SERVERS(2, "CLUSTER_SERVERS"),
    MASTER_SLAVE_SERVERS(3, "MASTER_SLAVE_SERVERS"),
    REPLICATED_SERVERS(4, "REPLICATED_SERVERS"),
    SENTINEL_SERVERS(5, "SENTINEL_SERVERS");

    private int code;
    private String type;

}
