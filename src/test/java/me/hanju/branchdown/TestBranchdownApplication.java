package me.hanju.branchdown;

import org.springframework.boot.SpringApplication;

import me.hanju.branchdown.config.TestcontainersConfig;

/**
 * Test application entry point for bootTestRun.
 *
 * <pre>
 * ./gradlew bootTestRun
 * </pre>
 */
public class TestBranchdownApplication {

  public static void main(String[] args) {
    SpringApplication.from(BranchdownApplication::main)
        .with(TestcontainersConfig.class)
        .run(args);
  }
}
