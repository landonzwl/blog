package com.np.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class NpApplication {

    static {
        new File("data").mkdirs();
    }

    public static void main(String[] args) {
        SpringApplication.run(NpApplication.class, args);
    }
}
