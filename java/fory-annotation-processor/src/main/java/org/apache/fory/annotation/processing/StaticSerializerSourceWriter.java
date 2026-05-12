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

package org.apache.fory.annotation.processing;

final class StaticSerializerSourceWriter {
  private static final int DISPATCH_GROUP_SIZE = 8;

  private final SourceStruct struct;
  private final StringBuilder builder = new StringBuilder(16384);

  StaticSerializerSourceWriter(SourceStruct struct) {
    this.struct = struct;
  }

  String write() {
    writeHeader();
    writeClassStart();
    writeDescriptors();
    writeConstructors();
    writeSerializerMethods();
    writeSchemaConsistentRead();
    writeWriteGroups();
    writeReadGroups();
    writeCompatibleRead();
    writeCopy();
    writeDescriptorHelpers();
    builder.append("}\n");
    return builder.toString();
  }

  private void writeHeader() {
    if (!struct.packageName.isEmpty()) {
      builder.append("package ").append(struct.packageName).append(";\n\n");
    }
    builder.append("import java.util.ArrayList;\n");
    builder.append("import java.util.Arrays;\n");
    builder.append("import java.util.Collections;\n");
    builder.append("import java.util.List;\n");
    builder.append("import org.apache.fory.annotation.ForyField;\n");
    builder.append("import org.apache.fory.context.CopyContext;\n");
    builder.append("import org.apache.fory.context.ReadContext;\n");
    builder.append("import org.apache.fory.context.WriteContext;\n");
    builder.append("import org.apache.fory.memory.MemoryBuffer;\n");
    builder.append("import org.apache.fory.meta.TypeDef;\n");
    builder.append("import org.apache.fory.meta.TypeExtMeta;\n");
    builder.append("import org.apache.fory.reflect.TypeRef;\n");
    builder.append("import org.apache.fory.resolver.TypeResolver;\n");
    builder.append("import org.apache.fory.serializer.FieldGroups;\n");
    builder.append("import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;\n");
    builder.append("import org.apache.fory.serializer.StaticGeneratedStructSerializer;\n");
    builder.append("import org.apache.fory.type.Descriptor;\n");
    builder.append("import org.apache.fory.type.Types;\n\n");
  }

  private void writeClassStart() {
    builder.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n");
    builder
        .append("public final class ")
        .append(struct.serializerName)
        .append(" extends StaticGeneratedStructSerializer<")
        .append(struct.typeName)
        .append("> {\n");
    builder
        .append("  private static final boolean HAS_NESTED_COMPATIBLE_STRUCT_FIELDS = ")
        .append(struct.hasNestedCompatibleStructFields)
        .append(";\n");
    if (struct.debug) {
      builder.append("  private static final boolean FORY_STRUCT_DEBUG = true;\n");
    }
    builder.append("  private static final List<Descriptor> DESCRIPTORS = buildDescriptors();\n\n");
    builder.append("  private final SerializationFieldInfo[] buildInFields;\n");
    builder.append("  private final int[] buildInFieldIds;\n");
    builder.append("  private final SerializationFieldInfo[] containerFields;\n");
    builder.append("  private final int[] containerFieldIds;\n");
    builder.append("  private final SerializationFieldInfo[] otherFields;\n");
    builder.append("  private final int[] otherFieldIds;\n");
    builder.append("  private final SerializationFieldInfo[] fieldsById;\n");
    builder.append("  private final int classVersionHash;\n");
    builder.append("  private final boolean sameSchemaCompatible;\n\n");
  }

  private void writeDescriptors() {
    builder.append("  private static List<Descriptor> buildDescriptors() {\n");
    builder
        .append("    ArrayList<Descriptor> descriptors = new ArrayList<Descriptor>(")
        .append(struct.fields.size())
        .append(");\n");
    for (SourceField field : struct.fields) {
      builder
          .append("    descriptors.add(new Descriptor(")
          .append(field.typeNode.typeRefExpression())
          .append(", \"")
          .append(escape(field.typeNode.typeName))
          .append("\", \"")
          .append(escape(field.name))
          .append("\", ")
          .append(field.modifiers)
          .append(", \"")
          .append(escape(field.declaringClass))
          .append("\", ")
          .append(field.hasForyField)
          .append(", ")
          .append(field.foryFieldId)
          .append(", ")
          .append(field.nullable)
          .append(", ")
          .append(field.trackingRef)
          .append(", ForyField.Dynamic.")
          .append(field.dynamic)
          .append(", ")
          .append(field.arrayType)
          .append("));\n");
    }
    builder.append("    return Collections.unmodifiableList(descriptors);\n");
    builder.append("  }\n\n");
    builder.append("  private static List<Descriptor> getGeneratedDescriptors() {\n");
    builder.append("    return DESCRIPTORS;\n");
    builder.append("  }\n\n");
    builder.append("  @Override\n");
    builder.append("  public List<Descriptor> getDescriptors() {\n");
    builder.append("    return DESCRIPTORS;\n");
    builder.append("  }\n\n");
  }

