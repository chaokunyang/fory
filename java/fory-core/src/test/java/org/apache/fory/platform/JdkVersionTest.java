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

package org.apache.fory.platform;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class JdkVersionTest {

  @Test
  public void testParseMajorVersion() {
    assertEquals(JdkVersion.parseMajorVersion("1.8"), 8);
    assertEquals(JdkVersion.parseMajorVersion("1.8.0_462"), 8);
    assertEquals(JdkVersion.parseMajorVersion("9"), 9);
    assertEquals(JdkVersion.parseMajorVersion("11.0.22"), 11);
    assertEquals(JdkVersion.parseMajorVersion("17.0.16"), 17);
    assertEquals(JdkVersion.parseMajorVersion("25.0.1"), 25);
    assertEquals(JdkVersion.parseMajorVersion("26-ea"), 26);
    assertEquals(JdkVersion.parseMajorVersion(""), 0);
  }
}
