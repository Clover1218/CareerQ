package com.careerq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.careerq.dao")
public class CareerQApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerQApplication.class, args);
    }
}