  private void writeConstructors() {
    builder
        .append("  public ")
        .append(struct.serializerName)
        .append("(TypeResolver typeResolver, Class<?> type) {\n");
    builder.append("    super(typeResolver, type);\n");
    writeConstructorBody("buildFieldGroups(DESCRIPTORS)", "false");
    builder.append("  }\n\n");
    builder
        .append("  public ")
        .append(struct.serializerName)
        .append("(TypeResolver typeResolver, Class<?> type, TypeDef typeDef) {\n");
    builder.append("    super(typeResolver, type, typeDef, DESCRIPTORS);\n");
    writeConstructorBody(
        "buildLocalFieldGroups(DESCRIPTORS)",
        "typeDef != null && !HAS_NESTED_COMPATIBLE_STRUCT_FIELDS && typeDef.getId() == TypeDef.buildTypeDef(typeResolver, type).getId()");
    builder.append("  }\n\n");
  }

  private void writeConstructorBody(String fieldGroupsExpression, String sameSchemaExpression) {
    builder.append("    FieldGroups fieldGroups = ").append(fieldGroupsExpression).append(";\n");
    builder.append("    this.buildInFields = fieldGroups.buildInFields;\n");
    builder.append("    this.buildInFieldIds = localFieldIds(buildInFields, DESCRIPTORS);\n");
    builder.append("    this.containerFields = fieldGroups.containerFields;\n");
    builder.append("    this.containerFieldIds = localFieldIds(containerFields, DESCRIPTORS);\n");
    builder.append("    this.otherFields = fieldGroups.userTypeFields;\n");
    builder.append("    this.otherFieldIds = localFieldIds(otherFields, DESCRIPTORS);\n");
    builder.append("    this.fieldsById = new SerializationFieldInfo[DESCRIPTORS.size()];\n");
    builder.append("    SerializationFieldInfo[] allFields = fieldGroups.allFields;\n");
    builder.append("    int[] allFieldIds = localFieldIds(allFields, DESCRIPTORS);\n");
    builder.append("    for (int i = 0; i < allFields.length; i++) {\n");
    builder.append("      this.fieldsById[allFieldIds[i]] = allFields[i];\n");
    builder.append("    }\n");
    builder.append(
        "    this.classVersionHash = typeResolver.checkClassVersion() ? computeClassVersionHash(DESCRIPTORS) : 0;\n");
    builder.append("    this.sameSchemaCompatible = ").append(sameSchemaExpression).append(";\n");
  }

  private void writeSerializerMethods() {
    builder.append("  @Override\n");
    builder
        .append("  public void write(WriteContext writeContext, ")
        .append(struct.typeName)
        .append(" value) {\n");
    builder.append("    MemoryBuffer buffer = writeContext.getBuffer();\n");
    builder.append("    if (typeResolver.checkClassVersion()) {\n");
    builder.append("      buffer.writeInt32(classVersionHash);\n");
    builder.append("    }\n");
    builder.append("    writeBuildInFields(writeContext, value);\n");
    builder.append("    writeContainerFields(writeContext, value);\n");
    builder.append("    writeOtherFields(writeContext, value);\n");
    builder.append("  }\n\n");
    builder.append("  @Override\n");
    builder
        .append("  public ")
        .append(struct.typeName)
        .append(" read(ReadContext readContext) {\n");
    builder.append("    if (typeDef != null) {\n");
    builder.append(
        "      return sameSchemaCompatible ? readSchemaConsistent(readContext) : readCompatible(readContext);\n");
    builder.append("    }\n");
    builder.append("    return readSchemaConsistent(readContext);\n");
    builder.append("  }\n\n");
  }

