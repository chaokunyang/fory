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

namespace Apache.Fory.Tests;

public sealed class ForyGeneratorTests
{
    [Fact]
    public void NegativeForyFieldIdReportsDiagnostic()
    {
        const string source = """
            using Apache.Fory;

            namespace GeneratedDiagnostics;

            [ForyObject]
            public sealed class InvalidFieldId
            {
                [ForyField(-1)]
                public int Value { get; set; }
            }
            """;

        CSharpCompilation compilation = CreateCompilation(source);
        GeneratorDriver driver = CSharpGeneratorDriver.Create(new ForyObjectGenerator());
        driver = driver.RunGeneratorsAndUpdateCompilation(compilation, out Compilation output, out ImmutableArray<Diagnostic> diagnostics);

        ImmutableArray<Diagnostic> generatorDiagnostics = driver.GetRunResult().Diagnostics;
        Assert.Contains(generatorDiagnostics.Concat(diagnostics), diagnostic => diagnostic.Id == "FORY004");
        Assert.DoesNotContain(output.GetDiagnostics(), diagnostic => diagnostic.Severity == DiagnosticSeverity.Error && diagnostic.Id != "FORY004");
    }

    private static CSharpCompilation CreateCompilation(string source)
    {
        IEnumerable<MetadataReference> platformReferences =
            ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!)
            .Split(Path.PathSeparator)
            .Select(path => MetadataReference.CreateFromFile(path));
        MetadataReference foryReference =
            MetadataReference.CreateFromFile(typeof(ForyObjectAttribute).Assembly.Location);

        return CSharpCompilation.Create(
            "ForyGeneratorDiagnostics",
            [CSharpSyntaxTree.ParseText(source, new CSharpParseOptions(LanguageVersion.CSharp12))],
            platformReferences.Append(foryReference),
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    }
}
