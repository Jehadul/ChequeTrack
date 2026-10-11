package com.chequetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChequeTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChequeTrackApplication.class, args);
    }
}
