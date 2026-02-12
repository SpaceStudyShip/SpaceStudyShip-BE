package com.elipair.spacestudyship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpaceStudyShipApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaceStudyShipApplication.class, args);
    }
}
