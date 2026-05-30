package com.keystone.auth;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Anchor class for Spring Boot test slices in {@code :infrastructure}. {@code @JooqTest} (and the
 * other slice annotations) walk the package tree upwards from the test class looking for a
 * {@code @SpringBootConfiguration}. The real launcher lives in {@code :boot}, which is not on this
 * module's test classpath, so we provide one here.
 *
 * <p>The class deliberately ships <em>nothing</em> beyond {@code @SpringBootApplication}: each
 * slice test imports the exact beans it needs via {@code @Import} or relies on the autoconfig the
 * slice opts into.
 */
@SpringBootApplication
public class TestApplication {}
