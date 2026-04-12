import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';

ReadContext _readImpl(ReadContext context) => context;

final class DeferredReadRef {
  final int id;

  const DeferredReadRef(this.id);
}

Object? readCompatibleField(
  ReadContext context,
  FieldMetadataInternal field,
) {
  return readCompatibleFieldRuntime(
    context,
    _readImpl(context).typeResolver.declaredFieldRuntime(field),
  );
}

Object? readCompatibleFieldRuntime(
  ReadContext context,
  DeclaredValueRuntimeInternal runtime,
) {
  final shape = runtime.shape;
  final internal = _readImpl(context);
  if (shape.isDynamic) {
    return context.readRef();
  }
  if (shape.isPrimitive && !shape.nullable) {
    return internal.readPrimitiveValue(shape.typeId);
  }
  final resolved = runtime.resolved!;
  if (!runtime.usesDeclaredType) {
    if (shape.ref) {
      return _readCompatibleRefValueWithTypeMetaRuntime(context, runtime);
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
    return internal.readResolvedValue(
      internal.readTypeMetaValue(resolved.isNamed ? resolved : null),
      shape,
    );
  }
  if (shape.nullable || shape.ref) {
    final flag = internal.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      final value = internal.refReader.getReadRef();
      if (value != null) {
        return value;
      }
      final refId = internal.refReader.readRefId;
      if (refId != null) {
        return DeferredReadRef(refId);
      }
      return null;
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
    return value;
  }
  return internal.readResolvedValue(resolved, shape);
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

Object? _readCompatibleRefValueWithTypeMetaRuntime(
  ReadContext context,
  DeclaredValueRuntimeInternal runtime,
) {
  final internal = _readImpl(context);
  final flag = internal.refReader.tryPreserveRefId(context.buffer);
  final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
  if (flag == RefWriter.nullFlag) {
    return null;
  }
  if (flag == RefWriter.refFlag) {
    final value = internal.refReader.getReadRef();
    if (value != null) {
      return value;
    }
    final refId = internal.refReader.readRefId;
    if (refId != null) {
      return DeferredReadRef(refId);
    }
    return null;
  }
  final declaredShape = runtime.shape;
  final expectedResolved = runtime.resolved!;
  final resolved = internal.readTypeMetaValue(
    expectedResolved.isNamed ? expectedResolved : null,
  );
  final value = internal.readResolvedValue(
    resolved,
    declaredShape,
    hasPreservedRef: preservedRefId != null,
  );
  if (preservedRefId != null &&
      resolved.supportsRef &&
      internal.refReader.readRefAt(preservedRefId) == null) {
    internal.refReader.setReadRef(preservedRefId, value);
  }
  return value;
}
