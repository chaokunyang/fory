/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fory.logging;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class Slf4jLoggerTest {

  @Test
  public void testInfo() {
    Slf4jLogger logger = new Slf4jLogger((Slf4jLoggerTest.class));
    ForyLogger foryLogger = new ForyLogger((Slf4jLoggerTest.class));
    logger.info("testInfo");
    logger.info("testInfo {}", "placeHolder");
    logger.warn("testInfo {}", "placeHolder");
    foryLogger.info("testInfo");
    foryLogger.info("testInfo {}", "placeHolder");
    foryLogger.warn("testInfo {}", "placeHolder");
    int previousLogLevel = LoggerFactory.getLogLevel();
    try {
      LoggerFactory.disableLogging();
      logger.error("testInfo {}", "placeHolder", new Exception("test log"));
      logger.error("testInfo {}", "placeHolder", new Exception("test log"));
      foryLogger.error("testInfo {}", "placeHolder", new Exception("test log"));
      foryLogger.error(null, new Exception("test log"));
      foryLogger.error("test log {} {}", new Exception("test log {} {}"));
    } finally {
      LoggerFactory.setLogLevel(previousLogLevel);
    }
  }

  @Test
  public void testDefaultLogLevel() {
    assertEquals(LoggerFactory.getLogLevel(), LogLevel.DEFAULT_LEVEL);
    String envLogLevel = System.getenv("FORY_LOG_LEVEL");
    boolean debugOutputEnabled = "1".equals(System.getenv("ENABLE_FORY_DEBUG_OUTPUT"));
    assertEquals(
        LogLevel.DEFAULT_LEVEL, LogLevel.getDefaultLogLevel(envLogLevel, debugOutputEnabled));
    if (envLogLevel == null) {
      assertEquals(
          LogLevel.DEFAULT_LEVEL, debugOutputEnabled ? LogLevel.INFO_LEVEL : LogLevel.WARN_LEVEL);
    }
    assertEquals(LogLevel.getDefaultLogLevel(null, false), LogLevel.WARN_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("", false), LogLevel.WARN_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel(null, true), LogLevel.INFO_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("", true), LogLevel.INFO_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("error", true), LogLevel.ERROR_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("WARN", true), LogLevel.WARN_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("Info", false), LogLevel.INFO_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("DEBUG", false), LogLevel.DEBUG_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("unknown", false), LogLevel.WARN_LEVEL);
    assertEquals(LogLevel.getDefaultLogLevel("unknown", true), LogLevel.INFO_LEVEL);
  }
}
