package com.heirloom.test

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Meta-annotation for all HeirloomsTest test classes.
 * Starts the Docker Compose stack once for the entire test suite.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(HeirloomsTestEnvironment::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
annotation class HeirloomsTest
