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

package org.apache.fory.serializer;

import org.apache.fory.Fory;
import org.apache.fory.collection.ObjectIntMap;
import org.apache.fory.memory.MemoryBuffer;

/**
 * Base class for meta-shared layer serializers. This provides the common interface for both
 * interpreter mode ({@link MetaSharedLayerSerializer}) and JIT-generated serializers.
 *
 * @see MetaSharedLayerSerializer
 * @see org.apache.fory.builder.MetaSharedLayerCodecBuilder
 */
public abstract class MetaSharedLayerSerializerBase<T> extends AbstractObjectSerializer<T> {

  public MetaSharedLayerSerializerBase(Fory fory, Class<T> type) {
    super(fory, type);
  }

  /**
   * Read fields and set values on the provided object. Note: When meta share is enabled, the caller
   * (typically ObjectStreamSerializer) is responsible for reading the layer class meta first. This
   * method only reads field data, not the layer class meta.
   *
   * @param buffer the memory buffer to read from
   * @param obj the object to set field values on
   * @return the object with fields set
   */
  public abstract T readAndSetFields(MemoryBuffer buffer, T obj);

  /**
   * Set field values on target object from putFields data. This method maps field names from
   * ObjectStreamClass to actual class fields and sets matching values.
   *
   * @param obj the target object
   * @param fieldIndexMap mapping from field name to index in vals array
   * @param vals the values array from putFields
   */
  @SuppressWarnings("rawtypes")
  public abstract void setFieldValuesFromPutFields(
      Object obj, ObjectIntMap fieldIndexMap, Object[] vals);

  /**
   * Get field values from object for putFields format. This method reads actual class field values
   * and puts them into an array based on ObjectStreamClass field order.
   *
   * @param obj the source object
   * @param fieldIndexMap mapping from field name to index in result array
   * @param arraySize size of the result array
   * @return array of field values in putFields order
   */
  @SuppressWarnings("rawtypes")
  public abstract Object[] getFieldValuesForPutFields(
      Object obj, ObjectIntMap fieldIndexMap, int arraySize);
}
