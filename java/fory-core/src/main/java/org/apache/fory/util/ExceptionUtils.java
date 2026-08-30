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

package org.apache.fory.util;

import java.lang.reflect.Field;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.reflect.ReflectionUtils;

/** Util for java exceptions. */
public class ExceptionUtils {
  private static final Field detailMessageField;

  static {
    try {
      detailMessageField = Throwable.class.getDeclaredField("detailMessage");
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Try to set `StackOverflowError` exception message. Returns passed exception if set succeed, or
   * null if failed.
   */
  public static StackOverflowError trySetStackOverflowErrorMessage(
      StackOverflowError e, String message) {
    if (detailMessageField != null && !AndroidSupport.IS_ANDROID) {
      ReflectionUtils.setObjectFieldValue(e, detailMessageField, message);
      return e;
    } else {
      return e;
    }
  }

  // Do not attach read-reference tables to the exception. Root cleanup must release the failed
  // object graph even when application code retains the exception for later inspection.
  public static RuntimeException handleReadFailed(Throwable t) {
    if (t instanceof ForyException) {
      throw (ForyException) t;
    }
    throw new DeserializationException("Failed to deserialize input", t);
  }

  public static void ignore(Object... args) {}

  /** Raises an exception bypassing compiler checks for checked exceptions. */
  public static RuntimeException throwException(Throwable t) {
    throw ExceptionUtils.<RuntimeException>throwEvadingChecks(t);
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <E extends Throwable> E throwEvadingChecks(Throwable throwable) throws E {
    throw (E) throwable;
  }
}
