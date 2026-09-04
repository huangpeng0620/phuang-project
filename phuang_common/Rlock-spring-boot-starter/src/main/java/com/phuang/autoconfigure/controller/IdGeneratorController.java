package com.phuang.autoconfigure.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * @author huangpeng
 * @description IdGeneratorController
 * @since 2023/8/18
 */

@RestController
@RequestMapping("/idGenerator")
public class IdGeneratorController {

    @RequestMapping("/getIdGeneratorToken")
    public String getIdGeneratorToken() {
        return UUID.randomUUID().toString();
    }

}
