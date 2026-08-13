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

package org.apache.fory.annotation;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import lombok.Data;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.meta.FieldInfo;
import org.apache.fory.meta.TypeDef;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ForyFieldTagIdTest extends ForyTestBase {

  @Data
  public static class TestClass {
    @ForyField(id = 0)
    public String fieldWithTag0;

    @ForyField(id = 5)
    public String fieldWithTag5;

    @ForyField public String fieldOptingOutOfTag;

    public String fieldWithoutAnnotation;
  }

  @Test(dataProvider = "languages")
  public void testFieldInfoCreationWithTagIds(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .requireClassRegistration(false)
            .withCompatible(xlang)
            .build();

    if (xlang) {
      fory.register(TestClass.class, "test.TestClass");
    }

    TypeDef typeDef = TypeDef.buildTypeDef(fory.getTypeResolver(), TestClass.class);
    List<FieldInfo> fieldsInfo = typeDef.getFieldsInfo();

    // Should have 4 fields
    assertEquals(fieldsInfo.size(), 4);

    // Find each field by name and verify tag behavior
    FieldInfo field0 = findFieldByName(fieldsInfo, "fieldWithTag0");
    FieldInfo field5 = findFieldByName(fieldsInfo, "fieldWithTag5");
    FieldInfo fieldOptOut = findFieldByName(fieldsInfo, "fieldOptingOutOfTag");
    FieldInfo fieldNoAnnotation = findFieldByName(fieldsInfo, "fieldWithoutAnnotation");

    // Verify field with id=0 has tag
    assertTrue(field0.hasFieldId(), "Field with id=0 should have tag in xlang=" + xlang);
    assertEquals(
        field0.getFieldId(), 0, "Field with id=0 should have tag value 0 in xlang=" + xlang);

    // Verify field with id=5 has tag
    assertTrue(field5.hasFieldId(), "Field with id=5 should have tag in xlang=" + xlang);
    assertEquals(
        field5.getFieldId(), 5, "Field with id=5 should have tag value 5 in xlang=" + xlang);

    // Verify field with annotation but no ID does NOT have tag
    assertFalse(
        fieldOptOut.hasFieldId(),
        "Field without configured ID should NOT have tag in xlang=" + xlang);
    assertEquals(
        fieldOptOut.getFieldName(),
        "fieldOptingOutOfTag",
        "Field without configured ID should use field name in xlang=" + xlang);

    // Verify field without annotation does NOT have tag
    assertFalse(
        fieldNoAnnotation.hasFieldId(),
        "Field without annotation should NOT have tag (use field name) in xlang=" + xlang);
    assertEquals(
        fieldNoAnnotation.getFieldName(),
        "fieldWithoutAnnotation",
        "Field without annotation should use field name in xlang=" + xlang);
  }

  @DataProvider(name = "languages")
  public Object[][] languages() {
    return new Object[][] {{false}, {true}};
  }

  /** Helper method to find a FieldInfo by field name */
  private FieldInfo findFieldByName(List<FieldInfo> fieldsInfo, String name) {
    for (FieldInfo fieldInfo : fieldsInfo) {
      if (fieldInfo.getFieldName().equals(name)) {
        return fieldInfo;
      }
    }
    throw new AssertionError("Field not found: " + name);
  }
}
