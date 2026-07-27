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
        Assert.Contains("ReadValuesFieldBridge(context, remoteField.FieldType", generated, StringComparison.Ordinal);
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
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalDerived))]
            internal abstract class DerivedSerializer
            {
                [ForyField(1)]
                public abstract int Id { get; }

                [ForyField(2)]
                public abstract string BaseName { get; }
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
            }

            [ForyStruct(
                Target = typeof(Fory.ExternalTypes.ExternalEvolutionOff),
                Evolving = false)]
            internal abstract class EvolutionOffSerializer
            {
                [ForyField(1)]
                public abstract int Value { get; }
            }

            [ForyStruct(Target = typeof(Fory.ExternalTypes.ExternalVersionOne))]
            internal abstract partial class VersionSerializer
            {
                [ForyField(1)]
                public abstract int Id { get; }
            }

            internal abstract partial class VersionSerializer
            {
                [ForyField(2)]
                public abstract string Name { get; }
            }

            [ForyStruct]
            internal sealed class NullablePointHolder
            {
                public Fory.ExternalTypes.ExternalPoint? Point { get; set; }
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
            $"RegisterGenerated<{userType},",
            generated,
            StringComparison.Ordinal);
        Assert.Contains("value.Name", generated, StringComparison.Ordinal);
        Assert.Contains("value.@event", generated, StringComparison.Ordinal);
        Assert.Contains(
            "RegisterGenerated<global::Fory.ExternalTypes.ExternalEvolutionOff,",
            generated,
            StringComparison.Ordinal);
        Assert.Contains(">(false);", generated, StringComparison.Ordinal);
        Assert.Contains(
            "EnumSerializer<global::Fory.ExternalTypes.ExternalStatus>",
            generated,
            StringComparison.Ordinal);
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
            [ForyStruct]
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
            "FORY009"
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
    public void ExternalDiagnosticsStopEmission(
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
        string generated = string.Join(
            "\n",
            driver.GetRunResult().Results.SelectMany(result => result.GeneratedSources)
                .Select(sourceResult => sourceResult.SourceText.ToString()));
        Assert.DoesNotContain(
            "__ForySerializer_global__GeneratedDiagnostics_InvalidSerializer",
            generated,
            StringComparison.Ordinal);
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

    private static CSharpCompilation CreateCompilation(
        string source,
        bool includeExternalTypes = true,
        IEnumerable<MetadataReference>? additionalReferences = null)
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
            "ForyGeneratorDiagnostics",
            [CSharpSyntaxTree.ParseText(source, new CSharpParseOptions(LanguageVersion.CSharp12))],
            references,
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
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