  private void writeSchemaConsistentRead() {
    builder
        .append("  private ")
        .append(struct.typeName)
        .append(" readSchemaConsistent(ReadContext readContext) {\n");
    builder.append("    MemoryBuffer buffer = readContext.getBuffer();\n");
    builder.append("    if (typeResolver.checkClassVersion()) {\n");
    builder.append("      checkClassVersion(buffer.readInt32(), classVersionHash);\n");
    builder.append("    }\n");
    if (struct.record) {
      for (SourceField field : struct.fields) {
        builder
            .append("    ")
            .append(field.erasedType)
            .append(" field")
            .append(field.id)
            .append(" = ")
            .append(field.defaultValue())
            .append(";\n");
      }
      builder.append("    Object[] values = new Object[DESCRIPTORS.size()];\n");
      builder.append("    readBuildInRecordFields(readContext, values);\n");
      builder.append("    readContainerRecordFields(readContext, values);\n");
      builder.append("    readOtherRecordFields(readContext, values);\n");
      for (SourceField field : struct.fields) {
        builder
            .append("    field")
            .append(field.id)
            .append(" = ")
            .append(field.castExpression("values[" + field.id + "]"))
            .append(";\n");
      }
      builder.append("    return new ").append(struct.typeName).append("(");
      appendRecordConstructorArguments("field");
      builder.append(");\n");
    } else {
      builder.append("    ").append(struct.typeName).append(" value = newBean();\n");
      builder.append("    readContext.reference(value);\n");
      builder.append("    readBuildInFields(readContext, value);\n");
      builder.append("    readContainerFields(readContext, value);\n");
      builder.append("    readOtherFields(readContext, value);\n");
      builder.append("    return value;\n");
    }
    builder.append("  }\n\n");
  }

  private void writeWriteGroups() {
    writeWriteGroup("BuildIn", "buildInFields", "buildInFieldIds", "writeBuildInFieldValue");
    writeWriteGroup(
        "Container", "containerFields", "containerFieldIds", "writeContainerFieldValue");
    writeWriteGroup("Other", "otherFields", "otherFieldIds", "writeOtherFieldValue");
  }

  private void writeWriteGroup(
      String groupName, String fieldsName, String idsName, String helperName) {
    builder
        .append("  private void write")
        .append(groupName)
        .append("Fields(WriteContext writeContext, ")
        .append(struct.typeName)
        .append(" value) {\n");
    builder.append("    for (int i = 0; i < ").append(fieldsName).append(".length; i++) {\n");
    builder.append("      SerializationFieldInfo fieldInfo = ").append(fieldsName).append("[i];\n");
    builder.append("      switch (").append(idsName).append("[i]) {\n");
    for (SourceField field : struct.fields) {
      builder.append("        case ").append(field.id).append(":\n");
      appendDebugWrite("before", "fieldInfo", 10);
      builder
          .append("          ")
          .append(helperName)
          .append("(writeContext, fieldInfo, ")
          .append(field.readExpression("value"))
          .append(");\n");
      appendDebugWrite("after", "fieldInfo", 10);
      builder.append("          break;\n");
    }
    builder.append("        default:\n");
    builder
        .append("          throw new IllegalStateException(\"Unknown generated field id \" + ")
        .append(idsName)
        .append("[i]);\n");
    builder.append("      }\n");
    builder.append("    }\n");
    builder.append("  }\n\n");
  }

  private void writeReadGroups() {
    if (struct.record) {
      writeReadRecordGroup("BuildIn", "buildInFields", "buildInFieldIds", "readBuildInFieldValue");
      writeReadRecordGroup(
          "Container", "containerFields", "containerFieldIds", "readContainerFieldValue");
      writeReadRecordGroup("Other", "otherFields", "otherFieldIds", "readOtherFieldValue");
    } else {
      writeReadBeanGroup("BuildIn", "buildInFields", "buildInFieldIds", "readBuildInFieldValue");
      writeReadBeanGroup(
          "Container", "containerFields", "containerFieldIds", "readContainerFieldValue");
      writeReadBeanGroup("Other", "otherFields", "otherFieldIds", "readOtherFieldValue");
    }
  }

