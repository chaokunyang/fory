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

package org.apache.fory.json.meta;

import java.util.HashMap;
import java.util.Map;
import org.apache.fory.json.reader.JsonReader;

public final class JsonFieldTable {
  private final Map<String, JsonFieldInfo> fields;
  private final Map<String, Integer> indexes;
  private final String[] tableNames;
  private final int[] tableHashes;
  private final JsonFieldInfo[] tableFields;
  private final int[] tableIndexes;
  private final int tableMask;

  public JsonFieldTable(JsonFieldInfo[] readFields) {
    fields = new HashMap<>(readFields.length * 2);
    indexes = new HashMap<>(readFields.length * 2);
    int tableSize = 1;
    while (tableSize < readFields.length * 4) {
      tableSize <<= 1;
    }
    tableNames = new String[tableSize];
    tableHashes = new int[tableSize];
    tableFields = new JsonFieldInfo[tableSize];
    tableIndexes = new int[tableSize];
    tableMask = tableSize - 1;
    for (int i = 0; i < readFields.length; i++) {
      JsonFieldInfo field = readFields[i];
      String name = field.name();
      fields.put(field.name(), field);
      indexes.put(field.name(), i);
      put(name, field, i);
    }
  }

  public JsonFieldInfo get(String name) {
    return fields.get(name);
  }

  public JsonFieldInfo get(JsonReader reader, int start, int end, int hash) {
    String[] localNames = tableNames;
    int[] localHashes = tableHashes;
    JsonFieldInfo[] localFields = tableFields;
    int mask = tableMask;
    int index = hash & mask;
    while (true) {
      String name = localNames[index];
      if (name == null) {
        return null;
      }
      if (localHashes[index] == hash && reader.regionEquals(name, start, end)) {
        return localFields[index];
      }
      index = (index + 1) & mask;
    }
  }

  public int index(String name) {
    Integer index = indexes.get(name);
    return index == null ? -1 : index.intValue();
  }

  public int index(JsonReader reader, int start, int end, int hash) {
    String[] localNames = tableNames;
    int[] localHashes = tableHashes;
    int[] localIndexes = tableIndexes;
    int mask = tableMask;
    int index = hash & mask;
    while (true) {
      String name = localNames[index];
      if (name == null) {
        return -1;
      }
      if (localHashes[index] == hash && reader.regionEquals(name, start, end)) {
        return localIndexes[index];
      }
      index = (index + 1) & mask;
    }
  }

  private void put(String name, JsonFieldInfo field, int fieldIndex) {
    int hash = name.hashCode();
    int index = hash & tableMask;
    while (tableNames[index] != null) {
      index = (index + 1) & tableMask;
    }
    tableNames[index] = name;
    tableHashes[index] = hash;
    tableFields[index] = field;
    tableIndexes[index] = fieldIndex;
  }
}
