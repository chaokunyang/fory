// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

using System.ComponentModel;

namespace Apache.Fory;

/// <summary>
/// Marks a class or struct directly for generated structural serialization, or
/// declares an external structural serializer.
/// This attribute is not inherited by derived classes.
/// </summary>
[AttributeUsage(
    AttributeTargets.Class | AttributeTargets.Struct,
    AllowMultiple = false,
    Inherited = false)]
public sealed class ForyStructAttribute : Attribute
{
    /// <summary>
    /// Gets or sets the external class or struct whose schema and exact mapped storage are
    /// declared by the annotated serializer declaration. When null, the annotated class or
    /// struct is the serialized target.
    /// </summary>
    public Type? Target { get; set; }

    /// <summary>
    /// Gets or sets whether the generated structural serializer uses schema evolution metadata
    /// in compatible mode.
    /// </summary>
    public bool Evolving { get; set; } = true;

    /// <summary>
    /// Gets or sets whether an external class declaration supplies only the generated
    /// hierarchy API consumed by directly annotated derived classes.
    /// </summary>
    /// <remarks>
    /// A base-only declaration does not generate a standalone serializer factory or
    /// registration for <see cref="Target"/>.
    /// </remarks>
    public bool BaseOnly { get; set; }
}

/// <summary>
/// Marks an enum as a generated Fory enum type, or declares an external enum serializer.
/// Enum numeric values are the wire tags and must be in the range
/// <c>0</c> through <see cref="uint.MaxValue"/>.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Enum)]
public sealed class ForyEnumAttribute : Attribute
{
    /// <summary>
    /// Gets or sets the external enum handled by the annotated serializer declaration.
    /// When null, the annotated enum is the serialized target.
    /// </summary>
    public Type? Target { get; set; }
}

/// <summary>
/// Marks a generated Fory union type.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public sealed class ForyUnionAttribute : Attribute
{
}

/// <summary>
/// Marks a nested case type within a generated Fory union.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public sealed class ForyCaseAttribute : Attribute
{
    public ForyCaseAttribute(int id)
    {
        if (id < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(id));
        }

        Id = id;
    }

    /// <summary>
    /// Stable union case id written on the wire.
    /// </summary>
    public int Id { get; }

    /// <summary>
    /// Optional Fory schema descriptor type from <c>Apache.Fory.Schema.Types</c>.
    /// </summary>
    public Type? Type { get; set; }
}

/// <summary>
/// Marks the runtime-owned unknown-case carrier inside a generated Fory union.
/// </summary>
[AttributeUsage(AttributeTargets.Class)]
public sealed class ForyUnknownCaseAttribute : Attribute
{
}

/// <summary>
/// Selects how an external structural declaration binds a target member.
/// </summary>
public enum ForyTargetMemberKind
{
    /// <summary>
    /// Resolves a visible field or property using the target member name.
    /// </summary>
    Auto = 0,

    /// <summary>
    /// Binds an exact target field.
    /// </summary>
    Field = 1,

    /// <summary>
    /// Binds an exact target property.
    /// </summary>
    Property = 2,
}

/// <summary>
/// Overrides generated serializer behavior for a field or property.
/// </summary>
[AttributeUsage(AttributeTargets.Field | AttributeTargets.Property)]
public sealed class ForyFieldAttribute : Attribute
{
    private short id = -1;

    public ForyFieldAttribute()
    {
    }

    public ForyFieldAttribute(short id)
    {
        ValidateId(id);
        this.id = id;
    }

    public ForyFieldAttribute(int id)
    {
        if (id is < 0 or > short.MaxValue)
        {
            throw new ArgumentOutOfRangeException(nameof(id));
        }

        this.id = (short)id;
    }

    /// <summary>
    /// Optional stable field tag id used for compatible metadata dispatch.
    /// Use a non-negative value to emit numeric field ids instead of field names.
    /// </summary>
    public short Id
    {
        get => id;
        set
        {
            ValidateId(value);
            id = value;
        }
    }

    /// <summary>
    /// Optional Fory schema descriptor type from <c>Apache.Fory.Schema.Types</c>.
    /// </summary>
    public Type? Type { get; set; }

    /// <summary>
    /// Gets or sets whether an external serializer declaration excludes an exact target
    /// field from the wire schema while retaining its storage in the graph-memory estimate.
    /// </summary>
    public bool Ignore { get; set; }

    /// <summary>
    /// Gets or sets the exact target or target-ancestor type that declares an externally
    /// mapped member.
    /// </summary>
    /// <remarks>
    /// This option is valid only on an external structural serializer declaration.
    /// </remarks>
    public Type? TargetDeclaringType { get; set; }

    /// <summary>
    /// Gets or sets the case-sensitive metadata name of an externally mapped target member.
    /// When null, the annotated declaration member name is used for visible-member lookup.
    /// </summary>
    /// <remarks>
    /// This option is valid only on an external structural serializer declaration.
    /// </remarks>
    public string? TargetMemberName { get; set; }

    /// <summary>
    /// Gets or sets whether an external structural serializer declaration resolves a visible
    /// same-name member or an exact field or property.
    /// </summary>
    /// <remarks>
    /// This option is valid only on an external structural serializer declaration.
    /// </remarks>
    public ForyTargetMemberKind TargetMemberKind { get; set; }

