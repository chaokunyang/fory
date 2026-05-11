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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.apache.fory.annotation.ForyStruct")
public final class ForyStructProcessor extends AbstractProcessor {
  private static final String ARRAY_TYPE = "org.apache.fory.annotation.ArrayType";
  private static final String BFLOAT16_TYPE = "org.apache.fory.annotation.BFloat16Type";
  private static final String EXPOSE = "org.apache.fory.annotation.Expose";
  private static final String FLOAT16_TYPE = "org.apache.fory.annotation.Float16Type";
  private static final String FORY_FIELD = "org.apache.fory.annotation.ForyField";
  private static final String FORY_STRUCT = "org.apache.fory.annotation.ForyStruct";
  private static final String IGNORE = "org.apache.fory.annotation.Ignore";
  private static final String INT32_TYPE = "org.apache.fory.annotation.Int32Type";
  private static final String INT64_TYPE = "org.apache.fory.annotation.Int64Type";
  private static final String INT8_TYPE = "org.apache.fory.annotation.Int8Type";
  private static final String REF = "org.apache.fory.annotation.Ref";
  private static final String UINT16_TYPE = "org.apache.fory.annotation.UInt16Type";
  private static final String UINT32_TYPE = "org.apache.fory.annotation.UInt32Type";
  private static final String UINT64_TYPE = "org.apache.fory.annotation.UInt64Type";
  private static final String UINT8_TYPE = "org.apache.fory.annotation.UInt8Type";

