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

using System.Collections.Immutable;
using Apache.Fory.Generator;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.Emit;

namespace Apache.Fory.Tests;

public sealed class ForyGeneratorTests
{
    [Fact]
    public void SplitAttributesCompile()
    {
        const string source = """
            using Apache.Fory;

            namespace GeneratedDiagnostics;

            [ForyEnum]
            public enum Status
            {
                Ready,
                Done,
            }

            [ForyUnion]
            public abstract partial record Choice
            {
                private Choice()
                {
                }

                [ForyUnknownCase]
                public sealed partial record Unknown(UnknownCase Value) : Choice;

                [ForyCase(0)]
                public sealed partial record Text(string Value) : Choice;
            }

            [ForyStruct]
            public sealed class Envelope
            {
                public Status Status { get; set; }
                public Choice Choice { get; set; } = new Choice.Text(string.Empty);
            }
            """;

        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver.RunGeneratorsAndUpdateCompilation(compilation, out Compilation output, out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
    }

    [Fact]
    public void NegativeForyFieldIdReportsDiagnostic()
    {
        const string source = """
            using Apache.Fory;

            namespace GeneratedDiagnostics;

            [ForyStruct]
            public sealed class InvalidFieldId
            {
                [ForyField(-1)]
                public int Value { get; set; }
            }
            """;

        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(compilation, out Compilation output, out ImmutableArray<Diagnostic> diagnostics);

        ImmutableArray<Diagnostic> generatorDiagnostics = driver.GetRunResult().Diagnostics;
        Assert.Contains(generatorDiagnostics.Concat(diagnostics), diagnostic => diagnostic.Id == "FORY004");
        Assert.DoesNotContain(output.GetDiagnostics(), diagnostic => diagnostic.Severity == DiagnosticSeverity.Error && diagnostic.Id != "FORY004");
    }

    [Fact]
    public void UnionRequiresRealCaseBeyondUnknown()
    {
        const string source = """
            using Apache.Fory;

            namespace GeneratedDiagnostics;

            [ForyUnion]
            public abstract partial record OnlyUnknown
            {
                private OnlyUnknown()
                {
                }

                [ForyUnknownCase]
                public sealed partial record Unknown(UnknownCase Value) : OnlyUnknown;
            }
            """;

        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(compilation, out Compilation output, out ImmutableArray<Diagnostic> diagnostics);

        ImmutableArray<Diagnostic> generatorDiagnostics = driver.GetRunResult().Diagnostics;
        Assert.Contains(
            generatorDiagnostics.Concat(diagnostics),
            diagnostic =>
                diagnostic.Id == "FORY006" &&
                diagnostic.GetMessage().Contains("at least one non-Unknown case", StringComparison.Ordinal));
        Assert.DoesNotContain(output.GetDiagnostics(), diagnostic => diagnostic.Severity == DiagnosticSeverity.Error && diagnostic.Id != "FORY006");
    }

    [Fact]
    public void CompatibleReadSourceUsesTypedCases()
    {
        const string source = """
            using System.Collections.Generic;
            using Apache.Fory;
            using S = Apache.Fory.Schema.Types;

            namespace GeneratedDiagnostics;

            [ForyStruct]
            public sealed class Shape
            {
                [ForyField(1, Type = typeof(S.Bool))]
                public bool Flag { get; set; }

                [ForyField(2, Type = typeof(S.Int32))]
                public int? Count { get; set; }

                [ForyField(3, Type = typeof(S.String))]
                public string? Name { get; set; }

                [ForyField(4, Type = typeof(S.Array<S.Int32>))]
                public int[] Values { get; set; } = [];
            }
            """;

        string generated = GenerateSource(source);

        Assert.Contains("case 0:", generated, StringComparison.Ordinal);
        Assert.Contains("case 1:", generated, StringComparison.Ordinal);
        Assert.Contains("case 2:", generated, StringComparison.Ordinal);
        Assert.Contains("case 3:", generated, StringComparison.Ordinal);
        Assert.DoesNotContain("__ForyLocalFields", generated, StringComparison.Ordinal);
        Assert.Contains("ReadBoolField(context, remoteField)", generated, StringComparison.Ordinal);
        Assert.Contains("ReadNullableStringField(context, remoteField)", generated, StringComparison.Ordinal);
        Assert.Contains("ReadNullableInt32Field(context, remoteField)", generated, StringComparison.Ordinal);
        Assert.Contains("ReadM3FieldBridge(context, remoteField.FieldType", generated, StringComparison.Ordinal);
        Assert.DoesNotContain("__ForyReadCompatibleField<", generated, StringComparison.Ordinal);
        Assert.DoesNotContain("RequiresScalarRead", generated, StringComparison.Ordinal);
        Assert.DoesNotContain("CompatibleScalarConverter.ReadBoolField(context, remoteField.FieldType", generated, StringComparison.Ordinal);
        Assert.DoesNotContain("if (remoteField.FieldType.TypeId ==", generated, StringComparison.Ordinal);
    }

    [Fact]
    public void CompatibleBinaryListChecksBeforeCapacity()
    {
        const string source = """
            using System.Collections.Generic;
            using Apache.Fory;
            using S = Apache.Fory.Schema.Types;

            namespace GeneratedDiagnostics;

            [ForyStruct]
            public sealed class BinaryListShape
            {
                [ForyField(1, Type = typeof(S.Array<S.UInt8>))]
                public List<byte> Value { get; set; } = [];
            }
            """;

        string generated = GenerateSource(source);

        int lengthIndex = generated.IndexOf(
            "int __foryLength = checked((int)context.Reader.ReadVarUInt32());",
            StringComparison.Ordinal);
        int checkIndex = generated.IndexOf(
            "context.Reader.CheckBound(__foryLength);",
            lengthIndex,
            StringComparison.Ordinal);
        int allocationIndex = generated.IndexOf(
            "new(__foryLength);",
            lengthIndex,
            StringComparison.Ordinal);

        Assert.True(lengthIndex >= 0);
        Assert.True(checkIndex > lengthIndex);
        Assert.True(allocationIndex > checkIndex);
    }

    [Fact]
    public void ExternalTargetsUseOneEmitter()
    {
        const string source = """
            #nullable enable
            using System.Collections.Generic;
            using Apache.Fory;
            using UserTarget = Fory.ExternalTypes.ExternalUser;
            using S = Apache.Fory.Schema.Types;

            namespace GeneratedDiagnostics;

            [ForyStruct(Target = typeof(UserTarget))]
            internal abstract class UserSerializer
            {
                [ForyField(1)]
                public abstract int Id { get; }

                [ForyField(2)]
                public abstract string Name { get; }

                [ForyField(3)]
                public abstract UserTarget? Friend { get; }

                [ForyField(4)]
                public abstract List<UserTarget> Links { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(UserTarget),
                    TargetMemberName = "<Id>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int IdStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(UserTarget),
                    TargetMemberName = "<Name>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string NameStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(UserTarget),
                    TargetMemberName = "<Friend>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract UserTarget? FriendStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(UserTarget),
                    TargetMemberName = "<Links>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract List<UserTarget> LinksStorage { get; }
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalPoint))]
            internal abstract class PointSerializer
            {
                [ForyField(1)]
                public abstract int X { get; }

                [ForyField(2)]
                public abstract int Y { get; }
            }

            [ForyEnum(Target = typeof(Fory.ExternalTypes.ExternalStatus))]
            internal static class StatusSerializer
            {
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalBox<string>))]
            internal abstract class StringBoxSerializer
            {
                [ForyField(1)]
                public abstract string Value { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalBox<string>),
                    TargetMemberName = "<Value>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string ValueStorage { get; }
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalDerived))]
            internal abstract class DerivedSerializer
            {
                [ForyField(1)]
                public abstract int Id { get; }

                [ForyField(2)]
                public abstract string BaseName { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalDerived),
                    TargetMemberName = "<Id>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int IdStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalBase),
                    TargetMemberName = "<BaseName>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string BaseNameStorage { get; }
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalFields))]
            internal abstract class FieldsSerializer
            {
                [ForyField(1)]
                public abstract int Count { get; }

                [ForyField(2)]
                public abstract string Name { get; }

                [ForyField(3)]
                public abstract int @event { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalFields),
                    TargetMemberName = "<Name>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string NameStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalFields),
                    TargetMemberName = "<event>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int EventStorage { get; }
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalSchemaModel))]
            internal abstract class SchemaSerializer
            {
                [ForyField(1, Type = typeof(S.Fixed<S.Int32>))]
                public abstract int FixedValue { get; }

                [ForyField(2, Type = typeof(S.Tagged<S.Int64>))]
                public abstract long TaggedValue { get; }

                [ForyField(3, Type = typeof(S.Array<S.Int32>))]
                public abstract int[] ArrayValue { get; }

                [ForyField(4, Type = typeof(S.List<S.Int32>))]
                public abstract List<int> ListValue { get; }

                [ForyField(5, Type = typeof(S.Set<S.Int32>))]
                public abstract HashSet<int> SetValue { get; }

                [ForyField(6, Type = typeof(S.Map<S.Fixed<S.UInt32>, S.List<S.Tagged<S.UInt64>>>))]
                public abstract Dictionary<uint, List<ulong?>?> NestedValue { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalSchemaModel),
                    TargetMemberName = "<FixedValue>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int FixedValueStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalSchemaModel),
                    TargetMemberName = "<TaggedValue>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract long TaggedValueStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalSchemaModel),
                    TargetMemberName = "<ArrayValue>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int[] ArrayValueStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalSchemaModel),
                    TargetMemberName = "<ListValue>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract List<int> ListValueStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalSchemaModel),
                    TargetMemberName = "<SetValue>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract HashSet<int> SetValueStorage { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalSchemaModel),
                    TargetMemberName = "<NestedValue>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract Dictionary<uint, List<ulong?>?> NestedValueStorage { get; }
            }

            [ForyStruct(
                Target = typeof(Fory.ExternalTypes.ExternalEvolutionOff),
                Evolving = false)]
            internal abstract class EvolutionOffSerializer
            {
                [ForyField(1)]
                public abstract int Value { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalEvolutionOff),
                    TargetMemberName = "<Value>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int ValueStorage { get; }
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalVersionOne))]
            internal abstract partial class VersionSerializer
            {
                [ForyField(1)]
                public abstract int Id { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalVersionOne),
                    TargetMemberName = "<Id>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int IdStorage { get; }
            }

            internal abstract partial class VersionSerializer
            {
                [ForyField(2)]
                public abstract string Name { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Fory.ExternalTypes.ExternalVersionOne),
                    TargetMemberName = "<Name>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string NameStorage { get; }
            }

            [ForyStruct]
            internal sealed class NullablePointHolder
            {
                public Fory.ExternalTypes.ExternalPoint? Point { get; set; }
            }

            [ForyStruct(Evolving = false)]
            internal sealed class LocalEvolutionOff
            {
                public int Value { get; set; }
            }
            """;

        string generated = GenerateSource(source);
        const string userType = "global::Fory.ExternalTypes.ExternalUser";

        Assert.Contains($"Serializer<{userType}>", generated, StringComparison.Ordinal);
        Assert.Contains($"in {userType} value", generated, StringComparison.Ordinal);
        Assert.Contains($"public override {userType} ReadData", generated, StringComparison.Ordinal);
        Assert.Contains($"{userType} value = new {userType}();", generated, StringComparison.Ordinal);
        Assert.Contains($"GetTypeMeta<{userType}>()", generated, StringComparison.Ordinal);
        Assert.Contains(
            generated.Split('\n'),
            line =>
                line.Contains(
                    $"RegisterGeneratedStruct<{userType},",
                    StringComparison.Ordinal)
                && line.TrimEnd().EndsWith(">(true);", StringComparison.Ordinal));
        Assert.Contains("value.Name", generated, StringComparison.Ordinal);
        Assert.Contains("value.@event", generated, StringComparison.Ordinal);
        Assert.Contains(
            generated.Split('\n'),
            line =>
                line.Contains(
                    "RegisterGeneratedStruct<global::Fory.ExternalTypes.ExternalEvolutionOff,",
                    StringComparison.Ordinal)
                && line.TrimEnd().EndsWith(">(false);", StringComparison.Ordinal));
        Assert.Contains(
            generated.Split('\n'),
            line =>
                line.Contains(
                    "RegisterGeneratedStruct<global::GeneratedDiagnostics.LocalEvolutionOff,",
                    StringComparison.Ordinal)
                && line.TrimEnd().EndsWith(">(false);", StringComparison.Ordinal));
        Assert.Contains(
            "EnumSerializer<global::Fory.ExternalTypes.ExternalStatus>",
            generated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void ExternalIgnoredFieldsOnlyAffectGraphMemory()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            public struct LargeValue
            {
                public long Left;
                public long Right;
            }

            public sealed class ExternalTarget
            {
                public int Value;
                public LargeValue PublicState;
                private readonly LargeValue HiddenState;

                public LargeValue ReadHiddenState() => HiddenState;
            }

            [ForyStruct(Target = typeof(ExternalTarget))]
            internal abstract class ExternalTargetSerializer
            {
                [ForyField(1)]
                public abstract int Value { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(ExternalTarget),
                    TargetMemberName = "HiddenState",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract LargeValue HiddenState { get; }
            }
            """;

        string generated = GenerateSource(source);
        const string largeValueSize =
            "global::System.Runtime.CompilerServices.Unsafe.SizeOf<global::GeneratedDiagnostics.LargeValue>()";

        Assert.Equal(
            2,
            generated.Split(largeValueSize, StringSplitOptions.None).Length - 1);
        Assert.Contains("value.Value", generated, StringComparison.Ordinal);
        Assert.DoesNotContain("HiddenState", generated, StringComparison.Ordinal);
    }

    [Fact]
    public void OrdinaryHierarchyEmitsProviderContracts()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            [ForyStruct]
            public abstract class Entity
            {
                [ForyField(1)]
                private int _identifier;

                [ForyField(2)]
                protected virtual string Text { get; set; } = string.Empty;

                private readonly long _cache = 1;
            }

            [ForyStruct]
            public sealed class Event : Entity
            {
                protected override string Text { get; set; } = string.Empty;

                [ForyField(3)]
                public long Timestamp;
            }
            """;

        string generated = GenerateSource(source);

        Assert.Equal(
            2,
            generated.Split(
                "ForyGeneratedSerializerApi(typeof(global::GeneratedDiagnostics.",
                StringSplitOptions.None).Length - 1);
        Assert.Contains(
            "ForyGeneratedProviderKind.Ordinary",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Field, Name = \"_identifier\"",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Method, Name = \"get_Text\"",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Method, Name = \"set_Text\"",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "\"Text\", \"Text\", global::Apache.Fory.ForyGeneratedMemberKind.Property, FieldId = 2",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            ".HierarchyShallowBytes + 4 + 8",
            generated,
            StringComparison.Ordinal);
        Assert.DoesNotContain(
            "RegisterGeneratedStruct<global::GeneratedDiagnostics.Entity,",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "RegisterGeneratedStruct<global::GeneratedDiagnostics.Event,",
            generated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void FlatPublicClassKeepsDirectMemberAccess()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            [ForyStruct]
            public sealed class FlatModel
            {
                public int Value { get; set; }
            }
            """;

        string generated = GenerateSource(source);

        Assert.Contains("value.Value", generated, StringComparison.Ordinal);
        Assert.DoesNotContain(
            "System.Runtime.CompilerServices.UnsafeAccessor(",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "HierarchyShallowBytes = checked(0L + 4);",
            generated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void BaseOnlyExternalProviderOwnsPrivatePrefix()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            public class VendorBase
            {
                private long _identifier;
                private string Secret { get; set; } = string.Empty;
                public int Count;
            }

            [ForyStruct(Target = typeof(VendorBase), BaseOnly = true)]
            public abstract class VendorProvider
            {
                [ForyField(
                    1,
                    TargetDeclaringType = typeof(VendorBase),
                    TargetMemberName = "_identifier",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract long Identifier { get; }

                [ForyField(
                    2,
                    TargetDeclaringType = typeof(VendorBase),
                    TargetMemberName = "Secret",
                    TargetMemberKind = ForyTargetMemberKind.Property)]
                public abstract string Secret { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(VendorBase),
                    TargetMemberName = "<Secret>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string SecretStorage { get; }

                [ForyField(3)]
                public abstract int Count { get; }
            }
            """;

        string generated = GenerateSource(source);

        Assert.Contains(
            "ForyGeneratedProviderKind.External",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "public abstract class __ForySerializer_",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "HierarchyShallowBytes = checked(0L + 4 + 4 + 8);",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Field, Name = \"_identifier\"",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Method, Name = \"get_Secret\"",
            generated,
            StringComparison.Ordinal);
        Assert.DoesNotContain(
            "__ForyTypeMetaCacheLock",
            generated,
            StringComparison.Ordinal);
        Assert.DoesNotContain(
            "RegisterGeneratedStruct<global::GeneratedDiagnostics.VendorBase,",
            generated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void ReferencedParentApiSuppliesWireAndBudget()
    {
        const string parentSource = """
            using Apache.Fory;
            namespace ParentModels;

            [ForyStruct]
            public abstract class Parent
            {
                [ForyField(1)]
                private int _identifier;
            }
            """;
        MetadataReference parentReference = CreateGeneratedReference(
            "Fory.ParentModels",
            parentSource,
            out string parentGenerated,
            publicSurfaceOnly: true);
        const string childSource = """
            using Apache.Fory;
            using ParentModels;
            namespace ChildModels;

            [ForyStruct]
            public sealed class Child : Parent
            {
                [ForyField(2)]
                public int Value { get; set; }
            }
            """;
        CSharpCompilation compilation = CreateCompilation(
            childSource,
            includeExternalTypes: false,
            additionalReferences: [parentReference],
            assemblyName: "Fory.ChildModels");
        GeneratorDriver driver = CSharpGeneratorDriver.Create(
            new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
        string childGenerated = string.Join(
            "\n",
            driver.GetRunResult().Results
                .SelectMany(result => result.GeneratedSources)
                .Select(result => result.SourceText.ToString()));
        Assert.Contains(
            "public abstract class __ForySerializer_",
            parentGenerated,
            StringComparison.Ordinal);
        Assert.Contains(
            ".HierarchyShallowBytes + 4",
            childGenerated,
            StringComparison.Ordinal);
        Assert.Contains(".F0(value)", childGenerated, StringComparison.Ordinal);
        Assert.DoesNotContain(
            "UnsafeAccessorKind.Field, Name = \"_identifier\"",
            childGenerated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void ReferencedParentPublishesAssemblyLocalAccess()
    {
        const string parentSource = """
            using Apache.Fory;
            namespace ParentModels;

            [ForyStruct]
            public class Parent
            {
                [ForyField(1)]
                internal int Value;
            }
            """;
        MetadataReference parentReference = CreateGeneratedReference(
            "Fory.InternalParent",
            parentSource,
            out string parentGenerated);
        const string childSource = """
            using Apache.Fory;
            using ParentModels;
            namespace ChildModels;

            [ForyStruct]
            public sealed class Child : Parent
            {
                [ForyField(2)]
                public int Added;
            }
            """;

        CSharpCompilation compilation = CreateCompilation(
            childSource,
            includeExternalTypes: false,
            additionalReferences: [parentReference],
            assemblyName: "Fory.InternalChild");
        GeneratorDriver driver = CSharpGeneratorDriver.Create(
            new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
        string childGenerated = string.Join(
            "\n",
            driver.GetRunResult().Results
                .SelectMany(result => result.GeneratedSources)
                .Select(result => result.SourceText.ToString()));
        Assert.Contains(
            "public static extern ref global::System.Int32 F0",
            parentGenerated,
            StringComparison.Ordinal);
        Assert.Contains("value.Value", parentGenerated, StringComparison.Ordinal);
        Assert.Contains(".F0(value)", childGenerated, StringComparison.Ordinal);
        Assert.DoesNotContain(
            "UnsafeAccessorKind.Field, Name = \"Value\"",
            childGenerated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void ReferencedAbstractSlotUsesConcreteOverride()
    {
        const string parentSource = """
            using Apache.Fory;
            namespace ParentModels;

            [ForyStruct]
            public abstract class Parent
            {
                [ForyField(1)]
                protected abstract int Value { get; set; }
            }
            """;
        MetadataReference parentReference = CreateGeneratedReference(
            "Fory.AbstractSlotParent",
            parentSource,
            out string parentGenerated);
        const string childSource = """
            using Apache.Fory;
            using ParentModels;
            namespace ChildModels;

            [ForyStruct]
            public sealed class Child : Parent
            {
                protected override int Value { get; set; }
            }
            """;

        CSharpCompilation compilation = CreateCompilation(
            childSource,
            includeExternalTypes: false,
            additionalReferences: [parentReference],
            assemblyName: "Fory.AbstractSlotChild");
        GeneratorDriver driver = CSharpGeneratorDriver.Create(
            new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
        string childGenerated = string.Join(
            "\n",
            driver.GetRunResult().Results
                .SelectMany(result => result.GeneratedSources)
                .Select(result => result.SourceText.ToString()));
        Assert.DoesNotContain(
            "UnsafeAccessorKind.Method, Name = \"get_Value\"",
            parentGenerated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Method, Name = \"get_Value\"",
            childGenerated,
            StringComparison.Ordinal);
        Assert.Contains(
            "UnsafeAccessorKind.Method, Name = \"set_Value\"",
            childGenerated,
            StringComparison.Ordinal);
    }

    [Fact]
    public void ReferencedProviderVersionIsValidated()
    {
        const string parentSource = """
            using Apache.Fory;
            namespace ParentModels;
            [ForyStruct]
            public abstract class Parent
            {
                [ForyField(1)]
                private int _identifier;
            }
            """;
        MetadataReference parentReference = CreateGeneratedReference(
            "Fory.VersionedParent",
            parentSource,
            out _,
            generated => generated.Replace(
                "public const int ContractVersion = 1;",
                "public const int ContractVersion = 2;",
                StringComparison.Ordinal));
        const string childSource = """
            using Apache.Fory;
            using ParentModels;
            namespace ChildModels;
            [ForyStruct]
            public sealed class Child : Parent
            {
                public int Value { get; set; }
            }
            """;

        Assert.Contains(
            GenerateDiagnostics(
                childSource,
                includeExternalTypes: false,
                additionalReferences: [parentReference],
                assemblyName: "Fory.VersionedChild"),
            diagnostic => diagnostic.Id == "FORY019");
    }

    [Fact]
    public void ReferencedAccessorContractIsValidated()
    {
        const string parentSource = """
            using Apache.Fory;
            namespace ParentModels;
            [ForyStruct]
            public abstract class Parent
            {
                [ForyField(1)]
                private int _identifier;
            }
            """;
        MetadataReference parentReference = CreateGeneratedReference(
            "Fory.AccessorParent",
            parentSource,
            out _,
            generated => generated.Replace(
                "FieldAccessorName = \"F0\"",
                "FieldAccessorName = \"Missing\"",
                StringComparison.Ordinal));
        const string childSource = """
            using Apache.Fory;
            using ParentModels;
            namespace ChildModels;
            [ForyStruct]
            public sealed class Child : Parent
            {
                public int Value { get; set; }
            }
            """;

        Assert.Contains(
            GenerateDiagnostics(
                childSource,
                includeExternalTypes: false,
                additionalReferences: [parentReference],
                assemblyName: "Fory.AccessorChild"),
            diagnostic => diagnostic.Id == "FORY020");
    }

    [Fact]
    public void ReferencedBaseWithoutProviderIsRejected()
    {
        MetadataReference parentReference = CreateReference(
            "Fory.UnannotatedParent",
            """
            namespace ParentModels;
            public abstract class Parent
            {
                private int _identifier;
            }
            """);
        const string childSource = """
            using Apache.Fory;
            using ParentModels;
            namespace ChildModels;
            [ForyStruct]
            public sealed class Child : Parent
            {
                public int Value { get; set; }
            }
            """;

        Assert.Contains(
            GenerateDiagnostics(
                childSource,
                includeExternalTypes: false,
                additionalReferences: [parentReference],
                assemblyName: "Fory.UnannotatedChild"),
            diagnostic => diagnostic.Id == "FORY019");
    }

    [Fact]
    public void LocalProviderCannotShadowReferencedProvider()
    {
        const string providerSource = """
            using Apache.Fory;
            namespace VendorModels;
            public class Vendor
            {
                public int Value;
            }
            [ForyStruct(Target = typeof(Vendor), BaseOnly = true)]
            public abstract class VendorProvider
            {
                [ForyField(1)]
                public abstract int Value { get; }
            }
            """;
        MetadataReference providerReference = CreateGeneratedReference(
            "Fory.VendorProvider",
            providerSource,
            out _);
        const string childSource = """
            using Apache.Fory;
            using VendorModels;
            namespace ChildModels;

            [ForyStruct(Target = typeof(Vendor), BaseOnly = true)]
            public abstract class DuplicateProvider
            {
                [ForyField(1)]
                public abstract int Value { get; }
            }

            [ForyStruct]
            public sealed class Child : Vendor
            {
                [ForyField(2)]
                public int Added { get; set; }
            }
            """;

        Assert.Contains(
            GenerateDiagnostics(
                childSource,
                includeExternalTypes: false,
                additionalReferences: [providerReference],
                assemblyName: "Fory.ProviderConflict"),
            diagnostic => diagnostic.Id == "FORY019");
    }

    [Fact]
    public void DuplicateReferencedProvidersAreRejected()
    {
        MetadataReference targetReference = CreateReference(
            "Fory.SharedVendor",
            """
            namespace VendorModels;
            public class Vendor
            {
                public int Value;
            }
            """);
        const string providerSource = """
            using Apache.Fory;
            using VendorModels;
            [ForyStruct(Target = typeof(Vendor), BaseOnly = true)]
            public abstract class VendorProvider
            {
                [ForyField(1)]
                public abstract int Value { get; }
            }
            """;
        MetadataReference firstProvider = CreateGeneratedReference(
            "Fory.FirstVendorProvider",
            providerSource,
            out _,
            additionalReferences: [targetReference]);
        MetadataReference secondProvider = CreateGeneratedReference(
            "Fory.SecondVendorProvider",
            providerSource,
            out _,
            additionalReferences: [targetReference]);
        const string childSource = """
            using Apache.Fory;
            using VendorModels;
            namespace ChildModels;
            [ForyStruct]
            public sealed class Child : Vendor
            {
                public int Added { get; set; }
            }
            """;

        Assert.Contains(
            GenerateDiagnostics(
                childSource,
                includeExternalTypes: false,
                additionalReferences:
                [
                    targetReference,
                    firstProvider,
                    secondProvider,
                ],
                assemblyName: "Fory.DuplicateProviderChild"),
            diagnostic => diagnostic.Id == "FORY019");
    }

    [Fact]
    public void ClosedGenericTargetsHaveDistinctProviders()
    {
        const string source = """
            using Apache.Fory;
            using Fory.ExternalTypes;
            namespace GeneratedDiagnostics;

            [ForyStruct(Target = typeof(ExternalBox<int>))]
            internal abstract class IntBoxSerializer
            {
                [ForyField(1)]
                public abstract int Value { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(ExternalBox<int>),
                    TargetMemberName = "<Value>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int ValueStorage { get; }
            }

            [ForyStruct(Target = typeof(ExternalBox<string>))]
            internal abstract class StringBoxSerializer
            {
                [ForyField(1)]
                public abstract string Value { get; }

                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(ExternalBox<string>),
                    TargetMemberName = "<Value>k__BackingField",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract string ValueStorage { get; }
            }
            """;

        string generated = GenerateSource(source);

        Assert.Equal(
            2,
            generated.Split(
                "ForyGeneratedSerializerApi(typeof(global::Fory.ExternalTypes.ExternalBox<",
                StringSplitOptions.None).Length - 1);
    }

    public static TheoryData<string, string> HierarchyDiagnosticCases => new()
    {
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct]
            public abstract class Base
            {
                [ForyField(1)]
                private int _baseValue;
            }
            [ForyStruct]
            public sealed class Derived : Base
            {
                [ForyField(1)]
                public int Value;
            }
            """,
            "FORY016"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct]
            public abstract class Base
            {
                public int BaseValue;
            }
            [ForyStruct]
            public sealed class Derived : Base
            {
                public int baseValue;
            }
            """,
            "FORY016"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct(Evolving = false)]
            public abstract class Base
            {
                public int Value { get; set; }
            }
            """,
            "FORY017"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct]
            public sealed class Holder
            {
                private Hidden _hidden;
                private struct Hidden
                {
                    public long Value;
                }
            }
            """,
            "FORY018"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct]
            public abstract class RequiredBase
            {
                public required int Value { get; set; }
            }
            [ForyStruct]
            public sealed class RequiredLeaf : RequiredBase
            {
            }
            """,
            "FORY002"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public class Base
            {
                public int Value { get; set; }
            }
            [ForyStruct]
            public sealed class Derived : Base
            {
                public int Added { get; set; }
            }
            """,
            "FORY019"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public class Vendor<T>
            {
                private T _value = default!;
            }
            [ForyStruct(Target = typeof(Vendor<int>), BaseOnly = true)]
            public abstract class VendorSerializer
            {
                [ForyField(
                    1,
                    TargetDeclaringType = typeof(Vendor<int>),
                    TargetMemberName = "_value",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int Value { get; }
            }
            """,
            "FORY020"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            internal struct HiddenValue
            {
                public int Value;
            }
            [ForyStruct]
            public abstract class PublicBase
            {
                [ForyField(1)]
                private HiddenValue _value;
            }
            """,
            "FORY020"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public class Target
            {
                public int Value { get; set; }
            }
            [ForyStruct(Target = typeof(Target), BaseOnly = true)]
            public abstract class TargetSerializer
            {
                [ForyField(
                    1,
                    TargetDeclaringType = typeof(Target),
                    TargetMemberName = "Value",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract int Value { get; }
            }
            """,
            "FORY011"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public class Target
            {
                public int Value;
            }
            [ForyStruct(Target = typeof(Target), BaseOnly = true)]
            public abstract class TargetSerializer
            {
                [ForyField(
                    Ignore = true,
                    TargetDeclaringType = typeof(Target),
                    TargetMemberName = "Value",
                    TargetMemberKind = ForyTargetMemberKind.Field)]
                public abstract long ValueStorage { get; }
            }
            """,
            "FORY012"
        },
    };

    [Theory]
    [MemberData(nameof(HierarchyDiagnosticCases))]
    public void HierarchyDiagnosticsAreReported(
        string source,
        string expectedDiagnostic)
    {
        Assert.Contains(
            GenerateDiagnostics(source),
            diagnostic => diagnostic.Id == expectedDiagnostic);
    }

    public static TheoryData<string, string> ExternalDiagnosticCases => new()
    {
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value { get; set; } }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal sealed class InvalidSerializer
            {
                public int Value { get; set; }
            }
            """,
            "FORY008"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value { get; set; } }
            public enum InvalidStatus { Ready }
            [ForyStruct(Target = typeof(InvalidTarget))]
            [ForyEnum(Target = typeof(InvalidStatus))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY008"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public enum InvalidTarget { Ready }
            [ForyEnum(Target = typeof(InvalidTarget))]
            internal static class InvalidSerializer
            {
                public const int Value = 1;
            }
            """,
            "FORY008"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct(Target = typeof(InvalidSerializer))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY009"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget<T> { public T Value { get; set; } = default!; }
            [ForyStruct(Target = typeof(InvalidTarget<>))]
            internal abstract class InvalidSerializer
            {
            }
            """,
            "FORY009"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct(Target = typeof(object))]
            internal abstract class InvalidSerializer
            {
            }
            """,
            "FORY009"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public enum InvalidTarget { Ready }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
            }
            """,
            "FORY009"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public static class Owner
            {
                private sealed class InvalidTarget { public int Value { get; set; } }
            }
            [ForyStruct(Target = typeof(Owner.InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY009"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct]
            public sealed class InvalidTarget { public int Value { get; set; } }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY009"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value { get; set; } }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class OtherSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY010"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public InvalidTarget(int value) { Value = value; }
                public int Value { get; set; }
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY002"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public required int Value { get; set; }
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY002"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public static int Value { get; set; }
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY011"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public int Value { get; } = 1;
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY011"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public int Value { get; init; }
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY011"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public readonly int Value;
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract int Value { get; }
            }
            """,
            "FORY011"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value { get; set; } }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract long Value { get; }
            }
            """,
            "FORY012"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public object Value { get; set; } = new(); }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract dynamic Value { get; }
            }
            """,
            "FORY012"
        },
        {
            """
            #nullable enable
            using System.Collections.Generic;
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget
            {
                public List<string?> Values { get; set; } = [];
            }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                public abstract List<string> Values { get; }
            }
            """,
            "FORY012"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value { get; set; } }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                [ForyField(Type = typeof(string))]
                public abstract int Value { get; }
            }
            """,
            "FORY003"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public enum InvalidTarget : long { Invalid = -1 }
            [ForyEnum(Target = typeof(InvalidTarget))]
            internal static class InvalidSerializer
            {
            }
            """,
            "FORY014"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct]
            public sealed class InvalidTarget
            {
                [ForyField(Ignore = true)]
                public int Value { get; set; }
            }
            """,
            "FORY015"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value; }
            [ForyStruct(Target = typeof(InvalidTarget))]
            internal abstract class InvalidSerializer
            {
                [ForyField(1, Ignore = true)]
                public abstract int Value { get; }
            }
            """,
            "FORY015"
        },
        {
            """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            public sealed class InvalidTarget { public int Value { get; set; } }
            public sealed class Outer<T>
            {
                [ForyStruct(Target = typeof(InvalidTarget))]
                internal abstract class InvalidSerializer
                {
                    public abstract int Value { get; }
                }
            }
            """,
            "FORY001"
        },
    };

    [Theory]
    [MemberData(nameof(ExternalDiagnosticCases))]
    public void ExternalDiagnosticsAreReported(
        string source,
        string expectedDiagnostic)
    {
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out ImmutableArray<Diagnostic> diagnostics);

        IEnumerable<Diagnostic> allDiagnostics =
            driver.GetRunResult().Diagnostics.Concat(diagnostics);
        Assert.Contains(allDiagnostics, diagnostic => diagnostic.Id == expectedDiagnostic);
    }

    [Fact]
    public void AliasOnlyTargetIsRejected()
    {
        const string source = """
            extern alias thirdparty;
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyStruct(Target = typeof(thirdparty::Fory.ExternalTypes.ExternalUser))]
            internal abstract class InvalidSerializer
            {
                public abstract int Id { get; }
                public abstract string Name { get; }
                public abstract thirdparty::Fory.ExternalTypes.ExternalUser? Friend { get; }
                public abstract System.Collections.Generic.List<
                    thirdparty::Fory.ExternalTypes.ExternalUser> Links { get; }
            }
            """;
        MetadataReference aliasReference = MetadataReference.CreateFromFile(
            typeof(global::Fory.ExternalTypes.ExternalUser).Assembly.Location,
            new MetadataReferenceProperties(
                aliases: ImmutableArray.Create("thirdparty")));
        CSharpCompilation compilation = CreateCompilation(
            source,
            includeExternalTypes: false,
            additionalReferences: [aliasReference]);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.Contains(
            driver.GetRunResult().Diagnostics.Concat(diagnostics),
            diagnostic => diagnostic.Id == "FORY013");
    }

    [Fact]
    public void DynamicAndObjectTargetsAreDuplicate()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalBox<dynamic>))]
            internal abstract class DynamicBoxSerializer
            {
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalBox<object>))]
            internal abstract class ObjectBoxSerializer
            {
            }
            """;
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out _);

        Assert.Equal(
            2,
            driver.GetRunResult().Diagnostics
                .Count(diagnostic => diagnostic.Id == "FORY010"));
    }

    [Fact]
    public void TupleNamesShareTargetIdentity()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            [ForyStruct(Target = typeof(
                Fory.ExternalTypes.ExternalBox<(int Left, string Name)>))]
            internal abstract class NamedTupleBoxSerializer
            {
            }

            [ForyStruct(Target = typeof(
                Fory.ExternalTypes.ExternalBox<(int X, string Y)>))]
            internal abstract class OtherTupleBoxSerializer
            {
            }
            """;
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out _);

        Assert.Equal(
            2,
            driver.GetRunResult().Diagnostics
                .Count(diagnostic => diagnostic.Id == "FORY010"));
    }

    [Fact]
    public void NativeIntsShareTargetIdentity()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalBox<nint>))]
            internal abstract class NativeIntBoxSerializer
            {
            }

            [ForyStruct(Target = typeof(
                Fory.ExternalTypes.ExternalBox<System.IntPtr>))]
            internal abstract class IntPtrBoxSerializer
            {
            }
            """;
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out _);

        Assert.Equal(
            2,
            driver.GetRunResult().Diagnostics
                .Count(diagnostic => diagnostic.Id == "FORY010"));
    }

    [Fact]
    public void ArrayElementsShareTargetIdentity()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;

            [ForyStruct(Target = typeof(
                Fory.ExternalTypes.ExternalBox<dynamic[]>))]
            internal abstract class DynamicArrayBoxSerializer
            {
            }

            [ForyStruct(Target = typeof(
                Fory.ExternalTypes.ExternalBox<object[]>))]
            internal abstract class ObjectArrayBoxSerializer
            {
            }
            """;
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out _);

        Assert.Equal(
            2,
            driver.GetRunResult().Diagnostics
                .Count(diagnostic => diagnostic.Id == "FORY010"));
    }

    [Fact]
    public void InternalTargetWithIvtCompiles()
    {
        const string targetSource = """
            using System.Runtime.CompilerServices;
            [assembly: InternalsVisibleTo("ForyGeneratorDiagnostics")]
            namespace ThirdParty;
            internal sealed class InternalTarget
            {
                internal int Value { get; set; }
            }
            """;
        MetadataReference targetReference = CreateReference(
            "ThirdPartyModels",
            targetSource);
        const string source = """
            using Apache.Fory;
            using ThirdParty;
            namespace GeneratedDiagnostics;
            [ForyStruct(Target = typeof(InternalTarget))]
            internal abstract class InternalTargetSerializer
            {
                public abstract int Value { get; }
            }
            """;

        CSharpCompilation compilation = CreateCompilation(
            source,
            includeExternalTypes: false,
            additionalReferences: [targetReference]);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
    }

    [Fact]
    public void ObliviousTargetCompiles()
    {
        const string targetSource = """
            #nullable disable
            namespace ThirdParty;
            public sealed class ObliviousTarget
            {
                public string Value { get; set; }
            }
            """;
        MetadataReference targetReference = CreateReference(
            "ObliviousModels",
            targetSource);
        const string source = """
            #nullable enable
            using Apache.Fory;
            using ThirdParty;
            namespace GeneratedDiagnostics;
            [ForyStruct(Target = typeof(ObliviousTarget))]
            internal abstract class ObliviousSerializer
            {
                public abstract string? Value { get; }
            }
            """;

        CSharpCompilation compilation = CreateCompilation(
            source,
            includeExternalTypes: false,
            additionalReferences: [targetReference]);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
        Assert.Contains(
            driver.GetRunResult().Results.SelectMany(result => result.GeneratedSources)
                .Select(sourceResult => sourceResult.SourceText.ToString()),
            generated => generated.Contains("value.Value", StringComparison.Ordinal));
    }

    [Fact]
    public void OrdinaryEnumRangeIsValidated()
    {
        const string source = """
            using Apache.Fory;
            namespace GeneratedDiagnostics;
            [ForyEnum]
            public enum InvalidStatus : ulong
            {
                TooLarge = 4294967296UL,
            }
            """;
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out _,
            out ImmutableArray<Diagnostic> diagnostics);

        Assert.Contains(
            driver.GetRunResult().Diagnostics.Concat(diagnostics),
            diagnostic => diagnostic.Id == "FORY014");
    }

    private static string GenerateSource(string source)
    {
        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(compilation, out Compilation output, out ImmutableArray<Diagnostic> diagnostics);

        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);

        return string.Join(
            "\n",
            driver.GetRunResult().Results.SelectMany(result => result.GeneratedSources)
                .Select(sourceResult => sourceResult.SourceText.ToString()));
    }

    private static IEnumerable<Diagnostic> GenerateDiagnostics(
        string source,
        bool includeExternalTypes = true,
        IEnumerable<MetadataReference>? additionalReferences = null,
        string assemblyName = "ForyGeneratorDiagnostics")
    {
        CSharpCompilation compilation = CreateCompilation(
            source,
            includeExternalTypes,
            additionalReferences,
            assemblyName);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(
            new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);
        return driver.GetRunResult().Diagnostics
            .Concat(diagnostics)
            .Concat(output.GetDiagnostics());
    }

    private static CSharpCompilation CreateCompilation(
        string source,
        bool includeExternalTypes = true,
        IEnumerable<MetadataReference>? additionalReferences = null,
        string assemblyName = "ForyGeneratorDiagnostics")
    {
        MetadataReference foryReference =
            MetadataReference.CreateFromFile(typeof(ForyStructAttribute).Assembly.Location);
        IEnumerable<MetadataReference> references =
            PlatformReferences().Append(foryReference);
        if (includeExternalTypes)
        {
            references = references.Append(
                MetadataReference.CreateFromFile(
                    typeof(global::Fory.ExternalTypes.ExternalUser).Assembly.Location));
        }

        if (additionalReferences is not null)
        {
            references = references.Concat(additionalReferences);
        }

        return CSharpCompilation.Create(
            assemblyName,
            [CSharpSyntaxTree.ParseText(source, new CSharpParseOptions(LanguageVersion.CSharp12))],
            references,
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }

    private static MetadataReference CreateGeneratedReference(
        string assemblyName,
        string source,
        out string generated,
        Func<string, string>? generatedTransform = null,
        IEnumerable<MetadataReference>? additionalReferences = null,
        bool publicSurfaceOnly = false)
    {
        CSharpCompilation compilation = CreateCompilation(
            source,
            includeExternalTypes: false,
            additionalReferences: additionalReferences,
            assemblyName: assemblyName);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(
            new ForyModelGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(
            compilation,
            out Compilation output,
            out ImmutableArray<Diagnostic> diagnostics);
        Assert.DoesNotContain(
            diagnostics.Concat(output.GetDiagnostics()),
            diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
        generated = string.Join(
            "\n",
            driver.GetRunResult().Results
                .SelectMany(result => result.GeneratedSources)
                .Select(result => result.SourceText.ToString()));
        if (generatedTransform is not null)
        {
            output = compilation.AddSyntaxTrees(
                CSharpSyntaxTree.ParseText(
                    generatedTransform(generated),
                    new CSharpParseOptions(LanguageVersion.CSharp12),
                    path: "Fory.GeneratedSerializers.g.cs"));
        }

        using MemoryStream stream = new();
        EmitResult emit = output.Emit(
            stream,
            options: publicSurfaceOnly
                ? new EmitOptions(
                    metadataOnly: true,
                    includePrivateMembers: false)
                : null);
        Assert.True(
            emit.Success,
            string.Join(Environment.NewLine, emit.Diagnostics));
        return MetadataReference.CreateFromImage(stream.ToArray());
    }

    private static MetadataReference CreateReference(
        string assemblyName,
        string source)
    {
        CSharpCompilation compilation = CSharpCompilation.Create(
            assemblyName,
            [CSharpSyntaxTree.ParseText(source, new CSharpParseOptions(LanguageVersion.CSharp12))],
            PlatformReferences(),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
        using MemoryStream stream = new();
        EmitResult result = compilation.Emit(stream);
        Assert.True(
            result.Success,
            string.Join(Environment.NewLine, result.Diagnostics));
        return MetadataReference.CreateFromImage(stream.ToArray());
    }

    private static IEnumerable<MetadataReference> PlatformReferences()
    {
        return ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!)
            .Split(Path.PathSeparator)
            .Select(path => MetadataReference.CreateFromFile(path));
    }
}