    private static void ValidateId(short id)
    {
        if (id < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(id));
        }
    }
}

/// <summary>
/// Identifies the logical member kind recorded in a generated serializer contract.
/// </summary>
[EditorBrowsable(EditorBrowsableState.Never)]
public enum ForyGeneratedMemberKind
{
    /// <summary>
    /// The logical member is a field.
    /// </summary>
    Field = 0,

    /// <summary>
    /// The logical member is a property.
    /// </summary>
    Property = 1,
}

/// <summary>
/// Identifies the owner form of a generated serializer contract.
/// </summary>
[EditorBrowsable(EditorBrowsableState.Never)]
public enum ForyGeneratedProviderKind
{
    /// <summary>
    /// The provider is generated from a directly annotated ordinary type.
    /// </summary>
    Ordinary = 0,

    /// <summary>
    /// The provider is generated from an external structural declaration.
    /// </summary>
    External = 1,
}

/// <summary>
/// Records the target and parent serializer types for a generated serializer contract.
/// </summary>
[AttributeUsage(AttributeTargets.Class, AllowMultiple = false, Inherited = false)]
[EditorBrowsable(EditorBrowsableState.Never)]
public sealed class ForyGeneratedSerializerApiAttribute : Attribute
{
    /// <summary>
    /// Initializes a generated serializer contract for <paramref name="targetType"/> with the
    /// specified provider owner form.
    /// </summary>
    /// <param name="targetType">The exact runtime type owned by the generated serializer.</param>
    /// <param name="providerKind">The declaration form that owns the generated provider.</param>
    public ForyGeneratedSerializerApiAttribute(
        Type targetType,
        ForyGeneratedProviderKind providerKind)
    {
        TargetType = targetType;
        ProviderKind = providerKind;
    }

    /// <summary>
    /// Gets the exact runtime type owned by the generated serializer.
    /// </summary>
    public Type TargetType { get; }

    /// <summary>
    /// Gets the declaration form that owns the generated provider.
    /// </summary>
    public ForyGeneratedProviderKind ProviderKind { get; }

    /// <summary>
    /// Gets or sets the generated serializer that supplies the immediate hierarchy prefix.
    /// </summary>
    public Type? ParentSerializerType { get; set; }
}

/// <summary>
/// Records one declaration-owned wire member in a generated serializer contract.
/// </summary>
[AttributeUsage(AttributeTargets.Field, AllowMultiple = false, Inherited = false)]
[EditorBrowsable(EditorBrowsableState.Never)]
public sealed class ForyGeneratedWireMemberAttribute : Attribute
{
    /// <summary>
    /// Initializes a generated wire-member contract.
    /// </summary>
    /// <param name="ordinal">Stable ordinal within the declaring structural type.</param>
    /// <param name="declaringType">The exact type that declares the target member.</param>
    /// <param name="memberType">The exact CLR member type.</param>
    /// <param name="logicalName">The logical CLR member name used by the schema.</param>
    /// <param name="targetMemberName">The exact target metadata member name.</param>
    /// <param name="memberKind">The logical field or property kind.</param>
    public ForyGeneratedWireMemberAttribute(
        int ordinal,
        Type declaringType,
        Type memberType,
        string logicalName,
        string targetMemberName,
        ForyGeneratedMemberKind memberKind)
    {
        Ordinal = ordinal;
        DeclaringType = declaringType;
        MemberType = memberType;
        LogicalName = logicalName;
        TargetMemberName = targetMemberName;
        MemberKind = memberKind;
    }

    /// <summary>
    /// Gets the stable ordinal within the declaring structural type.
    /// </summary>
    public int Ordinal { get; }

    /// <summary>
    /// Gets the exact type that declares the target member.
    /// </summary>
    public Type DeclaringType { get; }

    /// <summary>
    /// Gets the exact CLR member type.
    /// </summary>
    public Type MemberType { get; }

    /// <summary>
    /// Gets the logical CLR member name used by the schema.
    /// </summary>
    public string LogicalName { get; }

    /// <summary>
    /// Gets the exact target metadata member name.
    /// </summary>
    public string TargetMemberName { get; }

    /// <summary>
    /// Gets the logical field or property kind.
    /// </summary>
    public ForyGeneratedMemberKind MemberKind { get; }

    /// <summary>
    /// Gets or sets the explicit schema field ID, or <c>-1</c> for name-based identity.
    /// </summary>
    public int FieldId { get; set; } = -1;

    /// <summary>
    /// Gets or sets the optional Fory schema descriptor type.
    /// </summary>
    public Type? SchemaType { get; set; }

    /// <summary>
    /// Gets or sets the stable override-slot identity for a logical property.
    /// </summary>
    public string? Slot { get; set; }

    /// <summary>
    /// Gets or sets the generated ref-returning field accessor name.
    /// </summary>
    public string? FieldAccessorName { get; set; }

    /// <summary>
    /// Gets or sets the generated property getter accessor name.
    /// </summary>
    public string? GetterAccessorName { get; set; }

    /// <summary>
    /// Gets or sets the generated property setter accessor name.
    /// </summary>
    public string? SetterAccessorName { get; set; }

    /// <summary>
    /// Gets or sets the encoded nullable shape of the CLR member type.
    /// </summary>
    public byte[] NullableShape { get; set; } = [];
}
