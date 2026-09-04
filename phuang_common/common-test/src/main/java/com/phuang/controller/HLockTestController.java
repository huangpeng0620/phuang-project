package com.phuang.controller;

import com.phuang.hlock.annotation.HLock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/test")
public class HLockTestController {

    /**
     * 同时请求相同 id 时，只有第一个请求能获取锁并执行。
     */
    @GetMapping("/hlock/{id}")
    @HLock(prefixKey = "common-test", key = "#id", waitTime = 0, leaseTime = 10, unit = TimeUnit.SECONDS)
    public String testHLock(@PathVariable("id") Long id, @RequestParam(value = "workMillis", defaultValue = "3000") long workMillis) throws Exception {
        Thread.sleep(workMillis);
        return "HLock test success, id=" + id;
    }
}