  private void writeReadBeanGroup(
      String groupName, String fieldsName, String idsName, String helperName) {
    builder
        .append("  private void read")
        .append(groupName)
        .append("Fields(ReadContext readContext, ")
        .append(struct.typeName)
        .append(" value) {\n");
    builder.append("    for (int i = 0; i < ").append(fieldsName).append(".length; i++) {\n");
    builder.append("      SerializationFieldInfo fieldInfo = ").append(fieldsName).append("[i];\n");
    appendDebugRead("before", "fieldInfo", 6);
    builder
        .append("      Object fieldValue = ")
        .append(helperName)
        .append("(readContext, fieldInfo);\n");
    appendDebugRead("after", "fieldInfo", 6);
    builder.append("      switch (").append(idsName).append("[i]) {\n");
    for (SourceField field : struct.fields) {
      builder.append("        case ").append(field.id).append(":\n");
      builder
          .append("          ")
          .append(field.writeStatement("value", field.castExpression("fieldValue")))
          .append("\n");
      builder.append("          break;\n");
    }
    builder.append("        default:\n");
    builder
        .append("          throw new IllegalStateException(\"Unknown generated field id \" + ")
        .append(idsName)
        .append("[i]);\n");
    builder.append("      }\n");
    builder.append("    }\n");
    builder.append("  }\n\n");
  }

  private void writeReadRecordGroup(
      String groupName, String fieldsName, String idsName, String helperName) {
    builder
        .append("  private void read")
        .append(groupName)
        .append("RecordFields(ReadContext readContext, Object[] values) {\n");
    builder.append("    for (int i = 0; i < ").append(fieldsName).append(".length; i++) {\n");
    builder.append("      SerializationFieldInfo fieldInfo = ").append(fieldsName).append("[i];\n");
    appendDebugRead("before", "fieldInfo", 6);
    builder
        .append("      values[")
        .append(idsName)
        .append("[i]] = ")
        .append(helperName)
        .append("(readContext, fieldInfo);\n");
    appendDebugRead("after", "fieldInfo", 6);
    builder.append("    }\n");
    builder.append("  }\n\n");
  }

  private void writeCompatibleRead() {
    builder.append("  @Override\n");
    builder
        .append("  public ")
        .append(struct.typeName)
        .append(" readCompatible(ReadContext readContext) {\n");
    builder.append("    if (sameSchemaCompatible) {\n");
    builder.append("      return readSchemaConsistent(readContext);\n");
    builder.append("    }\n");
    if (struct.record) {
      builder.append("    Object[] values = new Object[DESCRIPTORS.size()];\n");
      for (SourceField field : struct.fields) {
        builder
            .append("    values[")
            .append(field.id)
            .append("] = ")
            .append(field.defaultValue())
            .append(";\n");
      }
      builder.append("    for (int i = 0; i < remoteFields.size(); i++) {\n");
      builder.append("      RemoteFieldInfo remoteField = remoteFields.get(i);\n");
      builder.append(
          "      readCompatibleRecordField(readContext, values, remoteField, matchedId(remoteField));\n");
      builder.append("    }\n");
      for (SourceField field : struct.fields) {
        builder
            .append("    ")
            .append(field.erasedType)
            .append(" field")
            .append(field.id)
            .append(" = ")
            .append(field.castExpression("values[" + field.id + "]"))
            .append(";\n");
      }
      builder.append("    return new ").append(struct.typeName).append("(");
      appendRecordConstructorArguments("field");
      builder.append(");\n");
    } else {
      builder.append("    ").append(struct.typeName).append(" value = newBean();\n");
      builder.append("    readContext.reference(value);\n");
      builder.append("    for (int i = 0; i < remoteFields.size(); i++) {\n");
      builder.append("      RemoteFieldInfo remoteField = remoteFields.get(i);\n");
      builder.append(
          "      readCompatibleField(readContext, value, remoteField, matchedId(remoteField));\n");
      builder.append("    }\n");
      builder.append("    return value;\n");
    }
    builder.append("  }\n\n");
    writeCompatibleDispatchMethods();
  }

  private void writeCompatibleDispatchMethods() {
    int groupCount = (struct.fields.size() + DISPATCH_GROUP_SIZE - 1) / DISPATCH_GROUP_SIZE;
    if (struct.record) {
      writeCompatibleDispatchRouter("readCompatibleRecordField", true, groupCount);
      for (int group = 0; group < groupCount; group++) {
        writeCompatibleRecordDispatchGroup(group);
      }
    } else {
      writeCompatibleDispatchRouter("readCompatibleField", false, groupCount);
      for (int group = 0; group < groupCount; group++) {
        writeCompatibleBeanDispatchGroup(group);
      }
    }
  }

