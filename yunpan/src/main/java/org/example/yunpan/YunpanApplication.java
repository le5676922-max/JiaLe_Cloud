package org.example.yunpan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.example.yunpan.mapper")
@EnableScheduling
public class YunpanApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunpanApplication.class, args);
    }

}
