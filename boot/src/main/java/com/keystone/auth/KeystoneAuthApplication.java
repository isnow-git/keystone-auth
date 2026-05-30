package com.keystone.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.keystone.auth")
public class KeystoneAuthApplication {

  public static void main(String[] args) {
    SpringApplication.run(KeystoneAuthApplication.class, args);
  }
}