  private void writeCompatibleDispatchRouter(String methodName, boolean record, int groupCount) {
    builder.append("  private void ").append(methodName).append("(");
    appendCompatibleDispatchParameters(record);
    builder.append(") {\n");
    for (int group = 0; group < groupCount; group++) {
      int upperBound = Math.min(struct.fields.size(), (group + 1) * DISPATCH_GROUP_SIZE);
      if (group == 0) {
        builder.append("    if (matchedId >= 0 && matchedId < ").append(upperBound).append(") {\n");
      } else {
        builder.append("    if (matchedId < ").append(upperBound).append(") {\n");
      }
      builder.append("      ").append(methodName).append(group).append("(");
      appendCompatibleDispatchArguments(record);
      builder.append(");\n");
      builder.append("      return;\n");
      builder.append("    }\n");
    }
    appendDebugRemoteRead("before skip", "remoteField", 4);
    builder.append("    skipField(readContext, remoteField);\n");
    appendDebugRemoteRead("after skip", "remoteField", 4);
    builder.append("  }\n\n");
  }

  private void writeCompatibleBeanDispatchGroup(int group) {
    int start = group * DISPATCH_GROUP_SIZE;
    int end = Math.min(struct.fields.size(), start + DISPATCH_GROUP_SIZE);
    builder.append("  private void readCompatibleField").append(group).append("(");
    appendCompatibleDispatchParameters(false);
    builder.append(") {\n");
    builder.append("    switch (matchedId) {\n");
    for (int i = start; i < end; i++) {
      SourceField field = struct.fields.get(i);
      builder.append("      case ").append(field.id).append(":\n");
      appendDebugRemoteRead("before read", "remoteField", 8);
      builder
          .append("        if (canReadRemoteField(remoteField, fieldsById[")
          .append(field.id)
          .append("])) {\n");
      builder
          .append(
              "          Object fieldValue = readCompatibleFieldValue(readContext, remoteField, fieldsById[")
          .append(field.id)
          .append("]);\n");
      appendDebugRemoteRead("after read", "remoteField", 10);
      builder
          .append("          ")
          .append(field.writeStatement("value", field.castExpression("fieldValue")))
          .append("\n");
      builder.append("        } else {\n");
      appendDebugRemoteRead("before skip", "remoteField", 10);
      builder.append("          skipField(readContext, remoteField);\n");
      appendDebugRemoteRead("after skip", "remoteField", 10);
      builder.append("        }\n");
      builder.append("        return;\n");
    }
    builder.append("      default:\n");
    appendDebugRemoteRead("before skip", "remoteField", 8);
    builder.append("        skipField(readContext, remoteField);\n");
    appendDebugRemoteRead("after skip", "remoteField", 8);
    builder.append("    }\n");
    builder.append("  }\n\n");
  }

  private void writeCompatibleRecordDispatchGroup(int group) {
    int start = group * DISPATCH_GROUP_SIZE;
    int end = Math.min(struct.fields.size(), start + DISPATCH_GROUP_SIZE);
    builder.append("  private void readCompatibleRecordField").append(group).append("(");
    appendCompatibleDispatchParameters(true);
    builder.append(") {\n");
    builder.append("    switch (matchedId) {\n");
    for (int i = start; i < end; i++) {
      SourceField field = struct.fields.get(i);
      builder.append("      case ").append(field.id).append(":\n");
      appendDebugRemoteRead("before read", "remoteField", 8);
      builder
          .append("        if (canReadRemoteField(remoteField, fieldsById[")
          .append(field.id)
          .append("])) {\n");
      builder
          .append("          values[")
          .append(field.id)
          .append("] = readCompatibleFieldValue(readContext, remoteField, fieldsById[")
          .append(field.id)
          .append("]);\n");
      appendDebugRemoteRead("after read", "remoteField", 10);
      builder.append("        } else {\n");
      appendDebugRemoteRead("before skip", "remoteField", 10);
      builder.append("          skipField(readContext, remoteField);\n");
      appendDebugRemoteRead("after skip", "remoteField", 10);
      builder.append("        }\n");
      builder.append("        return;\n");
    }
    builder.append("      default:\n");
    appendDebugRemoteRead("before skip", "remoteField", 8);
    builder.append("        skipField(readContext, remoteField);\n");
    appendDebugRemoteRead("after skip", "remoteField", 8);
    builder.append("    }\n");
    builder.append("  }\n\n");
  }

