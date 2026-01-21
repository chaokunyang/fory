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

package org.apache.fory.meta;

import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.config.Language;
import org.apache.fory.data.AllUnsignedFields;
import org.apache.fory.data.UnsignedArrayFields;
import org.apache.fory.data.UnsignedScalarFields;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.ClassDefTest.TestFieldsOrderClass1;
import org.apache.fory.meta.ClassDefTest.TestFieldsOrderClass2;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.type.Types;
import org.testng.annotations.Test;

public class TypeDefTest extends ForyTestBase {

  @Test
  public void testClassDefSerialization() {
    Fory fory = builder().withLanguage(Language.XLANG).withMetaShare(true).build();
    fory.register(TestFieldsOrderClass1.class, "demo.Class1");
    ClassDef classDef = ClassDef.buildClassDef(fory, TestFieldsOrderClass1.class, true);
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(32);
    classDef.writeClassDef(buffer);
    ClassDef classDef1 = ClassDef.readClassDef(fory, buffer);
    assertEquals(classDef1.getClassName(), classDef.getClassName());
    assertEquals(classDef1, classDef);
  }

  @Test
  public void testClassDefInheritanceDuplicatedFields() {
    Fory fory = builder().withLanguage(Language.XLANG).withMetaShare(true).build();
    fory.register(TestFieldsOrderClass2.class, "demo.Class2");
    ClassDef classDef = ClassDef.buildClassDef(fory, TestFieldsOrderClass2.class);
    assertEquals(classDef.getClassName(), TestFieldsOrderClass2.class.getName());
    // xtype ignore duplicate fields from parent class.
    assertEquals(
        classDef.getFieldsInfo().size(),
        ReflectionUtils.getFields(TestFieldsOrderClass2.class, true).size() - 1);
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(32);
    classDef.writeClassDef(buffer);
    ClassDef classDef1 = ClassDef.readClassDef(fory, buffer);
    assertEquals(classDef1.getClassName(), classDef.getClassName());
    assertEquals(classDef1, classDef);
  }

  @Test
  public void testUnsignedScalarFieldsTypeIds() {
    Fory fory = builder().withLanguage(Language.XLANG).withMetaShare(true).build();
    fory.register(UnsignedScalarFields.class, "test.UnsignedScalarFields");
    ClassDef classDef = ClassDef.buildClassDef(fory, UnsignedScalarFields.class);

    // Build expected type IDs map: field name -> type id
    Map<String, Integer> expectedTypeIds = new HashMap<>();
    expectedTypeIds.put("u8", Types.UINT8);
    expectedTypeIds.put("u16", Types.UINT16);
    expectedTypeIds.put("u32", Types.UINT32);
    expectedTypeIds.put("u32Var", Types.VAR_UINT32);
    expectedTypeIds.put("u64", Types.UINT64);
    expectedTypeIds.put("u64Var", Types.VAR_UINT64);
    expectedTypeIds.put("u64Tagged", Types.TAGGED_UINT64);

    for (FieldInfo fieldInfo : classDef.getFieldsInfo()) {
      String fieldName = fieldInfo.getFieldName();
      int actualTypeId = fieldInfo.getFieldType().typeId;
      Integer expectedTypeId = expectedTypeIds.get(fieldName);
      assertEquals(
          actualTypeId,
          expectedTypeId.intValue(),
          "Field " + fieldName + " should have type id " + expectedTypeId);
    }
  }

  @Test
  public void testUnsignedArrayFieldsTypeIds() {
    Fory fory = builder().withLanguage(Language.XLANG).withMetaShare(true).build();
    fory.register(UnsignedArrayFields.class, "test.UnsignedArrayFields");
    ClassDef classDef = ClassDef.buildClassDef(fory, UnsignedArrayFields.class);

    // Build expected type IDs map: field name -> type id
    Map<String, Integer> expectedTypeIds = new HashMap<>();
    expectedTypeIds.put("u8Array", Types.UINT8_ARRAY);
    expectedTypeIds.put("u16Array", Types.UINT16_ARRAY);
    expectedTypeIds.put("u32Array", Types.UINT32_ARRAY);
    expectedTypeIds.put("u64Array", Types.UINT64_ARRAY);

    for (FieldInfo fieldInfo : classDef.getFieldsInfo()) {
      String fieldName = fieldInfo.getFieldName();
      int actualTypeId = fieldInfo.getFieldType().typeId;
      Integer expectedTypeId = expectedTypeIds.get(fieldName);
      assertEquals(
          actualTypeId,
          expectedTypeId.intValue(),
          "Field " + fieldName + " should have type id " + expectedTypeId);
    }
  }

  @Test
  public void testAllUnsignedFieldsTypeIds() {
    Fory fory = builder().withLanguage(Language.XLANG).withMetaShare(true).build();
    fory.register(AllUnsignedFields.class, "test.AllUnsignedFields");
    ClassDef classDef = ClassDef.buildClassDef(fory, AllUnsignedFields.class);

    // Build expected type IDs map: field name -> type id
    Map<String, Integer> expectedTypeIds = new HashMap<>();
    // Scalar fields
    expectedTypeIds.put("u8", Types.UINT8);
    expectedTypeIds.put("u16", Types.UINT16);
    expectedTypeIds.put("u32", Types.UINT32);
    expectedTypeIds.put("u64", Types.UINT64);
    // Array fields
    expectedTypeIds.put("u8Array", Types.UINT8_ARRAY);
    expectedTypeIds.put("u16Array", Types.UINT16_ARRAY);
    expectedTypeIds.put("u32Array", Types.UINT32_ARRAY);
    expectedTypeIds.put("u64Array", Types.UINT64_ARRAY);

    for (FieldInfo fieldInfo : classDef.getFieldsInfo()) {
      String fieldName = fieldInfo.getFieldName();
      int actualTypeId = fieldInfo.getFieldType().typeId;
      Integer expectedTypeId = expectedTypeIds.get(fieldName);
      assertEquals(
          actualTypeId,
          expectedTypeId.intValue(),
          "Field " + fieldName + " should have type id " + expectedTypeId);
    }
  }
}
