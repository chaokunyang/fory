package org.apache.fory.type;

import org.apache.fory.Fory;
import org.apache.fory.meta.TypeExtMeta;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.ClassResolver;

public class DispatchId {
  public static final int UNKNOWN = 0;
  public static final int PRIMITIVE_BOOL = 1;
  public static final int PRIMITIVE_INT8 = 2;
  public static final int PRIMITIVE_INT16 = 3;
  public static final int PRIMITIVE_CHAR = 4;
  public static final int PRIMITIVE_INT32 = 5;
  public static final int PRIMITIVE_VARINT32 = 6;
  public static final int PRIMITIVE_INT64 = 7;
  public static final int PRIMITIVE_VARINT64 = 8;
  public static final int PRIMITIVE_TAGGED_INT64 = 9;
  public static final int PRIMITIVE_FLOAT32 = 10;
  public static final int PRIMITIVE_FLOAT64 = 11;
  public static final int PRIMITIVE_UINT8 = 12;
  public static final int PRIMITIVE_UINT16 = 13;
  public static final int PRIMITIVE_UINT32 = 14;
  public static final int PRIMITIVE_VAR_UINT32 = 15;
  public static final int PRIMITIVE_UINT64 = 16;
  public static final int PRIMITIVE_VAR_UINT64 = 17;
  public static final int PRIMITIVE_TAGGED_UINT64 = 18;

  public static final int BOOL = 19;
  public static final int INT8 = 20;
  public static final int CHAR = 21;
  public static final int INT16 = 22;
  public static final int INT32 = 23;
  public static final int VARINT32 = 24;
  public static final int INT64 = 25;
  public static final int VARINT64 = 26;
  public static final int TAGGED_INT64 = 27;
  public static final int FLOAT32 = 28;
  public static final int FLOAT64 = 29;
  public static final int UINT8 = 30;
  public static final int UINT16 = 31;
  public static final int UINT32 = 32;
  public static final int VAR_UINT32 = 33;
  public static final int UINT64 = 34;
  public static final int VAR_UINT64 = 35;
  public static final int TAGGED_UINT64 = 36;
  public static final int STRING = 37;

  public static int getDispatchId(Fory fory, Descriptor d) {
    int typeId = Types.getDescriptorTypeId(fory, d);
    TypeRef<?> typeRef = d.getTypeRef();
    Class<?> rawType = typeRef.getRawType();
    TypeExtMeta typeExtMeta = typeRef.getTypeExtMeta();
    // A field is treated as primitive for dispatch only if the Java type itself is primitive.
    // Boxed types with nullable=false are still dispatched as boxed types,
    // but serialized without null checks.
    boolean isPrimitive =
        typeRef.isPrimitive()
            || (rawType.isPrimitive()
                && typeExtMeta != null
                && !typeExtMeta.nullable());
    if (fory.isCrossLanguage()) {
      return xlangTypeIdToDispatchId(typeId, isPrimitive);
    } else {
      return nativeIdToDispatchId(typeId, d, isPrimitive);
    }
  }

  private static int xlangTypeIdToDispatchId(int typeId, boolean isPrimitive) {
    switch (typeId) {
      case Types.BOOL:
        return isPrimitive ? PRIMITIVE_BOOL : BOOL;
      case Types.INT8:
        return isPrimitive ? PRIMITIVE_INT8 : INT8;
      case Types.INT16:
        return isPrimitive ? PRIMITIVE_INT16 : INT16;
      case Types.INT32:
        return isPrimitive ? PRIMITIVE_INT32 : INT32;
      case Types.VARINT32:
        return isPrimitive ? PRIMITIVE_VARINT32 : VARINT32;
      case Types.INT64:
        return isPrimitive ? PRIMITIVE_INT64 : INT64;
      case Types.VARINT64:
        return isPrimitive ? PRIMITIVE_VARINT64 : VARINT64;
      case Types.TAGGED_INT64:
        return isPrimitive ? PRIMITIVE_TAGGED_INT64 : TAGGED_INT64;
      case Types.UINT8:
        return isPrimitive ? PRIMITIVE_UINT8 : UINT8;
      case Types.UINT16:
        return isPrimitive ? PRIMITIVE_UINT16 : UINT16;
      case Types.UINT32:
        return isPrimitive ? PRIMITIVE_UINT32 : UINT32;
      case Types.VAR_UINT32:
        return isPrimitive ? PRIMITIVE_VAR_UINT32 : VAR_UINT32;
      case Types.UINT64:
        return isPrimitive ? PRIMITIVE_UINT64 : UINT64;
      case Types.VAR_UINT64:
        return isPrimitive ? PRIMITIVE_VAR_UINT64 : VAR_UINT64;
      case Types.TAGGED_UINT64:
        return isPrimitive ? PRIMITIVE_TAGGED_UINT64 : TAGGED_UINT64;
      case Types.FLOAT32:
        return isPrimitive ? PRIMITIVE_FLOAT32 : FLOAT32;
      case Types.FLOAT64:
        return isPrimitive ? PRIMITIVE_FLOAT64 : FLOAT64;
      case Types.STRING:
        return STRING;
      default:
        return UNKNOWN;
    }
  }

  private static int nativeIdToDispatchId(
      int nativeId, Descriptor descriptor, boolean isPrimitive) {
    if (nativeId >= Types.BOOL && nativeId <= ClassResolver.NATIVE_START_ID) {
      return xlangTypeIdToDispatchId(nativeId, isPrimitive);
    }
    if (nativeId == ClassResolver.CHAR_ID) {
      return isPrimitive ? PRIMITIVE_CHAR : CHAR;
    }
    if (nativeId == ClassResolver.PRIMITIVE_CHAR_ID) {
      return PRIMITIVE_CHAR;
    }
    if (nativeId >= ClassResolver.PRIMITIVE_VOID_ID
        && nativeId <= ClassResolver.PRIMITIVE_FLOAT64_ID) {
      throw new IllegalArgumentException(
          String.format(
              "%s should use `Types.BOOL~Types.FLOAT64` with nullable meta instead, but got %s",
              descriptor.getField(), nativeId));
    }
    return xlangTypeIdToDispatchId(nativeId, isPrimitive);
  }

  public static boolean isPrimitive(int dispatchId) {
    return dispatchId >= PRIMITIVE_BOOL && dispatchId <= PRIMITIVE_TAGGED_UINT64;
  }
}
