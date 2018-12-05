package com.rbkmoney.shumway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication(scanBasePackages = "com.rbkmoney.shumway")
public class ShumwayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShumwayApplication.class, args);
    }
}
