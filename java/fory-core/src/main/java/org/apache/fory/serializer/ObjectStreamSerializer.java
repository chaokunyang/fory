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

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.apache.fory.Fory;
import org.apache.fory.builder.CodecUtils;
import org.apache.fory.builder.LayerMarkerClassGenerator;
import org.apache.fory.collection.ObjectArray;
import org.apache.fory.collection.ObjectIntMap;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.meta.ClassDef;
import org.apache.fory.reflect.ObjectCreator;
import org.apache.fory.reflect.ObjectCreators;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.type.Descriptor;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.GraalvmSupport;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.unsafe._JDKAccess;

/**
 * Implement jdk custom serialization only if following conditions are met:
 *
 * <ul>
 *   <li>`writeObject/readObject` occurs only at current class in class hierarchy.
 *   <li>`writeReplace/readResolve` don't occur in class hierarchy.
 *   <li>class hierarchy doesn't have duplicated fields. If any of those conditions are not met,
 *       fallback jdk custom serialization to {@link JavaSerializer}.
 * </ul>
 *
 * <p>`ObjectInputStream#setObjectInputFilter` will be ignored by this serializer.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ObjectStreamSerializer extends AbstractObjectSerializer {
  private static final Logger LOG = LoggerFactory.getLogger(ObjectStreamSerializer.class);

  private final SlotInfo[] slotsInfos;

  /**
   * Interface for slot information used in ObjectStreamSerializer. This allows both full SlotsInfo
   * and minimal MinimalSlotsInfo implementations.
   */
  private interface SlotInfo {
    Class<?> getCls();

    StreamClassInfo getStreamClassInfo();

    MetaSharedLayerSerializerBase getSlotsSerializer();

    ForyObjectOutputStream getObjectOutputStream();

    ForyObjectInputStream getObjectInputStream();

    ObjectArray getFieldPool();

    ObjectIntMap<String> getFieldIndexMap();

    int getNumPutFields();

    /** Get field types for putFields/writeFields support, in index order. */
    Class<?>[] getPutFieldTypes();
  }

  /**
   * Safe wrapper for ObjectStreamClass.lookup that handles GraalVM native image limitations. In
   * GraalVM native image, ObjectStreamClass.lookup may fail for certain classes like Throwable due
   * to missing SerializationConstructorAccessor. This method catches such errors and returns null,
   * allowing the serializer to use alternative approaches like Unsafe.allocateInstance.
   */
  private static ObjectStreamClass safeObjectStreamClassLookup(Class<?> type) {
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      try {
        return ObjectStreamClass.lookup(type);
      } catch (Throwable e) {
        // In GraalVM native image, ObjectStreamClass.lookup may fail for certain classes
        // due to missing SerializationConstructorAccessor. We catch this and return null
        // to allow fallback to Unsafe-based object creation.
        LOG.warn(
            "ObjectStreamClass.lookup failed for {} in GraalVM native image: {}",
            type.getName(),
            e.getMessage());
        return null;
      }
    } else {
      // In regular JVM, use normal lookup
      return ObjectStreamClass.lookup(type);
    }
  }

  public ObjectStreamSerializer(Fory fory, Class<?> type) {
    super(fory, type, createObjectCreatorForGraalVM(type));
    if (!Serializable.class.isAssignableFrom(type)) {
      throw new IllegalArgumentException(
          String.format("Class %s should implement %s.", type, Serializable.class));
    }
    LOG.warn(
        "{} customized jdk serialization, which is inefficient. "
            + "Please replace it with a {} or implements {}",
        type,
        Serializer.class.getName(),
        Externalizable.class.getName());
    // stream serializer may be data serializer of ReplaceResolver serializer.
    fory.getClassResolver().setSerializerIfAbsent(type, this);
    List<SlotInfo> slotsInfoList = new ArrayList<>();
    Class<?> end = type;
    // locate closest non-serializable superclass
    while (end != null && Serializable.class.isAssignableFrom(end)) {
      end = end.getSuperclass();
    }
    while (type != end) {
      slotsInfoList.add(new SlotsInfo(fory, type));
      type = type.getSuperclass();
    }
    Collections.reverse(slotsInfoList);
    slotsInfos = slotsInfoList.toArray(new SlotInfo[0]);
  }

  /**
   * Creates an appropriate ObjectCreator for GraalVM native image environment. In GraalVM, we
   * prefer UnsafeObjectCreator to avoid serialization constructor issues.
   */
  private static <T> ObjectCreator<T> createObjectCreatorForGraalVM(Class<T> type) {
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      // In GraalVM native image, use Unsafe to avoid serialization constructor issues
      return new ObjectCreators.UnsafeObjectCreator<>(type);
    } else {
      // In regular JVM, use the standard object creator
      return ObjectCreators.getObjectCreator(type);
    }
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {
    buffer.writeInt16((short) slotsInfos.length);
    try {
      for (SlotInfo slotsInfo : slotsInfos) {
        // create a classinfo to avoid null class bytes when class id is a
        // replacement id.
        classResolver.writeClassInternal(buffer, slotsInfo.getCls());
        StreamClassInfo streamClassInfo = slotsInfo.getStreamClassInfo();
        Method writeObjectMethod = streamClassInfo.writeObjectMethod;
        if (writeObjectMethod == null) {
          slotsInfo.getSlotsSerializer().write(buffer, value);
        } else {
          ForyObjectOutputStream objectOutputStream = slotsInfo.getObjectOutputStream();
          Object oldObject = objectOutputStream.targetObject;
          MemoryBuffer oldBuffer = objectOutputStream.buffer;
          ForyObjectOutputStream.PutFieldImpl oldPutField = objectOutputStream.curPut;
          boolean fieldsWritten = objectOutputStream.fieldsWritten;
          try {
            objectOutputStream.targetObject = value;
            objectOutputStream.buffer = buffer;
            objectOutputStream.curPut = null;
            objectOutputStream.fieldsWritten = false;
            if (streamClassInfo.writeObjectFunc != null) {
              streamClassInfo.writeObjectFunc.accept(value, objectOutputStream);
            } else {
              writeObjectMethod.invoke(value, objectOutputStream);
            }
          } finally {
            objectOutputStream.targetObject = oldObject;
            objectOutputStream.buffer = oldBuffer;
            objectOutputStream.curPut = oldPutField;
            objectOutputStream.fieldsWritten = fieldsWritten;
          }
        }
      }
    } catch (Exception e) {
      throwSerializationException(type, e);
    }
  }

  @Override
  public Object read(MemoryBuffer buffer) {
    Object obj = objectCreator.newInstance();
    fory.getRefResolver().reference(obj);
    int numClasses = buffer.readInt16();
    int slotIndex = 0;

    // Create context to track layer ClassDefs for schema evolution
    LayerReadContext layerContext = new LayerReadContext();

    try {
      TreeMap<Integer, ObjectInputValidation> callbacks = new TreeMap<>(Collections.reverseOrder());
      for (int i = 0; i < numClasses; i++) {
        Class<?> currentClass = classResolver.readClassInternal(buffer);

        // Find the matching local slot for sender's class
        SlotInfo matchedSlot = null;
        while (slotIndex < slotsInfos.length) {
          SlotInfo candidateSlot = slotsInfos[slotIndex];
          if (currentClass == candidateSlot.getCls()) {
            // Found matching slot
            matchedSlot = candidateSlot;
            slotIndex++;
            break;
          } else if (currentClass.isAssignableFrom(candidateSlot.getCls())) {
            // Sender's class is an ancestor of candidate's class but they don't match.
            // This means sender has a layer (currentClass) that receiver doesn't have.
            // We'll skip sender's data for this layer below.
            break;
          } else {
            // Receiver has an extra layer that sender doesn't have - call readObjectNoData
            StreamClassInfo streamClassInfo = candidateSlot.getStreamClassInfo();
            Method readObjectNoData = streamClassInfo.readObjectNoData;
            if (readObjectNoData != null) {
              if (streamClassInfo.readObjectNoDataFunc != null) {
                streamClassInfo.readObjectNoDataFunc.accept(obj);
              } else {
                readObjectNoData.invoke(obj);
              }
            }
            slotIndex++;
          }
        }

        if (matchedSlot == null) {
          // Sender has a layer that receiver doesn't have - skip the data
          // First read and track the layer meta, then skip the field data
          ClassDef classDef = readAndTrackLayerMeta(buffer, layerContext);
          skipUnknownLayerData(buffer, currentClass, layerContext, classDef);
          continue;
        }

        // Read data for the matched layer
        StreamClassInfo streamClassInfo = matchedSlot.getStreamClassInfo();
        Method readObjectMethod = streamClassInfo.readObjectMethod;
        if (readObjectMethod == null) {
          // For standard field serialization, the serializer handles layer meta internally
          // We also track the ClassDef for potential future references
          trackLayerMetaFromSerializer(buffer, layerContext, matchedSlot.getSlotsSerializer());
          matchedSlot.getSlotsSerializer().readAndSetFields(buffer, obj);
        } else {
          // For custom readObject, it handles its own format
          ForyObjectInputStream objectInputStream = matchedSlot.getObjectInputStream();
          MemoryBuffer oldBuffer = objectInputStream.buffer;
          Object oldObject = objectInputStream.targetObject;
          ForyObjectInputStream.GetFieldImpl oldGetField = objectInputStream.getField;
          ForyObjectInputStream.GetFieldImpl getField =
              (ForyObjectInputStream.GetFieldImpl) matchedSlot.getFieldPool().popOrNull();
          if (getField == null) {
            getField = new ForyObjectInputStream.GetFieldImpl(matchedSlot);
          }
          boolean fieldsRead = objectInputStream.fieldsRead;
          try {
            objectInputStream.fieldsRead = false;
            objectInputStream.buffer = buffer;
            objectInputStream.targetObject = obj;
            objectInputStream.getField = getField;
            objectInputStream.callbacks = callbacks;
            if (streamClassInfo.readObjectFunc != null) {
              streamClassInfo.readObjectFunc.accept(obj, objectInputStream);
            } else {
              readObjectMethod.invoke(obj, objectInputStream);
            }
          } finally {
            objectInputStream.fieldsRead = fieldsRead;
            objectInputStream.buffer = oldBuffer;
            objectInputStream.targetObject = oldObject;
            objectInputStream.getField = oldGetField;
            matchedSlot.getFieldPool().add(getField);
            objectInputStream.callbacks = null;
            Arrays.fill(getField.vals, ForyObjectInputStream.NO_VALUE_STUB);
          }
        }
      }

      // Handle any remaining receiver-only layers at the end
      while (slotIndex < slotsInfos.length) {
        SlotInfo remainingSlot = slotsInfos[slotIndex++];
        StreamClassInfo streamClassInfo = remainingSlot.getStreamClassInfo();
        Method readObjectNoData = streamClassInfo.readObjectNoData;
        if (readObjectNoData != null) {
          if (streamClassInfo.readObjectNoDataFunc != null) {
            streamClassInfo.readObjectNoDataFunc.accept(obj);
          } else {
            readObjectNoData.invoke(obj);
          }
        }
      }

      for (ObjectInputValidation validation : callbacks.values()) {
        validation.validateObject();
      }
    } catch (InvocationTargetException | IllegalAccessException | InvalidObjectException e) {
      throwSerializationException(type, e);
    }
    return obj;
  }

  /**
   * Track layer meta from a matched serializer. This allows us to track ClassDefs from matched
   * layers so they can be referenced by later unknown layers.
   *
   * <p>Note: This is called BEFORE the serializer reads the meta, so we peek at it and track but
   * don't consume the bytes. The serializer will read the same bytes again.
   */
  private void trackLayerMetaFromSerializer(
      MemoryBuffer buffer, LayerReadContext context, MetaSharedLayerSerializerBase<?> serializer) {
    if (!fory.getConfig().isMetaShareEnabled()) {
      return;
    }
    org.apache.fory.resolver.MetaContext metaContext =
        fory.getSerializationContext().getMetaContext();
    if (metaContext == null) {
      return;
    }

    // Peek at the buffer to see if this is a new ClassDef or a reference
    int readerIndex = buffer.readerIndex();
    int indexMarker = buffer.readVarUint32Small14();
    boolean isRef = (indexMarker & 1) == 1;
    int index = indexMarker >>> 1;

    if (isRef) {
      // Reference - already tracked, nothing to do
      // Reset buffer position so serializer can read it
      buffer.readerIndex(readerIndex);
    } else {
      // New type - read the ClassDef and track it
      long id = buffer.readInt64();
      ClassDef classDef = ClassDef.readClassDef(fory, buffer, id);
      int storedIndex = context.addClassDef(classDef);
      if (storedIndex != index) {
        throw new IllegalStateException(
            "Layer ClassDef index mismatch: expected " + index + ", got " + storedIndex);
      }
      // Reset buffer position so serializer can read and skip it
      buffer.readerIndex(readerIndex);
    }
  }

  /**
   * Context for tracking layer ClassDefs during deserialization. This is needed for schema
   * evolution when sender has layers that receiver doesn't have, and we need to skip data for
   * referenced ClassDefs.
   */
  private static class LayerReadContext {
    /** List of ClassDefs encountered in stream order, indexed by their stream index. */
    private final List<ClassDef> layerClassDefs = new ArrayList<>();

    /**
     * Cache of skip serializers for each ClassDef, to avoid recreating them for referenced types.
     */
    private final Map<Integer, MetaSharedLayerSerializer<?>> skipSerializerCache = new HashMap<>();

    /** Add a ClassDef to the tracking list and return its index. */
    int addClassDef(ClassDef classDef) {
      int index = layerClassDefs.size();
      layerClassDefs.add(classDef);
      return index;
    }

    /** Get a ClassDef by its stream index. */
    ClassDef getClassDef(int index) {
      if (index < 0 || index >= layerClassDefs.size()) {
        throw new IllegalStateException(
            "Invalid layer ClassDef index: " + index + ", tracked count: " + layerClassDefs.size());
      }
      return layerClassDefs.get(index);
    }

    /** Get or create a skip serializer for a ClassDef at the given index. */
    MetaSharedLayerSerializer<?> getOrCreateSkipSerializer(
        Fory fory, int index, Class<?> senderClass) {
      return skipSerializerCache.computeIfAbsent(
          index,
          i -> {
            ClassDef classDef = getClassDef(i);
            return new MetaSharedLayerSerializer<>(
                fory,
                senderClass,
                classDef,
                LayerMarkerClassGenerator.getOrCreate(fory, senderClass, 0));
          });
    }
  }

  /**
   * Read layer class meta from buffer and track in context. This method handles both new ClassDefs
   * (read and store) and references (lookup from tracked).
   *
   * @param buffer the memory buffer to read from
   * @param context the layer read context for tracking ClassDefs
   * @return the ClassDef for this layer (either newly read or looked up from reference)
   */
  private ClassDef readAndTrackLayerMeta(MemoryBuffer buffer, LayerReadContext context) {
    if (!fory.getConfig().isMetaShareEnabled()) {
      return null;
    }
    org.apache.fory.resolver.MetaContext metaContext =
        fory.getSerializationContext().getMetaContext();
    if (metaContext == null) {
      return null;
    }

    int indexMarker = buffer.readVarUint32Small14();
    boolean isRef = (indexMarker & 1) == 1;
    int index = indexMarker >>> 1;

    if (isRef) {
      // Reference to previously seen ClassDef - look it up
      return context.getClassDef(index);
    } else {
      // New type in stream - read ClassDef inline and track it
      long id = buffer.readInt64();
      ClassDef classDef = ClassDef.readClassDef(fory, buffer, id);
      int storedIndex = context.addClassDef(classDef);
      if (storedIndex != index) {
        throw new IllegalStateException(
            "Layer ClassDef index mismatch: expected " + index + ", got " + storedIndex);
      }
      return classDef;
    }
  }

  /**
   * Skip data for a layer that exists in sender but not in receiver. This is needed for schema
   * evolution when sender's class hierarchy has layers that receiver doesn't have.
   *
   * @param buffer the memory buffer to read from
   * @param senderClass the sender's class for this layer (not present in receiver)
   * @param context the layer read context for tracking ClassDefs
   * @param classDef the ClassDef for this layer (already read from stream)
   */
  private void skipUnknownLayerData(
      MemoryBuffer buffer, Class<?> senderClass, LayerReadContext context, ClassDef classDef) {
    // For layers without custom writeObject, we can skip using a temporary serializer
    // created from the ClassDef. Note: For layers with custom writeObject, the sender
    // would have that class locally, and we'd have a matching slot. This method is only
    // called when sender has a layer the receiver doesn't have.

    if (classDef == null) {
      throw new UnsupportedOperationException(
          "Cannot skip unknown layer data without meta share enabled for class: "
              + senderClass.getName()
              + ". Schema evolution with removed parent classes requires meta share.");
    }

    // Find the index of this ClassDef in the context
    int classDefIndex = -1;
    for (int i = 0; i < context.layerClassDefs.size(); i++) {
      if (context.layerClassDefs.get(i) == classDef) {
        classDefIndex = i;
        break;
      }
    }

    if (classDefIndex < 0) {
      // This shouldn't happen - we should have tracked it when reading
      throw new IllegalStateException(
          "ClassDef not found in context for layer: " + senderClass.getName());
    }

    // Get or create a skip serializer and use it to read (and discard) the fields
    MetaSharedLayerSerializer<?> skipSerializer =
        context.getOrCreateSkipSerializer(fory, classDefIndex, senderClass);
    // Note: We don't call readAndSetFields because we already read the layer meta above.
    // We need to just read the field data. The serializer's readAndSetFields would try to
    // read meta again, so we directly read the fields instead.
    skipLayerFields(buffer, skipSerializer);
  }

  /**
   * Skip field data using a serializer. This reads the field data without setting it on any object.
   * The layer meta should already be consumed before calling this method.
   *
   * @param buffer the memory buffer to read from
   * @param serializer the serializer to use for reading (and discarding) fields
   */
  private void skipLayerFields(MemoryBuffer buffer, MetaSharedLayerSerializer<?> serializer) {
    // Use SerializationBinding to read fields - the serializer has the field layout
    // from the ClassDef, and skipFields will read each field to advance buffer position
    SerializationBinding binding = SerializationBinding.createBinding(fory);
    serializer.skipFields(buffer, binding);
  }

  private static void throwUnsupportedEncodingException(Class<?> cls)
      throws UnsupportedEncodingException {
    throw new UnsupportedEncodingException(
        String.format(
            "Use %s instead by `fory.registerSerializer(%s, new JavaSerializer(fory, %s))` or "
                + "implement a custom %s.",
            JavaSerializer.class, cls, cls, Serializer.class));
  }

  private static void throwSerializationException(Class<?> type, Exception e) {
    throw new RuntimeException(
        String.format(
            "Serialize object of type %s failed, "
                + "Try to use %s instead by `fory.registerSerializer(%s, new JavaSerializer(fory, %s))` or "
                + "implement a custom %s.",
            type, JavaSerializer.class, type, type, Serializer.class),
        e);
  }

  /**
   * Ensures serializers are generated for all field types of a class during GraalVM build time.
   * This is necessary because when a new Fory instance is created at runtime in GraalVM native
   * images, it needs to reuse the serializers that were generated at build time.
   *
   * @param fory the Fory instance
   * @param layerClassDef the ClassDef for this layer
   * @param type the target class type
   */
  private static void ensureFieldSerializersGenerated(
      Fory fory, ClassDef layerClassDef, Class<?> type) {
    Collection<Descriptor> descriptors =
        layerClassDef.getDescriptors(fory.getClassResolver(), type);
    for (Descriptor descriptor : descriptors) {
      Class<?> fieldType = descriptor.getRawType();
      if (fieldType != null && !fieldType.isPrimitive()) {
        try {
          // Trigger serializer generation for this field type
          fory.getClassResolver().getSerializerClass(fieldType);
        } catch (Exception e) {
          // Ignore errors - some types may not need serializers or may be handled specially
          ExceptionUtils.ignore(e);
        }
      }
    }
  }

  /**
   * Information about a class's stream methods (writeObject, readObject, readObjectNoData) and
   * their optimized MethodHandle equivalents for fast invocation.
   */
  private static class StreamClassInfo {
    private final Method writeObjectMethod;
    private final Method readObjectMethod;
    private final Method readObjectNoData;
    private final BiConsumer writeObjectFunc;
    private final BiConsumer readObjectFunc;
    private final Consumer readObjectNoDataFunc;

    private StreamClassInfo(Class<?> type) {
      // ObjectStreamClass.lookup has cache inside, invocation cost won't be big.
      ObjectStreamClass objectStreamClass = safeObjectStreamClassLookup(type);
      // In JDK17, set private jdk method accessible will fail by default, use ObjectStreamClass
      // instead, since it set accessible.
      Method writeMethod = null;
      Method readMethod = null;
      Method noDataMethod = null;
      if (objectStreamClass != null) {
        writeMethod =
            (Method) ReflectionUtils.getObjectFieldValue(objectStreamClass, "writeObjectMethod");
        readMethod =
            (Method) ReflectionUtils.getObjectFieldValue(objectStreamClass, "readObjectMethod");
        noDataMethod =
            (Method) ReflectionUtils.getObjectFieldValue(objectStreamClass, "readObjectNoData");
      }
      this.writeObjectMethod = writeMethod;
      this.readObjectMethod = readMethod;
      this.readObjectNoData = noDataMethod;
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(type);
      BiConsumer writeObjectFunc = null, readObjectFunc = null;
      Consumer readObjectNoDataFunc = null;
      try {
        if (writeObjectMethod != null) {
          writeObjectFunc =
              _JDKAccess.makeJDKBiConsumer(lookup, lookup.unreflect(writeObjectMethod));
        }
        if (readObjectMethod != null) {
          readObjectFunc = _JDKAccess.makeJDKBiConsumer(lookup, lookup.unreflect(readObjectMethod));
        }
        if (readObjectNoData != null) {
          readObjectNoDataFunc =
              _JDKAccess.makeJDKConsumer(lookup, lookup.unreflect(readObjectNoData));
        }
      } catch (Exception e) {
        ExceptionUtils.ignore(e);
      }
      this.writeObjectFunc = writeObjectFunc;
      this.readObjectFunc = readObjectFunc;
      this.readObjectNoDataFunc = readObjectNoDataFunc;
    }
  }

  private static final ClassValue<StreamClassInfo> STREAM_CLASS_INFO_CACHE =
      new ClassValue<StreamClassInfo>() {
        @Override
        protected StreamClassInfo computeValue(Class<?> type) {
          return new StreamClassInfo(type);
        }
      };

  /**
   * Full implementation of SlotInfo for handling object stream serialization. This class manages
   * all the details of serializing and deserializing a single class in the class hierarchy using
   * Java's ObjectInputStream/ObjectOutputStream protocol.
   */
  private static class SlotsInfo implements SlotInfo {
    private final Class<?> cls;
    private final StreamClassInfo streamClassInfo;
    // mark non-final for async-jit to update it to jit-serializer.
    private MetaSharedLayerSerializerBase slotsSerializer;
    private final ObjectIntMap<String> fieldIndexMap;
    private final int numPutFields;
    private final Class<?>[] putFieldTypes;
    private final ForyObjectOutputStream objectOutputStream;
    private final ForyObjectInputStream objectInputStream;
    private final ObjectArray getFieldPool;

    public SlotsInfo(Fory fory, Class<?> type) {
      this.cls = type;
      ObjectStreamClass objectStreamClass = safeObjectStreamClassLookup(type);
      streamClassInfo = STREAM_CLASS_INFO_CACHE.get(type);

      // Build single-layer ClassDef (resolveParent=false)
      ClassDef layerClassDef = fory.getClassResolver().getTypeDef(type, false);
      // Generate marker class for this layer. Use 0 as layer index since each class
      // has its own SlotsInfo, and the (class, 0) pair is unique for each class.
      Class<?> layerMarkerClass = LayerMarkerClassGenerator.getOrCreate(fory, type, 0);

      // Create interpreter-mode serializer first
      this.slotsSerializer =
          new MetaSharedLayerSerializer(fory, type, layerClassDef, layerMarkerClass);

      // Register JIT callback to replace with JIT serializer when ready
      if (fory.getConfig().isCodeGenEnabled()) {
        SlotsInfo thisInfo = this;
        fory.getJITContext()
            .registerSerializerJITCallback(
                () -> MetaSharedLayerSerializer.class,
                () ->
                    CodecUtils.loadOrGenMetaSharedLayerCodecClass(
                        type, fory, layerClassDef, layerMarkerClass),
                c ->
                    thisInfo.slotsSerializer =
                        (MetaSharedLayerSerializerBase)
                            Serializers.newSerializer(fory, type, (Class<? extends Serializer>) c));
      }

      // In GraalVM, ensure serializers are generated for all field types at build time
      // so they're available when new Fory instances are created at runtime
      if (GraalvmSupport.isGraalBuildtime()) {
        ensureFieldSerializersGenerated(fory, layerClassDef, type);
      }

      fieldIndexMap = new ObjectIntMap<>(4, 0.4f);
      // Build field list from ObjectStreamClass or class fields
      List<PutFieldInfo> putFieldInfos = new ArrayList<>();
      if (objectStreamClass != null) {
        for (ObjectStreamField serialField : objectStreamClass.getFields()) {
          putFieldInfos.add(new PutFieldInfo(serialField.getName(), serialField.getType(), type));
        }
      } else {
        // Fallback when ObjectStreamClass is not available
        Collection<Descriptor> descriptors =
            layerClassDef.getDescriptors(fory.getClassResolver(), type);
        for (Descriptor descriptor : descriptors) {
          Field field = descriptor.getField();
          if (field != null) {
            putFieldInfos.add(new PutFieldInfo(field.getName(), field.getType(), type));
          }
        }
      }

      if (streamClassInfo != null
          && (streamClassInfo.writeObjectMethod != null
              || streamClassInfo.readObjectMethod != null)) {
        this.numPutFields = putFieldInfos.size();
        this.putFieldTypes = new Class<?>[numPutFields];
        AtomicInteger idx = new AtomicInteger(0);
        for (PutFieldInfo fieldInfo : putFieldInfos) {
          int index = idx.getAndIncrement();
          fieldIndexMap.put(fieldInfo.name, index);
          putFieldTypes[index] = fieldInfo.type;
        }
      } else {
        this.numPutFields = 0;
        this.putFieldTypes = null;
      }

      if (streamClassInfo != null && streamClassInfo.writeObjectMethod != null) {
        try {
          objectOutputStream = new ForyObjectOutputStream(this);
        } catch (IOException e) {
          Platform.throwException(e);
          throw new IllegalStateException("unreachable");
        }
      } else {
        objectOutputStream = null;
      }
      if (streamClassInfo != null && streamClassInfo.readObjectMethod != null) {
        try {
          objectInputStream = new ForyObjectInputStream(this);
        } catch (IOException e) {
          Platform.throwException(e);
          throw new IllegalStateException("unreachable");
        }
      } else {
        objectInputStream = null;
      }
      getFieldPool = new ObjectArray();
    }

    @Override
    public Class<?> getCls() {
      return cls;
    }

    @Override
    public StreamClassInfo getStreamClassInfo() {
      return streamClassInfo;
    }

    @Override
    public MetaSharedLayerSerializerBase getSlotsSerializer() {
      return slotsSerializer;
    }

    @Override
    public ForyObjectOutputStream getObjectOutputStream() {
      return objectOutputStream;
    }

    @Override
    public ForyObjectInputStream getObjectInputStream() {
      return objectInputStream;
    }

    @Override
    public ObjectArray getFieldPool() {
      return getFieldPool;
    }

    @Override
    public ObjectIntMap<String> getFieldIndexMap() {
      return fieldIndexMap;
    }

    @Override
    public int getNumPutFields() {
      return numPutFields;
    }

    @Override
    public Class<?>[] getPutFieldTypes() {
      return putFieldTypes;
    }

    @Override
    public String toString() {
      return "SlotsInfo{" + "cls=" + cls + '}';
    }
  }

  /** Simple field info for putFields/writeFields support. */
  private static class PutFieldInfo {
    final String name;
    final Class<?> type;
    final Class<?> declaringClass;

    PutFieldInfo(String name, Class<?> type, Class<?> declaringClass) {
      this.name = name;
      this.type = type;
      this.declaringClass = declaringClass;
    }
  }

  /**
   * Implement serialization for object output with `writeObject/readObject` defined by java
   * serialization output spec.
   *
   * @see <a href="https://docs.oracle.com/en/java/javase/18/docs/specs/serialization/output.html">
   *     Java Object Serialization Output Specification</a>
   */
  private static class ForyObjectOutputStream extends ObjectOutputStream {
    private final Fory fory;
    private final boolean compressInt;
    private final SlotInfo slotsInfo;
    private MemoryBuffer buffer;
    private Object targetObject;
    private boolean fieldsWritten;

    protected ForyObjectOutputStream(SlotInfo slotsInfo) throws IOException {
      super();
      this.slotsInfo = slotsInfo;
      this.fory = slotsInfo.getSlotsSerializer().fory;
      this.compressInt = fory.compressInt();
    }

    @Override
    protected final void writeObjectOverride(Object obj) throws IOException {
      fory.writeRef(buffer, obj);
    }

    @Override
    public void writeUnshared(Object obj) throws IOException {
      fory.writeNonRef(buffer, obj);
    }

    /**
     * PutField is used to write fields which may not exists in current class for compatibility.
     * Note that the protocol should be compatible with `defaultReadObject`.
     *
     * @see <a
     *     href="https://docs.oracle.com/en/java/javase/18/docs/specs/serialization/input.html#the-objectinputstream.getfield-class">ObjectInputStream.GetField</a>
     * @see java.util.concurrent.ConcurrentHashMap
     */
    private class PutFieldImpl extends PutField {
      private final Object[] vals;

      PutFieldImpl() {
        vals = new Object[slotsInfo.getNumPutFields()];
      }

      private void putValue(String name, Object val) {
        int index = slotsInfo.getFieldIndexMap().get(name, -1);
        if (index == -1) {
          throw new IllegalArgumentException(
              String.format(
                  "Field name %s not exist in class %s",
                  name, slotsInfo.getSlotsSerializer().type));
        }
        vals[index] = val;
      }

      @Override
      public void put(String name, boolean val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, byte val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, char val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, short val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, int val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, long val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, float val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, double val) {
        putValue(name, val);
      }

      @Override
      public void put(String name, Object val) {
        putValue(name, val);
      }

      @Deprecated
      @Override
      public void write(ObjectOutput out) throws IOException {
        Class cls = slotsInfo.getSlotsSerializer().getType();
        throwUnsupportedEncodingException(cls);
      }
    }

    private final ObjectArray putFieldsCache = new ObjectArray();
    private PutFieldImpl curPut;

    @Override
    public PutField putFields() throws IOException {
      if (curPut == null) {
        Object o = putFieldsCache.popOrNull();
        if (o == null) {
          o = new PutFieldImpl();
        }
        curPut = (PutFieldImpl) o;
      }
      return curPut;
    }

    @Override
    public void writeFields() throws IOException {
      if (fieldsWritten) {
        throw new NotActiveException("not in writeObject invocation or fields already written");
      }
      PutFieldImpl curPut = this.curPut;
      if (curPut == null) {
        throw new NotActiveException("no current PutField object");
      }
      // Write field values directly by their types
      Class<?>[] fieldTypes = slotsInfo.getPutFieldTypes();
      Object[] vals = curPut.vals;
      for (int i = 0; i < vals.length; i++) {
        Class<?> fieldType = fieldTypes[i];
        Object value = vals[i];
        writePutFieldValue(buffer, fieldType, value);
      }
      Arrays.fill(curPut.vals, null);
      putFieldsCache.add(curPut);
      this.curPut = null;
      fieldsWritten = true;
    }

    private void writePutFieldValue(MemoryBuffer buffer, Class<?> fieldType, Object value) {
      if (fieldType == boolean.class) {
        buffer.writeBoolean(value != null && (Boolean) value);
      } else if (fieldType == byte.class) {
        buffer.writeByte(value == null ? (byte) 0 : (Byte) value);
      } else if (fieldType == char.class) {
        buffer.writeChar(value == null ? (char) 0 : (Character) value);
      } else if (fieldType == short.class) {
        buffer.writeInt16(value == null ? (short) 0 : (Short) value);
      } else if (fieldType == int.class) {
        if (compressInt) {
          buffer.writeVarInt32(value == null ? 0 : (Integer) value);
        } else {
          buffer.writeInt32(value == null ? 0 : (Integer) value);
        }
      } else if (fieldType == long.class) {
        fory.writeInt64(buffer, value == null ? 0L : (Long) value);
      } else if (fieldType == float.class) {
        buffer.writeFloat32(value == null ? 0f : (Float) value);
      } else if (fieldType == double.class) {
        buffer.writeFloat64(value == null ? 0d : (Double) value);
      } else {
        // Object reference
        fory.writeRef(buffer, value);
      }
    }

    @Override
    public void defaultWriteObject() throws IOException {
      if (fieldsWritten) {
        throw new NotActiveException("not in writeObject invocation or fields already written");
      }
      // Write field values in the same format as writeFields (for compatibility with readFields)
      Class<?>[] fieldTypes = slotsInfo.getPutFieldTypes();
      if (fieldTypes != null && fieldTypes.length > 0) {
        // Write using putField format (for compatibility with readFields)
        ObjectIntMap<String> fieldIndexMap = slotsInfo.getFieldIndexMap();
        MetaSharedLayerSerializerBase slotsSerializer = slotsInfo.getSlotsSerializer();
        Object[] vals =
            slotsSerializer.getFieldValuesForPutFields(
                targetObject, fieldIndexMap, fieldTypes.length);
        for (int i = 0; i < vals.length; i++) {
          Class<?> fieldType = fieldTypes[i];
          Object value = vals[i];
          writePutFieldValue(buffer, fieldType, value);
        }
      } else {
        // No custom writeObject/readObject, use normal serialization
        slotsInfo.getSlotsSerializer().write(buffer, targetObject);
      }
      fieldsWritten = true;
    }

    @Override
    public void reset() throws IOException {
      Class cls = slotsInfo.getSlotsSerializer().getType();
      // Fory won't invoke this method, throw exception if the user invokes it.
      throwUnsupportedEncodingException(cls);
    }

    @Override
    protected void annotateClass(Class<?> cl) throws IOException {
      throw new IllegalStateException();
    }

    @Override
    protected void annotateProxyClass(Class<?> cl) throws IOException {
      throw new IllegalStateException();
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
      throw new UnsupportedEncodingException();
    }

    @Override
    protected Object replaceObject(Object obj) throws IOException {
      throw new UnsupportedEncodingException();
    }

    @Override
    protected boolean enableReplaceObject(boolean enable) throws SecurityException {
      throw new IllegalStateException();
    }

    @Override
    protected void writeStreamHeader() throws IOException {
      throw new IllegalStateException();
    }

    @Override
    public void write(int b) throws IOException {
      buffer.writeByte((byte) b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      buffer.writeBytes(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      buffer.writeBytes(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
      buffer.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
      buffer.writeByte((byte) v);
    }

    @Override
    public void writeShort(int v) throws IOException {
      buffer.writeInt16((short) v);
    }

    @Override
    public void writeChar(int v) throws IOException {
      buffer.writeChar((char) v);
    }

    @Override
    public void writeInt(int v) throws IOException {
      if (compressInt) {
        buffer.writeVarInt32(v);
      } else {
        buffer.writeInt32(v);
      }
    }

    @Override
    public void writeLong(long v) throws IOException {
      fory.writeInt64(buffer, v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
      buffer.writeFloat32(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
      buffer.writeFloat64(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
      Preconditions.checkNotNull(s);
      int len = s.length();
      for (int i = 0; i < len; i++) {
        buffer.writeByte((byte) s.charAt(i));
      }
    }

    @Override
    public void writeChars(String s) throws IOException {
      Preconditions.checkNotNull(s);
      fory.writeJavaString(buffer, s);
    }

    @Override
    public void writeUTF(String s) throws IOException {
      Preconditions.checkNotNull(s);
      fory.writeJavaString(buffer, s);
    }

    @Override
    public void useProtocolVersion(int version) throws IOException {
      Class cls = slotsInfo.getCls();
      throwUnsupportedEncodingException(cls);
    }

    @Override
    public void flush() throws IOException {}

    @Override
    protected void drain() throws IOException {}

    @Override
    public void close() throws IOException {}
  }

  /**
   * Implement serialization for object output with `writeObject/readObject` defined by java
   * serialization input spec.
   *
   * @see <a href="https://docs.oracle.com/en/java/javase/18/docs/specs/serialization/input.html">
   *     Java Object Serialization Input Specification</a>
   */
  private static class ForyObjectInputStream extends ObjectInputStream {
    private final Fory fory;
    private final boolean compressInt;
    private final SlotInfo slotsInfo;
    private MemoryBuffer buffer;
    private Object targetObject;
    private GetFieldImpl getField;
    private boolean fieldsRead;
    private TreeMap<Integer, ObjectInputValidation> callbacks;

    protected ForyObjectInputStream(SlotInfo slotsInfo) throws IOException {
      this.fory = slotsInfo.getSlotsSerializer().fory;
      this.compressInt = fory.compressInt();
      this.slotsInfo = slotsInfo;
    }

    @Override
    protected Object readObjectOverride() {
      return fory.readRef(buffer);
    }

    @Override
    public Object readUnshared() {
      return fory.readNonRef(buffer);
    }

    private static final Object NO_VALUE_STUB = new Object();

    /**
     * Implementation of ObjectInputStream.GetField for reading fields that may not exist in the
     * current class version.
     */
    private static class GetFieldImpl extends GetField {
      private final SlotInfo slotsInfo;
      private final Object[] vals;

      GetFieldImpl(SlotInfo slotsInfo) {
        this.slotsInfo = slotsInfo;
        vals = new Object[slotsInfo.getNumPutFields()];
        Arrays.fill(vals, NO_VALUE_STUB);
      }

      @Override
      public ObjectStreamClass getObjectStreamClass() {
        return safeObjectStreamClassLookup(slotsInfo.getCls());
      }

      @Override
      public boolean defaulted(String name) throws IOException {
        int index = slotsInfo.getFieldIndexMap().get(name, -1);
        checkFieldExists(name, index);
        return vals[index] == NO_VALUE_STUB;
      }

      @Override
      public boolean get(String name, boolean val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (boolean) fieldValue;
      }

      @Override
      public byte get(String name, byte val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (byte) fieldValue;
      }

      @Override
      public char get(String name, char val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (char) fieldValue;
      }

      @Override
      public short get(String name, short val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (short) fieldValue;
      }

      @Override
      public int get(String name, int val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (int) fieldValue;
      }

      @Override
      public long get(String name, long val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (long) fieldValue;
      }

      @Override
      public float get(String name, float val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (float) fieldValue;
      }

      @Override
      public double get(String name, double val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return (double) fieldValue;
      }

      @Override
      public Object get(String name, Object val) throws IOException {
        Object fieldValue = getFieldValue(name);
        if (fieldValue == NO_VALUE_STUB) {
          return val;
        }
        return fieldValue;
      }

      private Object getFieldValue(String name) {
        int index = slotsInfo.getFieldIndexMap().get(name, -1);
        checkFieldExists(name, index);
        return vals[index];
      }

      private void checkFieldExists(String name, int index) {
        if (index == -1) {
          throw new IllegalArgumentException(
              String.format(
                  "Field name %s not exist in class %s",
                  name, slotsInfo.getSlotsSerializer().getType()));
        }
      }
    }

    // `readFields`/`writeFields` can handle fields which doesn't exist in current class.
    // `defaultReadObject` will skip those fields.
    @Override
    public GetField readFields() throws IOException {
      if (fieldsRead) {
        throw new NotActiveException("not in readObject invocation or fields already read");
      }
      // Read field values directly by their types
      Class<?>[] fieldTypes = slotsInfo.getPutFieldTypes();
      Object[] vals = getField.vals;
      for (int i = 0; i < vals.length; i++) {
        vals[i] = readPutFieldValue(buffer, fieldTypes[i]);
      }
      fieldsRead = true;
      return getField;
    }

    private Object readPutFieldValue(MemoryBuffer buffer, Class<?> fieldType) {
      if (fieldType == boolean.class) {
        return buffer.readBoolean();
      } else if (fieldType == byte.class) {
        return buffer.readByte();
      } else if (fieldType == char.class) {
        return buffer.readChar();
      } else if (fieldType == short.class) {
        return buffer.readInt16();
      } else if (fieldType == int.class) {
        if (compressInt) {
          return buffer.readVarInt32();
        } else {
          return buffer.readInt32();
        }
      } else if (fieldType == long.class) {
        return fory.readInt64(buffer);
      } else if (fieldType == float.class) {
        return buffer.readFloat32();
      } else if (fieldType == double.class) {
        return buffer.readFloat64();
      } else {
        // Object reference
        return fory.readRef(buffer);
      }
    }

    @Override
    public void defaultReadObject() throws IOException, ClassNotFoundException {
      if (fieldsRead) {
        throw new NotActiveException("not in readObject invocation or fields already read");
      }
      // Read field values in the same format as writeFields, but set to actual class fields
      Class<?>[] fieldTypes = slotsInfo.getPutFieldTypes();
      if (fieldTypes != null && fieldTypes.length > 0) {
        // Read using putField format (for compatibility with writeFields)
        ObjectIntMap<String> fieldIndexMap = slotsInfo.getFieldIndexMap();
        Object[] vals = new Object[fieldTypes.length];
        for (int i = 0; i < vals.length; i++) {
          vals[i] = readPutFieldValue(buffer, fieldTypes[i]);
        }
        // Now set matching fields on the target object
        MetaSharedLayerSerializerBase slotsSerializer = slotsInfo.getSlotsSerializer();
        slotsSerializer.setFieldValuesFromPutFields(targetObject, fieldIndexMap, vals);
      } else {
        // No custom writeObject/readObject, use normal serialization
        slotsInfo.getSlotsSerializer().readAndSetFields(buffer, targetObject);
      }
      fieldsRead = true;
    }

    // At `registerValidation` point int `readObject` root is only partially correct. To fully
    // restore it user may need access to other state which is created by the subclass and
    // at this point will be null. Users thus use `registerValidation`.
    // see `javax.swing.text.AbstractDocument.readObject` as an example.
    @Override
    public void registerValidation(ObjectInputValidation obj, int prio)
        throws NotActiveException, InvalidObjectException {
      // Since this method is only visible to fory, it won't be invoked by users outside
      // `readObject`,
      // we can skip check whether the caller is in `readObject`.
      if (obj == null) {
        throw new InvalidObjectException("null callback");
      }
      callbacks.put(prio, obj);
    }

    @Override
    public int read() throws IOException {
      return buffer.readByte() & 0xFF;
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
      if (buf == null) {
        throw new NullPointerException();
      }
      int endOffset = offset + length;
      if (offset < 0 || length < 0 || endOffset > buf.length || endOffset < 0) {
        throw new IndexOutOfBoundsException();
      }
      int remaining = buffer.remaining();
      // When remaining = 0, also force to refill buffer to avoid getting stuck
      if (remaining > 0 && remaining < length) {
        buffer.readBytes(buf, offset, remaining);
        return remaining;
      } else {
        buffer.readBytes(buf, offset, length);
        return length;
      }
    }

    @Override
    public int available() throws IOException {
      return buffer.remaining();
    }

    @Override
    public boolean readBoolean() throws IOException {
      return buffer.readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
      return buffer.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
      int b = buffer.readByte();
      return b & 0xff;
    }

    @Override
    public short readShort() throws IOException {
      return buffer.readInt16();
    }

    public int readUnsignedShort() throws IOException {
      int b = buffer.readInt16();
      return b & 0xffff;
    }

    @Override
    public char readChar() throws IOException {
      return buffer.readChar();
    }

    @Override
    public int readInt() throws IOException {
      return compressInt ? buffer.readVarInt32() : buffer.readInt32();
    }

    @Override
    public long readLong() throws IOException {
      return fory.readInt64(buffer);
    }

    @Override
    public float readFloat() throws IOException {
      return buffer.readFloat32();
    }

    @Override
    public double readDouble() throws IOException {
      return buffer.readFloat64();
    }

    @Override
    public void readFully(byte[] data) throws IOException {
      buffer.readBytes(data);
    }

    @Override
    public void readFully(byte[] data, int offset, int size) throws IOException {
      buffer.readBytes(data, offset, size);
    }

    @Override
    public int skipBytes(int len) throws IOException {
      buffer.increaseReaderIndex(len);
      return len;
    }

    @Override
    public String readLine() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() throws IOException {
      return fory.readJavaString(buffer);
    }

    @Override
    public void close() throws IOException {}
  }
}