  private final Set<String> processed = new HashSet<>();
  private final Map<String, TypeElement> generatedTypes = new HashMap<>();
  private Messager messager;
  private Filer filer;
  private Elements elements;
  private javax.lang.model.util.Types types;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    filer = processingEnv.getFiler();
    elements = processingEnv.getElementUtils();
    types = processingEnv.getTypeUtils();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    TypeElement foryStruct = elements.getTypeElement(FORY_STRUCT);
    if (foryStruct == null) {
      return false;
    }
    for (Element element : roundEnv.getElementsAnnotatedWith(foryStruct)) {
      if (!(element instanceof TypeElement)) {
        continue;
      }
      TypeElement type = (TypeElement) element;
      String binaryName = elements.getBinaryName(type).toString();
      if (!processed.add(binaryName)) {
        continue;
      }
      try {
        SourceStruct struct = buildStruct(type);
        if (struct != null) {
          emit(struct, type);
        }
      } catch (InvalidStructException e) {
        messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.element);
      } catch (RuntimeException e) {
        messager.printMessage(
            Diagnostic.Kind.ERROR,
            "Failed to generate Fory static serializer for " + binaryName + ": " + e.getMessage(),
            type);
      }
    }
    return true;
  }

  private SourceStruct buildStruct(TypeElement type) {
    if (type.getModifiers().contains(Modifier.PRIVATE)) {
      throw new InvalidStructException("@ForyStruct classes must not be private", type);
    }
    NestingKind nestingKind = type.getNestingKind();
    if (nestingKind == NestingKind.LOCAL || nestingKind == NestingKind.ANONYMOUS) {
      throw new InvalidStructException(
          "@ForyStruct local and anonymous classes are unsupported", type);
    }
    if (nestingKind == NestingKind.MEMBER && !type.getModifiers().contains(Modifier.STATIC)) {
      throw new InvalidStructException("@ForyStruct member classes must be static", type);
    }

    PackageElement packageElement = elements.getPackageOf(type);
    String packageName =
        packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    String binaryName = elements.getBinaryName(type).toString();
    String serializerName =
        binaryName.substring(packageName.isEmpty() ? 0 : packageName.length() + 1)
            + "__ForyStaticSerializer__";
    String qualifiedSerializerName =
        packageName.isEmpty() ? serializerName : packageName + "." + serializerName;
    TypeElement existing = elements.getTypeElement(qualifiedSerializerName);
    if (existing != null && !existing.equals(type)) {
      throw new InvalidStructException(
          "Generated serializer name collides with existing type " + qualifiedSerializerName, type);
    }
    TypeElement previous = generatedTypes.put(qualifiedSerializerName, type);
    if (previous != null && !previous.equals(type)) {
      throw new InvalidStructException(
          "Generated serializer name "
              + qualifiedSerializerName
              + " is ambiguous for "
              + elements.getBinaryName(previous)
              + " and "
              + binaryName,
          type);
    }

    boolean record = isRecord(type);
    List<VariableElement> fields = record ? recordComponentFields(type) : serializableFields(type);
    List<SourceField> sourceFields = new ArrayList<>(fields.size());
    List<SourceField> recordConstructorFields = new ArrayList<>();
    Map<Integer, VariableElement> fieldIds = new HashMap<>();
    if (record) {
      int serializedId = 0;
      for (VariableElement field : fields) {
        boolean serialized = isSerializableRecordField(field, type);
        int id = serialized ? serializedId++ : -1;
        SourceField sourceField = buildField(id, type, packageName, field, true, serialized);
        recordConstructorFields.add(sourceField);
        if (serialized) {
          validateForyFieldId(binaryName, fieldIds, field);
          sourceFields.add(sourceField);
        }
      }
    } else {
      for (int i = 0; i < fields.size(); i++) {
        VariableElement field = fields.get(i);
        validateForyFieldId(binaryName, fieldIds, field);
        SourceField sourceField = buildField(i, type, packageName, field, false, true);
        sourceFields.add(sourceField);
        recordConstructorFields.add(sourceField);
      }
    }
    return new SourceStruct(
        packageName,
        canonicalName(type.asType()),
        serializerName,
        record,
        sourceFields,
        recordConstructorFields);
  }

  private void validateForyFieldId(
      String binaryName, Map<Integer, VariableElement> fieldIds, VariableElement field) {
    ForyFieldMeta foryField = foryField(field);
    if (foryField.hasForyField && foryField.id >= 0) {
      VariableElement previousField = fieldIds.put(foryField.id, field);
      if (previousField != null) {
        throw new InvalidStructException(
            "Duplicate @ForyField id " + foryField.id + " in " + binaryName, field);
      }
    }
  }

  private void emit(SourceStruct struct, TypeElement originatingType) {
    try {
      JavaFileObject file =
          filer.createSourceFile(struct.qualifiedSerializerName(), originatingType);
      try (Writer writer = file.openWriter()) {
        writer.write(new StaticSerializerSourceWriter(struct).write());
      }
    } catch (IOException e) {
      throw new InvalidStructException(
          "Failed to write generated serializer: " + e, originatingType);
    }
  }

  private SourceField buildField(
      int id,
      TypeElement owner,
      String generatedPackage,
      VariableElement field,
      boolean record,
      boolean serialized) {
    Set<Modifier> modifiers = field.getModifiers();
    if (!record && modifiers.contains(Modifier.FINAL)) {
      throw new InvalidStructException(
          "Static serializers cannot assign final field "
              + field.getSimpleName()
              + "; use a record component or mark the field @Ignore/transient",
          field);
    }
    SourceTypeNode typeNode = buildTypeNode(field.asType());
    String erasedType = canonicalName(types.erasure(field.asType()));
    String declaringClass =
        elements.getBinaryName((TypeElement) field.getEnclosingElement()).toString();
    ForyFieldMeta foryField = foryField(field);

    SourceField.AccessKind readKind;
    SourceField.AccessKind writeKind;
    String readAccess;
    String writeAccess;
    if (record) {
      readKind = SourceField.AccessKind.METHOD;
      writeKind = SourceField.AccessKind.METHOD;
      readAccess = field.getSimpleName().toString();
      writeAccess = null;
    } else if (isAccessibleFromGenerated(field, generatedPackage)) {
      readKind = SourceField.AccessKind.FIELD;
      writeKind = SourceField.AccessKind.FIELD;
      readAccess = field.getSimpleName().toString();
      writeAccess = readAccess;
    } else {
      ExecutableElement getter = findGetter(owner, field, generatedPackage);
      ExecutableElement setter = findSetter(owner, field, generatedPackage);
      if (getter == null || setter == null) {
        throw new InvalidStructException(
            "Field "
                + field.getSimpleName()
                + " is not directly accessible from the generated serializer. Add accessible "
                + "non-private getter/setter methods or mark it @Ignore/transient.",
            field);
      }
      readKind = SourceField.AccessKind.METHOD;
      writeKind = SourceField.AccessKind.METHOD;
      readAccess = getter.getSimpleName().toString();
      writeAccess = setter.getSimpleName().toString();
    }
    return new SourceField(
        id,
        field.getSimpleName().toString(),
        erasedType,
        typeNode,
        reflectionModifiers(modifiers),
        declaringClass,
        serialized,
        hasAnnotation(field, ARRAY_TYPE),
        readKind,
        readAccess,
        writeKind,
        writeAccess,
        foryField.hasForyField,
        foryField.id,
        foryField.hasForyField ? foryField.nullable : !typeNode.primitive,
        foryField.hasForyField && foryField.ref,
        foryField.dynamic);
  }

  private List<VariableElement> serializableFields(TypeElement type) {
    List<TypeElement> hierarchy = hierarchy(type);
    List<VariableElement> fields = new ArrayList<>();
    for (int i = hierarchy.size() - 1; i >= 0; i--) {
      TypeElement current = hierarchy.get(i);
      List<VariableElement> declaredFields = ElementFilter.fieldsIn(current.getEnclosedElements());
      boolean haveExpose = false;
      boolean haveIgnore = false;
      for (VariableElement field : declaredFields) {
        haveExpose |= hasAnnotation(field, EXPOSE);
        haveIgnore |= hasAnnotation(field, IGNORE);
        if (haveExpose && haveIgnore) {
          throw new InvalidStructException(
              "Fields of a class must not mix @Expose and @Ignore", field);
        }
      }
      for (VariableElement field : declaredFields) {
        Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.TRANSIENT)) {
          continue;
        }
        if (haveExpose) {
          if (hasAnnotation(field, EXPOSE)) {
            fields.add(field);
          }
        } else if (!hasAnnotation(field, IGNORE)) {
          fields.add(field);
        }
      }
    }
    return fields;
  }

  private List<VariableElement> recordComponentFields(TypeElement type) {
    Map<String, VariableElement> fieldsByName = new LinkedHashMap<>();
    for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
      fieldsByName.put(field.getSimpleName().toString(), field);
    }
    List<VariableElement> fields = new ArrayList<>();
    for (RecordComponentElement component : type.getRecordComponents()) {
      VariableElement field = fieldsByName.get(component.getSimpleName().toString());
      if (field != null) {
        fields.add(field);
      }
    }
    return fields;
  }

  private boolean isSerializableRecordField(VariableElement field, TypeElement owner) {
    if (field.getModifiers().contains(Modifier.TRANSIENT)) {
      return false;
    }
    if (hasAnnotation(field, IGNORE)) {
      return false;
    }
    ExecutableElement accessor = findRecordAccessor(owner, field);
    return accessor == null || !hasAnnotation(accessor, IGNORE);
  }

  private ExecutableElement findRecordAccessor(TypeElement owner, VariableElement field) {
    String name = field.getSimpleName().toString();
    for (ExecutableElement method : ElementFilter.methodsIn(owner.getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(name) && method.getParameters().isEmpty()) {
        return method;
      }
    }
    return null;
  }

  private List<TypeElement> hierarchy(TypeElement type) {
    List<TypeElement> hierarchy = new ArrayList<>();
    TypeElement current = type;
    while (current != null && !current.getQualifiedName().contentEquals("java.lang.Object")) {
      hierarchy.add(current);
      TypeMirror superclass = current.getSuperclass();
      if (superclass == null || superclass.getKind() == TypeKind.NONE) {
        break;
      }
      Element element = types.asElement(superclass);
      current = element instanceof TypeElement ? (TypeElement) element : null;
    }
    return hierarchy;
  }

  private ExecutableElement findGetter(
      TypeElement owner, VariableElement field, String generatedPackage) {
    String name = field.getSimpleName().toString();
    String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    List<String> candidates = new ArrayList<>();
    candidates.add("get" + suffix);
    if (field.asType().getKind() == TypeKind.BOOLEAN) {
      candidates.add("is" + suffix);
    }
    for (ExecutableElement method : methods(owner)) {
      if (!candidates.contains(method.getSimpleName().toString())) {
        continue;
      }
      if (!method.getParameters().isEmpty() || method.getReturnType().getKind() == TypeKind.VOID) {
        continue;
      }
      if (!isAccessibleFromGenerated(method, generatedPackage)) {
        continue;
      }
      if (types.isAssignable(method.getReturnType(), field.asType())) {
        return method;
      }
    }
    return null;
  }

  private ExecutableElement findSetter(
      TypeElement owner, VariableElement field, String generatedPackage) {
    String name = field.getSimpleName().toString();
    String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    String setterName = "set" + suffix;
    for (ExecutableElement method : methods(owner)) {
      if (!method.getSimpleName().contentEquals(setterName)) {
        continue;
      }
      if (method.getParameters().size() != 1 || method.getReturnType().getKind() != TypeKind.VOID) {
        continue;
      }
      if (!isAccessibleFromGenerated(method, generatedPackage)) {
        continue;
      }
      if (types.isAssignable(field.asType(), method.getParameters().get(0).asType())) {
        return method;
      }
    }
    return null;
  }

  private List<ExecutableElement> methods(TypeElement owner) {
    List<ExecutableElement> methods = new ArrayList<>();
    for (TypeElement type : hierarchy(owner)) {
      methods.addAll(ElementFilter.methodsIn(type.getEnclosedElements()));
    }
    return methods;
  }

  private boolean isAccessibleFromGenerated(Element element, String generatedPackage) {
    Set<Modifier> modifiers = element.getModifiers();
    if (modifiers.contains(Modifier.PUBLIC)) {
      return true;
    }
    if (modifiers.contains(Modifier.PRIVATE)) {
      return false;
    }
    return elements.getPackageOf(element).getQualifiedName().contentEquals(generatedPackage);
  }

  private boolean isRecord(TypeElement type) {
    return type.getKind().name().equals("RECORD");
  }

  private int reflectionModifiers(Set<Modifier> modifiers) {
    int value = 0;
    if (modifiers.contains(Modifier.PUBLIC)) {
      value |= java.lang.reflect.Modifier.PUBLIC;
    }
    if (modifiers.contains(Modifier.PROTECTED)) {
      value |= java.lang.reflect.Modifier.PROTECTED;
    }
    if (modifiers.contains(Modifier.PRIVATE)) {
      value |= java.lang.reflect.Modifier.PRIVATE;
    }
    if (modifiers.contains(Modifier.STATIC)) {
      value |= java.lang.reflect.Modifier.STATIC;
    }
    if (modifiers.contains(Modifier.FINAL)) {
      value |= java.lang.reflect.Modifier.FINAL;
    }
    if (modifiers.contains(Modifier.TRANSIENT)) {
      value |= java.lang.reflect.Modifier.TRANSIENT;
    }
    if (modifiers.contains(Modifier.VOLATILE)) {
      value |= java.lang.reflect.Modifier.VOLATILE;
    }
    return value;
  }

  private SourceTypeNode buildTypeNode(TypeMirror type) {
    TypeKind kind = type.getKind();
    if (kind == TypeKind.TYPEVAR) {
      TypeVariable typeVariable = (TypeVariable) type;
      return buildTypeNode(typeVariable.getUpperBound());
    }
    if (kind == TypeKind.WILDCARD) {
      WildcardType wildcard = (WildcardType) type;
      TypeMirror bound = wildcard.getExtendsBound();
      return buildTypeNode(
          bound == null ? elements.getTypeElement("java.lang.Object").asType() : bound);
    }
    List<SourceTypeNode> arguments = new ArrayList<>();
    SourceTypeNode componentType = null;
    if (kind == TypeKind.ARRAY) {
      componentType = buildTypeNode(((ArrayType) type).getComponentType());
    } else if (type instanceof DeclaredType) {
      for (TypeMirror argument : ((DeclaredType) type).getTypeArguments()) {
        arguments.add(buildTypeNode(argument));
      }
    }
    String rawType = canonicalName(types.erasure(type));
    String extMeta = typeExtMetaExpression(type, rawType);
    boolean primitive = kind.isPrimitive();
    boolean nestedStruct = isForyStructType(type);
    return new SourceTypeNode(
        rawType, typeName(type), extMeta, arguments, componentType, primitive, nestedStruct);
  }

  private boolean isForyStructType(TypeMirror type) {
    TypeMirror erased = types.erasure(type);
    Element element = types.asElement(erased);
    return element instanceof TypeElement && hasAnnotation(element, FORY_STRUCT);
  }

  private String typeExtMetaExpression(TypeMirror type, String rawType) {
    String typeId = scalarTypeId(type, rawType);
    AnnotationMirror refMirror = annotationMirror(type, REF);
    if (typeId == null && refMirror == null) {
      return null;
    }
    return "meta("
        + (typeId == null ? "Types.UNKNOWN" : typeId)
        + ", true, "
        + booleanValue(refMirror, "enable", true)
        + ")";
  }

  private String scalarTypeId(TypeMirror type, String rawType) {
    if (hasTypeAnnotation(type, INT8_TYPE)) {
      return rawType.equals("byte[]") ? "Types.INT8_ARRAY" : "Types.INT8";
    }
    if (hasTypeAnnotation(type, UINT8_TYPE)) {
      return rawType.equals("byte[]") ? "Types.UINT8_ARRAY" : "Types.UINT8";
    }
    if (hasTypeAnnotation(type, UINT16_TYPE)) {
      return rawType.equals("short[]") ? "Types.UINT16_ARRAY" : "Types.UINT16";
    }
    AnnotationMirror uint32Mirror = typeAnnotationMirror(type, UINT32_TYPE);
    if (uint32Mirror != null) {
      String encoding = int32Encoding(uint32Mirror);
      if (rawType.equals("int[]")) {
        return "Types.UINT32_ARRAY";
      }
      return "FIXED".equals(encoding) ? "Types.UINT32" : "Types.VAR_UINT32";
    }
    AnnotationMirror uint64Mirror = typeAnnotationMirror(type, UINT64_TYPE);
    if (uint64Mirror != null) {
      String encoding = int64Encoding(uint64Mirror);
      if (rawType.equals("long[]")) {
        return "Types.UINT64_ARRAY";
      }
      if ("FIXED".equals(encoding)) {
        return "Types.UINT64";
      }
      return "TAGGED".equals(encoding) ? "Types.TAGGED_UINT64" : "Types.VAR_UINT64";
    }
    AnnotationMirror int32Mirror = typeAnnotationMirror(type, INT32_TYPE);
    if (int32Mirror != null) {
      String encoding = int32Encoding(int32Mirror);
      return "FIXED".equals(encoding) ? "Types.INT32" : "Types.VARINT32";
    }
    AnnotationMirror int64Mirror = typeAnnotationMirror(type, INT64_TYPE);
    if (int64Mirror != null) {
      String encoding = int64Encoding(int64Mirror);
      if ("FIXED".equals(encoding)) {
        return "Types.INT64";
      }
      return "TAGGED".equals(encoding) ? "Types.TAGGED_INT64" : "Types.VARINT64";
    }
    if (hasTypeAnnotation(type, FLOAT16_TYPE)) {
      return "Types.FLOAT16_ARRAY";
    }
    if (hasTypeAnnotation(type, BFLOAT16_TYPE)) {
      return "Types.BFLOAT16_ARRAY";
    }
    return null;
  }

  private boolean hasTypeAnnotation(TypeMirror type, String annotationName) {
    return typeAnnotationMirror(type, annotationName) != null;
  }

  private AnnotationMirror typeAnnotationMirror(TypeMirror type, String annotationName) {
    AnnotationMirror mirror = annotationMirror(type, annotationName);
    if (mirror != null || type.getKind() != TypeKind.ARRAY) {
      return mirror;
    }
    TypeMirror componentType = ((ArrayType) type).getComponentType();
    if (!componentType.getKind().isPrimitive()) {
      return null;
    }
    return annotationMirror(componentType, annotationName);
  }

  private AnnotationMirror annotationMirror(TypeMirror type, String annotationName) {
    for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
      Element element = mirror.getAnnotationType().asElement();
      if (element instanceof TypeElement
          && ((TypeElement) element).getQualifiedName().contentEquals(annotationName)) {
        return mirror;
      }
    }
    return null;
  }

  private AnnotationMirror annotationMirror(Element element, String annotationName) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      Element annotationElement = mirror.getAnnotationType().asElement();
      if (annotationElement instanceof TypeElement
          && ((TypeElement) annotationElement).getQualifiedName().contentEquals(annotationName)) {
        return mirror;
      }
    }
    return null;
  }

  private boolean hasAnnotation(Element element, String annotationName) {
    return annotationMirror(element, annotationName) != null;
  }

  private boolean booleanValue(AnnotationMirror mirror, String name, boolean defaultValue) {
    if (mirror == null) {
      return defaultValue;
    }
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        mirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(name)) {
        return (Boolean) entry.getValue().getValue();
      }
    }
    return defaultValue;
  }

  private String int32Encoding(AnnotationMirror mirror) {
    return enumValue(mirror, "encoding", "VARINT");
  }

  private String int64Encoding(AnnotationMirror mirror) {
    return enumValue(mirror, "encoding", "VARINT");
  }

  private String enumValue(AnnotationMirror mirror, String name, String defaultValue) {
    if (mirror == null) {
      return defaultValue;
    }
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        mirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(name)) {
        return String.valueOf(entry.getValue().getValue());
      }
    }
    return defaultValue;
  }

  private ForyFieldMeta foryField(VariableElement field) {
    AnnotationMirror mirror = annotationMirror(field, FORY_FIELD);
    if (mirror == null) {
      return ForyFieldMeta.NONE;
    }
    Map<? extends ExecutableElement, ? extends AnnotationValue> values =
        elements.getElementValuesWithDefaults(mirror);
    int id = -1;
    boolean nullable = !field.asType().getKind().isPrimitive();
    boolean ref = false;
    String dynamic = "AUTO";
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        values.entrySet()) {
      String name = entry.getKey().getSimpleName().toString();
      Object value = entry.getValue().getValue();
      if ("id".equals(name)) {
        id = ((Number) value).intValue();
      } else if ("nullable".equals(name)) {
        nullable = (Boolean) value;
      } else if ("ref".equals(name)) {
        ref = (Boolean) value;
      } else if ("dynamic".equals(name)) {
        dynamic = String.valueOf(value);
      }
    }
    return new ForyFieldMeta(true, id, nullable, ref, dynamic);
  }

  private String canonicalName(TypeMirror type) {
    if (type.getKind().isPrimitive()) {
      return primitiveName(type.getKind());
    }
    if (type.getKind() == TypeKind.ARRAY) {
      return canonicalName(((ArrayType) type).getComponentType()) + "[]";
    }
    TypeMirror erased = types.erasure(type);
    Element element = types.asElement(erased);
    if (element instanceof TypeElement) {
      return ((TypeElement) element).getQualifiedName().toString();
    }
    return erased.toString().toLowerCase(Locale.ROOT);
  }

  private String typeName(TypeMirror type) {
    TypeKind kind = type.getKind();
    if (kind.isPrimitive()) {
      return primitiveName(kind);
    }
    if (kind == TypeKind.ARRAY) {
      return typeName(((ArrayType) type).getComponentType()) + "[]";
    }
    if (kind == TypeKind.TYPEVAR) {
      return typeName(((TypeVariable) type).getUpperBound());
    }
    if (kind == TypeKind.WILDCARD) {
      TypeMirror bound = ((WildcardType) type).getExtendsBound();
      return bound == null ? Object.class.getName() : typeName(bound);
    }
    TypeMirror erased = types.erasure(type);
    Element element = types.asElement(erased);
    String rawType =
        element instanceof TypeElement
            ? ((TypeElement) element).getQualifiedName().toString()
            : erased.toString();
    if (!(type instanceof DeclaredType)) {
      return rawType;
    }
    List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
    if (arguments.isEmpty()) {
      return rawType;
    }
    StringBuilder builder = new StringBuilder(rawType).append("<");
    for (int i = 0; i < arguments.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(typeName(arguments.get(i)));
    }
    return builder.append(">").toString();
  }

  private String primitiveName(TypeKind kind) {
    switch (kind) {
      case BOOLEAN:
        return "boolean";
      case BYTE:
        return "byte";
      case CHAR:
        return "char";
      case SHORT:
        return "short";
      case INT:
        return "int";
      case LONG:
        return "long";
      case FLOAT:
        return "float";
      case DOUBLE:
        return "double";
      case VOID:
        return "void";
      default:
        throw new IllegalArgumentException("Not a primitive kind: " + kind);
    }
  }

  private static final class InvalidStructException extends RuntimeException {
    final Element element;

    InvalidStructException(String message, Element element) {
      super(message);
      this.element = element;
    }
  }

  private static final class ForyFieldMeta {
    static final ForyFieldMeta NONE = new ForyFieldMeta(false, -1, false, false, "AUTO");

    final boolean hasForyField;
    final int id;
    final boolean nullable;
    final boolean ref;
    final String dynamic;

    ForyFieldMeta(boolean hasForyField, int id, boolean nullable, boolean ref, String dynamic) {
      this.hasForyField = hasForyField;
      this.id = id;
      this.nullable = nullable;
      this.ref = ref;
      this.dynamic = dynamic;
    }
  }
}
