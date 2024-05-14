package com.sixtymeters.thereabout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class ThereaboutApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThereaboutApplication.class, args);
    }

}
