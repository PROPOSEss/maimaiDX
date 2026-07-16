package com.maimai.maidx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.maimai.maidx.repository")
public class MaidxApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaidxApplication.class, args);
    }
}
