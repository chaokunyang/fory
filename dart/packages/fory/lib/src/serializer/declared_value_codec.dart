import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';

WriteContext _writeImpl(WriteContext context) => context;

ReadContext _readImpl(ReadContext context) => context;

void writeDeclaredValue(
  WriteContext context,
  FieldMetadataInternal field,
  Object? value,
) {
  final runtime = _writeImpl(context).typeResolver.declaredFieldRuntime(field);
  writeDeclaredValueRuntime(context, runtime, value);
}

void writeDeclaredValueRuntime(
  WriteContext context,
  DeclaredValueRuntimeInternal runtime,
  Object? value,
) {
  final shape = runtime.shape;
  final internal = _writeImpl(context);
  if (shape.isDynamic) {
    if (shape.ref) {
      context.writeRef(value);
      return;
    }
    if (context.writeNullFlag(value)) {
      return;
    }
    context.buffer.writeByte(RefWriter.notNullValueFlag);
    context.writeNonRef(value as Object);
    return;
  }
  if (shape.isPrimitive && !shape.nullable) {
    if (value == null) {
      throw StateError('Field ${runtime.metadata.name} is not nullable.');
    }
    internal.writePrimitiveValue(shape.typeId, value);
    return;
  }
  final resolved = runtime.resolved!;
  if (!runtime.usesDeclaredType) {
    if (shape.ref) {
      context.writeRef(value);
      return;
    }
    if (shape.nullable) {
      if (context.writeNullFlag(value)) {
        return;
      }
      context.buffer.writeByte(RefWriter.notNullValueFlag);
    } else if (value == null) {
      throw StateError('Field ${runtime.metadata.name} is not nullable.');
    }
    context.writeNonRef(value as Object);
    return;
  }
  if (shape.nullable || shape.ref) {
    final handled = internal.refWriter.writeRefOrNull(
      context.buffer,
      value,
      trackRef: shape.ref && resolved.supportsRef,
    );
    if (handled) {
      return;
    }
  }
  if (value == null) {
    throw StateError('Field ${runtime.metadata.name} is not nullable.');
  }
  internal.writeResolvedValue(resolved, value, shape);
}

T readDeclaredValue<T>(
  ReadContext context,
  FieldMetadataInternal field, [
  T? fallback,
]) {
  final runtime = _readImpl(context).typeResolver.declaredFieldRuntime(field);
  return readDeclaredValueRuntime(context, runtime, fallback);
}

T readDeclaredValueRuntime<T>(
  ReadContext context,
  DeclaredValueRuntimeInternal runtime, [
  T? fallback,
]) {
  final shape = runtime.shape;
  final internal = _readImpl(context);
  if (shape.isDynamic) {
    return context.readRef() as T;
  }
  if (shape.isPrimitive && !shape.nullable) {
    return internal.readPrimitiveValue(shape.typeId) as T;
  }
  final resolved = runtime.resolved!;
  if (!runtime.usesDeclaredType) {
    if (shape.ref) {
      return context.readRef() as T;
    }
    if (shape.nullable) {
      return context.readNullable() as T;
    }
    return context.readNonRef() as T;
  }
  if (shape.nullable || shape.ref) {
    final flag = internal.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return fallback as T;
    }
    if (flag == RefWriter.refFlag) {
      return internal.refReader.getReadRef() as T;
    }
    final value = internal.readResolvedValue(
      resolved,
      shape,
      hasPreservedRef: preservedRefId != null,
    );
    if (preservedRefId != null &&
        resolved.supportsRef &&
        internal.refReader.readRefAt(preservedRefId) == null) {
      internal.refReader.setReadRef(preservedRefId, value);
    }
    return value as T;
  }
  return internal.readResolvedValue(resolved, shape) as T;
}

FieldMetadataInternal fieldMetadata(
  TypeShapeInternal shape, {
  required String name,
  required String identifier,
  int? id,
  bool nullable = false,
  bool ref = false,
}) =>
    FieldMetadataInternal(
      name: name,
      identifier: identifier,
      id: id,
      shape: shapeWithRootOverrides(
        shape,
        nullable: nullable,
        ref: ref,
      ),
    );

FieldMetadataInternal declaredValueFieldMetadata(
  TypeShapeInternal shape, {
  required String identifier,
  bool nullable = false,
  bool ref = false,
}) =>
    FieldMetadataInternal(
      name: identifier,
      identifier: identifier,
      id: null,
      shape: shapeWithRootOverrides(
        shape,
        nullable: nullable,
        ref: ref,
      ),
    );

TypeShapeInternal shapeWithRootOverrides(
  TypeShapeInternal shape, {
  required bool nullable,
  required bool ref,
}) {
  return shape.withRootOverrides(nullable: nullable, ref: ref);
}
