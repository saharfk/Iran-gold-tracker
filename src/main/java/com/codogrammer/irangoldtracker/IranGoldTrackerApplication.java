package com.codogrammer.irangoldtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IranGoldTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IranGoldTrackerApplication.class, args);
    }
}