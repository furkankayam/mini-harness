package com.furkan.harness;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniHarnessApplication {

    public static void main(String[] args) {
        System.setOut(new PrintStream(
            new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(
            new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        SpringApplication.run(MiniHarnessApplication.class, args);
    }
}
