import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';

final class DeferredReadRef {
  final int id;

  const DeferredReadRef(this.id);
}

Object? readCompatibleField(
  ReadContext context,
  FieldMetadataInternal field,
) {
  final shape = field.shape;
  if (shape.isDynamic) {
    return context.readRef();
  }
  if (shape.isPrimitive && !shape.nullable) {
    return context.readPrimitiveValue(shape.typeId);
  }
  final resolved = context.typeResolver.resolveShape(shape);
  if (!_usesDeclaredTypeInfo(context.config.compatible, shape, resolved)) {
    if (shape.ref) {
      return _readCompatibleRefValueWithTypeMeta(context, shape);
    }
    if (shape.nullable) {
      final flag = context.buffer.readByte();
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag != RefWriter.notNullValueFlag) {
        throw StateError('Unexpected nullable flag $flag.');
      }
    }
    return context.readResolvedValue(context.readTypeMetaValue(), shape);
  }
  if (shape.nullable || shape.ref) {
    final flag = context.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      final value = context.refReader.getReadRef();
      if (value != null) {
        return value;
      }
      final refId = context.refReader.readRefId;
      if (refId != null) {
        return DeferredReadRef(refId);
      }
      return null;
    }
    final value = context.readResolvedValue(resolved, shape);
    if (preservedRefId != null &&
        resolved.supportsRef &&
        context.refReader.readRefAt(preservedRefId) == null) {
      context.refReader.setReadRef(preservedRefId, value);
    }
    return value;
  }
  return context.readResolvedValue(resolved, shape);
}

FieldMetadataInternal mergeCompatibleWriteField(
  FieldMetadataInternal localField,
  FieldMetadataInternal remoteField,
) {
  TypeShapeInternal mergeShape(
    TypeShapeInternal local,
    TypeShapeInternal remote,
  ) {
    final mergedArguments = <TypeShapeInternal>[];
    final argumentCount = remote.arguments.length;
    for (var index = 0; index < argumentCount; index += 1) {
      final remoteArgument = remote.arguments[index];
      final localArgument = index < local.arguments.length
          ? local.arguments[index]
          : remoteArgument;
      mergedArguments.add(mergeShape(localArgument, remoteArgument));
    }
    return TypeShapeInternal(
      type: local.type,
      typeId: remote.typeId,
      nullable: remote.nullable,
      ref: remote.ref,
      dynamic: local.dynamic ?? remote.dynamic,
      arguments: mergedArguments,
    );
  }

  return FieldMetadataInternal(
    name: localField.name,
    identifier: localField.identifier,
    id: localField.id,
    shape: mergeShape(localField.shape, remoteField.shape),
  );
}

FieldMetadataInternal mergeCompatibleReadField(
  FieldMetadataInternal localField,
  FieldMetadataInternal remoteField,
) {
  TypeShapeInternal mergeShape(
    TypeShapeInternal local,
    TypeShapeInternal remote,
  ) {
    final mergedArguments = <TypeShapeInternal>[];
    final argumentCount = remote.arguments.length;
    for (var index = 0; index < argumentCount; index += 1) {
      final remoteArgument = remote.arguments[index];
      final localArgument = index < local.arguments.length
          ? local.arguments[index]
          : remoteArgument;
      mergedArguments.add(mergeShape(localArgument, remoteArgument));
    }
    return TypeShapeInternal(
      type: local.type,
      typeId: remote.typeId,
      nullable: remote.nullable,
      ref: remote.ref,
      dynamic: local.dynamic ?? remote.dynamic,
      arguments: mergedArguments,
    );
  }

  return FieldMetadataInternal(
    name: localField.name,
    identifier: localField.identifier,
    id: localField.id,
    shape: mergeShape(localField.shape, remoteField.shape),
  );
}

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

Object? _readCompatibleRefValueWithTypeMeta(
  ReadContext context,
  TypeShapeInternal declaredShape,
) {
  final flag = context.refReader.tryPreserveRefId(context.buffer);
  final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
  if (flag == RefWriter.nullFlag) {
    return null;
  }
  if (flag == RefWriter.refFlag) {
    final value = context.refReader.getReadRef();
    if (value != null) {
      return value;
    }
    final refId = context.refReader.readRefId;
    if (refId != null) {
      return DeferredReadRef(refId);
    }
    return null;
  }
  final resolved = context.readTypeMetaValue();
  final value = context.readResolvedValue(resolved, declaredShape);
  if (preservedRefId != null &&
      resolved.supportsRef &&
      context.refReader.readRefAt(preservedRefId) == null) {
    context.refReader.setReadRef(preservedRefId, value);
  }
  return value;
}
