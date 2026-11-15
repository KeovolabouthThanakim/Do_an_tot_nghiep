package com.kosis.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync   // gửi email chạy nền (EmailService.send)
public class KosisShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(KosisShopApplication.class, args);
    }
}
