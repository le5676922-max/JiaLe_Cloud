package org.example.cun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CunApplication {

    public static void main(String[] args) {
        SpringApplication.run(CunApplication.class, args);
    }

}
