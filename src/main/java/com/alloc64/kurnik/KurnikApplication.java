package com.alloc64.kurnik;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication(scanBasePackages = "com.alloc64.*")
@EntityScan("com.alloc64.*")
public class KurnikApplication {
    public static void main(String[] args) {
        SpringApplication.run(KurnikApplication.class, args);
    }
}
