package com.phuang;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.phuang.bussiness.**.mapper")
@SpringBootApplication
public class GogoTravelApplication {
    public static void main(String[] args) {
        SpringApplication.run(GogoTravelApplication.class, args);
    }
}
