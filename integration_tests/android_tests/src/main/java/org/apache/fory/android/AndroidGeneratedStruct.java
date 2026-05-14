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

package org.apache.fory.android;

import java.util.Objects;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.ForyStruct;

@ForyStruct
public class AndroidGeneratedStruct {
  @ForyField(id = 1)
  public int id;

  @ForyField(id = 2)
  public String name;

  public AndroidGeneratedStruct() {}

  public AndroidGeneratedStruct(int id, String name) {
    this.id = id;
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AndroidGeneratedStruct)) {
      return false;
    }
    AndroidGeneratedStruct that = (AndroidGeneratedStruct) o;
    return id == that.id && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }
}
