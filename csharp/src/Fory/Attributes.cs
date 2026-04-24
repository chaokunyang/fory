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

namespace Apache.Fory;

/// <summary>
/// Marks a class, struct, or enum as a generated Fory object type.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Struct | AttributeTargets.Enum)]
public sealed class ForyObjectAttribute : Attribute
{
    /// <summary>
    /// Whether the annotated struct should use schema evolution metadata in compatible mode.
    /// </summary>
    public bool Evolving { get; set; } = true;
}

/// <summary>
/// Specifies field-level integer/number encoding strategy for generated serializers.
/// </summary>
public enum FieldEncoding
{
    /// <summary>
    /// Use the default encoding for the declared CLR type.
    /// </summary>
    None = -1,
    /// <summary>
    /// Variable-length integer encoding.
    /// </summary>
    Varint,
    /// <summary>
    /// Fixed-width integer encoding.
    /// </summary>
    Fixed,
    /// <summary>
    /// Tagged field encoding for schema-evolution scenarios.
    /// </summary>
    Tagged,
}

/// <summary>
/// Overrides generated serializer behavior for a field or property.
/// </summary>
[AttributeUsage(AttributeTargets.Field | AttributeTargets.Property)]
public sealed class FieldAttribute : Attribute
{
    /// <summary>
    /// Optional stable field tag id used for compatible metadata dispatch.
    /// Use a non-negative value to emit numeric field ids instead of field names.
    /// </summary>
    public short Id { get; set; } = -1;

    /// <summary>
    /// Gets or sets the field encoding strategy used by generated serializers.
    /// </summary>
    public FieldEncoding Encoding { get; set; } = FieldEncoding.None;
}

/// <summary>
/// Overrides the encoding metadata for a nested generic type node on a field or property.
/// Path segments use <c>element</c>, <c>key</c>, and <c>value</c>.
/// </summary>
[AttributeUsage(AttributeTargets.Field | AttributeTargets.Property, AllowMultiple = true)]
public sealed class NestedTypeAttribute : Attribute
{
    public NestedTypeAttribute(string path)
    {
        Path = path;
    }

    /// <summary>
    /// Gets the nested generic path from the annotated member root.
    /// </summary>
    public string Path { get; }

    /// <summary>
    /// Gets or sets the encoding override for the nested type node.
    /// </summary>
    public FieldEncoding Encoding { get; set; } = FieldEncoding.None;
}