  private void appendCompatibleDispatchParameters(boolean record) {
    builder.append("ReadContext readContext, ");
    if (record) {
      builder.append("Object[] values, ");
    } else {
      builder.append(struct.typeName).append(" value, ");
    }
    builder.append("RemoteFieldInfo remoteField, int matchedId");
  }

  private void appendCompatibleDispatchArguments(boolean record) {
    builder.append("readContext, ");
    if (record) {
      builder.append("values, ");
    } else {
      builder.append("value, ");
    }
    builder.append("remoteField, matchedId");
  }

  private void appendDebugWrite(String stage, String fieldInfoName, int indent) {
    if (!struct.debug) {
      return;
    }
    appendIndent(indent);
    builder
        .append("if (FORY_STRUCT_DEBUG) { debugWriteField(\"")
        .append(stage)
        .append("\", ")
        .append(fieldInfoName)
        .append(", writeContext); }\n");
  }

  private void appendDebugRead(String stage, String fieldInfoName, int indent) {
    if (!struct.debug) {
      return;
    }
    appendIndent(indent);
    builder
        .append("if (FORY_STRUCT_DEBUG) { debugReadField(\"")
        .append(stage)
        .append("\", ")
        .append(fieldInfoName)
        .append(", readContext); }\n");
  }

  private void appendDebugRemoteRead(String stage, String remoteFieldName, int indent) {
    if (!struct.debug) {
      return;
    }
    appendIndent(indent);
    builder
        .append("if (FORY_STRUCT_DEBUG) { debugRemoteReadField(\"")
        .append(stage)
        .append("\", ")
        .append(remoteFieldName)
        .append(", readContext); }\n");
  }

  private void appendIndent(int spaces) {
    for (int i = 0; i < spaces; i++) {
      builder.append(' ');
    }
  }

  private void writeCopy() {
    builder.append("  @Override\n");
    builder
        .append("  public ")
        .append(struct.typeName)
        .append(" copy(CopyContext copyContext, ")
        .append(struct.typeName)
        .append(" value) {\n");
    builder.append("    if (immutable) {\n");
    builder.append("      return value;\n");
    builder.append("    }\n");
    if (struct.record) {
      for (SourceField field : struct.fields) {
        builder
            .append("    ")
            .append(field.erasedType)
            .append(" field")
            .append(field.id)
            .append(" = ")
            .append(
                field.castExpression(
                    "copyFieldValue(copyContext, "
                        + field.readExpression("value")
                        + ", fieldsById["
                        + field.id
                        + "])"))
            .append(";\n");
      }
      builder
          .append("    ")
          .append(struct.typeName)
          .append(" copied = new ")
          .append(struct.typeName)
          .append("(");
      appendRecordConstructorArguments("field");
      builder.append(");\n");
      builder.append("    copyContext.reference(value, copied);\n");
      builder.append("    return copied;\n");
    } else {
      builder.append("    ").append(struct.typeName).append(" copied = newBean();\n");
      builder.append("    copyContext.reference(value, copied);\n");
      for (SourceField field : struct.fields) {
        builder
            .append("    ")
            .append(
                field.writeStatement(
                    "copied",
                    field.castExpression(
                        "copyFieldValue(copyContext, "
                            + field.readExpression("value")
                            + ", fieldsById["
                            + field.id
                            + "])")))
            .append("\n");
      }
      builder.append("    return copied;\n");
    }
    builder.append("  }\n\n");
  }

  private void writeDescriptorHelpers() {
    builder.append(
        "  private static TypeExtMeta meta(int typeId, boolean nullable, boolean trackingRef) {\n");
    builder.append("    return TypeExtMeta.of(typeId, nullable, trackingRef);\n");
    builder.append("  }\n");
  }

  private void appendRecordConstructorArguments(String prefix) {
    for (int i = 0; i < struct.recordConstructorFields.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      SourceField field = struct.recordConstructorFields.get(i);
      if (field.serialized) {
        builder.append(prefix).append(field.id);
      } else {
        builder.append(field.defaultValue());
      }
    }
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
