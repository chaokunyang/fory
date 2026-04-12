import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';

void writeDeclaredValue(
  WriteContext context,
  FieldMetadataInternal field,
  Object? value,
) {
  final shape = field.shape;
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
      throw StateError('Field ${field.name} is not nullable.');
    }
    context.writePrimitiveValue(shape.typeId, value);
    return;
  }
  final resolved = context.typeResolver.resolveShape(shape);
  if (!_usesDeclaredTypeInfo(context.config.compatible, shape, resolved)) {
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
      throw StateError('Field ${field.name} is not nullable.');
    }
    context.writeNonRef(value as Object);
    return;
  }
  if (shape.nullable || shape.ref) {
    final handled = context.refWriter.writeRefOrNull(
      context.buffer,
      value,
      trackRef: shape.ref && resolved.supportsRef,
    );
    if (handled) {
      return;
    }
  }
  if (value == null) {
    throw StateError('Field ${field.name} is not nullable.');
  }
  context.writeResolvedValue(resolved, value, shape);
}

T readDeclaredValue<T>(
  ReadContext context,
  FieldMetadataInternal field, [
  T? fallback,
]) {
  final shape = field.shape;
  if (shape.isDynamic) {
    return context.readRef() as T;
  }
  if (shape.isPrimitive && !shape.nullable) {
    return context.readPrimitiveValue(shape.typeId) as T;
  }
  final resolved = context.typeResolver.resolveShape(shape);
  if (!_usesDeclaredTypeInfo(context.config.compatible, shape, resolved)) {
    if (shape.ref) {
      return context.readRef() as T;
    }
    if (shape.nullable) {
      return context.readNullable() as T;
    }
    return context.readNonRef() as T;
  }
  if (shape.nullable || shape.ref) {
    final flag = context.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return fallback as T;
    }
    if (flag == RefWriter.refFlag) {
      return context.refReader.getReadRef() as T;
    }
    final value = context.readResolvedValue(resolved, shape);
    if (preservedRefId != null &&
        resolved.supportsRef &&
        context.refReader.readRefAt(preservedRefId) == null) {
      context.refReader.setReadRef(preservedRefId, value);
    }
    return value as T;
  }
  return context.readResolvedValue(resolved, shape) as T;
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

TypeShapeInternal shapeWithRootOverrides(
  TypeShapeInternal shape, {
  required bool nullable,
  required bool ref,
}) =>
    TypeShapeInternal(
      type: shape.type,
      typeId: shape.typeId,
      nullable: nullable,
      ref: ref,
      dynamic: shape.dynamic,
      arguments: List<TypeShapeInternal>.unmodifiable(shape.arguments),
    );

bool _usesDeclaredTypeInfo(
  bool compatible,
  TypeShapeInternal shape,
  ResolvedTypeInternal resolved,
) {
  if (shape.isDynamic) {
    return false;
  }
  if (!compatible) {
    return true;
  }
  switch (resolved.kind) {
    case RegistrationKindInternal.builtin:
    case RegistrationKindInternal.enumType:
    case RegistrationKindInternal.union:
      return true;
    case RegistrationKindInternal.struct:
    case RegistrationKindInternal.ext:
      return false;
  }
}
