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
using System.Globalization;
using System.Text;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis.Text;

namespace Apache.Fory.Generator;

[Generator(LanguageNames.CSharp)]
public sealed class ForyModelGenerator : IIncrementalGenerator
{
    private const uint UInt8ArrayTypeId = 48;

    private static readonly SymbolDisplayFormat FullNameFormat =
        SymbolDisplayFormat.FullyQualifiedFormat.WithMiscellaneousOptions(
            SymbolDisplayMiscellaneousOptions.IncludeNullableReferenceTypeModifier);

    private static readonly DiagnosticDescriptor GenericTypeNotSupported = new(
        id: "FORY001",
        title: "Generic types are not supported by the Fory source generator",
        messageFormat: "Type '{0}' is generic and is not supported by generated Fory attributes",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor MissingCtor = new(
        id: "FORY002",
        title: "Unsupported parameterless construction",
        messageFormat: "Class '{0}' must support legal accessible parameterless construction for [ForyStruct]",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor UnsupportedSchemaType = new(
        id: "FORY003",
        title: "Unsupported Fory field schema type",
        messageFormat: "Member '{0}' uses unsupported [ForyField] schema descriptor for type '{1}'",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidFieldId = new(
        id: "FORY004",
        title: "Invalid Fory field id",
        messageFormat: "Member '{0}' uses an invalid [ForyField] id; field ids must be non-negative and no greater than short.MaxValue",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidUnionType = new(
        id: "FORY005",
        title: "Invalid Fory union type",
        messageFormat: "Class '{0}' must declare nested [ForyUnknownCase] and [ForyCase] case types for [ForyUnion]",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidUnionCase = new(
        id: "FORY006",
        title: "Invalid Fory union case",
        messageFormat: "Union case '{0}' is invalid: {1}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor DuplicateUnionCaseId = new(
        id: "FORY007",
        title: "Duplicate Fory union case id",
        messageFormat: "Union case id {0} is declared more than once in '{1}'",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidExternalDeclaration = new(
        id: "FORY008",
        title: "Invalid external serializer declaration",
        messageFormat: "Serializer declaration '{0}' is invalid: {1}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidExternalTarget = new(
        id: "FORY009",
        title: "Invalid external serializer target",
        messageFormat: "External serializer target '{0}' is invalid: {1}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor DuplicateGeneratedTarget = new(
        id: "FORY010",
        title: "Duplicate generated serializer target",
        messageFormat: "Runtime target '{0}' has multiple generated serializer declarations",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidExternalMember = new(
        id: "FORY011",
        title: "Invalid external serializer member",
        messageFormat: "Schema member '{0}' cannot bind target '{1}': {2}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor ExternalMemberTypeMismatch = new(
        id: "FORY012",
        title: "External serializer member type mismatch",
        messageFormat: "Schema member '{0}' type '{1}' does not match target member type '{2}'",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor UnsupportedExternalAlias = new(
        id: "FORY013",
        title: "Unsupported extern alias",
        messageFormat: "Type '{0}' requires an extern alias that generated code cannot preserve",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor EnumValueOutOfRange = new(
        id: "FORY014",
        title: "Enum value is outside the supported range",
        messageFormat: "Enum member '{0}' has a value outside the supported unsigned 32-bit range",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidIgnoredField = new(
        id: "FORY015",
        title: "Invalid ignored Fory field",
        messageFormat: "Member '{0}' uses invalid [ForyField(Ignore = true)]: {1}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor DuplicateStructuralField = new(
        id: "FORY016",
        title: "Duplicate structural field identity",
        messageFormat: "Target '{0}' has duplicate structural field identity '{1}' on '{2}' and '{3}'",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidAbstractStructOption = new(
        id: "FORY017",
        title: "Invalid abstract structural option",
        messageFormat: "Abstract structural base '{0}' cannot explicitly set '{1}'",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor UnsupportedShallowField = new(
        id: "FORY018",
        title: "Unsupported shallow storage field",
        messageFormat: "Class '{0}' has physical field '{1}' whose value type '{2}' cannot be named by generated code",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor MissingHierarchyProvider = new(
        id: "FORY019",
        title: "Missing hierarchy provider serializer API",
        messageFormat: "Class '{0}' cannot use base class '{1}': {2}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    private static readonly DiagnosticDescriptor InvalidInheritedDescriptor = new(
        id: "FORY020",
        title: "Invalid inherited wire descriptor",
        messageFormat: "Class '{0}' cannot consume generated hierarchy API '{1}': {2}",
        category: "Fory",
        defaultSeverity: DiagnosticSeverity.Error,
        isEnabledByDefault: true);

    public void Initialize(IncrementalGeneratorInitializationContext context)
    {
        IncrementalValuesProvider<TypeModel?> typeModels = context.SyntaxProvider
            .CreateSyntaxProvider(
                static (node, _) => HasCandidateAttributes(node),
                static (syntaxContext, ct) => BuildTypeModel(syntaxContext, ct))
            .Where(static m => m is not null);

        context.RegisterSourceOutput(
            typeModels.Collect().Combine(context.CompilationProvider),
            static (spc, input) => Emit(spc, input.Left, input.Right));
    }

    private static bool HasCandidateAttributes(SyntaxNode node)
    {
        return node switch
        {
            TypeDeclarationSyntax typeDeclaration => typeDeclaration.AttributeLists.Count > 0,
            EnumDeclarationSyntax enumDeclaration => enumDeclaration.AttributeLists.Count > 0,
            _ => false,
        };
    }

    private static void Emit(
        SourceProductionContext context,
        ImmutableArray<TypeModel?> maybeModels,
        Compilation compilation)
    {
        if (maybeModels.IsDefaultOrEmpty)
        {
            return;
        }

        Dictionary<string, TypeModel> declarations = new(StringComparer.Ordinal);
        foreach (TypeModel? maybeModel in maybeModels)
        {
            if (maybeModel is null)
            {
                continue;
            }

            if (!declarations.ContainsKey(maybeModel.DeclarationName))
            {
                declarations.Add(maybeModel.DeclarationName, maybeModel);
            }
        }

        List<TypeModel> validModels = [];
        foreach (TypeModel model in declarations.Values)
        {
            if (!model.Diagnostics.IsDefaultOrEmpty)
            {
                foreach (Diagnostic diagnostic in model.Diagnostics)
                {
                    context.ReportDiagnostic(diagnostic);
                }

                continue;
            }

            validModels.Add(model);
        }

        IEqualityComparer<ITypeSymbol> targetTypeComparer = RuntimeTypeComparer.Instance;
        Dictionary<ITypeSymbol, TypeModel> models = new(targetTypeComparer);
        foreach (IGrouping<ITypeSymbol, TypeModel> targetGroup in validModels.GroupBy(
                     model => model.TargetType,
                     targetTypeComparer))
        {
            if (targetGroup.Skip(1).Any())
            {
                foreach (TypeModel model in targetGroup)
                {
                    context.ReportDiagnostic(Diagnostic.Create(
                        DuplicateGeneratedTarget,
                        model.DeclarationLocation,
                        model.TargetTypeName));
                }

                continue;
            }

            TypeModel targetModel = targetGroup.First();
            models.Add(targetModel.TargetType, targetModel);
        }

        foreach (IGrouping<string, TypeModel> serializerGroup in models.Values
                     .GroupBy(model => model.SerializerName, StringComparer.Ordinal)
                     .Where(group => group.Skip(1).Any())
                     .ToArray())
        {
            string targets = string.Join(
                ", ",
                serializerGroup
                    .Select(model => model.TargetTypeName)
                    .OrderBy(name => name, StringComparer.Ordinal));
            foreach (TypeModel model in serializerGroup)
            {
                context.ReportDiagnostic(Diagnostic.Create(
                    DuplicateGeneratedTarget,
                    model.DeclarationLocation,
                    targets));
                models.Remove(model.TargetType);
            }
        }

        if (models.Count == 0)
        {
            return;
        }

        ImmutableArray<TypeModel> emittedModels = ComposeHierarchyModels(
            context,
            compilation,
            models.Values.ToImmutableArray());
        if (emittedModels.IsEmpty)
        {
            return;
        }

        StringBuilder sb = new();
        sb.AppendLine("// <auto-generated/>");
        sb.AppendLine("#nullable enable");
        sb.AppendLine("namespace Apache.Fory.Generated;");
        sb.AppendLine();
        sb.AppendLine("file static class __ForyGraphElementBytes<T>");
        sb.AppendLine("{");
        sb.AppendLine("    internal static readonly int Bytes = typeof(T).IsValueType ? global::System.Runtime.CompilerServices.Unsafe.SizeOf<T>() : 4;");
        sb.AppendLine("}");
        sb.AppendLine();

        foreach (TypeModel model in emittedModels
                     .OrderBy(model => model.TargetTypeName, StringComparer.Ordinal)
                     .ThenBy(model => model.DeclarationName, StringComparer.Ordinal))
        {
            if (model.Kind == DeclKind.Struct || model.Kind == DeclKind.Class)
            {
                EmitObjectSerializer(sb, model);
                sb.AppendLine();
            }
            else if (model.Kind == DeclKind.Union)
            {
                EmitUnionSerializer(sb, model);
                sb.AppendLine();
            }
        }

        sb.AppendLine("internal static class __ForyGeneratedModuleInitializer");
        sb.AppendLine("{");
        sb.AppendLine("    [global::System.Runtime.CompilerServices.ModuleInitializer]");
        sb.AppendLine("    internal static void Register()");
        sb.AppendLine("    {");
        foreach (TypeModel model in emittedModels
                     .OrderBy(model => model.TargetTypeName, StringComparer.Ordinal)
                     .ThenBy(model => model.DeclarationName, StringComparer.Ordinal))
        {
            if (!model.RegisterSerializer)
            {
                continue;
            }

            if (model.Kind == DeclKind.Enum)
            {
                sb.AppendLine(
                    $"        global::Apache.Fory.TypeResolver.RegisterGenerated<{model.TargetTypeName}, global::Apache.Fory.EnumSerializer<{model.TargetTypeName}>>();");
            }
            else if (model.Kind == DeclKind.Union)
            {
                sb.AppendLine(
                    $"        global::Apache.Fory.TypeResolver.RegisterGenerated<{model.TargetTypeName}, {model.SerializerName}>();");
            }
            else
            {
                sb.AppendLine(
                    $"        global::Apache.Fory.TypeResolver.RegisterGeneratedStruct<{model.TargetTypeName}, {model.SerializerName}>({BoolLiteral(model.Evolving)});");
            }
        }

        sb.AppendLine("    }");
        sb.AppendLine("}");

        context.AddSource("Fory.GeneratedSerializers.g.cs", SourceText.From(sb.ToString(), Encoding.UTF8));
    }

    private static ImmutableArray<TypeModel> ComposeHierarchyModels(
        SourceProductionContext context,
        Compilation compilation,
        ImmutableArray<TypeModel> rawModels)
    {
        IEqualityComparer<ITypeSymbol> comparer = RuntimeTypeComparer.Instance;
        Dictionary<ITypeSymbol, TypeModel> ordinaryClasses = new(comparer);
        Dictionary<ITypeSymbol, TypeModel> externalClasses = new(comparer);
        foreach (TypeModel model in rawModels)
        {
            if (model.Kind != DeclKind.Class)
            {
                continue;
            }

            if (model.IsOrdinary)
            {
                ordinaryClasses.Add(model.TargetType, model);
            }
            else if (model.IsExternal)
            {
                externalClasses.Add(model.TargetType, model);
            }
        }

        List<TypeModel> result = [];
        foreach (TypeModel rawModel in rawModels)
        {
            if (rawModel.Kind != DeclKind.Class)
            {
                ImmutableArray<MemberModel> members = AssignCodeKeys(rawModel.Members);
                result.Add(rawModel.WithHierarchy(members, SortMembers(members), null));
                continue;
            }

            if (rawModel.IsExternal)
            {
                List<Diagnostic> diagnostics = [];
                ValidateStructuralIdentities(rawModel, rawModel.Members, diagnostics);
                if (diagnostics.Count > 0)
                {
                    foreach (Diagnostic diagnostic in diagnostics)
                    {
                        context.ReportDiagnostic(diagnostic);
                    }

                    continue;
                }

                ImmutableArray<MemberModel> members = AssignCodeKeys(rawModel.Members);
                result.Add(rawModel.WithHierarchy(members, SortMembers(members), null));
                continue;
            }

            if (!rawModel.IsOrdinary ||
                rawModel.TargetType is not INamedTypeSymbol target)
            {
                result.Add(rawModel);
                continue;
            }

            List<Diagnostic> hierarchyDiagnostics = [];
            List<MemberModel> flattened = [];
            string? parentProviderTypeName = null;
            if (target.BaseType is INamedTypeSymbol baseType &&
                baseType.SpecialType != SpecialType.System_Object)
            {
                HashSet<string> providerPath = new(StringComparer.Ordinal);
                if (!TryResolveProvider(
                        compilation,
                        rawModel,
                        baseType,
                        ordinaryClasses,
                        externalClasses,
                        providerPath,
                        hierarchyDiagnostics,
                        out ResolvedProvider? provider))
                {
                    foreach (Diagnostic diagnostic in hierarchyDiagnostics)
                    {
                        context.ReportDiagnostic(diagnostic);
                    }

                    continue;
                }

                parentProviderTypeName = provider!.ProviderTypeName;
                flattened.AddRange(provider.WireMembers);
            }

            flattened.AddRange(rawModel.DeclaredMembers);
            ImmutableArray<MemberModel> collapsed = CollapsePropertyOverrides(flattened);
            ValidateStructuralIdentities(rawModel, collapsed, hierarchyDiagnostics);
            if (hierarchyDiagnostics.Count > 0)
            {
                foreach (Diagnostic diagnostic in hierarchyDiagnostics)
                {
                    context.ReportDiagnostic(diagnostic);
                }

                continue;
            }

            ImmutableArray<MemberModel> membersWithKeys = AssignCodeKeys(collapsed);
            result.Add(rawModel.WithHierarchy(
                membersWithKeys,
                SortMembers(membersWithKeys),
                parentProviderTypeName));
        }

        return result.ToImmutableArray();
    }

    private static bool TryResolveProvider(
        Compilation compilation,
        TypeModel consumer,
        INamedTypeSymbol target,
        Dictionary<ITypeSymbol, TypeModel> ordinaryClasses,
        Dictionary<ITypeSymbol, TypeModel> externalClasses,
        HashSet<string> providerPath,
        List<Diagnostic> diagnostics,
        out ResolvedProvider? provider)
    {
        ImmutableArray<INamedTypeSymbol> referencedProviders =
            FindReferencedProviders(compilation, target);
        if (ordinaryClasses.TryGetValue(target, out TypeModel? ordinary))
        {
            if (!referencedProviders.IsEmpty)
            {
                ReportProviderConflict(
                    consumer,
                    target,
                    referencedProviders,
                    diagnostics);
                provider = null;
                return false;
            }

            string providerTypeName =
                $"global::Apache.Fory.Generated.{ordinary.SerializerName}";
            if (!providerPath.Add(providerTypeName))
            {
                diagnostics.Add(Diagnostic.Create(
                    MissingHierarchyProvider,
                    consumer.DeclarationLocation,
                    consumer.TargetTypeName,
                    target.ToDisplayString(FullNameFormat),
                    "the generated provider parent links contain a cycle"));
                provider = null;
                return false;
            }

            List<MemberModel> members = [];
            if (target.BaseType is INamedTypeSymbol baseType &&
                baseType.SpecialType != SpecialType.System_Object)
            {
                if (!TryResolveProvider(
                        compilation,
                        consumer,
                        baseType,
                        ordinaryClasses,
                        externalClasses,
                        providerPath,
                        diagnostics,
                        out ResolvedProvider? parent))
                {
                    provider = null;
                    return false;
                }

                members.AddRange(parent!.WireMembers);
            }

            members.AddRange(ordinary.DeclaredMembers.Select(ForInheritedUse));
            providerPath.Remove(providerTypeName);
            provider = new ResolvedProvider(
                providerTypeName,
                CollapsePropertyOverrides(members));
            return true;
        }

        if (externalClasses.TryGetValue(target, out TypeModel? external))
        {
            if (!referencedProviders.IsEmpty)
            {
                ReportProviderConflict(
                    consumer,
                    target,
                    referencedProviders,
                    diagnostics);
                provider = null;
                return false;
            }

            string providerTypeName =
                $"global::Apache.Fory.Generated.{external.SerializerName}";
            provider = new ResolvedProvider(
                providerTypeName,
                external.DeclaredMembers.Select(ForInheritedUse).ToImmutableArray());
            return true;
        }

        return TryResolveReferencedProvider(
            compilation,
            consumer,
            target,
            providerPath,
            diagnostics,
            out provider);
    }

    private static ImmutableArray<INamedTypeSymbol> FindReferencedProviders(
        Compilation compilation,
        INamedTypeSymbol target)
    {
        string metadataName =
            $"Apache.Fory.Generated.{GeneratedSerializerName(target)}";
        ImmutableArray<INamedTypeSymbol>.Builder matches =
            ImmutableArray.CreateBuilder<INamedTypeSymbol>();
        foreach (MetadataReference reference in compilation.References)
        {
            if (compilation.GetAssemblyOrModuleSymbol(reference) is IAssemblySymbol assembly &&
                assembly.GetTypeByMetadataName(metadataName) is INamedTypeSymbol match)
            {
                matches.Add(match);
            }
        }

        return matches.ToImmutable();
    }

    private static void ReportProviderConflict(
        TypeModel consumer,
        INamedTypeSymbol target,
        ImmutableArray<INamedTypeSymbol> referencedProviders,
        List<Diagnostic> diagnostics)
    {
        string assemblies = string.Join(
            ", ",
            referencedProviders
                .Select(provider => provider.ContainingAssembly.Identity.Name)
                .Distinct(StringComparer.Ordinal)
                .OrderBy(name => name, StringComparer.Ordinal));
        diagnostics.Add(Diagnostic.Create(
            MissingHierarchyProvider,
            consumer.DeclarationLocation,
            consumer.TargetTypeName,
            target.ToDisplayString(FullNameFormat),
            $"a local provider conflicts with referenced provider assemblies {assemblies}"));
    }

    private static bool TryResolveReferencedProvider(
        Compilation compilation,
        TypeModel consumer,
        INamedTypeSymbol target,
        HashSet<string> providerPath,
        List<Diagnostic> diagnostics,
        out ResolvedProvider? provider)
    {
        string metadataName =
            $"Apache.Fory.Generated.{GeneratedSerializerName(target)}";
        ImmutableArray<INamedTypeSymbol> matches =
            FindReferencedProviders(compilation, target);

        if (matches.Length != 1)
        {
            string reason = matches.Length == 0
                ? $"no generated provider named '{metadataName}' is referenced"
                : $"multiple generated providers are referenced from {string.Join(", ", matches.Select(match => match.ContainingAssembly.Identity.Name).Distinct(StringComparer.Ordinal).OrderBy(name => name, StringComparer.Ordinal))}";
            diagnostics.Add(Diagnostic.Create(
                MissingHierarchyProvider,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                target.ToDisplayString(FullNameFormat),
                reason));
            provider = null;
            return false;
        }

        return TryReadReferencedProvider(
            compilation,
            consumer,
            matches[0],
            target,
            providerPath,
            diagnostics,
            out provider);
    }

    private static bool TryReadReferencedProvider(
        Compilation compilation,
        TypeModel consumer,
        INamedTypeSymbol providerType,
        INamedTypeSymbol expectedTarget,
        HashSet<string> providerPath,
        List<Diagnostic> diagnostics,
        out ResolvedProvider? provider)
    {
        string providerTypeName = providerType.ToDisplayString(FullNameFormat);
        if (!providerPath.Add(providerTypeName))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerTypeName,
                "the provider parent links contain a cycle"));
            provider = null;
            return false;
        }

        string expectedMetadataName = GeneratedSerializerName(expectedTarget);
        if (providerType.ContainingType is not null ||
            providerType.MetadataName != expectedMetadataName ||
            providerType.ContainingNamespace.ToDisplayString() != "Apache.Fory.Generated")
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerTypeName,
                $"the provider name does not match target-keyed contract '{expectedMetadataName}'"));
            provider = null;
            return false;
        }

        if (!compilation.IsSymbolAccessibleWithin(providerType, compilation.Assembly))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerTypeName,
                "the provider type is not accessible"));
            provider = null;
            return false;
        }

        AttributeData? marker = providerType.GetAttributes().SingleOrDefault(attribute =>
            string.Equals(
                attribute.AttributeClass?.ToDisplayString(),
                "Apache.Fory.ForyGeneratedSerializerApiAttribute",
                StringComparison.Ordinal));
        if (marker is null ||
            marker.ConstructorArguments.Length != 2 ||
            marker.ConstructorArguments[0].Value is not INamedTypeSymbol markerTarget ||
            marker.ConstructorArguments[1].Value is not int providerKindValue ||
            providerKindValue is < 0 or > 1 ||
            !RuntimeTypeComparer.Instance.Equals(markerTarget, expectedTarget))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerTypeName,
                "the provider marker does not bind the expected target"));
            provider = null;
            return false;
        }

        if (!HasSerializerBase(providerType, expectedTarget))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerTypeName,
                "the provider base type is invalid"));
            provider = null;
            return false;
        }

        if (!HasProviderContractFields(providerType))
        {
            diagnostics.Add(Diagnostic.Create(
                MissingHierarchyProvider,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                expectedTarget.ToDisplayString(FullNameFormat),
                $"provider '{providerTypeName}' has an incompatible contract version or HierarchyShallowBytes field"));
            provider = null;
            return false;
        }

        INamedTypeSymbol? parentProviderType = null;
        foreach (KeyValuePair<string, TypedConstant> argument in marker.NamedArguments)
        {
            if (string.Equals(argument.Key, "ParentSerializerType", StringComparison.Ordinal))
            {
                parentProviderType = argument.Value.Value as INamedTypeSymbol;
            }
        }

        ForyProviderKind providerKind = (ForyProviderKind)providerKindValue;
        List<MemberModel> members = [];
        if (providerKind == ForyProviderKind.External)
        {
            if (parentProviderType is not null)
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidInheritedDescriptor,
                    consumer.DeclarationLocation,
                    consumer.TargetTypeName,
                    providerTypeName,
                    "an external hierarchy provider cannot have a parent provider link"));
                provider = null;
                return false;
            }
        }
        else if (expectedTarget.BaseType is INamedTypeSymbol expectedBase &&
                 expectedBase.SpecialType != SpecialType.System_Object)
        {
            if (parentProviderType is null ||
                !TryReadReferencedProvider(
                    compilation,
                    consumer,
                    parentProviderType,
                    expectedBase,
                    providerPath,
                    diagnostics,
                    out ResolvedProvider? parent))
            {
                if (parentProviderType is null)
                {
                    diagnostics.Add(Diagnostic.Create(
                        InvalidInheritedDescriptor,
                        consumer.DeclarationLocation,
                        consumer.TargetTypeName,
                        providerTypeName,
                        "a non-root ordinary provider is missing its parent provider link"));
                }

                provider = null;
                return false;
            }

            members.AddRange(parent!.WireMembers);
        }
        else if (parentProviderType is not null)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerTypeName,
                "a root ordinary provider cannot have a parent provider link"));
            provider = null;
            return false;
        }

        if (!TryReadWireDescriptors(
                compilation,
                consumer,
                providerType,
                expectedTarget,
                providerKind,
                diagnostics,
                out ImmutableArray<MemberModel> declaredMembers))
        {
            provider = null;
            return false;
        }

        members.AddRange(declaredMembers);
        providerPath.Remove(providerTypeName);
        provider = new ResolvedProvider(
            providerTypeName,
            CollapsePropertyOverrides(members));
        return true;
    }

    private static bool HasSerializerBase(
        INamedTypeSymbol providerType,
        INamedTypeSymbol expectedTarget)
    {
        INamedTypeSymbol? baseType = providerType.BaseType;
        return baseType is not null &&
               string.Equals(
                   baseType.OriginalDefinition.ToDisplayString(),
                   "Apache.Fory.Serializer<T>",
                   StringComparison.Ordinal) &&
               baseType.TypeArguments.Length == 1 &&
               RuntimeTypeComparer.Instance.Equals(
                   baseType.TypeArguments[0],
                   expectedTarget);
    }

    private static bool HasProviderContractFields(INamedTypeSymbol providerType)
    {
        IFieldSymbol? version = providerType.GetMembers("ContractVersion")
            .OfType<IFieldSymbol>()
            .SingleOrDefault();
        IFieldSymbol? shallowBytes = providerType.GetMembers("HierarchyShallowBytes")
            .OfType<IFieldSymbol>()
            .SingleOrDefault();
        return version is
        {
            DeclaredAccessibility: Accessibility.Public,
            IsStatic: true,
            HasConstantValue: true,
            ConstantValue: 1,
        } &&
               version.Type.SpecialType == SpecialType.System_Int32 &&
               shallowBytes is
               {
                   DeclaredAccessibility: Accessibility.Public,
                   IsStatic: true,
                   IsReadOnly: true,
                   IsConst: false,
               } &&
               shallowBytes.Type.SpecialType == SpecialType.System_Int64;
    }

    private static bool TryReadWireDescriptors(
        Compilation compilation,
        TypeModel consumer,
        INamedTypeSymbol providerType,
        INamedTypeSymbol providerTarget,
        ForyProviderKind providerKind,
        List<Diagnostic> diagnostics,
        out ImmutableArray<MemberModel> members)
    {
        List<(int Ordinal, MemberModel Member)> parsedMembers = [];
        HashSet<int> ordinals = [];
        foreach (IFieldSymbol descriptorField in providerType.GetMembers()
                     .OfType<IFieldSymbol>())
        {
            AttributeData? descriptor = descriptorField.GetAttributes().SingleOrDefault(attribute =>
                string.Equals(
                    attribute.AttributeClass?.ToDisplayString(),
                    "Apache.Fory.ForyGeneratedWireMemberAttribute",
                    StringComparison.Ordinal));
            if (descriptor is null)
            {
                continue;
            }

            if (!TryReadWireDescriptor(
                    compilation,
                    consumer,
                    providerType,
                    providerTarget,
                    providerKind,
                    descriptorField,
                    descriptor,
                    diagnostics,
                    out int ordinal,
                    out MemberModel? member))
            {
                members = ImmutableArray<MemberModel>.Empty;
                return false;
            }

            if (!ordinals.Add(ordinal))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidInheritedDescriptor,
                    consumer.DeclarationLocation,
                    consumer.TargetTypeName,
                    providerType.ToDisplayString(FullNameFormat),
                    $"wire descriptor ordinal {ordinal} is duplicated"));
                members = ImmutableArray<MemberModel>.Empty;
                return false;
            }

            parsedMembers.Add((ordinal, member!));
        }

        parsedMembers.Sort((left, right) => left.Ordinal.CompareTo(right.Ordinal));
        members = parsedMembers
            .Select(entry => entry.Member)
            .ToImmutableArray();
        return true;
    }

    private static bool TryReadWireDescriptor(
        Compilation compilation,
        TypeModel consumer,
        INamedTypeSymbol providerType,
        INamedTypeSymbol providerTarget,
        ForyProviderKind providerKind,
        IFieldSymbol descriptorField,
        AttributeData descriptor,
        List<Diagnostic> diagnostics,
        out int ordinal,
        out MemberModel? member)
    {
        ordinal = -1;
        member = null;
        if (descriptorField.DeclaredAccessibility != Accessibility.Public ||
            !descriptorField.IsStatic ||
            !descriptorField.IsConst ||
            descriptorField.Type.SpecialType != SpecialType.System_Byte ||
            descriptorField.ConstantValue is not byte descriptorValue ||
            descriptorValue != 0 ||
            descriptor.ConstructorArguments.Length != 6 ||
            descriptor.ConstructorArguments[0].Value is not int parsedOrdinal ||
            parsedOrdinal < 0 ||
            descriptor.ConstructorArguments[1].Value is not INamedTypeSymbol declaringType ||
            descriptor.ConstructorArguments[2].Value is not ITypeSymbol rawMemberType ||
            descriptor.ConstructorArguments[3].Value is not string logicalName ||
            descriptor.ConstructorArguments[4].Value is not string targetMemberName ||
            descriptor.ConstructorArguments[5].Value is not int memberKindValue ||
            memberKindValue is < 0 or > 1)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' has malformed wire metadata"));
            return false;
        }

        if (descriptorField.Name != $"__ForyWire{parsedOrdinal}" ||
            string.IsNullOrEmpty(logicalName) ||
            string.IsNullOrEmpty(targetMemberName) ||
            providerKind == ForyProviderKind.Ordinary &&
            !RuntimeTypeComparer.Instance.Equals(declaringType, providerTarget) ||
            providerKind == ForyProviderKind.External &&
            !IsTypeInHierarchy(providerTarget, declaringType))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' has invalid declaration ownership"));
            return false;
        }

        int fieldIdValue = -1;
        ITypeSymbol? schemaDescriptorType = null;
        string? slot = null;
        string? fieldAccessorName = null;
        string? getterAccessorName = null;
        string? setterAccessorName = null;
        ImmutableArray<byte> nullableShape = ImmutableArray<byte>.Empty;
        foreach (KeyValuePair<string, TypedConstant> argument in descriptor.NamedArguments)
        {
            switch (argument.Key)
            {
                case "FieldId":
                    if (argument.Value.Value is int configuredFieldId)
                    {
                        fieldIdValue = configuredFieldId;
                    }

                    break;
                case "SchemaType":
                    schemaDescriptorType = argument.Value.Value as ITypeSymbol;
                    break;
                case "Slot":
                    slot = argument.Value.Value as string;
                    break;
                case "FieldAccessorName":
                    fieldAccessorName = argument.Value.Value as string;
                    break;
                case "GetterAccessorName":
                    getterAccessorName = argument.Value.Value as string;
                    break;
                case "SetterAccessorName":
                    setterAccessorName = argument.Value.Value as string;
                    break;
                case "NullableShape":
                    nullableShape = argument.Value.Values
                        .Select(value => value.Value is byte byteValue ? byteValue : byte.MaxValue)
                        .ToImmutableArray();
                    break;
            }
        }

        if (fieldIdValue is < -1 or > short.MaxValue ||
            nullableShape.Any(value => value > 3) ||
            !TryApplyNullableShape(
                compilation,
                rawMemberType,
                nullableShape,
                out ITypeSymbol? memberType))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' has an invalid field ID or nullable shape"));
            return false;
        }

        SchemaTypeModel? schemaType = schemaDescriptorType is null
            ? null
            : TryParseSchemaType(schemaDescriptorType);
        if (schemaDescriptorType is not null && schemaType is null)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' has an unsupported schema descriptor"));
            return false;
        }

        int diagnosticCount = diagnostics.Count;
        MemberModel? parsed = BuildMemberModel(
            logicalName,
            memberType!,
            descriptorField,
            diagnostics,
            schemaType,
            parseFieldAttribute: false,
            fieldIdValue < 0 ? null : (short)fieldIdValue,
            schemaDescriptorType);
        if (parsed is null || diagnostics.Count != diagnosticCount)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' describes an unsupported wire type"));
            return false;
        }

        WireMemberKind memberKind = memberKindValue == 0
            ? WireMemberKind.Field
            : WireMemberKind.Property;
        if (memberKind == WireMemberKind.Field &&
            (slot is not null ||
             getterAccessorName is not null ||
             setterAccessorName is not null) ||
            memberKind == WireMemberKind.Property &&
            (string.IsNullOrEmpty(slot) ||
             fieldAccessorName is not null))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' has inconsistent member-access metadata"));
            return false;
        }

        if (!ValidateReferencedMemberAccess(
                compilation,
                providerType,
                declaringType,
                memberType!,
                targetMemberName,
                memberKind,
                providerKind,
                fieldAccessorName,
                getterAccessorName,
                setterAccessorName))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                consumer.DeclarationLocation,
                consumer.TargetTypeName,
                providerType.ToDisplayString(FullNameFormat),
                $"field '{descriptorField.Name}' has an inaccessible or mismatched member accessor"));
            return false;
        }

        string providerTypeName = providerType.ToDisplayString(FullNameFormat);
        parsed = parsed.WithDeclaration(
            memberType!,
            declaringType,
            targetMemberName,
            memberKind,
            slot,
            providerTypeName,
            fieldAccessorName,
            getterAccessorName,
            setterAccessorName,
            schemaDescriptorType,
            parsedOrdinal,
            nullableShape);
        if (fieldAccessorName is null &&
            getterAccessorName is null &&
            setterAccessorName is null)
        {
            parsed = parsed.WithAccess(
                declaringType,
                providerTypeName,
                null,
                null,
                null,
                useDeclaringCast: true);
        }

        ordinal = parsedOrdinal;
        member = parsed;
        return true;
    }

    private static bool ValidateReferencedMemberAccess(
        Compilation compilation,
        INamedTypeSymbol providerType,
        INamedTypeSymbol declaringType,
        ITypeSymbol memberType,
        string targetMemberName,
        WireMemberKind memberKind,
        ForyProviderKind providerKind,
        string? fieldAccessorName,
        string? getterAccessorName,
        string? setterAccessorName)
    {
        if (!compilation.IsSymbolAccessibleWithin(declaringType, compilation.Assembly) ||
            !compilation.IsSymbolAccessibleWithin(memberType, compilation.Assembly))
        {
            return false;
        }

        if (memberKind == WireMemberKind.Field)
        {
            if (fieldAccessorName is not null)
            {
                return IsValidFieldAccessor(
                    compilation,
                    providerType,
                    fieldAccessorName,
                    declaringType,
                    memberType,
                    targetMemberName);
            }

            return declaringType.GetMembers(targetMemberName)
                .OfType<IFieldSymbol>()
                .Any(field =>
                    !field.IsStatic &&
                    !field.IsConst &&
                    !field.IsReadOnly &&
                    ExternalMemberTypesMatch(memberType, field.Type) &&
                    compilation.IsSymbolAccessibleWithin(field, compilation.Assembly));
        }

        if ((getterAccessorName is null) != (setterAccessorName is null))
        {
            return false;
        }

        if (getterAccessorName is not null)
        {
            return IsValidGetterAccessor(
                       compilation,
                       providerType,
                       getterAccessorName,
                       declaringType,
                       memberType,
                       $"get_{targetMemberName}") &&
                   IsValidSetterAccessor(
                       compilation,
                       providerType,
                       setterAccessorName!,
                       declaringType,
                       memberType,
                       $"set_{targetMemberName}");
        }

        IPropertySymbol[] properties = declaringType.GetMembers(targetMemberName)
            .OfType<IPropertySymbol>()
            .Where(property =>
                !property.IsStatic &&
                property.GetMethod is not null &&
                property.SetMethod is { IsInitOnly: false } &&
                ExternalMemberTypesMatch(memberType, property.Type))
            .ToArray();
        if (providerKind == ForyProviderKind.Ordinary &&
            properties.Any(property => property.IsAbstract))
        {
            return true;
        }

        return properties.Any(property =>
                compilation.IsSymbolAccessibleWithin(property.GetMethod!, compilation.Assembly) &&
                compilation.IsSymbolAccessibleWithin(property.SetMethod!, compilation.Assembly));
    }

    private static bool IsValidFieldAccessor(
        Compilation compilation,
        INamedTypeSymbol providerType,
        string methodName,
        INamedTypeSymbol declaringType,
        ITypeSymbol memberType,
        string targetName)
    {
        return providerType.GetMembers(methodName)
            .OfType<IMethodSymbol>()
            .Any(method =>
                method.IsStatic &&
                method.ReturnsByRef &&
                method.Parameters.Length == 1 &&
                RuntimeTypeComparer.Instance.Equals(method.Parameters[0].Type, declaringType) &&
                ExternalMemberTypesMatch(method.ReturnType, memberType) &&
                compilation.IsSymbolAccessibleWithin(method, compilation.Assembly) &&
                HasUnsafeAccessor(method, "Field", targetName));
    }

    private static bool IsValidGetterAccessor(
        Compilation compilation,
        INamedTypeSymbol providerType,
        string methodName,
        INamedTypeSymbol declaringType,
        ITypeSymbol memberType,
        string targetName)
    {
        return providerType.GetMembers(methodName)
            .OfType<IMethodSymbol>()
            .Any(method =>
                method.IsStatic &&
                !method.ReturnsByRef &&
                method.Parameters.Length == 1 &&
                RuntimeTypeComparer.Instance.Equals(method.Parameters[0].Type, declaringType) &&
                ExternalMemberTypesMatch(method.ReturnType, memberType) &&
                compilation.IsSymbolAccessibleWithin(method, compilation.Assembly) &&
                HasUnsafeAccessor(method, "Method", targetName));
    }

    private static bool IsValidSetterAccessor(
        Compilation compilation,
        INamedTypeSymbol providerType,
        string methodName,
        INamedTypeSymbol declaringType,
        ITypeSymbol memberType,
        string targetName)
    {
        return providerType.GetMembers(methodName)
            .OfType<IMethodSymbol>()
            .Any(method =>
                method.IsStatic &&
                method.ReturnsVoid &&
                method.Parameters.Length == 2 &&
                RuntimeTypeComparer.Instance.Equals(method.Parameters[0].Type, declaringType) &&
                ExternalMemberTypesMatch(method.Parameters[1].Type, memberType) &&
                compilation.IsSymbolAccessibleWithin(method, compilation.Assembly) &&
                HasUnsafeAccessor(method, "Method", targetName));
    }

    private static bool HasUnsafeAccessor(
        IMethodSymbol method,
        string expectedKind,
        string targetName)
    {
        AttributeData? attribute = method.GetAttributes().SingleOrDefault(candidate =>
            string.Equals(
                candidate.AttributeClass?.ToDisplayString(),
                "System.Runtime.CompilerServices.UnsafeAccessorAttribute",
                StringComparison.Ordinal));
        if (attribute is null ||
            attribute.ConstructorArguments.Length != 1 ||
            attribute.ConstructorArguments[0].Type is not INamedTypeSymbol kindType ||
            kindType.GetMembers(expectedKind).OfType<IFieldSymbol>().SingleOrDefault()
                is not { HasConstantValue: true } expectedKindField ||
            !Equals(
                attribute.ConstructorArguments[0].Value,
                expectedKindField.ConstantValue))
        {
            return false;
        }

        string? declaredName = attribute.NamedArguments
            .SingleOrDefault(argument => argument.Key == "Name")
            .Value.Value as string;
        return string.Equals(declaredName, targetName, StringComparison.Ordinal);
    }

    private static bool TryApplyNullableShape(
        Compilation compilation,
        ITypeSymbol rawType,
        ImmutableArray<byte> shape,
        out ITypeSymbol? type)
    {
        if (shape.IsEmpty)
        {
            type = rawType;
            return true;
        }

        int index = 0;
        bool success = TryApplyNullableShape(
            compilation,
            rawType,
            shape,
            ref index,
            out type);
        return success && index == shape.Length;
    }

    private static bool TryApplyNullableShape(
        Compilation compilation,
        ITypeSymbol rawType,
        ImmutableArray<byte> shape,
        ref int index,
        out ITypeSymbol? type)
    {
        if (index >= shape.Length)
        {
            type = null;
            return false;
        }

        byte annotationValue = shape[index++];
        if (annotationValue == 3)
        {
            type = compilation.DynamicType;
            return true;
        }

        NullableAnnotation annotation = annotationValue switch
        {
            1 => NullableAnnotation.NotAnnotated,
            2 => NullableAnnotation.Annotated,
            _ => NullableAnnotation.None,
        };
        switch (rawType)
        {
            case IArrayTypeSymbol array:
                if (!TryApplyNullableShape(
                        compilation,
                        array.ElementType,
                        shape,
                        ref index,
                        out ITypeSymbol? elementType))
                {
                    type = null;
                    return false;
                }

                type = compilation.CreateArrayTypeSymbol(
                    elementType!,
                    array.Rank,
                    annotation);
                return true;
            case IPointerTypeSymbol pointer:
                if (!TryApplyNullableShape(
                        compilation,
                        pointer.PointedAtType,
                        shape,
                        ref index,
                        out ITypeSymbol? pointedAtType))
                {
                    type = null;
                    return false;
                }

                type = compilation.CreatePointerTypeSymbol(pointedAtType!);
                return true;
            case INamedTypeSymbol named:
                INamedTypeSymbol? containingType = null;
                ITypeSymbol? containing = null;
                if (named.ContainingType is not null &&
                    !TryApplyNullableShape(
                        compilation,
                        named.ContainingType,
                        shape,
                        ref index,
                        out containing))
                {
                    type = null;
                    return false;
                }
                else if (named.ContainingType is not null)
                {
                    containingType = (INamedTypeSymbol)containing!;
                }

                ITypeSymbol[] typeArguments = new ITypeSymbol[named.TypeArguments.Length];
                for (int argumentIndex = 0;
                     argumentIndex < typeArguments.Length;
                     argumentIndex++)
                {
                    if (!TryApplyNullableShape(
                            compilation,
                            named.TypeArguments[argumentIndex],
                            shape,
                            ref index,
                            out ITypeSymbol? typeArgument))
                    {
                        type = null;
                        return false;
                    }

                    typeArguments[argumentIndex] = typeArgument!;
                }

                INamedTypeSymbol definition = named.OriginalDefinition;
                if (containingType is not null)
                {
                    INamedTypeSymbol[] nested = containingType
                        .GetTypeMembers(definition.Name, definition.Arity)
                        .ToArray();
                    if (nested.Length != 1)
                    {
                        type = null;
                        return false;
                    }

                    definition = nested[0];
                }

                type = (typeArguments.Length == 0
                        ? definition
                        : definition.Construct(typeArguments))
                    .WithNullableAnnotation(annotation);
                return true;
            default:
                type = rawType.WithNullableAnnotation(annotation);
                return true;
        }
    }

    private static MemberModel ForInheritedUse(MemberModel member)
    {
        return member.WithAccess(
            member.DeclaringType,
            member.AccessorProviderTypeName,
            member.FieldAccessorName,
            member.GetterAccessorName,
            member.SetterAccessorName,
            useDeclaringCast: true);
    }

    private static ImmutableArray<MemberModel> CollapsePropertyOverrides(
        IEnumerable<MemberModel> members)
    {
        List<MemberModel> collapsed = [];
        Dictionary<string, int> propertySlots = new(StringComparer.Ordinal);
        foreach (MemberModel member in members)
        {
            if (member.MemberKind == WireMemberKind.Property &&
                member.SlotKey is not null)
            {
                if (propertySlots.TryGetValue(member.SlotKey, out int index))
                {
                    collapsed[index] = member;
                }
                else
                {
                    propertySlots.Add(member.SlotKey, collapsed.Count);
                    collapsed.Add(member);
                }
            }
            else
            {
                collapsed.Add(member);
            }
        }

        return collapsed.ToImmutableArray();
    }

    private static ImmutableArray<MemberModel> AssignCodeKeys(
        IEnumerable<MemberModel> members)
    {
        return members
            .Select((member, index) => member.WithCodeKey($"M{index}"))
            .ToImmutableArray();
    }

    private static void ValidateStructuralIdentities(
        TypeModel target,
        IEnumerable<MemberModel> members,
        List<Diagnostic> diagnostics)
    {
        Dictionary<short, MemberModel> fieldIds = [];
        Dictionary<string, MemberModel> fieldNames = new(StringComparer.Ordinal);
        foreach (MemberModel member in members)
        {
            if (member.FieldId.HasValue)
            {
                if (fieldIds.TryGetValue(member.FieldId.Value, out MemberModel? previous))
                {
                    diagnostics.Add(Diagnostic.Create(
                        DuplicateStructuralField,
                        target.DeclarationLocation,
                        target.TargetTypeName,
                        member.FieldId.Value.ToString(CultureInfo.InvariantCulture),
                        MemberDisplay(previous),
                        MemberDisplay(member)));
                }
                else
                {
                    fieldIds.Add(member.FieldId.Value, member);
                }
            }
            else if (fieldNames.TryGetValue(member.FieldIdentifier, out MemberModel? previous))
            {
                diagnostics.Add(Diagnostic.Create(
                    DuplicateStructuralField,
                    target.DeclarationLocation,
                    target.TargetTypeName,
                    member.FieldIdentifier,
                    MemberDisplay(previous),
                    MemberDisplay(member)));
            }
            else
            {
                fieldNames.Add(member.FieldIdentifier, member);
            }
        }
    }

    private static string MemberDisplay(MemberModel member)
    {
        string owner = member.DeclaringType?.ToDisplayString(FullNameFormat) ?? "<unknown>";
        return $"{owner}.{member.TargetMemberName}";
    }

    private static void EmitObjectSerializer(StringBuilder sb, TypeModel model)
    {
        if (model.Kind == DeclKind.Class)
        {
            sb.Append(
                $"[global::Apache.Fory.ForyGeneratedSerializerApi(typeof({model.TargetTypeName}), global::Apache.Fory.ForyGeneratedProviderKind.{(model.IsExternal ? "External" : "Ordinary")}");
            if (model.ShallowStorage.ParentProviderTypeName is not null)
            {
                sb.Append(
                    $", ParentSerializerType = typeof({model.ShallowStorage.ParentProviderTypeName})");
            }

            sb.AppendLine(")]");
            sb.AppendLine("[global::System.Runtime.CompilerServices.CompilerGenerated]");
            sb.AppendLine(
                "[global::System.ComponentModel.EditorBrowsable(global::System.ComponentModel.EditorBrowsableState.Never)]");
            string classModifier = model.EmitSerializerBody ? "sealed" : "abstract";
            sb.AppendLine(
                $"{model.ProviderVisibility} {classModifier} class {model.SerializerName} : global::Apache.Fory.Serializer<{model.TargetTypeName}>");
        }
        else
        {
            sb.AppendLine(
                $"file sealed class {model.SerializerName} : global::Apache.Fory.Serializer<{model.TargetTypeName}>");
        }

        sb.AppendLine("{");
        if (model.Kind == DeclKind.Class)
        {
            EmitHierarchyProviderApi(sb, model);
        }

        if (!model.EmitSerializerBody)
        {
            sb.AppendLine("}");
            return;
        }

        sb.AppendLine("    private static readonly object __ForyTypeMetaCacheLock = new();");
        sb.AppendLine("    private static ulong __ForyTypeMetaResolverVersion;");
        sb.AppendLine("    private static ulong __ForyNoRefTypeMetaHash;");
        sb.AppendLine("    private static ulong __ForyRefTypeMetaHash;");
        sb.AppendLine("    private static global::Apache.Fory.TypeMeta? __ForyNoRefMeta;");
        sb.AppendLine("    private static bool __ForyNoRefMetaMatches;");
        sb.AppendLine("    private static global::Apache.Fory.TypeMeta? __ForyRefMeta;");
        sb.AppendLine("    private static bool __ForyRefMetaMatches;");
        sb.AppendLine(
            $"    private const bool __ForyAllFieldsBuiltIn = {BoolLiteral(model.SortedMembers.All(m => m.DynamicAnyKind == DynamicAnyKind.None && m.Classification.IsBuiltIn))};");
        if (model.Kind == DeclKind.Class)
        {
            string graphMemoryExpr = ModelGraphMemoryExpr(model);
            sb.AppendLine(
                $"    private static readonly long __ForyGraphMemoryBytes = checked({graphMemoryExpr});");
        }

        sb.AppendLine(
            "    private static global::System.Collections.Generic.IReadOnlyList<global::Apache.Fory.TypeMetaFieldInfo>? __ForyNoRefTypeMetaFields;");
        sb.AppendLine(
            "    private static global::System.Collections.Generic.IReadOnlyList<global::Apache.Fory.TypeMetaFieldInfo>? __ForyRefTypeMetaFields;");

        if (model.SortedMembers.Length > 0)
        {
            sb.AppendLine();
        }

        sb.AppendLine("    private static global::Apache.Fory.RefMode __ForyRefMode(bool nullable, bool trackRef)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (trackRef)");
        sb.AppendLine("        {");
        sb.AppendLine("            return global::Apache.Fory.RefMode.Tracking;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        return nullable ? global::Apache.Fory.RefMode.NullOnly : global::Apache.Fory.RefMode.None;");
        sb.AppendLine("    }");
        sb.AppendLine();
        foreach (MemberModel member in model.SortedMembers)
        {
            if (member.FieldCodec is not null)
            {
                EmitFieldCodecMethods(sb, member);
            }
        }

        EmitCompatibleFieldCodecMethods(sb, model);

        sb.AppendLine(
            "    private static global::System.Collections.Generic.IReadOnlyList<global::Apache.Fory.TypeMetaFieldInfo> __ForyBuildTypeMetaFields(bool trackRef)");
        sb.AppendLine("    {");
        if (model.SortedMembers.Length == 0)
        {
            sb.AppendLine("        return global::System.Array.Empty<global::Apache.Fory.TypeMetaFieldInfo>();");
        }
        else
        {
            sb.AppendLine("        return new global::Apache.Fory.TypeMetaFieldInfo[]");
            sb.AppendLine("        {");
            foreach (MemberModel member in model.SortedMembers)
            {
                sb.AppendLine(
                    $"            new global::Apache.Fory.TypeMetaFieldInfo({BuildTypeMetaFieldIdExpression(member.FieldId)}, \"{EscapeString(member.FieldIdentifier)}\", {BuildTypeMetaExpression(member.TypeMeta, "trackRef")}),");
            }

            sb.AppendLine("        };");
        }

        sb.AppendLine("    }");
        sb.AppendLine();
        sb.AppendLine(
            "    private bool __ForyMatchesTypeMeta(global::Apache.Fory.TypeMeta typeMeta, bool trackRef)");
        sb.AppendLine("    {");
        sb.AppendLine(
            "        global::System.Collections.Generic.IReadOnlyList<global::Apache.Fory.TypeMetaFieldInfo> expectedFields = TypeMetaFields(trackRef);");
        sb.AppendLine("        if (typeMeta.Fields.Count != expectedFields.Count)");
        sb.AppendLine("        {");
        sb.AppendLine("            return false;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        for (int i = 0; i < expectedFields.Count; i++)");
        sb.AppendLine("        {");
        sb.AppendLine(
            "            global::Apache.Fory.TypeMetaFieldInfo remoteField = typeMeta.Fields[i];");
        sb.AppendLine(
            "            global::Apache.Fory.TypeMetaFieldInfo localField = expectedFields[i];");
        sb.AppendLine("            if (remoteField.FieldId.HasValue && localField.FieldId.HasValue)");
        sb.AppendLine("            {");
        sb.AppendLine(
            "                if (remoteField.FieldId.Value != localField.FieldId.Value || !remoteField.FieldType.Equals(localField.FieldType))");
        sb.AppendLine("                {");
        sb.AppendLine("                    return false;");
        sb.AppendLine("                }");
        sb.AppendLine();
        sb.AppendLine("                continue;");
        sb.AppendLine("            }");
        sb.AppendLine(
            "            if (remoteField.FieldName != localField.FieldName || !remoteField.FieldType.Equals(localField.FieldType))");
        sb.AppendLine("            {");
        sb.AppendLine("                return false;");
        sb.AppendLine("            }");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        return true;");
        sb.AppendLine("    }");
        sb.AppendLine();
        sb.AppendLine(
            "    private static void __ForyEnsureTypeMetaCache(global::Apache.Fory.TypeResolver typeResolver)");
        sb.AppendLine("    {");
        sb.AppendLine("        ulong resolverVersion = typeResolver.VersionHash();");
        sb.AppendLine("        if (__ForyTypeMetaResolverVersion == resolverVersion)");
        sb.AppendLine("        {");
        sb.AppendLine("            return;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        lock (__ForyTypeMetaCacheLock)");
        sb.AppendLine("        {");
        sb.AppendLine("            if (__ForyTypeMetaResolverVersion == resolverVersion)");
        sb.AppendLine("            {");
        sb.AppendLine("                return;");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine(
            $"            global::Apache.Fory.TypeInfo typeInfo = typeResolver.GetTypeInfo<{model.TargetTypeName}>();");
        sb.AppendLine(
            "            __ForyNoRefTypeMetaHash = typeInfo.GetTypeMetaHeaderHash(false);");
        sb.AppendLine(
            "            __ForyRefTypeMetaHash = typeInfo.GetTypeMetaHeaderHash(true);");
        sb.AppendLine("            __ForyNoRefMeta = null;");
        sb.AppendLine("            __ForyNoRefMetaMatches = false;");
        sb.AppendLine("            __ForyRefMeta = null;");
        sb.AppendLine("            __ForyRefMetaMatches = false;");
        sb.AppendLine("            __ForyTypeMetaResolverVersion = resolverVersion;");
        sb.AppendLine("        }");
        sb.AppendLine("    }");
        sb.AppendLine();
        sb.AppendLine(
            "    private bool __ForyMatchesCachedTypeMeta(global::Apache.Fory.TypeMeta typeMeta, bool trackRef, global::Apache.Fory.TypeResolver typeResolver)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (trackRef)");
        sb.AppendLine("        {");
        sb.AppendLine(
            "            if (global::System.Object.ReferenceEquals(__ForyRefMeta, typeMeta))");
        sb.AppendLine("            {");
        sb.AppendLine("                return __ForyRefMetaMatches;");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            __ForyEnsureTypeMetaCache(typeResolver);");
        sb.AppendLine();
        sb.AppendLine("            bool matched = false;");
        sb.AppendLine("            if (typeMeta.HeaderHash == __ForyRefTypeMetaHash)");
        sb.AppendLine("            {");
        sb.AppendLine("                matched = __ForyMatchesTypeMeta(typeMeta, true);");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            __ForyRefMeta = typeMeta;");
        sb.AppendLine("            __ForyRefMetaMatches = matched;");
        sb.AppendLine("            return matched;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine(
            "        if (global::System.Object.ReferenceEquals(__ForyNoRefMeta, typeMeta))");
        sb.AppendLine("        {");
        sb.AppendLine("            return __ForyNoRefMetaMatches;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        __ForyEnsureTypeMetaCache(typeResolver);");
        sb.AppendLine();
        sb.AppendLine("        bool noTrackMatched = false;");
        sb.AppendLine("        if (typeMeta.HeaderHash == __ForyNoRefTypeMetaHash)");
        sb.AppendLine("        {");
        sb.AppendLine("            noTrackMatched = __ForyMatchesTypeMeta(typeMeta, false);");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        __ForyNoRefMeta = typeMeta;");
        sb.AppendLine("        __ForyNoRefMetaMatches = noTrackMatched;");
        sb.AppendLine("        return noTrackMatched;");
        sb.AppendLine("    }");
        sb.AppendLine();
        sb.AppendLine("    private static uint? __ForyNoRefSchemaHash;");
        sb.AppendLine();
        sb.AppendLine("    private static uint __ForySchemaHash(bool trackRef, global::Apache.Fory.TypeResolver typeResolver)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (!trackRef && __ForyNoRefSchemaHash.HasValue)");
        sb.AppendLine("        {");
        sb.AppendLine("            return __ForyNoRefSchemaHash.Value;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.Append("        uint value = global::Apache.Fory.SchemaHash.StructHash32(");
        sb.Append(BuildSchemaFingerprintExpression(model.Members));
        sb.AppendLine(");");
        sb.AppendLine("        if (!trackRef)");
        sb.AppendLine("        {");
        sb.AppendLine("            __ForyNoRefSchemaHash = value;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        return value;");
        sb.AppendLine("    }");
        sb.AppendLine();
        if (model.Kind == DeclKind.Class)
        {
            sb.AppendLine($"    public override {model.TargetTypeName} DefaultValue => null!;");
        }
        else
        {
            sb.AppendLine($"    public override {model.TargetTypeName} DefaultValue => new {model.TargetTypeName}();");
        }

        sb.AppendLine();
        sb.AppendLine("    private global::System.Collections.Generic.IReadOnlyList<global::Apache.Fory.TypeMetaFieldInfo> TypeMetaFields(bool trackRef)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (trackRef)");
        sb.AppendLine("        {");
        sb.AppendLine(
            "            return __ForyRefTypeMetaFields ??= __ForyBuildTypeMetaFields(true);");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine(
            "        return __ForyNoRefTypeMetaFields ??= __ForyBuildTypeMetaFields(false);");
        sb.AppendLine("    }");
        sb.AppendLine();
        sb.AppendLine(
            $"    public override void WriteData(global::Apache.Fory.WriteContext context, in {model.TargetTypeName} value, bool hasGenerics)");
        sb.AppendLine("    {");
        sb.AppendLine("        _ = hasGenerics;");
        sb.AppendLine("        if (context.Compatible)");
        sb.AppendLine("        {");
        if (model.SortedMembers.Length == 0)
        {
            sb.AppendLine("            return;");
        }
        else
        {
            foreach (MemberModel member in model.SortedMembers)
            {
                EmitWriteMember(sb, member, true);
            }

            sb.AppendLine("            return;");
        }

        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        uint schemaHash = __ForySchemaHash(context.TrackRef, context.TypeResolver);");
        sb.AppendLine("        context.Writer.WriteInt32(unchecked((int)schemaHash));");
        foreach (MemberModel member in model.SortedMembers)
        {
            EmitWriteMember(sb, member, false);
        }

        sb.AppendLine("    }");
        sb.AppendLine();
        EmitReadDataWithoutTypeMeta(sb, model, "ReadDataWithoutTypeMeta");
        EmitReadDataMethod(sb, model, "ReadData", "ReadDataWithoutTypeMeta", "public");

        sb.AppendLine("}");
    }

    private static void EmitHierarchyProviderApi(StringBuilder sb, TypeModel model)
    {
        sb.AppendLine("    public const int ContractVersion = 1;");
        List<string> shallowParts = ["0L"];
        if (model.ShallowStorage.ParentProviderTypeName is not null)
        {
            shallowParts.Add(
                $"{model.ShallowStorage.ParentProviderTypeName}.HierarchyShallowBytes");
        }

        shallowParts.AddRange(
            model.ShallowStorage.DeclaredFields.Select(field => field.MemoryExpression));
        string shallowExpression = string.Join(" + ", shallowParts);
        sb.AppendLine(
            $"    public static readonly long HierarchyShallowBytes = checked({shallowExpression});");

        foreach (MemberModel member in model.DeclaredMembers
                     .OrderBy(member => member.DeclarationOrdinal))
        {
            if (member.MemberType is null || member.DeclaringType is null)
            {
                continue;
            }

            string memberTypeName = StripNullableForTypeOf(
                member.MemberType.ToDisplayString(FullNameFormat));
            string declaringTypeName = member.DeclaringType.ToDisplayString(FullNameFormat);
            string memberKind = member.MemberKind == WireMemberKind.Field ? "Field" : "Property";
            sb.Append(
                $"    [global::Apache.Fory.ForyGeneratedWireMember({member.DeclarationOrdinal}, typeof({declaringTypeName}), typeof({memberTypeName}), \"{EscapeString(member.Name)}\", \"{EscapeString(member.TargetMemberName)}\", global::Apache.Fory.ForyGeneratedMemberKind.{memberKind}");
            if (member.FieldId.HasValue)
            {
                sb.Append($", FieldId = {member.FieldId.Value}");
            }

            if (member.SchemaDescriptorType is not null)
            {
                sb.Append(
                    $", SchemaType = typeof({member.SchemaDescriptorType.ToDisplayString(FullNameFormat)})");
            }

            if (member.SlotKey is not null)
            {
                sb.Append($", Slot = \"{EscapeString(member.SlotKey)}\"");
            }

            if (member.PublishedFieldAccessorName is not null)
            {
                sb.Append($", FieldAccessorName = \"{member.PublishedFieldAccessorName}\"");
            }

            if (member.PublishedGetterAccessorName is not null)
            {
                sb.Append($", GetterAccessorName = \"{member.PublishedGetterAccessorName}\"");
            }

            if (member.PublishedSetterAccessorName is not null)
            {
                sb.Append($", SetterAccessorName = \"{member.PublishedSetterAccessorName}\"");
            }

            if (!member.NullableShape.IsEmpty)
            {
                sb.Append(", NullableShape = new byte[] { ");
                sb.Append(string.Join(
                    ", ",
                    member.NullableShape.Select(value =>
                        value.ToString(CultureInfo.InvariantCulture))));
                sb.Append(" }");
            }

            sb.AppendLine(")]");
            sb.AppendLine(
                $"    public const byte __ForyWire{member.DeclarationOrdinal} = 0;");
        }

        foreach (MemberModel member in model.DeclaredMembers
                     .OrderBy(member => member.DeclarationOrdinal))
        {
            EmitMemberAccessors(sb, model, member);
        }

        sb.AppendLine();
    }

    private static void EmitMemberAccessors(
        StringBuilder sb,
        TypeModel model,
        MemberModel member)
    {
        if (member.MemberType is null ||
            member.DeclaringType is null ||
            member.AccessorProviderTypeName is null)
        {
            return;
        }

        string visibility = AccessorVisibility(model, member);
        string memberTypeName = member.MemberType.ToDisplayString(FullNameFormat);
        string declaringTypeName = member.DeclaringType.ToDisplayString(FullNameFormat);
        if (member.PublishedFieldAccessorName is not null)
        {
            sb.AppendLine(
                $"    [global::System.Runtime.CompilerServices.UnsafeAccessor(global::System.Runtime.CompilerServices.UnsafeAccessorKind.Field, Name = \"{EscapeString(member.TargetMemberName)}\")]");
            sb.AppendLine(
                $"    {visibility} static extern ref {memberTypeName} {member.PublishedFieldAccessorName}({declaringTypeName} value);");
        }

        if (member.PublishedGetterAccessorName is not null)
        {
            sb.AppendLine(
                $"    [global::System.Runtime.CompilerServices.UnsafeAccessor(global::System.Runtime.CompilerServices.UnsafeAccessorKind.Method, Name = \"get_{EscapeString(member.TargetMemberName)}\")]");
            sb.AppendLine(
                $"    {visibility} static extern {memberTypeName} {member.PublishedGetterAccessorName}({declaringTypeName} value);");
        }

        if (member.PublishedSetterAccessorName is not null)
        {
            sb.AppendLine(
                $"    [global::System.Runtime.CompilerServices.UnsafeAccessor(global::System.Runtime.CompilerServices.UnsafeAccessorKind.Method, Name = \"set_{EscapeString(member.TargetMemberName)}\")]");
            sb.AppendLine(
                $"    {visibility} static extern void {member.PublishedSetterAccessorName}({declaringTypeName} value, {memberTypeName} fieldValue);");
        }
    }

    private static string AccessorVisibility(TypeModel model, MemberModel member)
    {
        return model.ProviderVisibility == "public" &&
               member.DeclaringType is not null &&
               IsPublicType(member.DeclaringType) &&
               member.MemberType is not null &&
               IsPublicSignatureType(member.MemberType)
            ? "public"
            : "internal";
    }

    private static bool IsPublicType(INamedTypeSymbol type)
    {
        for (INamedTypeSymbol? current = type; current is not null; current = current.ContainingType)
        {
            if (current.DeclaredAccessibility != Accessibility.Public)
            {
                return false;
            }
        }

        return true;
    }

    private static bool IsPublicSignatureType(ITypeSymbol type)
    {
        switch (type)
        {
            case IArrayTypeSymbol array:
                return IsPublicSignatureType(array.ElementType);
            case IPointerTypeSymbol pointer:
                return IsPublicSignatureType(pointer.PointedAtType);
            case INamedTypeSymbol named:
                if (named.SpecialType != SpecialType.None)
                {
                    return true;
                }

                return IsPublicType(named.OriginalDefinition) &&
                       named.TypeArguments.All(IsPublicSignatureType);
            default:
                return type.TypeKind == TypeKind.Dynamic;
        }
    }

    private static void EmitReadDataWithoutTypeMeta(
        StringBuilder sb,
        TypeModel model,
        string methodName)
    {
        if (model.Kind == DeclKind.Class)
        {
            sb.AppendLine(
                $"    private {model.TargetTypeName} {methodName}(global::Apache.Fory.ReadContext context, bool publishRef, uint refId)");
        }
        else
        {
            sb.AppendLine($"    private {model.TargetTypeName} {methodName}(global::Apache.Fory.ReadContext context)");
        }

        sb.AppendLine("    {");
        if (model.Kind == DeclKind.Class)
        {
            sb.AppendLine("        context.ReserveGraphMemory(__ForyGraphMemoryBytes);");
        }
        else
        {
            sb.AppendLine("        // Value serializers do not reserve their own graph memory because value storage is");
            sb.AppendLine("        // owned by the holder that stores or allocates the value. Containers, maps, arrays,");
            sb.AppendLine("        // pointer/box owners, class/reference owners, or dynamic boxing paths reserve");
            sb.AppendLine("        // the storage they own.");
        }

        sb.AppendLine($"        {model.TargetTypeName} valueNoTypeMeta = new {model.TargetTypeName}();");
        EmitRefPublication(sb, model, "valueNoTypeMeta", 2);

        foreach (MemberModel member in model.SortedMembers)
        {
            EmitReadMemberAssignment(
                sb,
                member,
                BuildWriteRefModeExpression(member),
                BuildFieldTypeInfoLiteral(member),
                "valueNoTypeMeta",
                "CompatNoTypeMeta",
                4,
                true);
        }

        sb.AppendLine("        return valueNoTypeMeta;");
        sb.AppendLine("    }");
        sb.AppendLine();
    }

    private static void EmitReadDataMethod(
        StringBuilder sb,
        TypeModel model,
        string methodName,
        string noTypeMetaMethodName,
        string accessibility)
    {
        sb.AppendLine("    [global::System.Runtime.CompilerServices.MethodImpl(global::System.Runtime.CompilerServices.MethodImplOptions.AggressiveInlining)]");
        sb.AppendLine($"    {accessibility} override {model.TargetTypeName} {methodName}(global::Apache.Fory.ReadContext context)");
        sb.AppendLine("    {");
        if (model.Kind == DeclKind.Class)
        {
            // Generated class serializers allocate the reference owner before reading fields. Keep
            // the ref-aware path in the existing Read API so self-references publish the final
            // owner, while structs continue to read as inline values with no generated ref
            // publication.
            sb.AppendLine($"        return {methodName}Core(context, publishRef: false, refId: 0);");
            sb.AppendLine("    }");
            sb.AppendLine();
            sb.AppendLine(
                $"    private {model.TargetTypeName} ReadReservedRefData(global::Apache.Fory.ReadContext context, uint refId)");
            sb.AppendLine("    {");
            sb.AppendLine($"        return {methodName}Core(context, publishRef: true, refId);");
            sb.AppendLine("    }");
            sb.AppendLine();
            sb.AppendLine(
                $"    public override {model.TargetTypeName} Read(global::Apache.Fory.ReadContext context, global::Apache.Fory.RefMode refMode, bool readTypeInfo)");
            sb.AppendLine("    {");
            sb.AppendLine("        if (refMode != global::Apache.Fory.RefMode.None)");
            sb.AppendLine("        {");
            sb.AppendLine("            global::Apache.Fory.RefFlag flag = context.RefReader.ReadRefFlag(context.Reader);");
            sb.AppendLine("            switch (flag)");
            sb.AppendLine("            {");
            sb.AppendLine("                case global::Apache.Fory.RefFlag.Null:");
            sb.AppendLine("                    return default!;");
            sb.AppendLine("                case global::Apache.Fory.RefFlag.Ref:");
            sb.AppendLine("                    {");
            sb.AppendLine("                        uint refId = context.RefReader.ReadRefId(context.Reader);");
            sb.AppendLine($"                        return context.RefReader.GetRef<{model.TargetTypeName}>(refId);");
            sb.AppendLine("                    }");
            sb.AppendLine("                case global::Apache.Fory.RefFlag.RefValue:");
            sb.AppendLine("                    {");
            sb.AppendLine("                        uint reservedRefId = context.RefReader.ReserveRefId();");
            sb.AppendLine("                        if (readTypeInfo)");
            sb.AppendLine("                        {");
            sb.AppendLine("                            context.TypeResolver.ReadTypeInfo(this, context);");
            sb.AppendLine("                        }");
            sb.AppendLine();
            sb.AppendLine($"                        return {methodName}Core(context, publishRef: true, reservedRefId);");
            sb.AppendLine("                    }");
            sb.AppendLine("                case global::Apache.Fory.RefFlag.NotNullValue:");
            sb.AppendLine("                    break;");
            sb.AppendLine("                default:");
            sb.AppendLine("                    throw new global::Apache.Fory.RefException($\"invalid ref flag {(sbyte)flag}\");");
            sb.AppendLine("            }");
            sb.AppendLine("        }");
            sb.AppendLine();
            sb.AppendLine("        if (readTypeInfo)");
            sb.AppendLine("        {");
            sb.AppendLine("            context.TypeResolver.ReadTypeInfo(this, context);");
            sb.AppendLine("        }");
            sb.AppendLine();
            sb.AppendLine($"        return {methodName}Core(context, publishRef: false, refId: 0);");
            sb.AppendLine("    }");
            sb.AppendLine();
            sb.AppendLine(
                $"    private {model.TargetTypeName} {methodName}Core(global::Apache.Fory.ReadContext context, bool publishRef, uint refId)");
            sb.AppendLine("    {");
        }

        sb.AppendLine("        if (context.Compatible)");
        sb.AppendLine("        {");
        sb.AppendLine(
            $"            global::Apache.Fory.TypeMeta? maybeTypeMeta = context.GetTypeMeta<{model.TargetTypeName}>();");
        sb.AppendLine("            if (maybeTypeMeta is null)");
        sb.AppendLine("            {");
        if (model.Kind == DeclKind.Class)
        {
            sb.AppendLine($"                return {noTypeMetaMethodName}(context, publishRef, refId);");
        }
        else
        {
            sb.AppendLine($"                return {noTypeMetaMethodName}(context);");
        }

        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            global::Apache.Fory.TypeMeta typeMeta = maybeTypeMeta;");
        if (model.Kind == DeclKind.Class)
        {
            sb.AppendLine("            context.ReserveGraphMemory(__ForyGraphMemoryBytes);");
        }
        else
        {
            sb.AppendLine("            // Value serializers do not reserve their own graph memory because value storage is");
            sb.AppendLine("            // owned by the holder that stores or allocates the value. Containers, maps, arrays,");
            sb.AppendLine("            // pointer/box owners, class/reference owners, or dynamic boxing paths reserve");
            sb.AppendLine("            // the storage they own.");
        }

        sb.AppendLine($"            {model.TargetTypeName} value = new {model.TargetTypeName}();");
        EmitRefPublication(sb, model, "value", 3);

        sb.AppendLine("            bool __ForyExactTypeMeta = __ForyMatchesCachedTypeMeta(typeMeta, context.TrackRef, context.TypeResolver);");
        sb.AppendLine("            if (__ForyAllFieldsBuiltIn && __ForyExactTypeMeta)");
        sb.AppendLine("            {");
        foreach (MemberModel member in model.SortedMembers)
        {
            EmitReadMemberAssignment(
                sb,
                member,
                BuildWriteRefModeExpression(member),
                "false",
                "value",
                "CompatExact",
                6,
                true);
        }

        sb.AppendLine("                return value;");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            if (__ForyExactTypeMeta)");
        sb.AppendLine("            {");
        foreach (MemberModel member in model.SortedMembers)
        {
            EmitReadMemberAssignment(
                sb,
                member,
                BuildWriteRefModeExpression(member),
                BuildFieldTypeInfoLiteral(member),
                "value",
                "CompatExactTyped",
                6,
                true);
        }

        sb.AppendLine("                return value;");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            for (int i = 0; i < typeMeta.Fields.Count; i++)");
        sb.AppendLine("            {");
        sb.AppendLine("                global::Apache.Fory.TypeMetaFieldInfo remoteField = typeMeta.Fields[i];");
        sb.AppendLine("                switch (remoteField.AssignedFieldId)");
        sb.AppendLine("                {");
        sb.AppendLine("                    case -1:");
        sb.AppendLine("                        global::Apache.Fory.FieldSkipper.SkipFieldValue(context, remoteField.FieldType);");
        sb.AppendLine("                        break;");
        for (int idx = 0; idx < model.SortedMembers.Length; idx++)
        {
            MemberModel member = model.SortedMembers[idx];
            sb.AppendLine($"                    case {idx * 2}:");
            sb.AppendLine("                        {");
            EmitReadMemberAssignment(
                sb,
                member,
                BuildWriteRefModeExpression(member),
                BuildFieldTypeInfoLiteral(member),
                "value",
                "CompatDirect",
                7,
                true);
            sb.AppendLine("                            break;");
            sb.AppendLine("                        }");
            sb.AppendLine($"                    case {idx * 2 + 1}:");
            sb.AppendLine("                        {");
            string compatRefModeExpr;
            if (CompatibleCaseNeedsRemoteRefMode(member))
            {
                sb.AppendLine("                            global::Apache.Fory.RefMode remoteRefMode = __ForyRefMode(remoteField.FieldType.Nullable, remoteField.FieldType.TrackRef);");
                compatRefModeExpr = "remoteRefMode";
            }
            else
            {
                compatRefModeExpr = "default";
            }

            EmitReadMemberAssignment(
                sb,
                member,
                compatRefModeExpr,
                BuildFieldTypeInfoLiteral(member),
                "value",
                "Compat",
                7,
                false);
            sb.AppendLine("                            break;");
            sb.AppendLine("                        }");
        }

        sb.AppendLine("                    default:");
        sb.AppendLine("                        throw new global::Apache.Fory.InvalidDataException($\"invalid compatible matched id {remoteField.AssignedFieldId}\");");
        sb.AppendLine("                }");
        sb.AppendLine("            }");
        sb.AppendLine("            return value;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        uint schemaHash = unchecked((uint)context.Reader.ReadInt32());");
        sb.AppendLine("        if (context.CheckStructVersion)");
        sb.AppendLine("        {");
        sb.AppendLine("            uint expectedHash = __ForySchemaHash(context.TrackRef, context.TypeResolver);");
        sb.AppendLine("            if (schemaHash != expectedHash)");
        sb.AppendLine("            {");
        sb.AppendLine("                throw new global::Apache.Fory.InvalidDataException($\"class version hash mismatch: expected {expectedHash}, got {schemaHash}\");");
        sb.AppendLine("            }");
        sb.AppendLine("        }");
        sb.AppendLine();
        if (model.Kind == DeclKind.Class)
        {
            sb.AppendLine("        context.ReserveGraphMemory(__ForyGraphMemoryBytes);");
        }
        else
        {
            sb.AppendLine("        // Value serializers do not reserve their own graph memory because value storage is");
            sb.AppendLine("        // owned by the holder that stores or allocates the value. Containers, maps, arrays,");
            sb.AppendLine("        // pointer/box owners, class/reference owners, or dynamic boxing paths reserve");
            sb.AppendLine("        // the storage they own.");
        }

        sb.AppendLine($"        {model.TargetTypeName} valueSchema = new {model.TargetTypeName}();");
        EmitRefPublication(sb, model, "valueSchema", 2);

        foreach (MemberModel member in model.SortedMembers)
        {
            EmitReadMemberAssignment(sb, member, BuildWriteRefModeExpression(member), "false", "valueSchema", "Schema", 2, true);
        }

        sb.AppendLine("        return valueSchema;");
        sb.AppendLine("    }");
        sb.AppendLine();
    }

    private static void EmitRefPublication(
        StringBuilder sb,
        TypeModel model,
        string valueName,
        int indentLevel)
    {
        if (model.Kind != DeclKind.Class)
        {
            return;
        }

        string indent = new(' ', indentLevel * 4);
        sb.AppendLine($"{indent}if (publishRef)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    context.RefReader.StoreRefAt(refId, {valueName});");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitUnionSerializer(StringBuilder sb, TypeModel model)
    {
        sb.AppendLine(
            $"file sealed class {model.SerializerName} : global::Apache.Fory.Serializer<{model.TargetTypeName}>");
        sb.AppendLine("{");
        sb.AppendLine($"    public override {model.TargetTypeName} DefaultValue => null!;");
        sb.AppendLine();
        sb.AppendLine("    private static global::Apache.Fory.RefMode __ForyRefMode(bool nullable, bool trackRef)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (trackRef)");
        sb.AppendLine("        {");
        sb.AppendLine("            return global::Apache.Fory.RefMode.Tracking;");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        return nullable ? global::Apache.Fory.RefMode.NullOnly : global::Apache.Fory.RefMode.None;");
        sb.AppendLine("    }");
        sb.AppendLine();
        foreach (UnionCaseModel unionCase in KnownUnionCases(model))
        {
            if (unionCase.ValueMember is { HasSchemaType: true } member)
            {
                EmitUnionCaseSerializer(sb, unionCase.KnownCaseId, member);
            }
        }

        sb.AppendLine(
            $"    public override void WriteData(global::Apache.Fory.WriteContext context, in {model.TargetTypeName} value, bool hasGenerics)");
        sb.AppendLine("    {");
        sb.AppendLine("        _ = hasGenerics;");
        sb.AppendLine("        if (value is null)");
        sb.AppendLine("        {");
        sb.AppendLine("            throw new global::Apache.Fory.InvalidDataException(\"union value is null\");");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        switch (value)");
        sb.AppendLine("        {");
        UnionCaseModel? unknownCase = model.UnionCases.FirstOrDefault(c => c.IsUnknown);
        if (unknownCase is not null)
        {
            sb.AppendLine($"            case {unknownCase.TypeName} __foryCase:");
            sb.AppendLine("            {");
            sb.AppendLine("                if (__foryCase.Value.CaseId < 0)");
            sb.AppendLine("                {");
            sb.AppendLine("                    throw new global::Apache.Fory.InvalidDataException($\"unknown union case id must be non-negative: {__foryCase.Value.CaseId}\");");
            sb.AppendLine("                }");
            sb.AppendLine();
            sb.AppendLine("                context.Writer.WriteVarUInt32((uint)__foryCase.Value.CaseId);");
            sb.AppendLine("                global::Apache.Fory.UnknownCaseSerializer.WritePayload(context, __foryCase.Value);");
            sb.AppendLine("                return;");
            sb.AppendLine("            }");
        }

        foreach (UnionCaseModel unionCase in KnownUnionCases(model))
        {
            sb.AppendLine($"            case {unionCase.TypeName} __foryCase:");
            sb.AppendLine("            {");
            sb.AppendLine($"                context.Writer.WriteVarUInt32({unionCase.KnownCaseId}u);");
            EmitWriteUnionCasePayload(sb, unionCase, "__foryCase.Value", 4);
            sb.AppendLine("                return;");
            sb.AppendLine("            }");
        }

        sb.AppendLine("            default:");
        sb.AppendLine("                throw new global::Apache.Fory.InvalidDataException($\"unsupported union case {value.GetType()}\");");
        sb.AppendLine("        }");
        sb.AppendLine("    }");
        sb.AppendLine();
        sb.AppendLine($"    public override {model.TargetTypeName} ReadData(global::Apache.Fory.ReadContext context)");
        sb.AppendLine("    {");
        sb.AppendLine("        uint rawCaseId = context.Reader.ReadVarUInt32();");
        sb.AppendLine("        if (rawCaseId > int.MaxValue)");
        sb.AppendLine("        {");
        sb.AppendLine("            throw new global::Apache.Fory.InvalidDataException($\"union case id out of range: {rawCaseId}\");");
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine("        int caseId = (int)rawCaseId;");
        sb.AppendLine("        switch (caseId)");
        sb.AppendLine("        {");
        foreach (UnionCaseModel unionCase in KnownUnionCases(model))
        {
            int caseId = unionCase.KnownCaseId;
            string valueVar = $"__foryCaseValue{caseId}";
            sb.AppendLine($"            case {caseId}:");
            sb.AppendLine("            {");
            EmitReadUnionCasePayload(sb, unionCase, valueVar, 4);
            sb.AppendLine($"                {model.TargetTypeName} __foryUnion = new {unionCase.TypeName}({valueVar});");
            sb.AppendLine("                return __foryUnion;");
            sb.AppendLine("            }");
        }

        sb.AppendLine("            default:");
        sb.AppendLine("            {");
        if (unknownCase is null)
        {
            sb.AppendLine("                throw new global::Apache.Fory.InvalidDataException($\"unknown union case {caseId}\");");
        }
        else
        {
            sb.AppendLine($"                {model.TargetTypeName} __foryUnion = new {unknownCase.TypeName}(global::Apache.Fory.UnknownCaseSerializer.ReadPayload(context, caseId));");
            sb.AppendLine("                return __foryUnion;");
        }

        sb.AppendLine("            }");
        sb.AppendLine("        }");
        sb.AppendLine("    }");
        sb.AppendLine("}");
    }

    private static void EmitUnionCaseSerializer(
        StringBuilder sb,
        int caseId,
        MemberModel member)
    {
        sb.AppendLine($"    private sealed class __ForyCaseSerializer{caseId} : global::Apache.Fory.Serializer<{member.TypeName}>");
        sb.AppendLine("    {");
        sb.AppendLine($"        internal static readonly __ForyCaseSerializer{caseId} Instance = new();");
        sb.AppendLine();
        sb.AppendLine($"        public override {member.TypeName} DefaultValue => default!;");
        sb.AppendLine();
        sb.AppendLine($"        public override void WriteData(global::Apache.Fory.WriteContext context, in {member.TypeName} value, bool hasGenerics)");
        sb.AppendLine("        {");
        sb.AppendLine("            _ = hasGenerics;");
        EmitWriteUnionTopType(sb, member.TypeMeta, 3);
        string payloadExpr = member.IsNullableValueType ? "value.GetValueOrDefault()" : "value";
        EmitWriteUnionPayload(sb, NonNullableMember(member), payloadExpr, 3);
        sb.AppendLine("        }");
        sb.AppendLine();
        sb.AppendLine($"        public override {member.TypeName} ReadData(global::Apache.Fory.ReadContext context)");
        sb.AppendLine("        {");
        EmitValidateUnionTopType(sb, member.TypeMeta, 3);
        EmitReadUnionPayload(sb, NonNullableMember(member), "__foryPayload", 3);
        sb.AppendLine("            return __foryPayload;");
        sb.AppendLine("        }");
        sb.AppendLine("    }");
        sb.AppendLine();
    }

    private static void EmitWriteUnionCasePayload(
        StringBuilder sb,
        UnionCaseModel unionCase,
        string valueExpr,
        int indentLevel)
    {
        MemberModel member = unionCase.ValueMember!;
        string indent = new(' ', indentLevel * 4);
        string refModeExpr = BuildUnionCaseRefModeExpression(member);
        string hasGenerics = member.IsCollection ? "true" : "false";

        if (member.DynamicAnyKind == DynamicAnyKind.AnyValue)
        {
            sb.AppendLine(
                $"{indent}global::Apache.Fory.DynamicAnyCodec.WriteAny(context, {valueExpr}, {refModeExpr}, true, false);");
            return;
        }

        if (!member.HasSchemaType)
        {
            sb.AppendLine(
                $"{indent}context.TypeResolver.GetSerializer<{member.TypeName}>().Write(context, {valueExpr}, {refModeExpr}, true, {hasGenerics});");
            return;
        }

        sb.AppendLine(
            $"{indent}__ForyCaseSerializer{unionCase.KnownCaseId}.Instance.Write(context, {valueExpr}, {refModeExpr}, false, false);");
    }

    private static void EmitReadUnionCasePayload(
        StringBuilder sb,
        UnionCaseModel unionCase,
        string valueVar,
        int indentLevel)
    {
        MemberModel member = unionCase.ValueMember!;
        string indent = new(' ', indentLevel * 4);
        string refModeExpr = BuildUnionCaseRefModeExpression(member);

        if (member.DynamicAnyKind == DynamicAnyKind.AnyValue)
        {
            string typeOfTypeName = StripNullableForTypeOf(member.TypeName);
            sb.AppendLine(
                $"{indent}{member.TypeName} {valueVar} = ({member.TypeName})global::Apache.Fory.DynamicAnyCodec.CastAnyDynamicValue(global::Apache.Fory.DynamicAnyCodec.ReadAny(context, {refModeExpr}, true), typeof({typeOfTypeName}))!;");
            return;
        }

        if (!member.HasSchemaType)
        {
            sb.AppendLine(
                $"{indent}{member.TypeName} {valueVar} = context.TypeResolver.GetSerializer<{member.TypeName}>().Read(context, {refModeExpr}, true);");
            return;
        }

        sb.AppendLine(
            $"{indent}{member.TypeName} {valueVar} = __ForyCaseSerializer{unionCase.KnownCaseId}.Instance.Read(context, {refModeExpr}, false);");
    }

    private static void EmitWriteUnionPayload(
        StringBuilder sb,
        MemberModel member,
        string valueExpr,
        int indentLevel)
    {
        int id = 0;
        if (member.FieldCodec is not null)
        {
            EmitWritePayload(sb, member.FieldCodec, valueExpr, indentLevel, ref id);
            return;
        }

        if (TryBuildDirectPayloadWrite(member.Classification.TypeId, valueExpr, out string? writeCode))
        {
            string indent = new(' ', indentLevel * 4);
            sb.AppendLine($"{indent}{writeCode}");
            return;
        }

        string hasGenerics = member.IsCollection ? "true" : "false";
        string fallbackIndent = new(' ', indentLevel * 4);
        sb.AppendLine(
            $"{fallbackIndent}context.TypeResolver.GetSerializer<{member.TypeName}>().WriteData(context, {valueExpr}, {hasGenerics});");
    }

    private static void EmitReadUnionPayload(
        StringBuilder sb,
        MemberModel member,
        string valueVar,
        int indentLevel)
    {
        int id = 0;
        if (member.FieldCodec is not null)
        {
            EmitReadPayload(sb, member.FieldCodec, valueVar, indentLevel, ref id);
            return;
        }

        if (TryBuildDirectPayloadRead(member.Classification.TypeId, out string? readExpr))
        {
            string indent = new(' ', indentLevel * 4);
            sb.AppendLine($"{indent}{member.TypeName} {valueVar} = {readExpr};");
            return;
        }

        string fallbackIndent = new(' ', indentLevel * 4);
        sb.AppendLine(
            $"{fallbackIndent}{member.TypeName} {valueVar} = context.TypeResolver.GetSerializer<{member.TypeName}>().ReadData(context);");
    }

    private static void EmitWriteUnionTopType(
        StringBuilder sb,
        TypeMetaFieldTypeModel model,
        int indentLevel)
    {
        string indent = new(' ', indentLevel * 4);
        sb.AppendLine($"{indent}context.Writer.WriteUInt8((byte)({model.TypeIdExpr}));");
    }

    private static void EmitValidateUnionTopType(
        StringBuilder sb,
        TypeMetaFieldTypeModel model,
        int indentLevel)
    {
        string indent = new(' ', indentLevel * 4);
        sb.AppendLine($"{indent}uint __foryTypeId = context.Reader.ReadUInt8();");
        sb.AppendLine($"{indent}if (__foryTypeId != ({model.TypeIdExpr}))");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    throw new global::Apache.Fory.TypeMismatchException({model.TypeIdExpr}, __foryTypeId);");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitFieldCodecMethods(StringBuilder sb, MemberModel member)
    {
        FieldCodecModel codec = member.FieldCodec!;
        string memberId = member.CodeKey;
        sb.AppendLine(
            $"    private static void __ForyWrite{memberId}Field(global::Apache.Fory.WriteContext context, {member.TypeName} value, global::Apache.Fory.RefMode refMode)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (refMode == global::Apache.Fory.RefMode.NullOnly)");
        sb.AppendLine("        {");
        if (member.IsNullableValueType)
        {
            sb.AppendLine("            if (!value.HasValue)");
        }
        else
        {
            sb.AppendLine("            if (value is null)");
        }

        sb.AppendLine("            {");
        sb.AppendLine("                context.Writer.WriteInt8((sbyte)global::Apache.Fory.RefFlag.Null);");
        sb.AppendLine("                return;");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            context.Writer.WriteInt8((sbyte)global::Apache.Fory.RefFlag.NotNullValue);");
        sb.AppendLine("        }");
        string writeValueExpr = member.IsNullableValueType ? "value.Value" : member.IsNullable ? "value!" : "value";
        int id = 0;
        EmitWritePayload(sb, codec, writeValueExpr, 2, ref id);
        sb.AppendLine("    }");
        sb.AppendLine();

        sb.AppendLine(
            $"    private static {member.TypeName} __ForyRead{memberId}Field(global::Apache.Fory.ReadContext context, global::Apache.Fory.RefMode refMode)");
        sb.AppendLine("    {");
        sb.AppendLine("        if (refMode == global::Apache.Fory.RefMode.NullOnly)");
        sb.AppendLine("        {");
        sb.AppendLine("            sbyte refFlag = context.Reader.ReadInt8();");
        sb.AppendLine("            if (refFlag == (sbyte)global::Apache.Fory.RefFlag.Null)");
        sb.AppendLine("            {");
        sb.AppendLine($"                return ({member.TypeName})default!;");
        sb.AppendLine("            }");
        sb.AppendLine();
        sb.AppendLine("            if (refFlag != (sbyte)global::Apache.Fory.RefFlag.NotNullValue)");
        sb.AppendLine("            {");
        sb.AppendLine("                throw new global::Apache.Fory.InvalidDataException($\"invalid nullOnly ref flag {refFlag}\");");
        sb.AppendLine("            }");
        sb.AppendLine("        }");
        string resultVar = $"__{memberId}Value";
        id = 0;
        EmitReadPayload(sb, codec, resultVar, 2, ref id);
        sb.AppendLine($"        return {resultVar};");
        sb.AppendLine("    }");
        sb.AppendLine();
    }

    private static void EmitCompatibleFieldCodecMethods(StringBuilder sb, TypeModel model)
    {
        bool hasCompatibleField = false;
        foreach (MemberModel member in model.SortedMembers)
        {
            if (member.FieldCodec is not null &&
                CanReadCompatibleField(member.FieldCodec))
            {
                hasCompatibleField = true;
                break;
            }
        }

        if (!hasCompatibleField)
        {
            return;
        }

        sb.AppendLine("    private static class __ForyCompatibleFieldReaders");
        sb.AppendLine("    {");
        foreach (MemberModel member in model.SortedMembers)
        {
            if (member.FieldCodec is not null &&
                CanReadCompatibleField(member.FieldCodec))
            {
                EmitCompatibleFieldCodecMethod(sb, member, member.FieldCodec);
            }
        }

        sb.AppendLine("    }");
        sb.AppendLine();
    }

    private static void EmitCompatibleFieldCodecMethod(
        StringBuilder sb,
        MemberModel member,
        FieldCodecModel codec)
    {
        string memberId = member.CodeKey;
        sb.AppendLine("        [global::System.Runtime.CompilerServices.MethodImpl(global::System.Runtime.CompilerServices.MethodImplOptions.NoInlining)]");
        sb.AppendLine(
            $"        internal static {member.TypeName} Read{memberId}FieldBridge(global::Apache.Fory.ReadContext context, global::Apache.Fory.TypeMetaFieldType remoteFieldType, global::Apache.Fory.RefMode refMode)");
        sb.AppendLine("        {");
        sb.AppendLine("            if (remoteFieldType.TypeId == " + codec.TypeId + ")");
        sb.AppendLine("            {");
        sb.AppendLine($"                return __ForyRead{memberId}Field(context, refMode);");
        sb.AppendLine("            }");
        sb.AppendLine();
        if (TryBuildCompatibleListArrayReadCodec(codec, out FieldCodecModel? alternateCodec))
        {
            sb.AppendLine("            if (remoteFieldType.TypeId == " + alternateCodec.TypeId + ")");
            sb.AppendLine("            {");
            if (codec.Kind == FieldCodecKind.PackedArray)
            {
                sb.AppendLine("                if (remoteFieldType.Generics.Count != 1)");
                sb.AppendLine("                {");
                sb.AppendLine("                    throw new global::Apache.Fory.InvalidDataException(\"compatible list to array field requires one element schema\");");
                sb.AppendLine("                }");
            }

            EmitReadNullOnlyPrefix(sb, member, 4);
            int id = 0;
            string compatibleResultVar = $"__{memberId}CompatibleValue";
            if (codec.Kind == FieldCodecKind.PackedArray && alternateCodec.Kind == FieldCodecKind.List)
            {
                EmitReadCompatibleListArrayPayload(sb, codec, compatibleResultVar, 4, ref id);
            }
            else
            {
                EmitReadPayload(sb, alternateCodec, compatibleResultVar, 4, ref id);
            }

            sb.AppendLine($"                return {compatibleResultVar};");
            sb.AppendLine("            }");
        }

        if (CanReadCompatibleBinaryField(codec))
        {
            sb.AppendLine("            if (remoteFieldType.TypeId == (uint)global::Apache.Fory.TypeId.Binary)");
            sb.AppendLine("            {");
            EmitReadNullOnlyPrefix(sb, member, 4);
            EmitReadBinaryField(sb, codec, $"__{memberId}BinaryValue", 4);
            sb.AppendLine($"                return __{memberId}BinaryValue;");
            sb.AppendLine("            }");
        }

        sb.AppendLine("            throw new global::Apache.Fory.InvalidDataException($\"unsupported compatible field schema pair: local " + codec.TypeId + ", remote {remoteFieldType.TypeId}\");");
        sb.AppendLine("        }");
    }

    private static void EmitReadNullOnlyPrefix(StringBuilder sb, MemberModel member, int indentLevel)
    {
        string indent = new(' ', indentLevel * 4);
        sb.AppendLine($"{indent}if (refMode == global::Apache.Fory.RefMode.NullOnly)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    sbyte refFlag = context.Reader.ReadInt8();");
        sb.AppendLine($"{indent}    if (refFlag == (sbyte)global::Apache.Fory.RefFlag.Null)");
        sb.AppendLine($"{indent}    {{");
        sb.AppendLine($"{indent}        return ({member.TypeName})default!;");
        sb.AppendLine($"{indent}    }}");
        sb.AppendLine();
        sb.AppendLine($"{indent}    if (refFlag != (sbyte)global::Apache.Fory.RefFlag.NotNullValue)");
        sb.AppendLine($"{indent}    {{");
        sb.AppendLine($"{indent}        throw new global::Apache.Fory.InvalidDataException($\"invalid nullOnly ref flag {{refFlag}}\");");
        sb.AppendLine($"{indent}    }}");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitReadBinaryField(
        StringBuilder sb,
        FieldCodecModel codec,
        string targetVar,
        int indentLevel)
    {
        string indent = new(' ', indentLevel * 4);
        sb.AppendLine($"{indent}int __foryLength = checked((int)context.Reader.ReadVarUInt32());");
        if (codec.CarrierKind == CarrierKind.Array)
        {
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = context.Reader.ReadBytes(__foryLength);");
            return;
        }

        if (codec.CarrierKind == CarrierKind.List)
        {
            sb.AppendLine($"{indent}context.Reader.CheckBound(__foryLength);");
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new(__foryLength);");
            sb.AppendLine($"{indent}for (int __foryIndex = 0; __foryIndex < __foryLength; __foryIndex++)");
            sb.AppendLine($"{indent}{{");
            sb.AppendLine($"{indent}    {targetVar}.Add(context.Reader.ReadUInt8());");
            sb.AppendLine($"{indent}}}");
            return;
        }

        throw new InvalidOperationException($"unsupported binary compatible carrier {codec.TypeName}");
    }

    private static bool CanReadCompatibleField(FieldCodecModel codec)
    {
        return TryBuildCompatibleListArrayReadCodec(codec, out _) || CanReadCompatibleBinaryField(codec);
    }

    private static bool CanReadCompatibleBinaryField(FieldCodecModel codec)
    {
        return codec.Kind == FieldCodecKind.PackedArray &&
               codec.TypeId == UInt8ArrayTypeId &&
               codec.CarrierKind is CarrierKind.Array or CarrierKind.List;
    }

    private static bool TryBuildCompatibleListArrayReadCodec(FieldCodecModel codec, out FieldCodecModel compatibleCodec)
    {
        if (codec.Kind == FieldCodecKind.PackedArray)
        {
            uint elementTypeId = PackedArrayElementTypeId(codec.TypeId);
            compatibleCodec = new FieldCodecModel(
                FieldCodecKind.List,
                22,
                codec.TypeName,
                codec.Nullable,
                codec.NullableValueType,
                codec.CarrierKind,
                ImmutableArray.Create(new FieldCodecModel(
                    FieldCodecKind.Scalar,
                    elementTypeId,
                    PackedArrayElementTypeName(codec.TypeId),
                    false,
                    false,
                    CarrierKind.Value,
                    ImmutableArray<FieldCodecModel>.Empty)));
            return true;
        }

        if (codec.Kind == FieldCodecKind.List &&
            codec.Generics.Length == 1 &&
            TryResolveArrayTypeIdForElement(codec.Generics[0].TypeId) is uint arrayTypeId)
        {
            compatibleCodec = new FieldCodecModel(
                FieldCodecKind.PackedArray,
                arrayTypeId,
                codec.TypeName,
                codec.Nullable,
                codec.NullableValueType,
                codec.CarrierKind,
                ImmutableArray<FieldCodecModel>.Empty);
            return true;
        }

        compatibleCodec = codec;
        return false;
    }

    private static void EmitReadCompatibleListArrayPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string targetVar,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        string lengthVar = $"__foryLength{id++}";
        string headerVar = $"__foryHeader{id++}";
        string declaredVar = $"__foryDeclared{id++}";
        string sameTypeVar = $"__forySameType{id++}";
        sb.AppendLine($"{indent}int {lengthVar} = checked((int)context.Reader.ReadVarUInt32());");
        sb.AppendLine($"{indent}if ({lengthVar} != 0)");
        sb.AppendLine($"{indent}{{");
        string innerIndent = indent + "    ";
        sb.AppendLine($"{innerIndent}byte {headerVar} = context.Reader.ReadUInt8();");
        sb.AppendLine($"{innerIndent}if (({headerVar} & 0b0000_0011) != 0)");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    throw new global::Apache.Fory.InvalidDataException(\"compatible list to array field requires non-null elements\");");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}bool {declaredVar} = ({headerVar} & 0b0000_0100) != 0;");
        sb.AppendLine($"{innerIndent}bool {sameTypeVar} = ({headerVar} & 0b0000_1000) != 0;");
        sb.AppendLine($"{innerIndent}if (!{sameTypeVar})");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    throw new global::Apache.Fory.InvalidDataException(\"compatible list to array field requires same-type elements\");");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}if (!{declaredVar})");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    uint __foryWireTypeId = context.Reader.ReadUInt8();");
        sb.AppendLine($"{innerIndent}    if (__foryWireTypeId != remoteFieldType.Generics[0].TypeId)");
        sb.AppendLine($"{innerIndent}    {{");
        sb.AppendLine($"{innerIndent}        throw new global::Apache.Fory.TypeMismatchException(remoteFieldType.Generics[0].TypeId, __foryWireTypeId);");
        sb.AppendLine($"{innerIndent}    }}");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{indent}}}");
        sb.AppendLine($"{indent}if ({lengthVar} != 0)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    context.Reader.CheckBound({lengthVar});");
        sb.AppendLine($"{indent}}}");
        string elementTypeName = codec.CarrierKind == CarrierKind.Array ? ElementTypeName(codec.TypeName) : PackedArrayElementTypeName(codec.TypeId);
        uint elementTypeId = PackedArrayElementTypeId(codec.TypeId);
        string elementBytesExpr = GraphElementBytesExpr(elementTypeName);
        if (codec.CarrierKind == CarrierKind.Array)
        {
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new {ElementTypeName(codec.TypeName)}[{lengthVar}];");
        }
        else
        {
            sb.AppendLine($"{indent}context.ReserveGraphMemory({GraphListOwnerBytesExpr} + (long){lengthVar} * {elementBytesExpr});");
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new({lengthVar});");
        }

        string indexVar = $"__foryIndex{id++}";
        sb.AppendLine($"{indent}switch (remoteFieldType.Generics[0].TypeId)");
        sb.AppendLine($"{indent}{{");
        foreach (uint remoteElementTypeId in CompatibleElementReadTypeIds(elementTypeId))
        {
            if (!TryBuildDirectPayloadRead(remoteElementTypeId, out string? itemReadExpr))
            {
                throw new InvalidOperationException($"unsupported compatible list element type id {remoteElementTypeId}");
            }

            sb.AppendLine($"{indent}    case {remoteElementTypeId}:");
            sb.AppendLine($"{indent}        for (int {indexVar} = 0; {indexVar} < {lengthVar}; {indexVar}++)");
            sb.AppendLine($"{indent}        {{");
            sb.AppendLine($"{indent}            {elementTypeName} __foryItem = {itemReadExpr};");
            if (codec.CarrierKind == CarrierKind.Array)
            {
                sb.AppendLine($"{indent}            {targetVar}[{indexVar}] = __foryItem;");
            }
            else
            {
                sb.AppendLine($"{indent}            {targetVar}.Add(__foryItem);");
            }

            sb.AppendLine($"{indent}        }}");
            sb.AppendLine($"{indent}        break;");
        }
        sb.AppendLine($"{indent}    default:");
        sb.AppendLine($"{indent}        throw new global::Apache.Fory.InvalidDataException($\"unsupported compatible list element type {{remoteFieldType.Generics[0].TypeId}}\");");
        sb.AppendLine($"{indent}}}");
    }

    private static uint[] CompatibleElementReadTypeIds(uint elementTypeId)
    {
        return elementTypeId switch
        {
            4 or 5 => [4, 5],
            6 or 7 or 8 => [6, 7, 8],
            11 or 12 => [11, 12],
            13 or 14 or 15 => [13, 14, 15],
            _ => [elementTypeId],
        };
    }

    private static void EmitWritePayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string valueExpr,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        switch (codec.Kind)
        {
            case FieldCodecKind.Scalar:
                if (!TryBuildDirectPayloadWrite(codec.TypeId, valueExpr, out string? writeCode))
                {
                    sb.AppendLine($"{indent}context.TypeResolver.GetSerializer<{codec.TypeName}>().WriteData(context, {valueExpr}, false);");
                    return;
                }

                sb.AppendLine($"{indent}{writeCode}");
                return;
            case FieldCodecKind.PackedArray:
                EmitWritePackedArrayPayload(sb, codec, valueExpr, indentLevel, ref id);
                return;
            case FieldCodecKind.List:
                EmitWriteCollectionPayload(sb, codec, valueExpr, indentLevel, ref id, isSet: false);
                return;
            case FieldCodecKind.Set:
                EmitWriteCollectionPayload(sb, codec, valueExpr, indentLevel, ref id, isSet: true);
                return;
            case FieldCodecKind.Map:
                EmitWriteMapPayload(sb, codec, valueExpr, indentLevel, ref id);
                return;
        }
    }

    private static void EmitWritePackedArrayPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string valueExpr,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        string valuesVar = $"__foryPacked{id++}";
        sb.AppendLine($"{indent}{codec.TypeName} {valuesVar} = {valueExpr} ?? [];");
        string countExpr = codec.CarrierKind == CarrierKind.Array ? $"{valuesVar}.Length" : $"{valuesVar}.Count";
        int width = PackedArrayElementWidth(codec.TypeId);
        string lengthExpr = width == 1 ? countExpr : $"checked({countExpr} * {width})";
        sb.AppendLine($"{indent}context.Writer.WriteVarUInt32((uint){lengthExpr});");
        string packedIndexVar = $"__foryIndex{id++}";
        sb.AppendLine($"{indent}for (int {packedIndexVar} = 0; {packedIndexVar} < {countExpr}; {packedIndexVar}++)");
        sb.AppendLine($"{indent}{{");
        string itemExpr = $"{valuesVar}[{packedIndexVar}]";
        uint elementTypeId = PackedArrayElementTypeId(codec.TypeId);
        if (!TryBuildDirectPayloadWrite(elementTypeId, itemExpr, out string? writeCode))
        {
            throw new InvalidOperationException($"unsupported packed array type id {codec.TypeId}");
        }

        sb.AppendLine($"{indent}    {writeCode}");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitWriteCollectionPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string valueExpr,
        int indentLevel,
        ref int id,
        bool isSet)
    {
        string indent = new(' ', indentLevel * 4);
        FieldCodecModel element = codec.Generics[0];
        string valuesVar = $"__foryCollection{id++}";
        sb.AppendLine($"{indent}{codec.TypeName} {valuesVar} = {valueExpr} ?? [];");
        string countExpr = codec.CarrierKind == CarrierKind.Array ? $"{valuesVar}.Length" : $"{valuesVar}.Count";
        sb.AppendLine($"{indent}int __foryCount{id} = {countExpr};");
        string countVar = $"__foryCount{id++}";
        sb.AppendLine($"{indent}context.Writer.WriteVarUInt32((uint){countVar});");
        sb.AppendLine($"{indent}if ({countVar} != 0)");
        sb.AppendLine($"{indent}{{");
        string innerIndent = indent + "    ";
        string hasNullVar = $"__foryHasNull{id++}";
        if (element.Nullable)
        {
            sb.AppendLine($"{innerIndent}bool {hasNullVar} = false;");
            if (isSet)
            {
                sb.AppendLine($"{innerIndent}foreach ({element.TypeName} __foryItem in {valuesVar})");
                sb.AppendLine($"{innerIndent}{{");
                sb.AppendLine($"{innerIndent}    if (__foryItem is null)");
                sb.AppendLine($"{innerIndent}    {{");
                sb.AppendLine($"{innerIndent}        {hasNullVar} = true;");
                sb.AppendLine($"{innerIndent}        break;");
                sb.AppendLine($"{innerIndent}    }}");
                sb.AppendLine($"{innerIndent}}}");
            }
            else
            {
                string scanIndexVar = $"__foryIndex{id++}";
                sb.AppendLine($"{innerIndent}for (int {scanIndexVar} = 0; {scanIndexVar} < {countVar}; {scanIndexVar}++)");
                sb.AppendLine($"{innerIndent}{{");
                string itemExpr = $"{valuesVar}[{scanIndexVar}]";
                sb.AppendLine($"{innerIndent}    if ({itemExpr} is null)");
                sb.AppendLine($"{innerIndent}    {{");
                sb.AppendLine($"{innerIndent}        {hasNullVar} = true;");
                sb.AppendLine($"{innerIndent}        break;");
                sb.AppendLine($"{innerIndent}    }}");
                sb.AppendLine($"{innerIndent}}}");
            }
        }
        else
        {
            sb.AppendLine($"{innerIndent}bool {hasNullVar} = false;");
        }

        string collectionHeaderVar = $"__foryHeader{id++}";
        sb.AppendLine($"{innerIndent}byte {collectionHeaderVar} = 0b0000_1000 | 0b0000_0100;");
        sb.AppendLine($"{innerIndent}if ({hasNullVar})");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    {collectionHeaderVar} |= 0b0000_0010;");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}context.Writer.WriteUInt8({collectionHeaderVar});");
        if (isSet)
        {
            sb.AppendLine($"{innerIndent}foreach ({element.TypeName} __foryItem in {valuesVar})");
            sb.AppendLine($"{innerIndent}{{");
            EmitWriteNullableElementPayload(sb, element, "__foryItem", indentLevel + 2, ref id, hasNullVar);
            sb.AppendLine($"{innerIndent}}}");
        }
        else
        {
            string writeIndexVar = $"__foryIndex{id++}";
            sb.AppendLine($"{innerIndent}for (int {writeIndexVar} = 0; {writeIndexVar} < {countVar}; {writeIndexVar}++)");
            sb.AppendLine($"{innerIndent}{{");
            sb.AppendLine($"{innerIndent}    {element.TypeName} __foryItem = {valuesVar}[{writeIndexVar}];");
            EmitWriteNullableElementPayload(sb, element, "__foryItem", indentLevel + 2, ref id, hasNullVar);
            sb.AppendLine($"{innerIndent}}}");
        }

        sb.AppendLine($"{indent}}}");
    }

    private static void EmitWriteNullableElementPayload(
        StringBuilder sb,
        FieldCodecModel element,
        string itemExpr,
        int indentLevel,
        ref int id,
        string hasNullVar)
    {
        string indent = new(' ', indentLevel * 4);
        if (!element.Nullable)
        {
            EmitWritePayload(sb, element, itemExpr, indentLevel, ref id);
            return;
        }

        sb.AppendLine($"{indent}if ({hasNullVar})");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    if ({itemExpr} is null)");
        sb.AppendLine($"{indent}    {{");
        sb.AppendLine($"{indent}        context.Writer.WriteInt8((sbyte)global::Apache.Fory.RefFlag.Null);");
        sb.AppendLine($"{indent}        continue;");
        sb.AppendLine($"{indent}    }}");
        sb.AppendLine();
        sb.AppendLine($"{indent}    context.Writer.WriteInt8((sbyte)global::Apache.Fory.RefFlag.NotNullValue);");
        string nonNullExpr = element.NullableValueType ? $"{itemExpr}.GetValueOrDefault()" : $"{itemExpr}!";
        EmitWritePayload(sb, element, nonNullExpr, indentLevel + 1, ref id);
        sb.AppendLine($"{indent}}}");
        sb.AppendLine($"{indent}else");
        sb.AppendLine($"{indent}{{");
        EmitWritePayload(sb, element, element.NullableValueType ? $"{itemExpr}.GetValueOrDefault()" : $"{itemExpr}!", indentLevel + 1, ref id);
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitWriteMapPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string valueExpr,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        FieldCodecModel key = codec.Generics[0];
        FieldCodecModel value = codec.Generics[1];
        string mapVar = $"__foryMap{id++}";
        sb.AppendLine($"{indent}{codec.TypeName} {mapVar} = {valueExpr} ?? [];");
        sb.AppendLine($"{indent}context.Writer.WriteVarUInt32((uint){mapVar}.Count);");
        sb.AppendLine($"{indent}foreach (global::System.Collections.Generic.KeyValuePair<{key.TypeName}, {value.TypeName}> __foryEntry in {mapVar})");
        sb.AppendLine($"{indent}{{");
        string innerIndent = indent + "    ";
        string keyNullVar = $"__foryKeyNull{id++}";
        string valueNullVar = $"__foryValueNull{id++}";
        if (key.Nullable)
        {
            sb.AppendLine($"{innerIndent}bool {keyNullVar} = __foryEntry.Key is null;");
        }
        else
        {
            sb.AppendLine($"{innerIndent}bool {keyNullVar} = false;");
        }

        if (value.Nullable)
        {
            sb.AppendLine($"{innerIndent}bool {valueNullVar} = __foryEntry.Value is null;");
        }
        else
        {
            sb.AppendLine($"{innerIndent}bool {valueNullVar} = false;");
        }

        string mapHeaderVar = $"__foryHeader{id++}";
        sb.AppendLine($"{innerIndent}byte {mapHeaderVar} = 0;");
        sb.AppendLine($"{innerIndent}if ({keyNullVar}) {mapHeaderVar} |= 0b0000_0010; else {mapHeaderVar} |= 0b0000_0100;");
        sb.AppendLine($"{innerIndent}if ({valueNullVar}) {mapHeaderVar} |= 0b0001_0000; else {mapHeaderVar} |= 0b0010_0000;");
        sb.AppendLine($"{innerIndent}context.Writer.WriteUInt8({mapHeaderVar});");
        sb.AppendLine($"{innerIndent}if (!{keyNullVar} && !{valueNullVar})");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    context.Writer.WriteUInt8(1);");
        EmitWritePayload(sb, key, key.NullableValueType ? "__foryEntry.Key.GetValueOrDefault()" : "__foryEntry.Key!", indentLevel + 2, ref id);
        EmitWritePayload(sb, value, value.NullableValueType ? "__foryEntry.Value.GetValueOrDefault()" : "__foryEntry.Value!", indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}    continue;");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}if (!{keyNullVar})");
        sb.AppendLine($"{innerIndent}{{");
        EmitWritePayload(sb, key, key.NullableValueType ? "__foryEntry.Key.GetValueOrDefault()" : "__foryEntry.Key!", indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}if (!{valueNullVar})");
        sb.AppendLine($"{innerIndent}{{");
        EmitWritePayload(sb, value, value.NullableValueType ? "__foryEntry.Value.GetValueOrDefault()" : "__foryEntry.Value!", indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitReadPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string targetVar,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        switch (codec.Kind)
        {
            case FieldCodecKind.Scalar:
                if (TryBuildDirectPayloadRead(codec.TypeId, out string? readExpr))
                {
                    sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = {readExpr};");
                }
                else
                {
                    sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = context.TypeResolver.GetSerializer<{codec.TypeName}>().ReadData(context);");
                }

                return;
            case FieldCodecKind.PackedArray:
                EmitReadPackedArrayPayload(sb, codec, targetVar, indentLevel, ref id);
                return;
            case FieldCodecKind.List:
                EmitReadCollectionPayload(sb, codec, targetVar, indentLevel, ref id, isSet: false);
                return;
            case FieldCodecKind.Set:
                EmitReadCollectionPayload(sb, codec, targetVar, indentLevel, ref id, isSet: true);
                return;
            case FieldCodecKind.Map:
                EmitReadMapPayload(sb, codec, targetVar, indentLevel, ref id);
                return;
        }
    }

    private static void EmitReadPackedArrayPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string targetVar,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        int width = PackedArrayElementWidth(codec.TypeId);
        uint elementTypeId = PackedArrayElementTypeId(codec.TypeId);
        string byteSizeVar = $"__foryByteSize{id++}";
        string countVar = $"__foryPackedCount{id++}";
        sb.AppendLine($"{indent}int {byteSizeVar} = checked((int)context.Reader.ReadVarUInt32());");
        if (width > 1)
        {
            int mask = width - 1;
            sb.AppendLine($"{indent}if (({byteSizeVar} & {mask}) != 0)");
            sb.AppendLine($"{indent}{{");
            sb.AppendLine($"{indent}    throw new global::Apache.Fory.InvalidDataException(\"packed array byte size mismatch\");");
            sb.AppendLine($"{indent}}}");
        }

        sb.AppendLine($"{indent}context.Reader.CheckBound({byteSizeVar});");
        sb.AppendLine($"{indent}int {countVar} = {byteSizeVar}{(width == 1 ? string.Empty : $" / {width}")};");
        if (codec.CarrierKind == CarrierKind.Array)
        {
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new {ElementTypeName(codec.TypeName)}[{countVar}];");
        }
        else
        {
            string elementBytesExpr = GraphElementBytesExpr(PackedArrayElementTypeName(codec.TypeId));
            sb.AppendLine($"{indent}context.ReserveGraphMemory({GraphListOwnerBytesExpr} + (long){countVar} * {elementBytesExpr});");
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new({countVar});");
        }

        string packedIndexVar = $"__foryIndex{id++}";
        sb.AppendLine($"{indent}for (int {packedIndexVar} = 0; {packedIndexVar} < {countVar}; {packedIndexVar}++)");
        sb.AppendLine($"{indent}{{");
        if (!TryBuildDirectPayloadRead(elementTypeId, out string? readExpr))
        {
            throw new InvalidOperationException($"unsupported packed array type id {codec.TypeId}");
        }

        if (codec.CarrierKind == CarrierKind.Array)
        {
            sb.AppendLine($"{indent}    {targetVar}[{packedIndexVar}] = {readExpr};");
        }
        else
        {
            sb.AppendLine($"{indent}    {targetVar}.Add({readExpr});");
        }

        sb.AppendLine($"{indent}}}");
    }

    private static void EmitReadCollectionPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string targetVar,
        int indentLevel,
        ref int id,
        bool isSet)
    {
        string indent = new(' ', indentLevel * 4);
        FieldCodecModel element = codec.Generics[0];
        string lengthVar = $"__foryLength{id++}";
        string headerVar = $"__foryHeader{id++}";
        string hasNullVar = $"__foryHasNull{id++}";
        string sameTypeVar = $"__forySameType{id++}";
        string declaredVar = $"__foryDeclared{id++}";
        sb.AppendLine($"{indent}int {lengthVar} = checked((int)context.Reader.ReadVarUInt32());");
        string ownerBytesExpr = isSet ? GraphSetOwnerBytesExpr : GraphListOwnerBytesExpr;
        sb.AppendLine($"{indent}context.ReserveGraphMemory({ownerBytesExpr} + (long){lengthVar} * {GraphElementBytesExpr(element)});");
        sb.AppendLine($"{indent}if ({lengthVar} != 0)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    context.Reader.CheckBound({lengthVar});");
        sb.AppendLine($"{indent}}}");
        if (isSet)
        {
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new();");
        }
        else if (codec.CarrierKind == CarrierKind.Array)
        {
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new {ElementTypeName(codec.TypeName)}[{lengthVar}];");
        }
        else
        {
            sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new({lengthVar});");
        }

        sb.AppendLine($"{indent}if ({lengthVar} != 0)");
        sb.AppendLine($"{indent}{{");
        string innerIndent = indent + "    ";
        sb.AppendLine($"{innerIndent}byte {headerVar} = context.Reader.ReadUInt8();");
        sb.AppendLine($"{innerIndent}bool {hasNullVar} = ({headerVar} & 0b0000_0010) != 0;");
        sb.AppendLine($"{innerIndent}bool {declaredVar} = ({headerVar} & 0b0000_0100) != 0;");
        sb.AppendLine($"{innerIndent}bool {sameTypeVar} = ({headerVar} & 0b0000_1000) != 0;");
        sb.AppendLine($"{innerIndent}if (!{sameTypeVar})");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    throw new global::Apache.Fory.InvalidDataException(\"generated collection fields require same-type element payloads\");");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}if (!{declaredVar})");
        sb.AppendLine($"{innerIndent}{{");
        EmitReadInlineTypeInfo(sb, NonNullableCodec(element), indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}}}");
        string collectionIndexVar = $"__foryIndex{id++}";
        sb.AppendLine($"{innerIndent}for (int {collectionIndexVar} = 0; {collectionIndexVar} < {lengthVar}; {collectionIndexVar}++)");
        sb.AppendLine($"{innerIndent}{{");
        EmitReadNullableElementPayload(sb, element, "__foryItem", indentLevel + 2, ref id, hasNullVar);
        if (codec.CarrierKind == CarrierKind.Array)
        {
            sb.AppendLine($"{innerIndent}    {targetVar}[{collectionIndexVar}] = __foryItem;");
        }
        else
        {
            sb.AppendLine($"{innerIndent}    {targetVar}.Add(__foryItem);");
        }

        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitReadNullableElementPayload(
        StringBuilder sb,
        FieldCodecModel element,
        string targetVar,
        int indentLevel,
        ref int id,
        string hasNullVar)
    {
        string indent = new(' ', indentLevel * 4);
        sb.AppendLine($"{indent}{element.TypeName} {targetVar};");
        if (element.Nullable)
        {
            sb.AppendLine($"{indent}if ({hasNullVar})");
            sb.AppendLine($"{indent}{{");
            sb.AppendLine($"{indent}    sbyte __foryRefFlag = context.Reader.ReadInt8();");
            sb.AppendLine($"{indent}    if (__foryRefFlag == (sbyte)global::Apache.Fory.RefFlag.Null)");
            sb.AppendLine($"{indent}    {{");
            sb.AppendLine($"{indent}        {targetVar} = ({element.TypeName})default!;");
            sb.AppendLine($"{indent}    }}");
            sb.AppendLine($"{indent}    else if (__foryRefFlag == (sbyte)global::Apache.Fory.RefFlag.NotNullValue)");
            sb.AppendLine($"{indent}    {{");
            string nullableNonNullVar = $"__foryNonNull{id++}";
            EmitReadPayload(sb, NonNullableCodec(element), nullableNonNullVar, indentLevel + 2, ref id);
            sb.AppendLine($"{indent}        {targetVar} = {nullableNonNullVar};");
            sb.AppendLine($"{indent}    }}");
            sb.AppendLine($"{indent}    else");
            sb.AppendLine($"{indent}    {{");
            sb.AppendLine($"{indent}        throw new global::Apache.Fory.InvalidDataException($\"invalid collection null flag {{__foryRefFlag}}\");");
            sb.AppendLine($"{indent}    }}");
            sb.AppendLine($"{indent}}}");
            sb.AppendLine($"{indent}else");
            sb.AppendLine($"{indent}{{");
            string nonNullVar = $"__foryNonNull{id++}";
            EmitReadPayload(sb, NonNullableCodec(element), nonNullVar, indentLevel + 1, ref id);
            sb.AppendLine($"{indent}    {targetVar} = {nonNullVar};");
            sb.AppendLine($"{indent}}}");
            return;
        }

        string directNonNullVar = $"__foryNonNull{id++}";
        EmitReadPayload(sb, element, directNonNullVar, indentLevel, ref id);
        sb.AppendLine($"{indent}{targetVar} = {directNonNullVar};");
    }

    private static void EmitReadMapPayload(
        StringBuilder sb,
        FieldCodecModel codec,
        string targetVar,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        FieldCodecModel key = codec.Generics[0];
        FieldCodecModel value = codec.Generics[1];
        string totalVar = $"__foryTotal{id++}";
        sb.AppendLine($"{indent}int {totalVar} = checked((int)context.Reader.ReadVarUInt32());");
        sb.AppendLine($"{indent}context.ReserveGraphMemory({GraphMapOwnerBytesExpr} + (long){totalVar} * {GraphMapElementBytesExpr(key, value)});");
        sb.AppendLine($"{indent}if ({totalVar} != 0)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    context.Reader.CheckBound({totalVar});");
        sb.AppendLine($"{indent}}}");
        sb.AppendLine($"{indent}{codec.TypeName} {targetVar} = new({totalVar});");
        sb.AppendLine($"{indent}int __foryRead = 0;");
        sb.AppendLine($"{indent}while (__foryRead < {totalVar})");
        sb.AppendLine($"{indent}{{");
        string innerIndent = indent + "    ";
        sb.AppendLine($"{innerIndent}byte __foryHeader = context.Reader.ReadUInt8();");
        sb.AppendLine($"{innerIndent}bool __foryKeyNull = (__foryHeader & 0b0000_0010) != 0;");
        sb.AppendLine($"{innerIndent}bool __foryKeyDeclared = (__foryHeader & 0b0000_0100) != 0;");
        sb.AppendLine($"{innerIndent}bool __foryValueNull = (__foryHeader & 0b0001_0000) != 0;");
        sb.AppendLine($"{innerIndent}bool __foryValueDeclared = (__foryHeader & 0b0010_0000) != 0;");
        sb.AppendLine($"{innerIndent}if (__foryKeyNull || __foryValueNull)");
        sb.AppendLine($"{innerIndent}{{");
        sb.AppendLine($"{innerIndent}    {key.TypeName} __foryKey = ({key.TypeName})default!;");
        sb.AppendLine($"{innerIndent}    {value.TypeName} __foryValue = ({value.TypeName})default!;");
        sb.AppendLine($"{innerIndent}    if (!__foryKeyNull)");
        sb.AppendLine($"{innerIndent}    {{");
        sb.AppendLine($"{innerIndent}        if (!__foryKeyDeclared)");
        sb.AppendLine($"{innerIndent}        {{");
        EmitReadInlineTypeInfo(sb, NonNullableCodec(key), indentLevel + 3, ref id);
        sb.AppendLine($"{innerIndent}        }}");
        EmitReadPayload(sb, NonNullableCodec(key), "__foryReadKey", indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}        __foryKey = __foryReadKey;");
        sb.AppendLine($"{innerIndent}    }}");
        sb.AppendLine($"{innerIndent}    if (!__foryValueNull)");
        sb.AppendLine($"{innerIndent}    {{");
        sb.AppendLine($"{innerIndent}        if (!__foryValueDeclared)");
        sb.AppendLine($"{innerIndent}        {{");
        EmitReadInlineTypeInfo(sb, NonNullableCodec(value), indentLevel + 3, ref id);
        sb.AppendLine($"{innerIndent}        }}");
        EmitReadPayload(sb, NonNullableCodec(value), "__foryReadValue", indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}        __foryValue = __foryReadValue;");
        sb.AppendLine($"{innerIndent}    }}");
        if (codec.CarrierKind == CarrierKind.NullableKeyDictionary)
        {
            sb.AppendLine($"{innerIndent}    {targetVar}[__foryKey] = __foryValue;");
        }
        else
        {
            sb.AppendLine($"{innerIndent}    if (!__foryKeyNull)");
            sb.AppendLine($"{innerIndent}    {{");
            sb.AppendLine($"{innerIndent}        {targetVar}[__foryKey] = __foryValue;");
            sb.AppendLine($"{innerIndent}    }}");
        }

        sb.AppendLine($"{innerIndent}    __foryRead++;");
        sb.AppendLine($"{innerIndent}    continue;");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}int __foryChunkSize = context.Reader.ReadUInt8();");
        sb.AppendLine($"{innerIndent}if (!__foryKeyDeclared)");
        sb.AppendLine($"{innerIndent}{{");
        EmitReadInlineTypeInfo(sb, NonNullableCodec(key), indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}if (!__foryValueDeclared)");
        sb.AppendLine($"{innerIndent}{{");
        EmitReadInlineTypeInfo(sb, NonNullableCodec(value), indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}}}");
        string mapIndexVar = $"__foryIndex{id++}";
        sb.AppendLine($"{innerIndent}for (int {mapIndexVar} = 0; {mapIndexVar} < __foryChunkSize; {mapIndexVar}++)");
        sb.AppendLine($"{innerIndent}{{");
        EmitReadPayload(sb, NonNullableCodec(key), "__foryKey", indentLevel + 2, ref id);
        EmitReadPayload(sb, NonNullableCodec(value), "__foryValue", indentLevel + 2, ref id);
        sb.AppendLine($"{innerIndent}    {targetVar}[__foryKey] = __foryValue;");
        sb.AppendLine($"{innerIndent}}}");
        sb.AppendLine($"{innerIndent}__foryRead += __foryChunkSize;");
        sb.AppendLine($"{indent}}}");
    }

    private static void EmitReadInlineTypeInfo(
        StringBuilder sb,
        FieldCodecModel codec,
        int indentLevel,
        ref int id)
    {
        string indent = new(' ', indentLevel * 4);
        if (!CanValidateInlineTypeInfo(codec.TypeId))
        {
            sb.AppendLine(
                $"{indent}throw new global::Apache.Fory.InvalidDataException(\"generated field value requires declared nested user type metadata\");");
            return;
        }

        string typeIdVar = $"__foryWireTypeId{id++}";
        sb.AppendLine($"{indent}uint {typeIdVar} = context.Reader.ReadUInt8();");
        sb.AppendLine($"{indent}if ({typeIdVar} != {codec.TypeId}u)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{indent}    throw new global::Apache.Fory.TypeMismatchException({codec.TypeId}u, {typeIdVar});");
        sb.AppendLine($"{indent}}}");
    }

    private static bool CanValidateInlineTypeInfo(uint typeId)
    {
        return typeId is > 0 and <= 24 or >= 36 and <= 56;
    }

    private static FieldCodecModel NonNullableCodec(FieldCodecModel codec)
    {
        if (!codec.Nullable)
        {
            return codec;
        }

        return new FieldCodecModel(
            codec.Kind,
            codec.TypeId,
            codec.NullableValueType && codec.TypeName.EndsWith("?", StringComparison.Ordinal)
                ? codec.TypeName.Substring(0, codec.TypeName.Length - 1)
                : codec.TypeName,
            false,
            false,
            codec.CarrierKind,
            codec.Generics);
    }

    private static MemberModel NonNullableMember(MemberModel member)
    {
        if (!member.IsNullable)
        {
            return member;
        }

        return new MemberModel(
            member.Name,
            member.FieldIdentifier,
            member.IsNullableValueType && member.TypeName.EndsWith("?", StringComparison.Ordinal)
                ? member.TypeName.Substring(0, member.TypeName.Length - 1)
                : StripNullableForTypeOf(member.TypeName),
            false,
            false,
            member.FieldId,
            member.Classification,
            member.Group,
            member.IsCollection,
            member.UseDictionaryTypeInfoCache,
            member.IsRefType,
            member.NeedsFieldTypeInfo,
            member.DynamicAnyKind,
            new TypeMetaFieldTypeModel(
                member.TypeMeta.TypeIdExpr,
                false,
                member.TypeMeta.TrackRefByContext,
                member.TypeMeta.Generics),
            member.FieldCodec is null ? null : NonNullableCodec(member.FieldCodec),
            member.HasSchemaType);
    }

    private static string ElementTypeName(string arrayTypeName)
    {
        return arrayTypeName.EndsWith("[]", StringComparison.Ordinal)
            ? arrayTypeName.Substring(0, arrayTypeName.Length - 2)
            : "object";
    }

    private const string GraphObjectOwnerBytesExpr =
        "(global::System.IntPtr.Size + global::System.IntPtr.Size + 4)";
    private const string GraphListOwnerBytesExpr =
        "(global::System.IntPtr.Size + global::System.IntPtr.Size + 12)";
    private const string GraphSetOwnerBytesExpr =
        "(global::System.IntPtr.Size + global::System.IntPtr.Size + 28)";
    private const string GraphMapOwnerBytesExpr =
        "(global::System.IntPtr.Size + global::System.IntPtr.Size + 32)";

    private static string GraphElementBytesExpr(FieldCodecModel codec)
    {
        return GraphElementBytesExpr(
            codec.Nullable && !codec.NullableValueType
                ? StripNullableForTypeOf(codec.TypeName)
                : codec.TypeName);
    }

    private static string GraphElementBytesExpr(string typeName)
    {
        return $"__ForyGraphElementBytes<{typeName}>.Bytes";
    }

    private static string GraphMapElementBytesExpr(FieldCodecModel key, FieldCodecModel value)
    {
        return $"((long){GraphElementBytesExpr(key)} + {GraphElementBytesExpr(value)})";
    }

    private static string ModelGraphMemoryExpr(TypeModel model)
    {
        return $"{GraphObjectOwnerBytesExpr} + HierarchyShallowBytes";
    }

    private static string FieldGraphMemoryExpr(
        ITypeSymbol fieldType,
        IFieldSymbol? targetField = null)
    {
        if (targetField is { IsFixedSizeBuffer: true })
        {
            ITypeSymbol elementType = targetField.Type is IPointerTypeSymbol pointer
                ? pointer.PointedAtType
                : targetField.Type;
            return $"({targetField.FixedSize} * {FieldGraphMemoryExpr(elementType)})";
        }

        if (fieldType.TypeKind is TypeKind.Pointer or TypeKind.FunctionPointer)
        {
            return "global::System.IntPtr.Size";
        }

        if (!fieldType.IsValueType)
        {
            return "4";
        }

        if (fieldType is INamedTypeSymbol nullableType &&
            nullableType.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T)
        {
            return $"global::System.Runtime.CompilerServices.Unsafe.SizeOf<{fieldType.ToDisplayString(FullNameFormat)}>()";
        }

        TypeClassification classification = ClassifyType(fieldType);
        int fixedValueBytes = FixedGraphValueBytes(fieldType, classification);
        if (fixedValueBytes > 0)
        {
            return fixedValueBytes.ToString(CultureInfo.InvariantCulture);
        }

        return $"global::System.Runtime.CompilerServices.Unsafe.SizeOf<{fieldType.ToDisplayString(FullNameFormat)}>()";
    }

    private static string PackedArrayElementTypeName(uint typeId)
    {
        return typeId switch
        {
            41 => "byte",
            43 => "bool",
            44 => "sbyte",
            45 => "short",
            46 => "int",
            47 => "long",
            48 => "byte",
            49 => "ushort",
            50 => "uint",
            51 => "ulong",
            53 => "global::System.Half",
            54 => "global::Apache.Fory.BFloat16",
            55 => "float",
            56 => "double",
            _ => throw new InvalidOperationException($"unsupported packed array type id {typeId}"),
        };
    }

    private static int PackedArrayElementWidth(uint typeId)
    {
        return typeId switch
        {
            41 or 43 or 44 or 48 => 1,
            45 or 49 or 53 or 54 => 2,
            46 or 50 or 55 => 4,
            47 or 51 or 56 => 8,
            _ => throw new InvalidOperationException($"unsupported packed array type id {typeId}"),
        };
    }

    private static uint PackedArrayElementTypeId(uint typeId)
    {
        return typeId switch
        {
            41 => 9,
            43 => 1,
            44 => 2,
            45 => 3,
            46 => 4,
            47 => 6,
            48 => 9,
            49 => 10,
            50 => 11,
            51 => 13,
            53 => 17,
            54 => 18,
            55 => 19,
            56 => 20,
            _ => throw new InvalidOperationException($"unsupported packed array type id {typeId}"),
        };
    }

    private static void EmitWriteMember(StringBuilder sb, MemberModel member, bool compatibleMode)
    {
        string refModeExpr = BuildWriteRefModeExpression(member);
        string memberAccess = member.ReadExpression("value");
        string hasGenerics = member.IsCollection ? "true" : "false";
        string writeTypeInfo = compatibleMode
            ? BuildFieldTypeInfoLiteral(member)
            : "false";

        switch (member.DynamicAnyKind)
        {
            case DynamicAnyKind.AnyValue:
                sb.AppendLine(
                    $"            global::Apache.Fory.DynamicAnyCodec.WriteAny(context, {memberAccess}, {refModeExpr}, true, false);");
                return;
            case DynamicAnyKind.None:
                break;
            default:
                throw new InvalidOperationException($"unsupported dynamic any kind {member.DynamicAnyKind}");
        }

        if (member.FieldCodec is not null)
        {
            sb.AppendLine(
                $"            __ForyWrite{member.CodeKey}Field(context, {memberAccess}, {refModeExpr});");
            return;
        }

        if (member.UseDictionaryTypeInfoCache)
        {
            EmitWriteDictionaryWithTypeInfoCache(
                sb,
                member,
                memberAccess,
                refModeExpr,
                writeTypeInfo,
                hasGenerics,
                compatibleMode);
            return;
        }

        if (!member.IsNullable && TryBuildDirectFieldWrite(member, memberAccess, out string? directWriteCode))
        {
            sb.AppendLine($"            {directWriteCode}");
            return;
        }

        if (TryBuildNullableFixedTaggedFieldWrite(member, memberAccess, out string? nullableWriteCode))
        {
            sb.AppendLine($"            {nullableWriteCode}");
            return;
        }

        if (writeTypeInfo == "false")
        {
            if (CanUseDirectWriteDataInvocation(member))
            {
                sb.AppendLine(
                    $"            context.TypeResolver.GetSerializer<{member.TypeName}>().WriteData(context, {memberAccess}, {hasGenerics});");
                return;
            }

            if (CanUseTrackRefBranchWriteDataInvocation(member))
            {
                sb.AppendLine("            if (context.TrackRef)");
                sb.AppendLine("            {");
                sb.AppendLine(
                    $"                context.TypeResolver.GetSerializer<{member.TypeName}>().Write(context, {memberAccess}, global::Apache.Fory.RefMode.Tracking, false, {hasGenerics});");
                sb.AppendLine("            }");
                sb.AppendLine("            else");
                sb.AppendLine("            {");
                sb.AppendLine(
                    $"                context.TypeResolver.GetSerializer<{member.TypeName}>().WriteData(context, {memberAccess}, {hasGenerics});");
                sb.AppendLine("            }");
                return;
            }
        }

        sb.AppendLine(
            $"            context.TypeResolver.GetSerializer<{member.TypeName}>().Write(context, {memberAccess}, {refModeExpr}, {writeTypeInfo}, {hasGenerics});");
    }

    private static void EmitWriteDictionaryWithTypeInfoCache(
        StringBuilder sb,
        MemberModel member,
        string memberAccess,
        string refModeExpr,
        string writeTypeInfo,
        string hasGenerics,
        bool compatibleMode)
    {
        string memberId = member.CodeKey;
        string modeSuffix = compatibleMode ? "Compat" : "Schema";
        string fieldValueVar = $"__{memberId}DictValue{modeSuffix}";
        string runtimeTypeVar = $"__{memberId}DictRuntimeType{modeSuffix}";
        string typeInfoVar = $"__{memberId}DictTypeInfo{modeSuffix}";
        sb.AppendLine($"            {member.TypeName} {fieldValueVar} = {memberAccess};");
        sb.AppendLine($"            if ({fieldValueVar} is null)");
        sb.AppendLine("            {");
        sb.AppendLine(
            $"                context.TypeResolver.GetSerializer<{member.TypeName}>().Write(context, ({member.TypeName})null!, {refModeExpr}, {writeTypeInfo}, {hasGenerics});");
        sb.AppendLine("            }");
        sb.AppendLine("            else");
        sb.AppendLine("            {");
        sb.AppendLine($"                global::System.Type {runtimeTypeVar} = {fieldValueVar}.GetType();");
        sb.AppendLine($"                global::Apache.Fory.TypeInfo {typeInfoVar} = context.TypeResolver.GetTypeInfo({runtimeTypeVar});");
        sb.AppendLine(
            $"                context.TypeResolver.WriteObject({typeInfoVar}, context, {fieldValueVar}, {refModeExpr}, {writeTypeInfo}, {hasGenerics});");
        sb.AppendLine("            }");
    }

    private static void EmitReadMemberAssignment(
        StringBuilder sb,
        MemberModel member,
        string refModeExpr,
        string readTypeInfoExpr,
        string valueVar,
        string variableSuffix,
        int indentLevel,
        bool allowDirectRead)
    {
        string indent = new(' ', indentLevel * 2);
        if (member.SetterAccessorName is null)
        {
            EmitReadMemberAssignmentCore(
                sb,
                member,
                refModeExpr,
                readTypeInfoExpr,
                member.AssignmentTarget(valueVar),
                variableSuffix,
                indent,
                allowDirectRead);
            return;
        }

        string fieldValueVar = $"__fory{member.CodeKey}Value{variableSuffix}";
        sb.AppendLine($"{indent}{member.TypeName} {fieldValueVar};");
        EmitReadMemberAssignmentCore(
            sb,
            member,
            refModeExpr,
            readTypeInfoExpr,
            fieldValueVar,
            variableSuffix,
            indent,
            allowDirectRead);
        sb.AppendLine(
            $"{indent}{member.AccessorProviderTypeName}.{member.SetterAccessorName}({valueVar}, {fieldValueVar});");
    }

    private static void EmitReadMemberAssignmentCore(
        StringBuilder sb,
        MemberModel member,
        string refModeExpr,
        string readTypeInfoExpr,
        string assignmentTarget,
        string variableSuffix,
        string indent,
        bool allowDirectRead)
    {
        string typeOfTypeName = StripNullableForTypeOf(member.TypeName);
        switch (member.DynamicAnyKind)
        {
            case DynamicAnyKind.AnyValue:
                sb.AppendLine(
                    $"{indent}{assignmentTarget} = ({member.TypeName})global::Apache.Fory.DynamicAnyCodec.CastAnyDynamicValue(global::Apache.Fory.DynamicAnyCodec.ReadAny(context, {refModeExpr}, true), typeof({typeOfTypeName}))!;");
                return;
            case DynamicAnyKind.None:
                break;
            default:
                throw new InvalidOperationException($"unsupported dynamic any kind {member.DynamicAnyKind}");
        }

        if (variableSuffix == "Compat" &&
            TryBuildCompatibleScalarReadExpression(member, out string? compatibleScalarReadExpr))
        {
            sb.AppendLine($"{indent}{assignmentTarget} = {compatibleScalarReadExpr};");
            return;
        }

        if (member.FieldCodec is not null)
        {
            if (variableSuffix == "Compat" &&
                CanReadCompatibleField(member.FieldCodec))
            {
                sb.AppendLine(
                    $"{indent}{assignmentTarget} = __ForyCompatibleFieldReaders.Read{member.CodeKey}FieldBridge(context, remoteField.FieldType, {refModeExpr});");
            }
            else
            {
                sb.AppendLine(
                    $"{indent}{assignmentTarget} = __ForyRead{member.CodeKey}Field(context, {refModeExpr});");
            }

            return;
        }

        if (allowDirectRead && !member.IsNullable && TryBuildDirectFieldRead(member, out string? directReadExpr))
        {
            sb.AppendLine($"{indent}{assignmentTarget} = {directReadExpr};");
            return;
        }

        if (allowDirectRead && TryBuildNullableFixedTaggedFieldRead(member, assignmentTarget, variableSuffix, indent, out string? nullableReadCode))
        {
            sb.AppendLine(nullableReadCode);
            return;
        }

        if (CanReadInlineValueData(member))
        {
            EmitInlineValueDataRead(sb, member, assignmentTarget, readTypeInfoExpr, indent);
            return;
        }

        if (variableSuffix == "Compat")
        {
            sb.AppendLine(
                $"{indent}{assignmentTarget} = context.TypeResolver.GetSerializer<{member.TypeName}>().Read(context, {refModeExpr}, {readTypeInfoExpr});");
            return;
        }

        sb.AppendLine(
            $"{indent}{assignmentTarget} = context.TypeResolver.GetSerializer<{member.TypeName}>().Read(context, {refModeExpr}, {readTypeInfoExpr});");
    }

    private static void EmitInlineValueDataRead(
        StringBuilder sb,
        MemberModel member,
        string assignmentTarget,
        string readTypeInfoExpr,
        string indent)
    {
        if (readTypeInfoExpr == "false")
        {
            sb.AppendLine(
                $"{indent}{assignmentTarget} = context.TypeResolver.GetSerializer<{member.TypeName}>().ReadData(context);");
            return;
        }

        string serializerVar = $"__fory{member.CodeKey}Serializer";
        sb.AppendLine(
            $"{indent}global::Apache.Fory.Serializer<{member.TypeName}> {serializerVar} = context.TypeResolver.GetSerializer<{member.TypeName}>();");
        if (readTypeInfoExpr == "true")
        {
            sb.AppendLine($"{indent}context.TypeResolver.ReadTypeInfo({serializerVar}, context);");
        }
        else
        {
            sb.AppendLine($"{indent}if ({readTypeInfoExpr})");
            sb.AppendLine($"{indent}{{");
            sb.AppendLine($"{indent}  context.TypeResolver.ReadTypeInfo({serializerVar}, context);");
            sb.AppendLine($"{indent}}}");
        }

        sb.AppendLine($"{indent}{assignmentTarget} = {serializerVar}.ReadData(context);");
    }

    private static bool CompatibleCaseNeedsRemoteRefMode(MemberModel member)
    {
        return !IsCompatibleScalarMember(member);
    }

    private static bool CanReadInlineValueData(MemberModel member)
    {
        if (member.IsNullable ||
            member.DynamicAnyKind != DynamicAnyKind.None ||
            member.FieldCodec is not null ||
            member.IsRefType ||
            member.IsCollection ||
            member.Classification.IsMap)
        {
            return false;
        }

        return !member.Classification.IsBuiltIn;
    }

    private static bool IsCompatibleScalarMember(MemberModel member)
    {
        return TryResolveCompatibleScalarTarget(member, out _);
    }

    private static bool TryBuildCompatibleScalarReadExpression(MemberModel member, out string? readExpr)
    {
        readExpr = null;
        if (!TryResolveCompatibleScalarTarget(member, out string? methodTarget))
        {
            return false;
        }

        string methodName = member.IsNullable ? $"ReadNullable{methodTarget}Field" : $"Read{methodTarget}Field";
        readExpr =
            $"global::Apache.Fory.CompatibleScalarConverter.{methodName}(context, remoteField)";
        return true;
    }

    private static bool TryResolveCompatibleScalarTarget(MemberModel member, out string? methodTarget)
    {
        methodTarget = null;
        if (member.DynamicAnyKind != DynamicAnyKind.None ||
            !IsCompatibleScalarTypeId(member.Classification.TypeId))
        {
            return false;
        }

        string targetName = StripNullableForTypeOf(member.TypeName);
        methodTarget = targetName switch
        {
            "bool" or "global::System.Boolean" => "Bool",
            "sbyte" or "global::System.SByte" => "SByte",
            "short" or "global::System.Int16" => "Int16",
            "int" or "global::System.Int32" => "Int32",
            "long" or "global::System.Int64" => "Int64",
            "byte" or "global::System.Byte" => "Byte",
            "ushort" or "global::System.UInt16" => "UInt16",
            "uint" or "global::System.UInt32" => "UInt32",
            "ulong" or "global::System.UInt64" => "UInt64",
            "global::System.Half" => "Half",
            "global::Apache.Fory.BFloat16" => "BFloat16",
            "float" or "global::System.Single" => "Float",
            "double" or "global::System.Double" => "Double",
            "string" or "global::System.String" => "String",
            "decimal" or "global::System.Decimal" => "Decimal",
            "global::Apache.Fory.ForyDecimal" => "ForyDecimal",
            _ => null,
        };

        return methodTarget is not null;
    }

    private static bool IsCompatibleScalarTypeId(uint typeId)
    {
        return typeId is >= 1 and <= 15 or >= 17 and <= 21 or 40;
    }

    private static string StripNullableForTypeOf(string typeName)
    {
        return typeName.Replace("?", string.Empty);
    }

    private static bool TryBuildDirectFieldWrite(MemberModel member, string memberAccess, out string? writeCode)
    {
        writeCode = null;
        if (!CanUseDirectBuiltInFieldAccess(member))
        {
            return false;
        }

        return TryBuildDirectPayloadWrite(member.Classification.TypeId, memberAccess, out writeCode);
    }

    private static bool TryBuildDirectFieldRead(MemberModel member, out string? readExpr)
    {
        readExpr = null;
        if (!CanUseDirectBuiltInFieldAccess(member))
        {
            return false;
        }

        return TryBuildDirectPayloadRead(member.Classification.TypeId, out readExpr);
    }

    private static bool TryBuildNullableFixedTaggedFieldWrite(MemberModel member, string memberAccess, out string? writeCode)
    {
        writeCode = null;
        if (!member.IsNullableValueType || !IsFixedTaggedTypeId(member.Classification.TypeId))
        {
            return false;
        }

        if (!TryBuildDirectPayloadWrite(member.Classification.TypeId, $"{memberAccess}.Value", out string? payloadWriteCode))
        {
            return false;
        }

        writeCode = $"if (!{memberAccess}.HasValue) {{ context.Writer.WriteInt8((sbyte)global::Apache.Fory.RefFlag.Null); }} else {{ context.Writer.WriteInt8((sbyte)global::Apache.Fory.RefFlag.NotNullValue); {payloadWriteCode} }}";
        return true;
    }

    private static bool TryBuildNullableFixedTaggedFieldRead(
        MemberModel member,
        string assignmentTarget,
        string variableSuffix,
        string indent,
        out string code)
    {
        code = string.Empty;
        if (!member.IsNullableValueType || !IsFixedTaggedTypeId(member.Classification.TypeId))
        {
            return false;
        }

        if (!TryBuildDirectPayloadRead(member.Classification.TypeId, out string? payloadReadExpr))
        {
            return false;
        }

        string refFlagVar = $"__{member.CodeKey}RefFlag{variableSuffix}";
        string nestedIndent = indent + "  ";
        StringBuilder sb = new();
        sb.AppendLine($"{indent}sbyte {refFlagVar} = context.Reader.ReadInt8();");
        sb.AppendLine($"{indent}if ({refFlagVar} == (sbyte)global::Apache.Fory.RefFlag.Null)");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{nestedIndent}{assignmentTarget} = ({member.TypeName})null!;");
        sb.AppendLine($"{indent}}}");
        sb.AppendLine($"{indent}else");
        sb.AppendLine($"{indent}{{");
        sb.AppendLine($"{nestedIndent}{assignmentTarget} = {payloadReadExpr};");
        sb.Append($"{indent}}}");
        code = sb.ToString();
        return true;
    }

    private static bool IsFixedTaggedTypeId(uint typeId)
    {
        return typeId is 4 or 6 or 8 or 11 or 13 or 15;
    }

    private static bool TryBuildDirectPayloadWrite(uint typeId, string valueExpr, out string? writeCode)
    {
        writeCode = null;
        switch (typeId)
        {
            case 1:
                writeCode = $"context.Writer.WriteUInt8({valueExpr} ? (byte)1 : (byte)0);";
                return true;
            case 2:
                writeCode = $"context.Writer.WriteInt8({valueExpr});";
                return true;
            case 3:
                writeCode = $"context.Writer.WriteInt16({valueExpr});";
                return true;
            case 4:
                writeCode = $"context.Writer.WriteInt32({valueExpr});";
                return true;
            case 5:
                writeCode = $"context.Writer.WriteVarInt32({valueExpr});";
                return true;
            case 6:
                writeCode = $"context.Writer.WriteInt64({valueExpr});";
                return true;
            case 7:
                writeCode = $"context.Writer.WriteVarInt64({valueExpr});";
                return true;
            case 8:
                writeCode = $"context.Writer.WriteTaggedInt64({valueExpr});";
                return true;
            case 9:
                writeCode = $"context.Writer.WriteUInt8({valueExpr});";
                return true;
            case 10:
                writeCode = $"context.Writer.WriteUInt16({valueExpr});";
                return true;
            case 11:
                writeCode = $"context.Writer.WriteUInt32({valueExpr});";
                return true;
            case 12:
                writeCode = $"context.Writer.WriteVarUInt32({valueExpr});";
                return true;
            case 13:
                writeCode = $"context.Writer.WriteUInt64({valueExpr});";
                return true;
            case 14:
                writeCode = $"context.Writer.WriteVarUInt64({valueExpr});";
                return true;
            case 15:
                writeCode = $"context.Writer.WriteTaggedUInt64({valueExpr});";
                return true;
            case 17:
                writeCode = $"context.Writer.WriteUInt16(global::System.BitConverter.HalfToUInt16Bits({valueExpr}));";
                return true;
            case 18:
                writeCode = $"context.Writer.WriteUInt16({valueExpr}.ToBits());";
                return true;
            case 19:
                writeCode = $"context.Writer.WriteFloat32({valueExpr});";
                return true;
            case 20:
                writeCode = $"context.Writer.WriteFloat64({valueExpr});";
                return true;
            case 21:
                writeCode = $"global::Apache.Fory.StringSerializer.WriteString(context, {valueExpr});";
                return true;
            default:
                return false;
        }
    }

    private static bool TryBuildDirectPayloadRead(uint typeId, out string? readExpr)
    {
        readExpr = null;
        switch (typeId)
        {
            case 1:
                readExpr = "context.Reader.ReadUInt8() != 0";
                return true;
            case 2:
                readExpr = "context.Reader.ReadInt8()";
                return true;
            case 3:
                readExpr = "context.Reader.ReadInt16()";
                return true;
            case 4:
                readExpr = "context.Reader.ReadInt32()";
                return true;
            case 5:
                readExpr = "context.Reader.ReadVarInt32()";
                return true;
            case 6:
                readExpr = "context.Reader.ReadInt64()";
                return true;
            case 7:
                readExpr = "context.Reader.ReadVarInt64()";
                return true;
            case 8:
                readExpr = "context.Reader.ReadTaggedInt64()";
                return true;
            case 9:
                readExpr = "context.Reader.ReadUInt8()";
                return true;
            case 10:
                readExpr = "context.Reader.ReadUInt16()";
                return true;
            case 11:
                readExpr = "context.Reader.ReadUInt32()";
                return true;
            case 12:
                readExpr = "context.Reader.ReadVarUInt32()";
                return true;
            case 13:
                readExpr = "context.Reader.ReadUInt64()";
                return true;
            case 14:
                readExpr = "context.Reader.ReadVarUInt64()";
                return true;
            case 15:
                readExpr = "context.Reader.ReadTaggedUInt64()";
                return true;
            case 17:
                readExpr = "global::System.BitConverter.UInt16BitsToHalf(context.Reader.ReadUInt16())";
                return true;
            case 18:
                readExpr = "global::Apache.Fory.BFloat16.FromBits(context.Reader.ReadUInt16())";
                return true;
            case 19:
                readExpr = "context.Reader.ReadFloat32()";
                return true;
            case 20:
                readExpr = "context.Reader.ReadFloat64()";
                return true;
            case 21:
                readExpr = "global::Apache.Fory.StringSerializer.ReadString(context)";
                return true;
            default:
                return false;
        }
    }

    private static bool CanUseDirectBuiltInFieldAccess(MemberModel member)
    {
        if (member.IsNullable ||
            member.DynamicAnyKind != DynamicAnyKind.None ||
            member.IsCollection ||
            member.Classification.IsMap)
        {
            return false;
        }

        return member.Classification.IsPrimitive || member.Classification.TypeId == 21;
    }

    private static bool CanUseDirectWriteDataInvocation(MemberModel member)
    {
        if (member.IsNullable || member.DynamicAnyKind != DynamicAnyKind.None)
        {
            return false;
        }

        return member.Classification.IsBuiltIn || !member.IsRefType;
    }

    private static bool CanUseTrackRefBranchWriteDataInvocation(MemberModel member)
    {
        if (member.IsNullable || member.DynamicAnyKind != DynamicAnyKind.None)
        {
            return false;
        }

        return !member.Classification.IsBuiltIn && member.IsRefType;
    }

    private static string BuildSchemaFingerprintExpression(ImmutableArray<MemberModel> members)
    {
        if (members.IsDefaultOrEmpty)
        {
            return "\"\"";
        }

        IEnumerable<MemberModel> ordered = members
            .OrderBy(m => m.FieldId.HasValue ? 0 : 1)
            .ThenBy(m => m.FieldId.GetValueOrDefault())
            .ThenBy(m => m.FieldIdentifier, StringComparer.Ordinal)
            .ThenBy(
                m => m.DeclaringType is null
                    ? string.Empty
                    : BuildRuntimeTypeKey(m.DeclaringType),
                StringComparer.Ordinal)
            .ThenBy(m => m.Name, StringComparer.Ordinal)
            .ThenBy(m => m.DeclarationOrdinal);

        StringBuilder sb = new();
        bool first = true;
        foreach (MemberModel member in ordered)
        {
            string piece =
                $"\"{EscapeString(BuildSchemaFieldIdentifier(member))},\" + {BuildSchemaFieldTypeFingerprintExpression(member.TypeMeta, "trackRef", includeNullable: true)} + \";\"";
            if (!first)
            {
                sb.Append(" + ");
            }

            first = false;
            sb.Append(piece);
        }

        return sb.ToString().Replace("b_float16", "bfloat16");
    }

    private static string BuildSchemaFieldIdentifier(MemberModel member)
    {
        return member.FieldId.HasValue
            ? member.FieldId.Value.ToString(CultureInfo.InvariantCulture)
            : member.FieldIdentifier;
    }

    private static string BuildSchemaFieldTypeFingerprintExpression(
        TypeMetaFieldTypeModel model,
        string trackRefExpr,
        bool includeNullable)
    {
        string localTrackRefExpr = model.TrackRefByContext
            ? $"({trackRefExpr} ? 1 : 0)"
            : "0";
        string prefix =
            $"\"{NormalizeSchemaFingerprintTypeId(model.TypeIdExpr).ToString(CultureInfo.InvariantCulture)},\" + {localTrackRefExpr} + \","
            + (includeNullable && model.Nullable ? "1" : "0")
            + "\"";
        if (model.Generics.Length == 0)
        {
            return prefix;
        }

        if (model.Generics.Length == 1)
        {
            string child = BuildSchemaFieldTypeFingerprintExpression(model.Generics[0], "false", includeNullable: false);
            return $"{prefix} + \"[\" + {child} + \"]\"";
        }

        if (model.Generics.Length == 2)
        {
            string key = BuildSchemaFieldTypeFingerprintExpression(model.Generics[0], "false", includeNullable: false);
            string value = BuildSchemaFieldTypeFingerprintExpression(model.Generics[1], "false", includeNullable: false);
            return $"{prefix} + \"[\" + {key} + \"|\" + {value} + \"]\"";
        }

        throw new InvalidOperationException("schema fingerprint supports only list/set/map generic arity");
    }

    private static uint NormalizeSchemaFingerprintTypeId(string typeIdExpr)
    {
        if (!TryParseSchemaFingerprintTypeId(typeIdExpr, out uint typeId))
        {
            throw new InvalidOperationException($"unsupported schema fingerprint type id expression {typeIdExpr}");
        }

        return typeId switch
        {
            0 or 25 or 26 or 27 or 28 or 29 or 30 or 31 or 32 or 33 or 34 or 35 => 0,
            _ => typeId,
        };
    }

    private static bool TryParseSchemaFingerprintTypeId(string typeIdExpr, out uint typeId)
    {
        string normalized = typeIdExpr.Replace(" ", string.Empty);
        if (normalized.StartsWith("(uint)", StringComparison.Ordinal))
        {
            normalized = normalized.Substring(6);
        }

        if (uint.TryParse(normalized, NumberStyles.None, CultureInfo.InvariantCulture, out typeId))
        {
            return true;
        }

        switch (normalized)
        {
            case "global::Apache.Fory.TypeId.Unknown":
                typeId = 0;
                return true;
            case "global::Apache.Fory.TypeId.List":
                typeId = 22;
                return true;
            case "global::Apache.Fory.TypeId.Set":
                typeId = 23;
                return true;
            case "global::Apache.Fory.TypeId.Map":
                typeId = 24;
                return true;
            case "global::Apache.Fory.TypeId.Enum":
                typeId = 25;
                return true;
            case "global::Apache.Fory.TypeId.Union":
                typeId = 33;
                return true;
            default:
                typeId = 0;
                return false;
        }
    }

    private static string BuildTypeMetaExpression(TypeMetaFieldTypeModel model, string trackRefExpr)
    {
        string localTrackRefExpr = model.TrackRefByContext ? trackRefExpr : "false";
        if (model.Generics.Length > 0)
        {
            string generics = string.Join(
                ", ",
                model.Generics.Select(g => BuildTypeMetaExpression(g, trackRefExpr)));
            return
                $"new global::Apache.Fory.TypeMetaFieldType({model.TypeIdExpr}, {BoolLiteral(model.Nullable)}, {localTrackRefExpr}, new global::Apache.Fory.TypeMetaFieldType[] {{ {generics} }})";
        }

        return $"new global::Apache.Fory.TypeMetaFieldType({model.TypeIdExpr}, {BoolLiteral(model.Nullable)}, {localTrackRefExpr})";
    }

    private static string BuildTypeMetaFieldIdExpression(short? fieldId)
    {
        return fieldId.HasValue ? $"(short){fieldId.Value}" : "null";
    }

    private static string BuildWriteRefModeExpression(MemberModel member)
    {
        return member.DynamicAnyKind switch
        {
            DynamicAnyKind.AnyValue => $"__ForyRefMode({BoolLiteral(member.IsNullable)}, context.TrackRef)",
            _ => member.Classification.IsBuiltIn || !member.IsRefType
                ? $"__ForyRefMode({BoolLiteral(member.IsNullable)}, false)"
                : $"__ForyRefMode({BoolLiteral(member.IsNullable)}, context.TrackRef)",
        };
    }

    private static string BuildUnionCaseRefModeExpression(MemberModel member)
    {
        return member.IsRefType
            ? "__ForyRefMode(true, context.TrackRef)"
            : "global::Apache.Fory.RefMode.NullOnly";
    }

    private static string BuildFieldTypeInfoLiteral(MemberModel member)
    {
        return BoolLiteral(member.NeedsFieldTypeInfo);
    }

    private static TypeModel? BuildTypeModel(GeneratorSyntaxContext context, CancellationToken cancellationToken)
    {
        if (context.SemanticModel.GetDeclaredSymbol(context.Node, cancellationToken) is not INamedTypeSymbol typeSymbol)
        {
            return null;
        }

        AttributeData? attribute = GetForyAttribute(
            typeSymbol,
            out ForyAttributeKind attributeKind,
            out bool hasConflictingAttributes);
        if (attribute is null)
        {
            return null;
        }

        string declarationName = typeSymbol.ToDisplayString(FullNameFormat);
        string serializerName = GeneratedSerializerName(typeSymbol);
        Location? declarationLocation = typeSymbol.Locations.FirstOrDefault(location => location.IsInSource);
        bool evolving = GetEvolving(attribute);
        bool evolvingExplicit = HasNamedArgument(attribute, "Evolving");
        bool baseOnly = GetBaseOnly(attribute);
        bool baseOnlyExplicit = HasNamedArgument(attribute, "BaseOnly");
        if (hasConflictingAttributes)
        {
            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                DeclKind.Unknown,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray.Create(Diagnostic.Create(
                    InvalidExternalDeclaration,
                    declarationLocation,
                    declarationName,
                    "exactly one of ForyStruct, ForyEnum, or ForyUnion is allowed")));
        }

        Location? targetLocation = GetNamedArgumentLocation(attribute, "Target", cancellationToken) ??
                                   declarationLocation;
        ITypeSymbol? target = null;
        bool invalidTargetValue = false;
        foreach (KeyValuePair<string, TypedConstant> namedArgument in attribute.NamedArguments)
        {
            if (!string.Equals(namedArgument.Key, "Target", StringComparison.Ordinal))
            {
                continue;
            }

            if (namedArgument.Value.Value is ITypeSymbol targetSymbol)
            {
                target = targetSymbol;
            }
            else if (!namedArgument.Value.IsNull)
            {
                invalidTargetValue = true;
            }

            break;
        }

        if (invalidTargetValue)
        {
            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                DeclKind.Unknown,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray.Create(Diagnostic.Create(
                    InvalidExternalTarget,
                    targetLocation,
                    "<unknown>",
                    "Target must name one closed CLR type")));
        }

        if (target is null)
        {
            return BuildOrdinaryTypeModel(
                context.SemanticModel.Compilation,
                typeSymbol,
                attributeKind,
                declarationName,
                serializerName,
                evolving,
                evolvingExplicit,
                baseOnly,
                baseOnlyExplicit,
                declarationLocation);
        }

        if (attributeKind == ForyAttributeKind.Struct &&
            target is INamedTypeSymbol
            {
                TypeKind: not TypeKind.Error,
                ContainingAssembly: not null,
            } namedTarget &&
            !ContainsOpenType(namedTarget))
        {
            serializerName = GeneratedSerializerName(namedTarget);
        }

        return attributeKind switch
        {
            ForyAttributeKind.Struct => BuildExternalStructModel(
                context.SemanticModel.Compilation,
                typeSymbol,
                target,
                declarationName,
                serializerName,
                evolving,
                evolvingExplicit,
                baseOnly,
                declarationLocation,
                targetLocation),
            ForyAttributeKind.Enum => BuildExternalEnumModel(
                context.SemanticModel.Compilation,
                typeSymbol,
                target,
                declarationName,
                serializerName,
                declarationLocation,
                targetLocation),
            _ => new TypeModel(
                declarationName,
                declarationName,
                target,
                serializerName,
                DeclKind.Unknown,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray.Create(Diagnostic.Create(
                    InvalidExternalDeclaration,
                    declarationLocation,
                    declarationName,
                    "Target is supported only by ForyStruct and ForyEnum"))),
        };
    }

    private static TypeModel BuildOrdinaryTypeModel(
        Compilation compilation,
        INamedTypeSymbol typeSymbol,
        ForyAttributeKind attributeKind,
        string declarationName,
        string serializerName,
        bool evolving,
        bool evolvingExplicit,
        bool baseOnly,
        bool baseOnlyExplicit,
        Location? declarationLocation)
    {
        ImmutableArray<Diagnostic> ignoredFieldDiagnostics = typeSymbol.GetMembers()
            .Where(member =>
                member is IFieldSymbol or IPropertySymbol &&
                TryGetIgnoredField(member, out _))
            .Select(member => Diagnostic.Create(
                InvalidIgnoredField,
                member.Locations.FirstOrDefault(location => location.IsInSource),
                member.Name,
                "Ignore is supported only by external ForyStruct serializer declarations"))
            .ToImmutableArray();
        if (!ignoredFieldDiagnostics.IsEmpty)
        {
            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                DeclKind.Unknown,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                ignoredFieldDiagnostics);
        }

        if (HasGenericContext(typeSymbol))
        {
            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                DeclKind.Unknown,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray.Create(Diagnostic.Create(
                    GenericTypeNotSupported,
                    declarationLocation,
                    declarationName)));
        }

        if (attributeKind == ForyAttributeKind.Enum)
        {
            if (typeSymbol.TypeKind != TypeKind.Enum)
            {
                return new TypeModel(
                    declarationName,
                    declarationName,
                    typeSymbol,
                    serializerName,
                    DeclKind.Unknown,
                    evolving,
                    declarationLocation,
                    ImmutableArray<MemberModel>.Empty,
                    ImmutableArray<MemberModel>.Empty,
                    ImmutableArray.Create(Diagnostic.Create(
                        InvalidExternalDeclaration,
                        declarationLocation,
                        declarationName,
                        "ForyEnum without Target is valid only on an enum")));
            }

            List<Diagnostic> enumDiagnostics = [];
            ValidateEnumValues(typeSymbol, declarationLocation, enumDiagnostics);
            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                DeclKind.Enum,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                enumDiagnostics.ToImmutableArray());
        }

        if (attributeKind == ForyAttributeKind.Union)
        {
            if (typeSymbol.TypeKind != TypeKind.Class)
            {
                return new TypeModel(
                    declarationName,
                    declarationName,
                    typeSymbol,
                    serializerName,
                    DeclKind.Unknown,
                    evolving,
                    declarationLocation,
                    ImmutableArray<MemberModel>.Empty,
                    ImmutableArray<MemberModel>.Empty,
                    ImmutableArray.Create(Diagnostic.Create(
                        InvalidUnionType,
                        declarationLocation,
                        declarationName)));
            }

            List<Diagnostic> unionDiagnostics = [];
            ImmutableArray<UnionCaseModel> unionCases = BuildUnionCases(typeSymbol, unionDiagnostics);
            if (unionCases.IsEmpty)
            {
                unionDiagnostics.Add(Diagnostic.Create(
                    InvalidUnionType,
                    declarationLocation,
                    declarationName));
            }

            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                DeclKind.Union,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                unionDiagnostics.ToImmutableArray(),
                unionCases);
        }

        DeclKind kind = typeSymbol.TypeKind switch
        {
            TypeKind.Struct => DeclKind.Struct,
            TypeKind.Class => DeclKind.Class,
            _ => DeclKind.Unknown,
        };
        if (kind == DeclKind.Unknown)
        {
            return new TypeModel(
                declarationName,
                declarationName,
                typeSymbol,
                serializerName,
                kind,
                evolving,
                declarationLocation,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray<MemberModel>.Empty,
                ImmutableArray.Create(Diagnostic.Create(
                    InvalidExternalDeclaration,
                    declarationLocation,
                    declarationName,
                    "ForyStruct without Target is valid only on a class or struct")));
        }

        List<Diagnostic> diagnostics = [];
        if (baseOnlyExplicit)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                declarationLocation,
                declarationName,
                "BaseOnly is valid only on an external class declaration"));
        }

        bool abstractClass = kind == DeclKind.Class && typeSymbol.IsAbstract;
        if (abstractClass && evolvingExplicit)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidAbstractStructOption,
                GetNamedArgumentLocation(
                    GetForyAttribute(typeSymbol, out _)!,
                    "Evolving",
                    CancellationToken.None) ?? declarationLocation,
                declarationName,
                "Evolving"));
        }

        if (kind == DeclKind.Class && !abstractClass)
        {
            IMethodSymbol? constructor = FindAccessibleParameterlessCtor(typeSymbol, compilation);
            if (constructor is null)
            {
                diagnostics.Add(Diagnostic.Create(
                    MissingCtor,
                    declarationLocation,
                    declarationName));
            }
            else if (HasRequiredMembers(typeSymbol) && !SetsRequiredMembers(constructor))
            {
                diagnostics.Add(Diagnostic.Create(
                    MissingCtor,
                    declarationLocation,
                    declarationName));
            }
        }

        List<MemberModel> members = [];
        string providerVisibility = ProviderVisibility(typeSymbol);
        bool publishProviderApi = providerVisibility == "public";
        ISymbol[] declaredSymbols = typeSymbol.GetMembers()
            .Where(member => member is IFieldSymbol or IPropertySymbol)
            .Where(member => !member.IsImplicitlyDeclared)
            .OrderBy(member => member.MetadataName, StringComparer.Ordinal)
            .ThenBy(member => member is IFieldSymbol ? 0 : 1)
            .ToArray();
        for (int declarationOrdinal = 0; declarationOrdinal < declaredSymbols.Length; declarationOrdinal++)
        {
            ISymbol member = declaredSymbols[declarationOrdinal];
            if (member.IsStatic)
            {
                continue;
            }

            if (member is IFieldSymbol field)
            {
                bool explicitField = HasForyFieldAttribute(field);
                if (!ValidateOrdinaryFieldOptions(field, diagnostics))
                {
                    continue;
                }

                bool accessible = compilation.IsSymbolAccessibleWithin(field, compilation.Assembly);
                if (!explicitField && !accessible)
                {
                    continue;
                }

                if (field.IsConst ||
                    field.IsReadOnly ||
                    field.IsFixedSizeBuffer ||
                    field.Type.TypeKind is TypeKind.Pointer or TypeKind.FunctionPointer ||
                    field.Type.IsRefLikeType)
                {
                    if (explicitField)
                    {
                        diagnostics.Add(Diagnostic.Create(
                            InvalidExternalMember,
                            field.Locations.FirstOrDefault(location => location.IsInSource),
                            field.Name,
                            declarationName,
                            "an explicitly selected field must be a mutable supported instance field"));
                    }

                    continue;
                }

                if (!accessible &&
                    RequiresGenericUnsafeAccessor(typeSymbol, field.Type))
                {
                    diagnostics.Add(Diagnostic.Create(
                        InvalidInheritedDescriptor,
                        field.Locations.FirstOrDefault(location => location.IsInSource),
                        declarationName,
                        field.Name,
                        "private generic UnsafeAccessor signatures are not supported on .NET 8"));
                    continue;
                }

                MemberModel? parsedField = BuildMemberModel(field.Name, field.Type, field, diagnostics);
                if (parsedField is not null)
                {
                    members.Add(BindSourceMember(
                        compilation,
                        serializerName,
                        field,
                        parsedField,
                        declarationOrdinal,
                        publishProviderApi));
                }

                continue;
            }

            if (member is IPropertySymbol property)
            {
                bool explicitField = HasForyFieldAttribute(property);
                if (!ValidateOrdinaryFieldOptions(property, diagnostics))
                {
                    continue;
                }

                if (property.ExplicitInterfaceImplementations.Length > 0)
                {
                    if (explicitField)
                    {
                        diagnostics.Add(Diagnostic.Create(
                            InvalidExternalMember,
                            property.Locations.FirstOrDefault(location => location.IsInSource),
                            property.Name,
                            declarationName,
                            "explicit interface implementations are not structural class members"));
                    }

                    continue;
                }

                if (property.IsIndexer ||
                    property.GetMethod is null ||
                    property.SetMethod is null ||
                    property.SetMethod.IsInitOnly ||
                    property.ReturnsByRef ||
                    property.ReturnsByRefReadonly ||
                    property.Type.TypeKind is TypeKind.Pointer or TypeKind.FunctionPointer ||
                    property.Type.IsRefLikeType)
                {
                    if (explicitField)
                    {
                        diagnostics.Add(Diagnostic.Create(
                            InvalidExternalMember,
                            property.Locations.FirstOrDefault(location => location.IsInSource),
                            property.Name,
                            declarationName,
                            "an explicitly selected property must have a supported getter and non-init setter"));
                    }

                    continue;
                }

                bool getterAccessible = compilation.IsSymbolAccessibleWithin(
                    property.GetMethod,
                    compilation.Assembly);
                bool setterAccessible = compilation.IsSymbolAccessibleWithin(
                    property.SetMethod,
                    compilation.Assembly);
                if (!explicitField && (!getterAccessible || !setterAccessible))
                {
                    continue;
                }

                if ((!getterAccessible || !setterAccessible) &&
                    RequiresGenericUnsafeAccessor(typeSymbol, property.Type))
                {
                    diagnostics.Add(Diagnostic.Create(
                        InvalidInheritedDescriptor,
                        property.Locations.FirstOrDefault(location => location.IsInSource),
                        declarationName,
                        property.Name,
                        "private generic UnsafeAccessor signatures are not supported on .NET 8"));
                    continue;
                }

                MemberModel? parsedProperty = BuildMemberModel(
                    property.Name,
                    property.Type,
                    property,
                    diagnostics);
                if (parsedProperty is not null)
                {
                    members.Add(BindSourceMember(
                        compilation,
                        serializerName,
                        property,
                        parsedProperty,
                        declarationOrdinal,
                        publishProviderApi));
                }
            }
        }

        ImmutableArray<MemberModel> ordered = members
            .OrderBy(m => m.DeclarationOrdinal)
            .ToImmutableArray();
        ValidatePublicProviderSurface(
            declarationName,
            serializerName,
            providerVisibility,
            ordered,
            declarationLocation,
            diagnostics);
        ImmutableArray<MemberModel> sorted = SortMembers(ordered);
        ImmutableArray<ShallowFieldModel> shallowFields = kind == DeclKind.Class
            ? BuildDeclaredShallowFields(compilation, typeSymbol, diagnostics)
            : ImmutableArray<ShallowFieldModel>.Empty;

        return new TypeModel(
            declarationName,
            declarationName,
            typeSymbol,
            serializerName,
            kind,
            evolving,
            declarationLocation,
            ordered,
            sorted,
            diagnostics.ToImmutableArray(),
            declaredMembers: ordered,
            shallowStorage: kind == DeclKind.Class
                ? new ShallowStorageModel(null, shallowFields)
                : null,
            isOrdinary: true,
            emitSerializerBody: !abstractClass,
            registerSerializer: !abstractClass,
            providerVisibility: providerVisibility);
    }

    private static TypeModel BuildExternalStructModel(
        Compilation compilation,
        INamedTypeSymbol declaration,
        ITypeSymbol targetSymbol,
        string declarationName,
        string serializerName,
        bool evolving,
        bool evolvingExplicit,
        bool baseOnly,
        Location? declarationLocation,
        Location? targetLocation)
    {
        List<Diagnostic> diagnostics = [];
        bool validDeclaration = ValidateExternalStructDeclaration(
            declaration,
            declarationName,
            declarationLocation,
            diagnostics);
        bool validTarget = ValidateExternalStructTarget(
            compilation,
            declaration,
            targetSymbol,
            targetLocation,
            baseOnly,
            diagnostics,
            out INamedTypeSymbol? target,
            out DeclKind targetKind);
        if (baseOnly && evolvingExplicit)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                declarationLocation,
                declarationName,
                "BaseOnly declarations cannot explicitly set Evolving"));
        }

        string targetTypeName = targetSymbol.ToDisplayString(FullNameFormat);
        string providerVisibility =
            target is null ? "internal" : ProviderVisibility(target);
        bool publishProviderApi = providerVisibility == "public";
        List<MemberModel> members = [];
        Dictionary<string, ShallowFieldModel> shallowFields = new(StringComparer.Ordinal);
        if (validDeclaration && validTarget)
        {
            IPropertySymbol[] schemaProperties = declaration.GetMembers()
                .OfType<IPropertySymbol>()
                .Where(property => !property.IsImplicitlyDeclared)
                .OrderBy(property => property.MetadataName, StringComparer.Ordinal)
                .ToArray();
            for (int declarationOrdinal = 0;
                 declarationOrdinal < schemaProperties.Length;
                 declarationOrdinal++)
            {
                IPropertySymbol schemaProperty = schemaProperties[declarationOrdinal];
                if (!TryParseExternalMapping(
                        target!,
                        schemaProperty,
                        targetTypeName,
                        diagnostics,
                        out ExternalMemberMapping? mapping))
                {
                    continue;
                }

                if (mapping!.Ignore)
                {
                    if (!TryResolveAccessibleExactTargetMember(
                            compilation,
                            mapping.DeclaringType!,
                            mapping.TargetMemberName,
                            ExternalTargetMemberKind.Field,
                            out ISymbol? visibleMember,
                            out string? reason))
                    {
                        diagnostics.Add(Diagnostic.Create(
                            InvalidExternalMember,
                            schemaProperty.Locations.FirstOrDefault(
                                location => location.IsInSource),
                            schemaProperty.Name,
                            targetTypeName,
                            reason));
                        continue;
                    }

                    IFieldSymbol? visibleField = visibleMember as IFieldSymbol;
                    if (visibleField is not null &&
                        (visibleField.IsStatic ||
                         visibleField.IsConst ||
                         !ExternalMemberTypesMatch(
                             schemaProperty.Type,
                             visibleField.Type)))
                    {
                        if (!ExternalMemberTypesMatch(
                                schemaProperty.Type,
                                visibleField.Type))
                        {
                            ReportExternalTypeMismatch(
                                schemaProperty,
                                visibleField.Type,
                                diagnostics);
                        }
                        else
                        {
                            diagnostics.Add(Diagnostic.Create(
                                InvalidExternalMember,
                                schemaProperty.Locations.FirstOrDefault(
                                    location => location.IsInSource),
                                schemaProperty.Name,
                                targetTypeName,
                                "a storage-only mapping must name an instance field"));
                        }

                        continue;
                    }

                    TryAddExternalShallowField(
                        compilation,
                        target!,
                        schemaProperty,
                        mapping,
                        shallowFields,
                        diagnostics,
                        visibleField);
                    continue;
                }

                if (!TryBindExternalMapping(
                        compilation,
                        target!,
                        schemaProperty,
                        mapping,
                        targetTypeName,
                        diagnostics,
                        out ISymbol? targetMember))
                {
                    continue;
                }

                int diagnosticCount = diagnostics.Count;
                MemberModel? member = BuildMemberModel(
                    schemaProperty.Name,
                    schemaProperty.Type,
                    schemaProperty,
                    diagnostics);
                if (member is not null)
                {
                    MemberModel boundMember = BindExternalMember(
                        compilation,
                        serializerName,
                        schemaProperty,
                        mapping,
                        targetMember,
                        member,
                        declarationOrdinal,
                        publishProviderApi);
                    members.Add(boundMember);
                    if (EffectiveExternalMemberKind(mapping, targetMember) ==
                        ExternalTargetMemberKind.Field)
                    {
                        TryAddExternalShallowField(
                            compilation,
                            target!,
                            schemaProperty,
                            mapping,
                            shallowFields,
                            diagnostics,
                            targetMember as IFieldSymbol);
                    }
                }
                else if (diagnostics.Count == diagnosticCount)
                {
                    diagnostics.Add(Diagnostic.Create(
                        InvalidExternalMember,
                        schemaProperty.Locations.FirstOrDefault(location => location.IsInSource),
                        schemaProperty.Name,
                        targetTypeName,
                        "the schema property type or descriptor is not supported"));
                }
            }

            foreach (IFieldSymbol targetField in PublicInstanceFields(target!))
            {
                string identity = ExternalFieldIdentity(targetField.ContainingType, targetField.MetadataName);
                if (shallowFields.ContainsKey(identity))
                {
                    continue;
                }

                if (TryBuildShallowField(
                        compilation,
                        target!,
                        targetField.Name,
                        targetField.Type,
                        targetField,
                        identity,
                        targetField.Locations.FirstOrDefault(location => location.IsInSource),
                        diagnostics,
                        out ShallowFieldModel? shallowField))
                {
                    shallowFields.Add(identity, shallowField!);
                }
            }
        }

        ImmutableArray<MemberModel> ordered = members
            .OrderBy(member => member.DeclarationOrdinal)
            .ToImmutableArray();
        ValidatePublicProviderSurface(
            targetTypeName,
            serializerName,
            providerVisibility,
            ordered,
            declarationLocation,
            diagnostics);
        return new TypeModel(
            declarationName,
            targetTypeName,
            targetSymbol,
            serializerName,
            targetKind,
            evolving,
            declarationLocation,
            ordered,
            SortMembers(ordered),
            diagnostics.ToImmutableArray(),
            declaredMembers: ordered,
            shallowStorage: targetKind == DeclKind.Class
                ? new ShallowStorageModel(
                    null,
                    shallowFields.Values
                        .OrderBy(field => field.Identity, StringComparer.Ordinal)
                        .ToImmutableArray())
                : null,
            isExternal: true,
            emitSerializerBody: !baseOnly,
            registerSerializer: !baseOnly,
            providerVisibility: providerVisibility);
    }

    private static void ValidatePublicProviderSurface(
        string targetName,
        string serializerName,
        string providerVisibility,
        ImmutableArray<MemberModel> members,
        Location? location,
        List<Diagnostic> diagnostics)
    {
        if (providerVisibility != "public")
        {
            return;
        }

        foreach (MemberModel member in members)
        {
            if (member.DeclaringType is not null &&
                IsPublicType(member.DeclaringType) &&
                member.MemberType is not null &&
                IsPublicSignatureType(member.MemberType) &&
                (member.SchemaDescriptorType is null ||
                 IsPublicSignatureType(member.SchemaDescriptorType)))
            {
                continue;
            }

            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                location,
                targetName,
                serializerName,
                $"wire member '{member.Name}' has a signature that cannot be published to derived assemblies"));
        }
    }

    private static TypeModel BuildExternalEnumModel(
        Compilation compilation,
        INamedTypeSymbol declaration,
        ITypeSymbol targetSymbol,
        string declarationName,
        string serializerName,
        Location? declarationLocation,
        Location? targetLocation)
    {
        List<Diagnostic> diagnostics = [];
        ValidateExternalEnumDeclaration(
            declaration,
            declarationName,
            declarationLocation,
            diagnostics);
        INamedTypeSymbol? target = ValidateExternalEnumTarget(
            compilation,
            declaration,
            targetSymbol,
            targetLocation,
            diagnostics);
        if (target is not null)
        {
            ValidateEnumValues(target, targetLocation, diagnostics);
        }

        return new TypeModel(
            declarationName,
            targetSymbol.ToDisplayString(FullNameFormat),
            targetSymbol,
            serializerName,
            DeclKind.Enum,
            true,
            declarationLocation,
            ImmutableArray<MemberModel>.Empty,
            ImmutableArray<MemberModel>.Empty,
            diagnostics.ToImmutableArray());
    }

    private static bool ValidateExternalStructDeclaration(
        INamedTypeSymbol declaration,
        string declarationName,
        Location? declarationLocation,
        List<Diagnostic> diagnostics)
    {
        int initialDiagnosticCount = diagnostics.Count;
        if (declaration.TypeKind != TypeKind.Class ||
            !declaration.IsAbstract ||
            declaration.IsStatic ||
            declaration.IsRecord)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                declarationLocation,
                declarationName,
                "ForyStruct with Target requires a non-record abstract class"));
        }

        if (HasGenericContext(declaration))
        {
            diagnostics.Add(Diagnostic.Create(
                GenericTypeNotSupported,
                declarationLocation,
                declarationName));
        }

        if (declaration.BaseType?.SpecialType != SpecialType.System_Object)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                declarationLocation,
                declarationName,
                "the declaration cannot have a base class other than object"));
        }

        foreach (ISymbol member in declaration.GetMembers())
        {
            if (member.IsImplicitlyDeclared ||
                member is IMethodSymbol { AssociatedSymbol: not null })
            {
                continue;
            }

            if (member is IPropertySymbol property &&
                property.IsAbstract &&
                !property.IsStatic &&
                !property.IsIndexer &&
                property.GetMethod is { IsAbstract: true } &&
                property.SetMethod is null)
            {
                continue;
            }

            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                member.Locations.FirstOrDefault(location => location.IsInSource) ?? declarationLocation,
                declarationName,
                $"member '{member.Name}' must be an abstract instance get-only schema property"));
        }

        return diagnostics.Count == initialDiagnosticCount;
    }

    private static bool ValidateExternalEnumDeclaration(
        INamedTypeSymbol declaration,
        string declarationName,
        Location? declarationLocation,
        List<Diagnostic> diagnostics)
    {
        int initialDiagnosticCount = diagnostics.Count;
        if (declaration.TypeKind != TypeKind.Class || !declaration.IsStatic)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                declarationLocation,
                declarationName,
                "ForyEnum with Target requires an empty static class"));
        }

        if (HasGenericContext(declaration))
        {
            diagnostics.Add(Diagnostic.Create(
                GenericTypeNotSupported,
                declarationLocation,
                declarationName));
        }

        foreach (ISymbol member in declaration.GetMembers())
        {
            if (member.IsImplicitlyDeclared ||
                member is IMethodSymbol { AssociatedSymbol: not null })
            {
                continue;
            }

            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                member.Locations.FirstOrDefault(location => location.IsInSource) ?? declarationLocation,
                declarationName,
                $"external enum declarations must be empty; found member '{member.Name}'"));
        }

        return diagnostics.Count == initialDiagnosticCount;
    }

    private static bool ValidateExternalStructTarget(
        Compilation compilation,
        INamedTypeSymbol declaration,
        ITypeSymbol targetSymbol,
        Location? targetLocation,
        bool baseOnly,
        List<Diagnostic> diagnostics,
        out INamedTypeSymbol? target,
        out DeclKind targetKind)
    {
        int initialDiagnosticCount = diagnostics.Count;
        target = targetSymbol as INamedTypeSymbol;
        targetKind = DeclKind.Unknown;
        string targetName = targetSymbol.ToDisplayString(FullNameFormat);
        if (target is null || target.TypeKind == TypeKind.Error)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "Target must resolve to one closed class or struct"));
            return false;
        }

        if (SymbolEqualityComparer.Default.Equals(declaration, target))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "the serializer declaration cannot target itself"));
        }

        if (ContainsOpenType(target))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "open generic targets are not supported"));
        }

        targetKind = target.TypeKind switch
        {
            TypeKind.Class => DeclKind.Class,
            TypeKind.Struct => DeclKind.Struct,
            _ => DeclKind.Unknown,
        };
        TypeClassification classification = ClassifyType(target);
        if (targetKind == DeclKind.Unknown ||
            target.IsStatic ||
            target.IsRefLikeType ||
            target.IsReadOnly ||
            classification.IsBuiltIn ||
            classification.IsCollection ||
            classification.IsMap ||
            IsRuntimeOwnedTarget(target) ||
            IsUnionType(target))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "Target must be a supported user class or struct"));
        }

        if (baseOnly && (targetKind != DeclKind.Class || target.IsSealed))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalDeclaration,
                targetLocation,
                declaration.ToDisplayString(FullNameFormat),
                "BaseOnly Target must be a non-sealed class"));
        }
        else if (!baseOnly && target.IsAbstract)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "a standalone external Target must be concrete"));
        }

        if (GetForyAttributeKind(target) == ForyAttributeKind.Struct)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "the target already owns a direct ForyStruct serializer"));
        }

        if (!compilation.IsSymbolAccessibleWithin(target, compilation.Assembly))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "the target is not accessible from generated code"));
        }

        if (RequiresExternAlias(target, compilation))
        {
            diagnostics.Add(Diagnostic.Create(
                UnsupportedExternalAlias,
                targetLocation,
                targetName));
        }

        IMethodSymbol? constructor = baseOnly
            ? null
            : FindAccessibleParameterlessCtor(target, compilation);
        if (!baseOnly && targetKind == DeclKind.Class && constructor is null)
        {
            diagnostics.Add(Diagnostic.Create(
                MissingCtor,
                targetLocation,
                targetName));
        }

        if (!baseOnly &&
            HasRequiredMembers(target) &&
            (constructor is null || !SetsRequiredMembers(constructor)))
        {
            diagnostics.Add(Diagnostic.Create(
                MissingCtor,
                targetLocation,
                targetName));
        }

        return diagnostics.Count == initialDiagnosticCount;
    }

    private static INamedTypeSymbol? ValidateExternalEnumTarget(
        Compilation compilation,
        INamedTypeSymbol declaration,
        ITypeSymbol targetSymbol,
        Location? targetLocation,
        List<Diagnostic> diagnostics)
    {
        string targetName = targetSymbol.ToDisplayString(FullNameFormat);
        if (targetSymbol is not INamedTypeSymbol target ||
            target.TypeKind == TypeKind.Error ||
            target.TypeKind != TypeKind.Enum)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "ForyEnum Target must resolve to one closed enum"));
            return null;
        }

        if (SymbolEqualityComparer.Default.Equals(declaration, target))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "the serializer declaration cannot target itself"));
        }

        if (ContainsOpenType(target))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "open generic targets are not supported"));
        }

        if (GetForyAttributeKind(target) == ForyAttributeKind.Enum)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "the target already owns a direct ForyEnum serializer"));
        }

        if (!compilation.IsSymbolAccessibleWithin(target, compilation.Assembly))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalTarget,
                targetLocation,
                targetName,
                "the target is not accessible from generated code"));
        }

        if (RequiresExternAlias(target, compilation))
        {
            diagnostics.Add(Diagnostic.Create(
                UnsupportedExternalAlias,
                targetLocation,
                targetName));
        }

        return target;
    }

    private static bool TryParseExternalMapping(
        INamedTypeSymbol target,
        IPropertySymbol schemaProperty,
        string targetTypeName,
        List<Diagnostic> diagnostics,
        out ExternalMemberMapping? mapping)
    {
        AttributeData? attribute = GetEffectiveForyFieldAttribute(schemaProperty);
        bool ignore = attribute is not null &&
                      TryGetIgnoredField(schemaProperty, out _);
        INamedTypeSymbol? declaringType = null;
        string targetMemberName = schemaProperty.Name;
        ExternalTargetMemberKind memberKind = ExternalTargetMemberKind.Auto;
        if (attribute is not null)
        {
            foreach (KeyValuePair<string, TypedConstant> argument in attribute.NamedArguments)
            {
                switch (argument.Key)
                {
                    case "TargetDeclaringType":
                        declaringType = argument.Value.Value as INamedTypeSymbol;
                        break;
                    case "TargetMemberName":
                        if (argument.Value.Value is string configuredName)
                        {
                            targetMemberName = configuredName;
                        }

                        break;
                    case "TargetMemberKind":
                        if (argument.Value.Value is int configuredKind &&
                            Enum.IsDefined(typeof(ExternalTargetMemberKind), configuredKind))
                        {
                            memberKind = (ExternalTargetMemberKind)configuredKind;
                        }
                        else
                        {
                            diagnostics.Add(Diagnostic.Create(
                                InvalidExternalMember,
                                schemaProperty.Locations.FirstOrDefault(location => location.IsInSource),
                                schemaProperty.Name,
                                targetTypeName,
                                "TargetMemberKind is invalid"));
                            mapping = null;
                            return false;
                        }

                        break;
                }
            }
        }

        Location? location = schemaProperty.Locations.FirstOrDefault(sourceLocation => sourceLocation.IsInSource);
        if (string.IsNullOrEmpty(targetMemberName))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                location,
                schemaProperty.Name,
                targetTypeName,
                "TargetMemberName cannot be empty"));
            mapping = null;
            return false;
        }

        if (memberKind == ExternalTargetMemberKind.Auto && declaringType is not null)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                location,
                schemaProperty.Name,
                targetTypeName,
                "TargetDeclaringType requires TargetMemberKind Field or Property"));
            mapping = null;
            return false;
        }

        if (memberKind != ExternalTargetMemberKind.Auto && declaringType is null)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                location,
                schemaProperty.Name,
                targetTypeName,
                "an exact TargetMemberKind requires TargetDeclaringType"));
            mapping = null;
            return false;
        }

        if (declaringType is not null && !IsTypeInHierarchy(target, declaringType))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                location,
                schemaProperty.Name,
                targetTypeName,
                "TargetDeclaringType must be the Target or one of its base classes"));
            mapping = null;
            return false;
        }

        if (ignore)
        {
            if (IgnoredFieldHasWireOptions(attribute!))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidIgnoredField,
                    location,
                    schemaProperty.Name,
                    "Id and Type cannot be combined with Ignore"));
                mapping = null;
                return false;
            }

            if (memberKind != ExternalTargetMemberKind.Field || declaringType is null)
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidIgnoredField,
                    location,
                    schemaProperty.Name,
                    "Ignore requires exact TargetDeclaringType and TargetMemberKind.Field"));
                mapping = null;
                return false;
            }
        }

        mapping = new ExternalMemberMapping(
            ignore,
            declaringType,
            targetMemberName,
            memberKind);
        return true;
    }

    private static bool TryBindExternalMapping(
        Compilation compilation,
        INamedTypeSymbol target,
        IPropertySymbol schemaProperty,
        ExternalMemberMapping mapping,
        string targetTypeName,
        List<Diagnostic> diagnostics,
        out ISymbol? targetMember)
    {
        targetMember = null;
        Location? location = schemaProperty.Locations.FirstOrDefault(sourceLocation => sourceLocation.IsInSource);
        if (schemaProperty.Type.TypeKind is TypeKind.Pointer or TypeKind.FunctionPointer ||
            schemaProperty.Type.IsRefLikeType)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                location,
                schemaProperty.Name,
                targetTypeName,
                "the member type cannot be used by Serializer<T>"));
            return false;
        }

        if (mapping.MemberKind == ExternalTargetMemberKind.Auto)
        {
            if (!TryFindTargetMember(target, mapping.TargetMemberName, out targetMember, out string reason))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidExternalMember,
                    location,
                    schemaProperty.Name,
                    targetTypeName,
                    reason));
                return false;
            }

        }
        else
        {
            if (!TryResolveAccessibleExactTargetMember(
                compilation,
                mapping.DeclaringType!,
                mapping.TargetMemberName,
                mapping.MemberKind,
                out targetMember,
                out string? reason))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidExternalMember,
                    location,
                    schemaProperty.Name,
                    targetTypeName,
                    reason));
                return false;
            }
        }

        if (targetMember is IFieldSymbol field)
        {
            if (field.IsStatic ||
                field.IsConst ||
                field.IsReadOnly ||
                field.IsFixedSizeBuffer ||
                !compilation.IsSymbolAccessibleWithin(field, compilation.Assembly))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidExternalMember,
                    location,
                    schemaProperty.Name,
                    targetTypeName,
                    "the target field must be an accessible mutable instance field"));
                return false;
            }

            if (!ExternalMemberTypesMatch(schemaProperty.Type, field.Type))
            {
                ReportExternalTypeMismatch(schemaProperty, field.Type, diagnostics);
                return false;
            }
        }
        else if (targetMember is IPropertySymbol property)
        {
            if (property.IsStatic ||
                property.IsIndexer ||
                property.GetMethod is null ||
                property.SetMethod is null ||
                property.SetMethod.IsInitOnly ||
                !compilation.IsSymbolAccessibleWithin(property.GetMethod, compilation.Assembly) ||
                !compilation.IsSymbolAccessibleWithin(property.SetMethod, compilation.Assembly))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidExternalMember,
                    location,
                    schemaProperty.Name,
                    targetTypeName,
                    "the target property must have accessible get and non-init set accessors"));
                return false;
            }

            if (!ExternalMemberTypesMatch(schemaProperty.Type, property.Type))
            {
                ReportExternalTypeMismatch(schemaProperty, property.Type, diagnostics);
                return false;
            }
        }
        else if (mapping.MemberKind == ExternalTargetMemberKind.Auto)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                location,
                schemaProperty.Name,
                targetTypeName,
                "the matching target symbol is not a field or property"));
            return false;
        }
        else if (RequiresGenericUnsafeAccessor(mapping.DeclaringType!, schemaProperty.Type))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidInheritedDescriptor,
                location,
                targetTypeName,
                mapping.TargetMemberName,
                "private generic UnsafeAccessor signatures are not supported on .NET 8"));
            return false;
        }

        if (RequiresExternAlias(schemaProperty.Type, compilation))
        {
            diagnostics.Add(Diagnostic.Create(
                UnsupportedExternalAlias,
                location,
                schemaProperty.Type.ToDisplayString(FullNameFormat)));
            return false;
        }

        return true;
    }

    private static bool TryResolveAccessibleExactTargetMember(
        Compilation compilation,
        INamedTypeSymbol declaringType,
        string memberName,
        ExternalTargetMemberKind memberKind,
        out ISymbol? targetMember,
        out string? reason)
    {
        ISymbol[] accessibleMembers = declaringType.GetMembers(memberName)
            .Where(member => compilation.IsSymbolAccessibleWithin(member, compilation.Assembly))
            .ToArray();
        if (accessibleMembers.Length == 0)
        {
            targetMember = null;
            reason = null;
            return true;
        }

        ISymbol[] candidates = accessibleMembers
            .Where(member =>
                memberKind == ExternalTargetMemberKind.Field
                    ? member is IFieldSymbol
                    : member is IPropertySymbol)
            .ToArray();
        if (accessibleMembers.Length != 1 || candidates.Length != 1)
        {
            targetMember = null;
            reason =
                $"the accessible target member does not match exact {memberKind.ToString().ToLowerInvariant()} identity";
            return false;
        }

        targetMember = candidates[0];
        reason = null;
        return true;
    }

    private static void ReportExternalTypeMismatch(
        IPropertySymbol schemaProperty,
        ITypeSymbol targetMemberType,
        List<Diagnostic> diagnostics)
    {
        diagnostics.Add(Diagnostic.Create(
            ExternalMemberTypeMismatch,
            schemaProperty.Locations.FirstOrDefault(location => location.IsInSource),
            schemaProperty.Name,
            schemaProperty.Type.ToDisplayString(FullNameFormat),
            targetMemberType.ToDisplayString(FullNameFormat)));
    }

    private static MemberModel BindExternalMember(
        Compilation compilation,
        string serializerName,
        IPropertySymbol schemaProperty,
        ExternalMemberMapping mapping,
        ISymbol? targetMember,
        MemberModel member,
        int declarationOrdinal,
        bool publishProviderApi)
    {
        INamedTypeSymbol declaringType = targetMember?.ContainingType ?? mapping.DeclaringType!;
        string providerName = $"global::Apache.Fory.Generated.{serializerName}";
        string? fieldAccessorName = null;
        string? getterAccessorName = null;
        string? setterAccessorName = null;
        string? publishedFieldAccessorName = null;
        string? publishedGetterAccessorName = null;
        string? publishedSetterAccessorName = null;
        WireMemberKind memberKind;
        string? slotKey = null;
        ExternalTargetMemberKind effectiveKind =
            EffectiveExternalMemberKind(mapping, targetMember);
        if (effectiveKind == ExternalTargetMemberKind.Field)
        {
            memberKind = WireMemberKind.Field;
            if (targetMember is null)
            {
                fieldAccessorName = $"F{declarationOrdinal}";
            }

            publishedFieldAccessorName = fieldAccessorName ??
                (publishProviderApi &&
                 targetMember?.DeclaredAccessibility != Accessibility.Public
                    ? $"F{declarationOrdinal}"
                    : null);
        }
        else
        {
            memberKind = WireMemberKind.Property;
            if (targetMember is IPropertySymbol targetProperty)
            {
                slotKey = BuildPropertySlotKey(targetProperty);
            }
            else
            {
                slotKey =
                    $"{BuildRuntimeTypeKey(declaringType)}|P|{mapping.TargetMemberName}";
                getterAccessorName = $"G{declarationOrdinal}";
                setterAccessorName = $"S{declarationOrdinal}";
            }

            if (targetMember is not IPropertySymbol { IsAbstract: true })
            {
                IPropertySymbol? property = targetMember as IPropertySymbol;
                publishedGetterAccessorName = getterAccessorName ??
                    (publishProviderApi &&
                     property?.GetMethod?.DeclaredAccessibility != Accessibility.Public
                        ? $"G{declarationOrdinal}"
                        : null);
                publishedSetterAccessorName = setterAccessorName ??
                    (publishProviderApi &&
                     property?.SetMethod?.DeclaredAccessibility != Accessibility.Public
                        ? $"S{declarationOrdinal}"
                        : null);
            }
        }

        return member.WithDeclaration(
            schemaProperty.Type,
            declaringType,
            mapping.TargetMemberName,
            memberKind,
            slotKey,
            providerName,
            fieldAccessorName,
            getterAccessorName,
            setterAccessorName,
            member.SchemaDescriptorType,
            declarationOrdinal,
            BuildNullableShape(schemaProperty.Type))
            .WithPublishedAccessors(
                publishedFieldAccessorName,
                publishedGetterAccessorName,
                publishedSetterAccessorName);
    }

    private static ExternalTargetMemberKind EffectiveExternalMemberKind(
        ExternalMemberMapping mapping,
        ISymbol? targetMember)
    {
        if (mapping.MemberKind != ExternalTargetMemberKind.Auto)
        {
            return mapping.MemberKind;
        }

        return targetMember is IFieldSymbol
            ? ExternalTargetMemberKind.Field
            : ExternalTargetMemberKind.Property;
    }

    private static bool TryAddExternalShallowField(
        Compilation compilation,
        INamedTypeSymbol target,
        IPropertySymbol schemaProperty,
        ExternalMemberMapping mapping,
        Dictionary<string, ShallowFieldModel> shallowFields,
        List<Diagnostic> diagnostics,
        IFieldSymbol? targetField = null)
    {
        INamedTypeSymbol declaringType = targetField?.ContainingType ?? mapping.DeclaringType!;
        string fieldName = targetField?.MetadataName ?? mapping.TargetMemberName;
        string identity = ExternalFieldIdentity(declaringType, fieldName);
        if (shallowFields.ContainsKey(identity))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidExternalMember,
                schemaProperty.Locations.FirstOrDefault(location => location.IsInSource),
                schemaProperty.Name,
                target.ToDisplayString(FullNameFormat),
                $"physical field '{declaringType.ToDisplayString(FullNameFormat)}.{fieldName}' is mapped more than once"));
            return false;
        }

        ITypeSymbol fieldType = targetField?.Type ?? schemaProperty.Type;
        if (!TryBuildShallowField(
                compilation,
                target,
                fieldName,
                fieldType,
                targetField,
                identity,
                schemaProperty.Locations.FirstOrDefault(location => location.IsInSource),
                diagnostics,
                out ShallowFieldModel? shallowField))
        {
            return false;
        }

        shallowFields.Add(identity, shallowField!);
        return true;
    }

    private static string ExternalFieldIdentity(INamedTypeSymbol declaringType, string fieldName)
    {
        return $"{BuildRuntimeTypeKey(declaringType)}|F|{fieldName}";
    }

    private static bool IsTypeInHierarchy(INamedTypeSymbol target, INamedTypeSymbol candidate)
    {
        for (INamedTypeSymbol? current = target; current is not null; current = current.BaseType)
        {
            if (RuntimeTypeComparer.Instance.Equals(current, candidate))
            {
                return true;
            }
        }

        return false;
    }

    private static bool RequiresGenericUnsafeAccessor(
        INamedTypeSymbol declaringType,
        ITypeSymbol memberType)
    {
        return ContainsGenericSignatureType(declaringType) ||
               ContainsGenericSignatureType(memberType);
    }

    private static bool ContainsGenericSignatureType(ITypeSymbol type)
    {
        switch (type)
        {
            case IArrayTypeSymbol array:
                return ContainsGenericSignatureType(array.ElementType);
            case IPointerTypeSymbol pointer:
                return ContainsGenericSignatureType(pointer.PointedAtType);
            case INamedTypeSymbol named:
                return named.OriginalDefinition.Arity > 0 ||
                       named.ContainingType is not null &&
                       ContainsGenericSignatureType(named.ContainingType) ||
                       named.TypeArguments.Any(ContainsGenericSignatureType);
            default:
                return false;
        }
    }

    private static IEnumerable<IFieldSymbol> PublicInstanceFields(INamedTypeSymbol target)
    {
        for (INamedTypeSymbol? current = target; current is not null; current = current.BaseType)
        {
            foreach (IFieldSymbol field in current.GetMembers()
                         .OfType<IFieldSymbol>()
                         .Where(field =>
                             !field.IsImplicitlyDeclared &&
                             !field.IsStatic &&
                             !field.IsConst &&
                             field.DeclaredAccessibility == Accessibility.Public)
                         .OrderBy(field => field.MetadataName, StringComparer.Ordinal))
            {
                yield return field;
            }
        }
    }

    private static bool TryFindTargetMember(
        INamedTypeSymbol target,
        string name,
        out ISymbol? targetMember,
        out string reason)
    {
        for (INamedTypeSymbol? current = target; current is not null; current = current.BaseType)
        {
            ImmutableArray<ISymbol> namedMembers = current.GetMembers(name);
            if (namedMembers.IsEmpty)
            {
                continue;
            }

            if (namedMembers.Length != 1)
            {
                targetMember = null;
                reason = "the target member name is ambiguous";
                return false;
            }

            targetMember = namedMembers[0];
            reason = string.Empty;
            return true;
        }

        targetMember = null;
        reason = "no target member has the same case-sensitive name";
        return false;
    }

    private static bool ExternalMemberTypesMatch(ITypeSymbol declarationType, ITypeSymbol targetType)
    {
        if ((declarationType.TypeKind == TypeKind.Dynamic) != (targetType.TypeKind == TypeKind.Dynamic))
        {
            return false;
        }

        if (targetType.NullableAnnotation != NullableAnnotation.None &&
            declarationType.NullableAnnotation != targetType.NullableAnnotation)
        {
            return false;
        }

        if (declarationType is IArrayTypeSymbol declarationArray &&
            targetType is IArrayTypeSymbol targetArray)
        {
            return declarationArray.Rank == targetArray.Rank &&
                   declarationArray.IsSZArray == targetArray.IsSZArray &&
                   ExternalMemberTypesMatch(declarationArray.ElementType, targetArray.ElementType);
        }

        if (declarationType is IPointerTypeSymbol declarationPointer &&
            targetType is IPointerTypeSymbol targetPointer)
        {
            return ExternalMemberTypesMatch(declarationPointer.PointedAtType, targetPointer.PointedAtType);
        }

        if (declarationType is INamedTypeSymbol declarationNamed &&
            targetType is INamedTypeSymbol targetNamed)
        {
            if (!SymbolEqualityComparer.Default.Equals(
                    declarationNamed.OriginalDefinition,
                    targetNamed.OriginalDefinition) ||
                declarationNamed.TypeArguments.Length != targetNamed.TypeArguments.Length)
            {
                return false;
            }

            if ((declarationNamed.ContainingType is null) != (targetNamed.ContainingType is null) ||
                declarationNamed.ContainingType is not null &&
                !ExternalMemberTypesMatch(declarationNamed.ContainingType, targetNamed.ContainingType!))
            {
                return false;
            }

            for (int i = 0; i < declarationNamed.TypeArguments.Length; i++)
            {
                if (!ExternalMemberTypesMatch(
                        declarationNamed.TypeArguments[i],
                        targetNamed.TypeArguments[i]))
                {
                    return false;
                }
            }

            return true;
        }

        return SymbolEqualityComparer.Default.Equals(declarationType, targetType);
    }

    private static bool HasGenericContext(INamedTypeSymbol type)
    {
        for (INamedTypeSymbol? current = type; current is not null; current = current.ContainingType)
        {
            if (current.TypeParameters.Length != 0)
            {
                return true;
            }
        }

        return false;
    }

    private static bool ContainsOpenType(ITypeSymbol type)
    {
        if (type.TypeKind == TypeKind.TypeParameter)
        {
            return true;
        }

        if (type is IArrayTypeSymbol array)
        {
            return ContainsOpenType(array.ElementType);
        }

        if (type is IPointerTypeSymbol pointer)
        {
            return ContainsOpenType(pointer.PointedAtType);
        }

        if (type is not INamedTypeSymbol named)
        {
            return false;
        }

        if (named.IsUnboundGenericType ||
            named.ContainingType is not null && ContainsOpenType(named.ContainingType))
        {
            return true;
        }

        foreach (ITypeSymbol argument in named.TypeArguments)
        {
            if (ContainsOpenType(argument))
            {
                return true;
            }
        }

        return false;
    }

    private static bool IsRuntimeOwnedTarget(INamedTypeSymbol type)
    {
        if (type.SpecialType == SpecialType.System_Object)
        {
            return true;
        }

        string containingNamespace = type.ContainingNamespace.ToDisplayString();
        if (string.Equals(containingNamespace, "System", StringComparison.Ordinal))
        {
            return type.Name is
                "ArraySegment" or
                "Memory" or
                "Nullable" or
                "ReadOnlyMemory" or
                "Tuple" or
                "ValueTuple";
        }

        return string.Equals(
                   containingNamespace,
                   "System.Collections.Generic",
                   StringComparison.Ordinal) &&
               string.Equals(type.Name, "KeyValuePair", StringComparison.Ordinal);
    }

    private static bool RequiresExternAlias(ITypeSymbol type, Compilation compilation)
    {
        if (type is IArrayTypeSymbol array)
        {
            return RequiresExternAlias(array.ElementType, compilation);
        }

        if (type is IPointerTypeSymbol pointer)
        {
            return RequiresExternAlias(pointer.PointedAtType, compilation);
        }

        if (type is not INamedTypeSymbol named)
        {
            return false;
        }

        if (AssemblyRequiresExternAlias(named.ContainingAssembly, compilation))
        {
            return true;
        }

        if (named.ContainingType is not null &&
            RequiresExternAlias(named.ContainingType, compilation))
        {
            return true;
        }

        foreach (ITypeSymbol argument in named.TypeArguments)
        {
            if (RequiresExternAlias(argument, compilation))
            {
                return true;
            }
        }

        return false;
    }

    private static bool AssemblyRequiresExternAlias(IAssemblySymbol assembly, Compilation compilation)
    {
        if (SymbolEqualityComparer.Default.Equals(assembly, compilation.Assembly))
        {
            return false;
        }

        bool foundAssembly = false;
        foreach (MetadataReference reference in compilation.References)
        {
            if (!SymbolEqualityComparer.Default.Equals(
                    compilation.GetAssemblyOrModuleSymbol(reference),
                    assembly))
            {
                continue;
            }

            foundAssembly = true;
            if (reference.Properties.Aliases.IsDefaultOrEmpty)
            {
                return false;
            }

            foreach (string alias in reference.Properties.Aliases)
            {
                if (string.Equals(alias, "global", StringComparison.Ordinal))
                {
                    return false;
                }
            }
        }

        return foundAssembly;
    }

    private static IMethodSymbol? FindAccessibleParameterlessCtor(
        INamedTypeSymbol type,
        Compilation compilation)
    {
        foreach (IMethodSymbol constructor in type.InstanceConstructors)
        {
            if (constructor.Parameters.Length == 0 &&
                compilation.IsSymbolAccessibleWithin(constructor, compilation.Assembly))
            {
                return constructor;
            }
        }

        return null;
    }

    private static bool HasRequiredMembers(INamedTypeSymbol type)
    {
        for (INamedTypeSymbol? current = type; current is not null; current = current.BaseType)
        {
            foreach (ISymbol member in current.GetMembers())
            {
                if (member is IFieldSymbol { IsRequired: true } or
                    IPropertySymbol { IsRequired: true })
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static bool SetsRequiredMembers(IMethodSymbol constructor)
    {
        foreach (AttributeData attribute in constructor.GetAttributes())
        {
            if (string.Equals(
                    attribute.AttributeClass?.ToDisplayString(),
                    "System.Diagnostics.CodeAnalysis.SetsRequiredMembersAttribute",
                    StringComparison.Ordinal))
            {
                return true;
            }
        }

        return false;
    }

    private static void ValidateEnumValues(
        INamedTypeSymbol enumType,
        Location? fallbackLocation,
        List<Diagnostic> diagnostics)
    {
        foreach (IFieldSymbol field in enumType.GetMembers().OfType<IFieldSymbol>())
        {
            if (!field.HasConstantValue ||
                IsSupportedEnumValue(field.ConstantValue))
            {
                continue;
            }

            diagnostics.Add(Diagnostic.Create(
                EnumValueOutOfRange,
                field.Locations.FirstOrDefault(location => location.IsInSource) ?? fallbackLocation,
                $"{enumType.ToDisplayString(FullNameFormat)}.{field.Name}"));
        }
    }

    private static bool IsSupportedEnumValue(object? value)
    {
        return value switch
        {
            byte => true,
            ushort => true,
            uint => true,
            ulong unsignedValue => unsignedValue <= uint.MaxValue,
            sbyte signedValue => signedValue >= 0,
            short signedValue => signedValue >= 0,
            int signedValue => signedValue >= 0,
            long signedValue => signedValue is >= 0 and <= uint.MaxValue,
            _ => false,
        };
    }

    private static bool GetEvolving(AttributeData attribute)
    {
        foreach (KeyValuePair<string, TypedConstant> namedArgument in attribute.NamedArguments)
        {
            if (string.Equals(namedArgument.Key, "Evolving", StringComparison.Ordinal) &&
                namedArgument.Value.Value is bool evolving)
            {
                return evolving;
            }
        }

        return true;
    }

    private static bool GetBaseOnly(AttributeData attribute)
    {
        foreach (KeyValuePair<string, TypedConstant> namedArgument in attribute.NamedArguments)
        {
            if (string.Equals(namedArgument.Key, "BaseOnly", StringComparison.Ordinal) &&
                namedArgument.Value.Value is bool baseOnly)
            {
                return baseOnly;
            }
        }

        return false;
    }

    private static bool HasNamedArgument(AttributeData attribute, string name)
    {
        return attribute.NamedArguments.Any(argument =>
            string.Equals(argument.Key, name, StringComparison.Ordinal));
    }

    private static Location? GetNamedArgumentLocation(
        AttributeData attribute,
        string argumentName,
        CancellationToken cancellationToken)
    {
        if (attribute.ApplicationSyntaxReference?.GetSyntax(cancellationToken) is not AttributeSyntax attributeSyntax)
        {
            return null;
        }

        foreach (AttributeArgumentSyntax argument in attributeSyntax.ArgumentList?.Arguments ??
                                                            default(SeparatedSyntaxList<AttributeArgumentSyntax>))
        {
            if (string.Equals(argument.NameEquals?.Name.Identifier.ValueText, argumentName, StringComparison.Ordinal))
            {
                return argument.Expression.GetLocation();
            }
        }

        return attributeSyntax.GetLocation();
    }

    private static ImmutableArray<UnionCaseModel> BuildUnionCases(
        INamedTypeSymbol unionType,
        List<Diagnostic> diagnostics)
    {
        List<UnionCaseModel> cases = [];
        HashSet<int> caseIds = [];
        foreach (INamedTypeSymbol caseType in unionType.GetTypeMembers())
        {
            bool isUnknown = HasForyUnknownCase(caseType);
            if (!TryGetForyCase(caseType, diagnostics, out int caseId, out SchemaTypeModel? schemaType))
            {
                if (isUnknown)
                {
                    string unknownCaseTypeName = caseType.ToDisplayString(FullNameFormat);
                    if (!SymbolEqualityComparer.Default.Equals(caseType.BaseType, unionType))
                    {
                        diagnostics.Add(Diagnostic.Create(
                            InvalidUnionCase,
                            caseType.Locations.FirstOrDefault(),
                            unknownCaseTypeName,
                            "unknown case type must directly derive from the annotated union root"));
                        continue;
                    }

                    if (!string.Equals(caseType.Name, "Unknown", StringComparison.Ordinal) ||
                        !HasUnknownCaseValueProperty(caseType))
                    {
                        diagnostics.Add(Diagnostic.Create(
                            InvalidUnionCase,
                            caseType.Locations.FirstOrDefault(),
                            unknownCaseTypeName,
                            "unknown case must be named Unknown and expose Value:UnknownCase"));
                        continue;
                    }

                    cases.Add(new UnionCaseModel(null, unknownCaseTypeName, isUnknown: true, valueMember: null));
                }

                continue;
            }

            if (isUnknown)
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidUnionCase,
                    caseType.Locations.FirstOrDefault(),
                    caseType.ToDisplayString(FullNameFormat),
                    "unknown case must use [ForyUnknownCase] without [ForyCase]"));
                continue;
            }

            if (!caseIds.Add(caseId))
            {
                diagnostics.Add(Diagnostic.Create(
                    DuplicateUnionCaseId,
                    caseType.Locations.FirstOrDefault(),
                    caseId,
                    unionType.ToDisplayString(FullNameFormat)));
                continue;
            }

            if (!SymbolEqualityComparer.Default.Equals(caseType.BaseType, unionType))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidUnionCase,
                    caseType.Locations.FirstOrDefault(),
                    caseType.ToDisplayString(FullNameFormat),
                    "case type must directly derive from the annotated union root"));
                continue;
            }

            string caseTypeName = caseType.ToDisplayString(FullNameFormat);
            IPropertySymbol? valueProperty = FindProperty(caseType, "Value");
            if (valueProperty is null)
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidUnionCase,
                    caseType.Locations.FirstOrDefault(),
                    caseTypeName,
                    "known cases must expose a Value property"));
                continue;
            }

            MemberModel? valueMember = BuildMemberModel(
                valueProperty.Name,
                valueProperty.Type,
                valueProperty,
                diagnostics,
                schemaType,
                parseFieldAttribute: false);
            if (valueMember is null)
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidUnionCase,
                    valueProperty.Locations.FirstOrDefault(),
                    caseTypeName,
                    "case Value type is not supported"));
                continue;
            }

            cases.Add(new UnionCaseModel(caseId, caseTypeName, isUnknown: false, valueMember));
        }

        if (cases.Count(c => c.IsUnknown) > 1)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidUnionCase,
                unionType.Locations.FirstOrDefault(),
                unionType.ToDisplayString(FullNameFormat),
                "union must declare exactly one [ForyUnknownCase] Unknown"));
        }
        else if (!cases.Any(c => c.IsUnknown))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidUnionCase,
                unionType.Locations.FirstOrDefault(),
                unionType.ToDisplayString(FullNameFormat),
                "union must declare [ForyUnknownCase] Unknown"));
        }
        else if (!cases.Any(c => !c.IsUnknown))
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidUnionCase,
                unionType.Locations.FirstOrDefault(),
                unionType.ToDisplayString(FullNameFormat),
                "union must declare at least one non-Unknown case; Unknown is a forward-compatibility carrier and cannot be the default"));
        }

        return cases
            .OrderBy(c => c.CaseId ?? -1)
            .ToImmutableArray();
    }

    private static IEnumerable<UnionCaseModel> KnownUnionCases(TypeModel model)
    {
        return model.UnionCases
            .Where(c => !c.IsUnknown)
            .OrderBy(c => c.KnownCaseId);
    }

    private static bool TryGetForyCase(
        INamedTypeSymbol caseType,
        List<Diagnostic> diagnostics,
        out int caseId,
        out SchemaTypeModel? schemaType)
    {
        caseId = default;
        schemaType = null;
        foreach (AttributeData attribute in caseType.GetAttributes())
        {
            string? attrName = attribute.AttributeClass?.ToDisplayString();
            if (!string.Equals(attrName, "Apache.Fory.ForyCaseAttribute", StringComparison.Ordinal))
            {
                continue;
            }

            if (attribute.ConstructorArguments.Length != 1 ||
                !TryGetUnionCaseId(attribute.ConstructorArguments[0], out caseId))
            {
                diagnostics.Add(Diagnostic.Create(
                    InvalidUnionCase,
                    caseType.Locations.FirstOrDefault(),
                    caseType.ToDisplayString(FullNameFormat),
                    "case id must be a non-negative int"));
                return true;
            }

            foreach (KeyValuePair<string, TypedConstant> namedArg in attribute.NamedArguments)
            {
                if (!string.Equals(namedArg.Key, "Type", StringComparison.Ordinal))
                {
                    continue;
                }

                if (namedArg.Value.Value is not ITypeSymbol schemaSymbol ||
                    TryParseSchemaType(schemaSymbol) is not SchemaTypeModel parsedSchema)
                {
                    diagnostics.Add(Diagnostic.Create(
                        InvalidUnionCase,
                        caseType.Locations.FirstOrDefault(),
                        caseType.ToDisplayString(FullNameFormat),
                        "ForyCase.Type must be an Apache.Fory.Schema.Types descriptor"));
                    continue;
                }

                schemaType = parsedSchema;
            }

            return true;
        }

        return false;
    }

    private static bool TryGetUnionCaseId(TypedConstant value, out int caseId)
    {
        caseId = default;
        if (value.Value is int id && id >= 0)
        {
            caseId = id;
            return true;
        }

        return false;
    }

    private static bool HasForyUnknownCase(INamedTypeSymbol caseType)
    {
        foreach (AttributeData attribute in caseType.GetAttributes())
        {
            string? attrName = attribute.AttributeClass?.ToDisplayString();
            if (string.Equals(attrName, "Apache.Fory.ForyUnknownCaseAttribute", StringComparison.Ordinal))
            {
                return true;
            }
        }

        return false;
    }

    private static IPropertySymbol? FindProperty(INamedTypeSymbol type, string name)
    {
        foreach (ISymbol member in type.GetMembers(name))
        {
            if (member is IPropertySymbol property && !property.IsStatic)
            {
                return property;
            }
        }

        return null;
    }

    private static bool HasUnknownCaseValueProperty(INamedTypeSymbol type)
    {
        IPropertySymbol? property = FindProperty(type, "Value");
        return property is not null &&
               string.Equals(
                   property.Type.ToDisplayString(FullNameFormat),
                   "global::Apache.Fory.UnknownCase",
                   StringComparison.Ordinal);
    }

    private static AttributeData? GetForyAttribute(
        INamedTypeSymbol typeSymbol,
        out ForyAttributeKind attributeKind)
    {
        return GetForyAttribute(typeSymbol, out attributeKind, out _);
    }

    private static AttributeData? GetForyAttribute(
        INamedTypeSymbol typeSymbol,
        out ForyAttributeKind attributeKind,
        out bool hasConflict)
    {
        AttributeData? result = null;
        attributeKind = ForyAttributeKind.None;
        hasConflict = false;
        foreach (AttributeData attribute in typeSymbol.GetAttributes())
        {
            string? attrName = attribute.AttributeClass?.ToDisplayString();
            ForyAttributeKind currentKind;
            if (string.Equals(attrName, "Apache.Fory.ForyStructAttribute", StringComparison.Ordinal))
            {
                currentKind = ForyAttributeKind.Struct;
            }
            else if (string.Equals(attrName, "Apache.Fory.ForyEnumAttribute", StringComparison.Ordinal))
            {
                currentKind = ForyAttributeKind.Enum;
            }
            else if (string.Equals(attrName, "Apache.Fory.ForyUnionAttribute", StringComparison.Ordinal))
            {
                currentKind = ForyAttributeKind.Union;
            }
            else
            {
                continue;
            }

            if (result is not null)
            {
                hasConflict = true;
                continue;
            }

            result = attribute;
            attributeKind = currentKind;
        }

        return result;
    }

    private static ForyAttributeKind GetForyAttributeKind(INamedTypeSymbol typeSymbol)
    {
        _ = GetForyAttribute(typeSymbol, out ForyAttributeKind attributeKind);
        return attributeKind;
    }

    private static bool TryGetIgnoredField(
        ISymbol member,
        out AttributeData? fieldAttribute)
    {
        fieldAttribute = null;
        foreach (AttributeData attribute in member.GetAttributes())
        {
            if (!string.Equals(
                    attribute.AttributeClass?.ToDisplayString(),
                    "Apache.Fory.ForyFieldAttribute",
                    StringComparison.Ordinal))
            {
                continue;
            }

            fieldAttribute = attribute;
            foreach (KeyValuePair<string, TypedConstant> namedArgument in attribute.NamedArguments)
            {
                if (string.Equals(namedArgument.Key, "Ignore", StringComparison.Ordinal) &&
                    namedArgument.Value.Value is true)
                {
                    return true;
                }
            }

            return false;
        }

        return false;
    }

    private static AttributeData? GetEffectiveForyFieldAttribute(ISymbol member)
    {
        for (ISymbol? current = member;
             current is not null;
             current = current is IPropertySymbol property ? property.OverriddenProperty : null)
        {
            foreach (AttributeData attribute in current.GetAttributes())
            {
                if (string.Equals(
                        attribute.AttributeClass?.ToDisplayString(),
                        "Apache.Fory.ForyFieldAttribute",
                        StringComparison.Ordinal))
                {
                    return attribute;
                }
            }
        }

        return null;
    }

    private static bool HasForyFieldAttribute(ISymbol member)
    {
        return GetEffectiveForyFieldAttribute(member) is not null;
    }

    private static string BuildPropertySlotKey(IPropertySymbol property)
    {
        while (property.OverriddenProperty is IPropertySymbol overridden)
        {
            property = overridden;
        }

        return $"{BuildRuntimeTypeKey(property.ContainingType)}|P|{property.MetadataName}";
    }

    private static ImmutableArray<byte> BuildNullableShape(ITypeSymbol type)
    {
        ImmutableArray<byte>.Builder shape = ImmutableArray.CreateBuilder<byte>();
        AppendNullableShape(type, shape);
        return shape.ToImmutable();
    }

    private static void AppendNullableShape(ITypeSymbol type, ImmutableArray<byte>.Builder shape)
    {
        shape.Add(type.TypeKind == TypeKind.Dynamic
            ? (byte)3
            : type.NullableAnnotation switch
            {
                NullableAnnotation.NotAnnotated => (byte)1,
                NullableAnnotation.Annotated => (byte)2,
                _ => (byte)0,
            });
        switch (type)
        {
            case IArrayTypeSymbol array:
                AppendNullableShape(array.ElementType, shape);
                break;
            case IPointerTypeSymbol pointer:
                AppendNullableShape(pointer.PointedAtType, shape);
                break;
            case INamedTypeSymbol named:
                if (named.ContainingType is not null)
                {
                    AppendNullableShape(named.ContainingType, shape);
                }

                foreach (ITypeSymbol typeArgument in named.TypeArguments)
                {
                    AppendNullableShape(typeArgument, shape);
                }

                break;
        }
    }

    private static bool IgnoredFieldHasWireOptions(AttributeData fieldAttribute)
    {
        if (!fieldAttribute.ConstructorArguments.IsEmpty)
        {
            return true;
        }

        return fieldAttribute.NamedArguments.Any(
            argument => argument.Key is "Id" or "Type");
    }

    private static MemberModel? BuildMemberModel(
        string name,
        ITypeSymbol memberType,
        ISymbol memberSymbol,
        List<Diagnostic> diagnostics)
    {
        return BuildMemberModel(
            name,
            memberType,
            memberSymbol,
            diagnostics,
            schemaTypeOverride: null,
            parseFieldAttribute: true);
    }

    private static MemberModel? BuildMemberModel(
        string name,
        ITypeSymbol memberType,
        ISymbol memberSymbol,
        List<Diagnostic> diagnostics,
        SchemaTypeModel? schemaTypeOverride,
        bool parseFieldAttribute,
        short? fieldIdOverride = null,
        ITypeSymbol? schemaDescriptorTypeOverride = null)
    {
        (bool isOptional, ITypeSymbol unwrappedType) = UnwrapNullable(memberType);
        short? fieldId = fieldIdOverride;
        SchemaTypeModel? schemaType = schemaTypeOverride;
        ITypeSymbol? schemaDescriptorType = schemaDescriptorTypeOverride;
        bool invalidSchemaType = false;
        if (parseFieldAttribute)
        {
            AttributeData? fieldAttribute = GetEffectiveForyFieldAttribute(memberSymbol);
            if (fieldAttribute is not null)
            {
                if (fieldAttribute.ConstructorArguments.Length == 1 &&
                    TryGetFieldId(fieldAttribute.ConstructorArguments[0], memberSymbol, diagnostics, out short ctorFieldId))
                {
                    fieldId = ctorFieldId;
                }

                foreach (KeyValuePair<string, TypedConstant> namedArg in fieldAttribute.NamedArguments)
                {
                    if (string.Equals(namedArg.Key, "Id", StringComparison.Ordinal))
                    {
                        if (TryGetFieldId(namedArg.Value, memberSymbol, diagnostics, out short parsedFieldId))
                        {
                            fieldId = parsedFieldId;
                        }

                        continue;
                    }

                    if (!string.Equals(namedArg.Key, "Type", StringComparison.Ordinal))
                    {
                        continue;
                    }

                    if (namedArg.Value.Value is ITypeSymbol schemaSymbol)
                    {
                        schemaDescriptorType = schemaSymbol;
                        schemaType = TryParseSchemaType(schemaSymbol);
                        if (schemaType is null)
                        {
                            invalidSchemaType = true;
                            diagnostics.Add(Diagnostic.Create(
                                UnsupportedSchemaType,
                                memberSymbol.Locations.FirstOrDefault(),
                                memberSymbol.Name,
                                memberType.ToDisplayString(FullNameFormat)));
                        }
                    }
                    else if (!namedArg.Value.IsNull)
                    {
                        invalidSchemaType = true;
                        diagnostics.Add(Diagnostic.Create(
                            UnsupportedSchemaType,
                            memberSymbol.Locations.FirstOrDefault(),
                            memberSymbol.Name,
                            memberType.ToDisplayString(FullNameFormat)));
                    }
                }
            }
        }

        if (invalidSchemaType)
        {
            return null;
        }

        DynamicAnyKind dynamicAnyKind = ResolveDynamicAnyKind(unwrappedType);
        TypeResolution resolution = ResolveTypeResolution(unwrappedType, schemaType);
        if (!resolution.Supported)
        {
            return null;
        }

        TypeClassification classification = resolution.Classification;
        int group = classification.IsPrimitive
            ? (isOptional ? 2 : 1)
            : 3;

        string typeName = memberType.ToDisplayString(FullNameFormat);
        TypeMetaFieldTypeModel typeMeta = BuildTypeMetaFieldTypeModel(
            memberType,
            isOptional,
            dynamicAnyKind,
            resolution.Classification.TypeId,
            schemaType);
        FieldCodecModel? fieldCodec = BuildFieldCodecModel(memberType, typeMeta, schemaType, classification);

        return new MemberModel(
            name,
            ToSnakeCase(name),
            typeName,
            isOptional,
            memberType is INamedTypeSymbol nts &&
            nts.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T,
            fieldId,
            classification,
            group,
            classification.IsCollection || classification.IsMap,
            classification.IsMap && !IsTypeSealed(unwrappedType),
            !unwrappedType.IsValueType && classification.TypeId != 21,
            FieldNeedsTypeInfo(classification, dynamicAnyKind, unwrappedType),
            dynamicAnyKind == DynamicAnyKind.None ? DynamicAnyKind.None : dynamicAnyKind,
            typeMeta,
            fieldCodec,
            schemaType is not null,
            memberType,
            memberSymbol.ContainingType,
            memberSymbol.Name,
            memberSymbol is IPropertySymbol ? WireMemberKind.Property : WireMemberKind.Field,
            memberSymbol is IPropertySymbol property
                ? BuildPropertySlotKey(property)
                : null,
            schemaDescriptorType: schemaDescriptorType,
            nullableShape: BuildNullableShape(memberType));
    }

    private static int FixedGraphValueBytes(ITypeSymbol type, TypeClassification classification)
    {
        if (classification.IsPrimitive && classification.PrimitiveSize > 0)
        {
            return classification.PrimitiveSize;
        }

        if (type.TypeKind == TypeKind.Enum &&
            type is INamedTypeSymbol enumType &&
            enumType.EnumUnderlyingType is not null)
        {
            return SpecialTypeBytes(enumType.EnumUnderlyingType.SpecialType);
        }

        return type.SpecialType == SpecialType.System_Decimal ? 16 : 0;
    }

    private static int SpecialTypeBytes(SpecialType specialType)
    {
        return specialType switch
        {
            SpecialType.System_Boolean or
            SpecialType.System_SByte or
            SpecialType.System_Byte => 1,
            SpecialType.System_Int16 or
            SpecialType.System_UInt16 => 2,
            SpecialType.System_Int32 or
            SpecialType.System_UInt32 or
            SpecialType.System_Single => 4,
            SpecialType.System_Int64 or
            SpecialType.System_UInt64 or
            SpecialType.System_Double => 8,
            _ => 0,
        };
    }

    private static TypeMetaFieldTypeModel BuildTypeMetaFieldTypeModel(
        ITypeSymbol memberType,
        bool nullable,
        DynamicAnyKind dynamicAnyKind,
        uint explicitTypeId,
        SchemaTypeModel? schemaType = null)
    {
        (bool _, ITypeSymbol unwrapped) = UnwrapNullable(memberType);

        if (schemaType is not null)
        {
            return BuildSchemaTypeMetaFieldTypeModel(memberType, nullable, schemaType);
        }

        if (unwrapped is IArrayTypeSymbol &&
            ClassifyType(unwrapped) is { TypeId: not 22 } arrayClassification &&
            IsPackedArrayTypeId(arrayClassification.TypeId))
        {
            return new TypeMetaFieldTypeModel(
                $"(uint){arrayClassification.TypeId}",
                nullable,
                false,
                ImmutableArray<TypeMetaFieldTypeModel>.Empty);
        }

        if (TryGetListElementType(unwrapped, out ITypeSymbol? listElementType))
        {
            bool elementNullable = GenericNullable(listElementType!);
            TypeMetaFieldTypeModel element = BuildTypeMetaFieldTypeModel(
                listElementType!,
                elementNullable,
                ResolveDynamicAnyKind(UnwrapNullable(listElementType!).Item2),
                0);
            return new TypeMetaFieldTypeModel(
                "(uint)global::Apache.Fory.TypeId.List",
                nullable,
                false,
                ImmutableArray.Create(element));
        }

        if (TryGetSetElementType(unwrapped, out ITypeSymbol? setElementType))
        {
            bool elementNullable = GenericNullable(setElementType!);
            TypeMetaFieldTypeModel element = BuildTypeMetaFieldTypeModel(
                setElementType!,
                elementNullable,
                ResolveDynamicAnyKind(UnwrapNullable(setElementType!).Item2),
                0);
            return new TypeMetaFieldTypeModel(
                "(uint)global::Apache.Fory.TypeId.Set",
                nullable,
                false,
                ImmutableArray.Create(element));
        }

        if (TryGetMapTypeArguments(unwrapped, out ITypeSymbol? keyType, out ITypeSymbol? valueType))
        {
            bool keyNullable = GenericNullable(keyType!);
            bool valueNullable = GenericNullable(valueType!);
            TypeMetaFieldTypeModel key = BuildTypeMetaFieldTypeModel(
                keyType!,
                keyNullable,
                ResolveDynamicAnyKind(UnwrapNullable(keyType!).Item2),
                0);
            TypeMetaFieldTypeModel value = BuildTypeMetaFieldTypeModel(
                valueType!,
                valueNullable,
                ResolveDynamicAnyKind(UnwrapNullable(valueType!).Item2),
                0);
            return new TypeMetaFieldTypeModel(
                "(uint)global::Apache.Fory.TypeId.Map",
                nullable,
                false,
                ImmutableArray.Create(key, value));
        }

        TypeClassification classification = ClassifyType(unwrapped);
        if (explicitTypeId != 0 && classification.IsPrimitive && classification.TypeId != explicitTypeId)
        {
            return new TypeMetaFieldTypeModel(
                explicitTypeId.ToString(),
                nullable,
                false,
                ImmutableArray<TypeMetaFieldTypeModel>.Empty);
        }

        if (IsUnionType(unwrapped))
        {
            // The field owner supplies the union schema, so static union fields
            // must use UNION. TYPED_UNION/NAMED_UNION are root or dynamic Any
            // identities where no field schema is available.
            return new TypeMetaFieldTypeModel(
                "(uint)global::Apache.Fory.TypeId.Union",
                nullable,
                true,
                ImmutableArray<TypeMetaFieldTypeModel>.Empty);
        }

        if (dynamicAnyKind == DynamicAnyKind.AnyValue)
        {
            return new TypeMetaFieldTypeModel(
                "(uint)global::Apache.Fory.TypeId.Unknown",
                nullable,
                true,
                ImmutableArray<TypeMetaFieldTypeModel>.Empty);
        }

        if (unwrapped.TypeKind == TypeKind.Enum)
        {
            return new TypeMetaFieldTypeModel(
                "(uint)global::Apache.Fory.TypeId.Enum",
                nullable,
                false,
                ImmutableArray<TypeMetaFieldTypeModel>.Empty);
        }

        return new TypeMetaFieldTypeModel(
            $"(uint){classification.TypeId}",
            nullable,
            !classification.IsBuiltIn && unwrapped.TypeKind != TypeKind.Enum,
            ImmutableArray<TypeMetaFieldTypeModel>.Empty);
    }

    private static TypeMetaFieldTypeModel BuildSchemaTypeMetaFieldTypeModel(
        ITypeSymbol carrierType,
        bool nullable,
        SchemaTypeModel schemaType)
    {
        (bool _, ITypeSymbol unwrapped) = UnwrapNullable(carrierType);
        switch (schemaType.Kind)
        {
            case SchemaTypeKind.List:
                if (!TryGetListElementType(unwrapped, out ITypeSymbol? listElementType))
                {
                    return new TypeMetaFieldTypeModel(
                        schemaType.TypeId.ToString(),
                        nullable,
                        false,
                        ImmutableArray<TypeMetaFieldTypeModel>.Empty);
                }

                bool elementNullable = GenericNullable(listElementType!);
                return new TypeMetaFieldTypeModel(
                    "(uint)global::Apache.Fory.TypeId.List",
                    nullable,
                    false,
                    ImmutableArray.Create(
                        BuildSchemaTypeMetaFieldTypeModel(
                            listElementType!,
                            elementNullable,
                            schemaType.Generics[0])));
            case SchemaTypeKind.Set:
                if (!TryGetSetElementType(unwrapped, out ITypeSymbol? setElementType))
                {
                    return new TypeMetaFieldTypeModel(
                        schemaType.TypeId.ToString(),
                        nullable,
                        false,
                        ImmutableArray<TypeMetaFieldTypeModel>.Empty);
                }

                bool setElementNullable = GenericNullable(setElementType!);
                return new TypeMetaFieldTypeModel(
                    "(uint)global::Apache.Fory.TypeId.Set",
                    nullable,
                    false,
                    ImmutableArray.Create(
                        BuildSchemaTypeMetaFieldTypeModel(
                            setElementType!,
                            setElementNullable,
                            schemaType.Generics[0])));
            case SchemaTypeKind.Map:
                if (!TryGetMapTypeArguments(unwrapped, out ITypeSymbol? keyType, out ITypeSymbol? valueType))
                {
                    return new TypeMetaFieldTypeModel(
                        schemaType.TypeId.ToString(),
                        nullable,
                        false,
                        ImmutableArray<TypeMetaFieldTypeModel>.Empty);
                }

                bool keyNullable = GenericNullable(keyType!);
                bool valueNullable = GenericNullable(valueType!);
                return new TypeMetaFieldTypeModel(
                    "(uint)global::Apache.Fory.TypeId.Map",
                    nullable,
                    false,
                    ImmutableArray.Create(
                        BuildSchemaTypeMetaFieldTypeModel(keyType!, keyNullable, schemaType.Generics[0]),
                        BuildSchemaTypeMetaFieldTypeModel(valueType!, valueNullable, schemaType.Generics[1])));
            default:
                return new TypeMetaFieldTypeModel(
                    schemaType.TypeId.ToString(),
                    nullable,
                    false,
                    ImmutableArray<TypeMetaFieldTypeModel>.Empty);
        }
    }

    private static FieldCodecModel? BuildFieldCodecModel(
        ITypeSymbol carrierType,
        TypeMetaFieldTypeModel typeMeta,
        SchemaTypeModel? schemaType,
        TypeClassification classification)
    {
        (bool nullable, ITypeSymbol unwrapped) = UnwrapNullable(carrierType);
        bool nullableValueType = carrierType is INamedTypeSymbol nts &&
                                 nts.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T;

        if (schemaType is not null)
        {
            FieldCodecModel codec = BuildFieldCodecFromSchema(carrierType, nullable, nullableValueType, schemaType);
            return codec.Kind == FieldCodecKind.Scalar ? null : codec;
        }

        _ = typeMeta;
        _ = classification;
        return null;
    }

    private static FieldCodecModel BuildFieldCodecFromSchema(
        ITypeSymbol carrierType,
        bool nullable,
        bool nullableValueType,
        SchemaTypeModel schemaType)
    {
        (bool _, ITypeSymbol unwrapped) = UnwrapNullable(carrierType);
        switch (schemaType.Kind)
        {
            case SchemaTypeKind.List:
                {
                    ITypeSymbol elementType = TryGetListElementType(unwrapped, out ITypeSymbol? listElementType)
                        ? listElementType!
                        : carrierType;
                    FieldCodecModel element = BuildFieldCodecFromSchema(
                        elementType,
                        GenericNullable(elementType),
                        elementType is INamedTypeSymbol elementNamed &&
                        elementNamed.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T,
                        schemaType.Generics[0]);
                    return new FieldCodecModel(
                        FieldCodecKind.List,
                        schemaType.TypeId,
                        carrierType.ToDisplayString(FullNameFormat),
                        nullable,
                        nullableValueType,
                        GetCarrierKind(unwrapped),
                        ImmutableArray.Create(element));
                }
            case SchemaTypeKind.Set:
                {
                    ITypeSymbol elementType = TryGetSetElementType(unwrapped, out ITypeSymbol? setElementType)
                        ? setElementType!
                        : carrierType;
                    FieldCodecModel element = BuildFieldCodecFromSchema(
                        elementType,
                        GenericNullable(elementType),
                        elementType is INamedTypeSymbol elementNamed &&
                        elementNamed.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T,
                        schemaType.Generics[0]);
                    return new FieldCodecModel(
                        FieldCodecKind.Set,
                        schemaType.TypeId,
                        carrierType.ToDisplayString(FullNameFormat),
                        nullable,
                        nullableValueType,
                        GetCarrierKind(unwrapped),
                        ImmutableArray.Create(element));
                }
            case SchemaTypeKind.Map:
                {
                    ITypeSymbol keyType = carrierType;
                    ITypeSymbol valueType = carrierType;
                    if (TryGetMapTypeArguments(unwrapped, out ITypeSymbol? parsedKeyType, out ITypeSymbol? parsedValueType))
                    {
                        keyType = parsedKeyType!;
                        valueType = parsedValueType!;
                    }

                    FieldCodecModel key = BuildFieldCodecFromSchema(
                        keyType,
                        GenericNullable(keyType),
                        keyType is INamedTypeSymbol keyNamed &&
                        keyNamed.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T,
                        schemaType.Generics[0]);
                    FieldCodecModel value = BuildFieldCodecFromSchema(
                        valueType,
                        GenericNullable(valueType),
                        valueType is INamedTypeSymbol valueNamed &&
                        valueNamed.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T,
                        schemaType.Generics[1]);
                    return new FieldCodecModel(
                        FieldCodecKind.Map,
                        schemaType.TypeId,
                        carrierType.ToDisplayString(FullNameFormat),
                        nullable,
                        nullableValueType,
                        GetCarrierKind(unwrapped),
                        ImmutableArray.Create(key, value));
                }
            case SchemaTypeKind.PackedArray:
                return new FieldCodecModel(
                    FieldCodecKind.PackedArray,
                    schemaType.TypeId,
                    carrierType.ToDisplayString(FullNameFormat),
                    nullable,
                    nullableValueType,
                    GetCarrierKind(unwrapped),
                    ImmutableArray<FieldCodecModel>.Empty);
            default:
                return new FieldCodecModel(
                    FieldCodecKind.Scalar,
                    schemaType.TypeId,
                    carrierType.ToDisplayString(FullNameFormat),
                    nullable,
                    nullableValueType,
                    GetCarrierKind(unwrapped),
                    ImmutableArray<FieldCodecModel>.Empty);
        }
    }

    private static CarrierKind GetCarrierKind(ITypeSymbol unwrappedType)
    {
        if (unwrappedType is IArrayTypeSymbol)
        {
            return CarrierKind.Array;
        }

        if (unwrappedType is not INamedTypeSymbol named)
        {
            return CarrierKind.Value;
        }

        string genericName = named.ConstructedFrom.ToDisplayString();
        return genericName switch
        {
            "System.Collections.Generic.List<T>" => CarrierKind.List,
            "System.Collections.Generic.HashSet<T>" => CarrierKind.HashSet,
            "System.Collections.Generic.Dictionary<TKey, TValue>" => CarrierKind.Dictionary,
            "Apache.Fory.NullableKeyDictionary<TKey, TValue>" => CarrierKind.NullableKeyDictionary,
            _ => CarrierKind.Value,
        };
    }

    private static bool TryGetFieldId(
        TypedConstant value,
        ISymbol memberSymbol,
        List<Diagnostic> diagnostics,
        out short fieldId)
    {
        fieldId = default;
        object? raw = value.Value;
        if (raw is null)
        {
            return false;
        }

        long numeric;
        switch (raw)
        {
            case byte v:
                numeric = v;
                break;
            case sbyte v:
                numeric = v;
                break;
            case short v:
                numeric = v;
                break;
            case ushort v:
                numeric = v;
                break;
            case int v:
                numeric = v;
                break;
            case uint v:
                numeric = v;
                break;
            case long v:
                numeric = v;
                break;
            case ulong v:
                if (v > (ulong)short.MaxValue)
                {
                    diagnostics.Add(Diagnostic.Create(
                        InvalidFieldId,
                        memberSymbol.Locations.FirstOrDefault(),
                        memberSymbol.Name));
                    return false;
                }

                numeric = (long)v;
                break;
            default:
                return false;
        }

        if (numeric < 0 || numeric > short.MaxValue)
        {
            diagnostics.Add(Diagnostic.Create(
                InvalidFieldId,
                memberSymbol.Locations.FirstOrDefault(),
                memberSymbol.Name));
            return false;
        }

        fieldId = (short)numeric;
        return true;
    }

    private static ImmutableArray<MemberModel> SortMembers(ImmutableArray<MemberModel> members)
    {
        return members
            .OrderBy(m => m.Group)
            .ThenBy(m =>
            {
                if (m.Group is 1 or 2)
                {
                    return m.Classification.IsCompressedNumeric ? 1 : 0;
                }

                return 0;
            })
            .ThenByDescending(m => m.Group is 1 or 2 ? m.Classification.PrimitiveSize : 0)
            .ThenBy(m =>
            {
                if (m.Group is 1 or 2)
                {
                    return (int)m.Classification.TypeId;
                }

                return 0;
            })
            .ThenBy(m => m.FieldId.HasValue ? 0 : 1)
            .ThenBy(m => m.FieldId.GetValueOrDefault())
            .ThenBy(m => m.FieldIdentifier, StringComparer.Ordinal)
            .ThenBy(
                m => m.DeclaringType is null ? string.Empty : BuildRuntimeTypeKey(m.DeclaringType),
                StringComparer.Ordinal)
            .ThenBy(m => m.Name, StringComparer.Ordinal)
            .ThenBy(m => m.DeclarationOrdinal)
            .ToImmutableArray();
    }

    private static bool GenericNullable(ITypeSymbol type)
    {
        (bool optional, ITypeSymbol unwrapped) = UnwrapNullable(type);
        if (optional)
        {
            return true;
        }

        if (unwrapped.IsValueType)
        {
            return false;
        }

        TypeClassification c = ClassifyType(unwrapped);
        return !c.IsPrimitive;
    }

    private static bool FieldNeedsTypeInfo(
        TypeClassification classification,
        DynamicAnyKind dynamicAnyKind,
        ITypeSymbol unwrappedType)
    {
        if (dynamicAnyKind == DynamicAnyKind.AnyValue)
        {
            return true;
        }

        if (classification.IsBuiltIn || IsUnionType(unwrappedType) || unwrappedType.TypeKind == TypeKind.Enum)
        {
            return false;
        }

        return true;
    }

    private static bool ValidateOrdinaryFieldOptions(
        ISymbol member,
        List<Diagnostic> diagnostics)
    {
        AttributeData? attribute = GetEffectiveForyFieldAttribute(member);
        if (attribute is null)
        {
            return true;
        }

        string? invalidOption = attribute.NamedArguments
            .Select(argument => argument.Key)
            .FirstOrDefault(key => key is "TargetDeclaringType" or "TargetMemberName" or "TargetMemberKind");
        if (invalidOption is null)
        {
            return true;
        }

        diagnostics.Add(Diagnostic.Create(
            InvalidExternalMember,
            member.Locations.FirstOrDefault(location => location.IsInSource),
            member.Name,
            member.ContainingType.ToDisplayString(FullNameFormat),
            $"{invalidOption} is valid only on an external ForyStruct declaration"));
        return false;
    }

    private static MemberModel BindSourceMember(
        Compilation compilation,
        string serializerName,
        ISymbol symbol,
        MemberModel member,
        int declarationOrdinal,
        bool publishProviderApi)
    {
        string providerName = $"global::Apache.Fory.Generated.{serializerName}";
        string? fieldAccessorName = null;
        string? getterAccessorName = null;
        string? setterAccessorName = null;
        string? publishedFieldAccessorName = null;
        string? publishedGetterAccessorName = null;
        string? publishedSetterAccessorName = null;
        WireMemberKind memberKind;
        ITypeSymbol memberType;
        string targetMemberName;
        string? slotKey;
        if (symbol is IFieldSymbol field)
        {
            memberKind = WireMemberKind.Field;
            memberType = field.Type;
            targetMemberName = field.MetadataName;
            slotKey = null;
            if (!compilation.IsSymbolAccessibleWithin(field, compilation.Assembly))
            {
                fieldAccessorName = $"F{declarationOrdinal}";
            }

            publishedFieldAccessorName = fieldAccessorName ??
                (publishProviderApi && field.DeclaredAccessibility != Accessibility.Public
                    ? $"F{declarationOrdinal}"
                    : null);
        }
        else
        {
            IPropertySymbol property = (IPropertySymbol)symbol;
            memberKind = WireMemberKind.Property;
            memberType = property.Type;
            targetMemberName = property.MetadataName;
            slotKey = BuildPropertySlotKey(property);
            if (!property.IsAbstract &&
                !compilation.IsSymbolAccessibleWithin(property.GetMethod!, compilation.Assembly))
            {
                getterAccessorName = $"G{declarationOrdinal}";
            }

            if (!property.IsAbstract &&
                !compilation.IsSymbolAccessibleWithin(property.SetMethod!, compilation.Assembly))
            {
                setterAccessorName = $"S{declarationOrdinal}";
            }

            if (!property.IsAbstract)
            {
                publishedGetterAccessorName = getterAccessorName ??
                    (publishProviderApi &&
                     property.GetMethod!.DeclaredAccessibility != Accessibility.Public
                        ? $"G{declarationOrdinal}"
                        : null);
                publishedSetterAccessorName = setterAccessorName ??
                    (publishProviderApi &&
                     property.SetMethod!.DeclaredAccessibility != Accessibility.Public
                        ? $"S{declarationOrdinal}"
                        : null);
            }
        }

        return member.WithDeclaration(
            memberType,
            symbol.ContainingType,
            targetMemberName,
            memberKind,
            slotKey,
            providerName,
            fieldAccessorName,
            getterAccessorName,
            setterAccessorName,
            member.SchemaDescriptorType,
            declarationOrdinal,
            BuildNullableShape(memberType))
            .WithPublishedAccessors(
                publishedFieldAccessorName,
                publishedGetterAccessorName,
                publishedSetterAccessorName);
    }

    private static ImmutableArray<ShallowFieldModel> BuildDeclaredShallowFields(
        Compilation compilation,
        INamedTypeSymbol type,
        List<Diagnostic> diagnostics)
    {
        Dictionary<string, ShallowFieldModel> fields = new(StringComparer.Ordinal);
        foreach (IFieldSymbol field in type.GetMembers().OfType<IFieldSymbol>())
        {
            if (field.IsStatic)
            {
                continue;
            }

            string identity = $"{BuildRuntimeTypeKey(type)}|F|{field.MetadataName}";
            if (!TryBuildShallowField(
                    compilation,
                    type,
                    field.Name,
                    field.Type,
                    field,
                    identity,
                    field.Locations.FirstOrDefault(location => location.IsInSource),
                    diagnostics,
                    out ShallowFieldModel? shallowField))
            {
                continue;
            }

            if (!fields.ContainsKey(identity))
            {
                fields.Add(identity, shallowField!);
            }
        }

        foreach (SyntaxReference syntaxReference in type.DeclaringSyntaxReferences)
        {
            if (syntaxReference.GetSyntax() is not TypeDeclarationSyntax declaration)
            {
                continue;
            }

            SemanticModel semanticModel = compilation.GetSemanticModel(declaration.SyntaxTree);
            foreach (EventFieldDeclarationSyntax eventDeclaration in declaration.Members
                         .OfType<EventFieldDeclarationSyntax>())
            {
                foreach (VariableDeclaratorSyntax variable in eventDeclaration.Declaration.Variables)
                {
                    if (semanticModel.GetDeclaredSymbol(variable) is not IEventSymbol eventSymbol ||
                        eventSymbol.IsStatic)
                    {
                        continue;
                    }

                    string identity = $"{BuildRuntimeTypeKey(type)}|F|{eventSymbol.MetadataName}";
                    if (!fields.ContainsKey(identity))
                    {
                        fields.Add(
                            identity,
                            new ShallowFieldModel(
                            identity,
                            eventSymbol.Name,
                            eventSymbol.Type.ToDisplayString(FullNameFormat),
                            "4",
                            variable.GetLocation()));
                    }
                }
            }
        }

        return fields.Values
            .OrderBy(field => field.Identity, StringComparer.Ordinal)
            .ToImmutableArray();
    }

    private static bool TryBuildShallowField(
        Compilation compilation,
        INamedTypeSymbol owner,
        string fieldName,
        ITypeSymbol fieldType,
        IFieldSymbol? field,
        string identity,
        Location? location,
        List<Diagnostic> diagnostics,
        out ShallowFieldModel? shallowField)
    {
        string expression;
        if (field is { IsFixedSizeBuffer: true })
        {
            ITypeSymbol elementType = field.Type is IPointerTypeSymbol pointer
                ? pointer.PointedAtType
                : field.Type;
            expression = $"checked((long){field.FixedSize} * {FieldGraphMemoryExpr(elementType)})";
        }
        else if (fieldType.TypeKind is TypeKind.Pointer or TypeKind.FunctionPointer)
        {
            expression = "global::System.IntPtr.Size";
        }
        else if (!fieldType.IsValueType)
        {
            expression = "4";
        }
        else
        {
            TypeClassification classification = ClassifyType(fieldType);
            int fixedValueBytes = FixedGraphValueBytes(fieldType, classification);
            if (fixedValueBytes > 0)
            {
                expression = fixedValueBytes.ToString(CultureInfo.InvariantCulture);
            }
            else
            {
                if (!compilation.IsSymbolAccessibleWithin(fieldType, compilation.Assembly) ||
                    RequiresExternAlias(fieldType, compilation))
                {
                    diagnostics.Add(Diagnostic.Create(
                        UnsupportedShallowField,
                        location,
                        owner.ToDisplayString(FullNameFormat),
                        fieldName,
                        fieldType.ToDisplayString(FullNameFormat)));
                    shallowField = null;
                    return false;
                }

                expression =
                    $"global::System.Runtime.CompilerServices.Unsafe.SizeOf<{fieldType.ToDisplayString(FullNameFormat)}>()";
            }
        }

        shallowField = new ShallowFieldModel(
            identity,
            fieldName,
            fieldType.ToDisplayString(FullNameFormat),
            expression,
            location);
        return true;
    }

    private static string ProviderVisibility(INamedTypeSymbol target)
    {
        if (target.TypeKind != TypeKind.Class || target.IsSealed)
        {
            return "internal";
        }

        for (INamedTypeSymbol? current = target; current is not null; current = current.ContainingType)
        {
            if (current.DeclaredAccessibility != Accessibility.Public)
            {
                return "internal";
            }
        }

        return "public";
    }

    private static bool HasAccessibleParameterlessCtor(INamedTypeSymbol type, Compilation compilation) =>
        FindAccessibleParameterlessCtor(type, compilation) is not null;

    private static SchemaTypeModel? TryParseSchemaType(ITypeSymbol symbol)
    {
        if (symbol is not INamedTypeSymbol named)
        {
            return null;
        }

        string fullName = named.ConstructedFrom.ToDisplayString(SymbolDisplayFormat.FullyQualifiedFormat);
        fullName = fullName.StartsWith("global::", StringComparison.Ordinal)
            ? fullName.Substring("global::".Length)
            : fullName;

        if (fullName == "Apache.Fory.Schema.Types.List<TElement>")
        {
            if (named.TypeArguments.Length != 1 ||
                TryParseSchemaType(named.TypeArguments[0]) is not SchemaTypeModel element)
            {
                return null;
            }

            return new SchemaTypeModel(22, SchemaTypeKind.List, ImmutableArray.Create(element));
        }

        if (fullName == "Apache.Fory.Schema.Types.Array<TElement>")
        {
            if (named.TypeArguments.Length != 1 ||
                TryParseSchemaType(named.TypeArguments[0]) is not SchemaTypeModel element ||
                element.HasExplicitScalarEncoding ||
                TryResolveArrayTypeIdForElement(element.TypeId) is not uint arrayTypeId)
            {
                return null;
            }

            return new SchemaTypeModel(arrayTypeId, SchemaTypeKind.PackedArray, ImmutableArray.Create(element));
        }

        if (fullName == "Apache.Fory.Schema.Types.Fixed<TScalar>")
        {
            if (named.TypeArguments.Length != 1 ||
                TryParseSchemaType(named.TypeArguments[0]) is not SchemaTypeModel scalar ||
                TryResolveFixedTypeId(scalar.TypeId) is not uint fixedTypeId)
            {
                return null;
            }

            return new SchemaTypeModel(
                fixedTypeId,
                SchemaTypeKind.Scalar,
                ImmutableArray<SchemaTypeModel>.Empty,
                hasExplicitScalarEncoding: true);
        }

        if (fullName == "Apache.Fory.Schema.Types.Tagged<TScalar>")
        {
            if (named.TypeArguments.Length != 1 ||
                TryParseSchemaType(named.TypeArguments[0]) is not SchemaTypeModel scalar ||
                TryResolveTaggedTypeId(scalar.TypeId) is not uint taggedTypeId)
            {
                return null;
            }

            return new SchemaTypeModel(
                taggedTypeId,
                SchemaTypeKind.Scalar,
                ImmutableArray<SchemaTypeModel>.Empty,
                hasExplicitScalarEncoding: true);
        }

        if (fullName == "Apache.Fory.Schema.Types.Set<TElement>")
        {
            if (named.TypeArguments.Length != 1 ||
                TryParseSchemaType(named.TypeArguments[0]) is not SchemaTypeModel element)
            {
                return null;
            }

            return new SchemaTypeModel(23, SchemaTypeKind.Set, ImmutableArray.Create(element));
        }

        if (fullName == "Apache.Fory.Schema.Types.Map<TKey, TValue>")
        {
            if (named.TypeArguments.Length != 2 ||
                TryParseSchemaType(named.TypeArguments[0]) is not SchemaTypeModel key ||
                TryParseSchemaType(named.TypeArguments[1]) is not SchemaTypeModel value)
            {
                return null;
            }

            return new SchemaTypeModel(24, SchemaTypeKind.Map, ImmutableArray.Create(key, value));
        }

        return TryResolveSchemaTypeId(fullName, out uint typeId, out SchemaTypeKind kind)
            ? new SchemaTypeModel(typeId, kind, ImmutableArray<SchemaTypeModel>.Empty)
            : null;
    }

    private static bool TryResolveSchemaTypeId(string fullName, out uint typeId, out SchemaTypeKind kind)
    {
        kind = SchemaTypeKind.Scalar;
        switch (fullName)
        {
            case "Apache.Fory.Schema.Types.Bool":
                typeId = 1;
                return true;
            case "Apache.Fory.Schema.Types.Int8":
                typeId = 2;
                return true;
            case "Apache.Fory.Schema.Types.Int16":
                typeId = 3;
                return true;
            case "Apache.Fory.Schema.Types.Int32":
                typeId = 5;
                return true;
            case "Apache.Fory.Schema.Types.Int64":
                typeId = 7;
                return true;
            case "Apache.Fory.Schema.Types.UInt8":
                typeId = 9;
                return true;
            case "Apache.Fory.Schema.Types.UInt16":
                typeId = 10;
                return true;
            case "Apache.Fory.Schema.Types.UInt32":
                typeId = 12;
                return true;
            case "Apache.Fory.Schema.Types.UInt64":
                typeId = 14;
                return true;
            case "Apache.Fory.Schema.Types.Float16":
                typeId = 17;
                return true;
            case "Apache.Fory.Schema.Types.BFloat16":
                typeId = 18;
                return true;
            case "Apache.Fory.Schema.Types.Float32":
                typeId = 19;
                return true;
            case "Apache.Fory.Schema.Types.Float64":
                typeId = 20;
                return true;
            case "Apache.Fory.Schema.Types.String":
                typeId = 21;
                return true;
            case "Apache.Fory.Schema.Types.Binary":
                typeId = 41;
                return true;
            case "Apache.Fory.Schema.Types.Duration":
                typeId = 37;
                return true;
            case "Apache.Fory.Schema.Types.Timestamp":
                typeId = 38;
                return true;
            case "Apache.Fory.Schema.Types.Date":
                typeId = 39;
                return true;
            case "Apache.Fory.Schema.Types.Decimal":
                typeId = 40;
                return true;
            default:
                typeId = 0;
                return false;
        }
    }

    private static uint? TryResolveFixedTypeId(uint scalarTypeId)
    {
        return scalarTypeId switch
        {
            5 => 4,
            7 => 6,
            12 => 11,
            14 => 13,
            4 or 6 or 11 or 13 => scalarTypeId,
            _ => null,
        };
    }

    private static uint? TryResolveTaggedTypeId(uint scalarTypeId)
    {
        return scalarTypeId switch
        {
            7 or 6 => 8,
            14 or 13 => 15,
            _ => null,
        };
    }

    private static uint? TryResolveArrayTypeIdForElement(uint elementTypeId)
    {
        return elementTypeId switch
        {
            1 => 43,
            2 => 44,
            3 => 45,
            4 or 5 => 46,
            6 or 7 or 8 => 47,
            9 => 48,
            10 => 49,
            11 or 12 => 50,
            13 or 14 or 15 => 51,
            17 => 53,
            18 => 54,
            19 => 55,
            20 => 56,
            _ => null,
        };
    }

    private static bool IsPackedArrayTypeId(uint typeId)
    {
        return typeId is 41 or 43 or 44 or 45 or 46 or 47 or 48 or 49 or 50 or 51 or 53 or 54 or 55 or 56;
    }

    private static TypeResolution ResolveTypeResolution(ITypeSymbol type, SchemaTypeModel? schemaType)
    {
        TypeClassification baseType = ClassifyType(type);
        if (schemaType is null)
        {
            return new TypeResolution(true, baseType);
        }

        bool isPrimitive = schemaType.Kind == SchemaTypeKind.Scalar;
        bool isCollection = schemaType.Kind == SchemaTypeKind.List ||
                            schemaType.Kind == SchemaTypeKind.Set;
        bool isMap = schemaType.Kind == SchemaTypeKind.Map;
        bool isCompressedNumeric = schemaType.TypeId is 5 or 7 or 8 or 12 or 14 or 15;
        int primitiveSize = schemaType.TypeId switch
        {
            1 or 2 or 9 => 1,
            3 or 10 or 17 or 18 => 2,
            4 or 5 or 11 or 12 or 19 => 4,
            6 or 7 or 8 or 13 or 14 or 15 or 20 => 8,
            _ => 0,
        };
        return new TypeResolution(
            true,
            new TypeClassification(
                schemaType.TypeId,
                isPrimitive,
                true,
                isCollection,
                isMap,
                isCompressedNumeric,
                primitiveSize));
    }

    private static TypeClassification ClassifyType(ITypeSymbol type)
    {
        if (ResolveDynamicAnyKind(type) == DynamicAnyKind.AnyValue)
        {
            return new TypeClassification(0, false, true, false, false, false, 0);
        }

        if (type.SpecialType == SpecialType.System_Boolean)
        {
            return new TypeClassification(1, true, true, false, false, false, 1);
        }

        if (type.SpecialType == SpecialType.System_SByte)
        {
            return new TypeClassification(2, true, true, false, false, false, 1);
        }

        if (type.SpecialType == SpecialType.System_Int16)
        {
            return new TypeClassification(3, true, true, false, false, false, 2);
        }

        if (type.SpecialType == SpecialType.System_Int32)
        {
            return new TypeClassification(5, true, true, false, false, true, 4);
        }

        if (type.SpecialType == SpecialType.System_Int64)
        {
            return new TypeClassification(7, true, true, false, false, true, 8);
        }

        if (type.SpecialType == SpecialType.System_Byte)
        {
            return new TypeClassification(9, true, true, false, false, false, 1);
        }

        if (type.SpecialType == SpecialType.System_UInt16)
        {
            return new TypeClassification(10, true, true, false, false, false, 2);
        }

        if (type.SpecialType == SpecialType.System_UInt32)
        {
            return new TypeClassification(12, true, true, false, false, true, 4);
        }

        if (type.SpecialType == SpecialType.System_UInt64)
        {
            return new TypeClassification(14, true, true, false, false, true, 8);
        }

        if (type.SpecialType == SpecialType.System_Single)
        {
            return new TypeClassification(19, true, true, false, false, false, 4);
        }

        if (string.Equals(type.ToDisplayString(), "System.Half", StringComparison.Ordinal))
        {
            return new TypeClassification(17, true, true, false, false, false, 2);
        }

        if (string.Equals(type.ToDisplayString(), "Apache.Fory.BFloat16", StringComparison.Ordinal))
        {
            return new TypeClassification(18, true, true, false, false, false, 2);
        }

        if (type.SpecialType == SpecialType.System_Double)
        {
            return new TypeClassification(20, true, true, false, false, false, 8);
        }

        if (type.SpecialType == SpecialType.System_String)
        {
            return new TypeClassification(21, false, true, false, false, false, 0);
        }

        if (IsDateType(type))
        {
            return new TypeClassification(39, false, true, false, false, false, 0);
        }

        if (IsTimestampType(type))
        {
            return new TypeClassification(38, false, true, false, false, false, 0);
        }

        if (IsDurationType(type))
        {
            return new TypeClassification(37, false, true, false, false, false, 0);
        }

        if (type.SpecialType == SpecialType.System_Decimal ||
            string.Equals(type.ToDisplayString(), "Apache.Fory.ForyDecimal", StringComparison.Ordinal))
        {
            return new TypeClassification(40, false, true, false, false, false, 0);
        }

        if (type is IArrayTypeSymbol arrayType)
        {
            if (TryResolvePackedArrayTypeIdForElement(arrayType.ElementType) is uint packedArrayTypeId)
            {
                return new TypeClassification(packedArrayTypeId, false, true, false, false, false, 0);
            }

            return new TypeClassification(22, false, true, true, false, false, 0);
        }

        if (TryGetListElementType(type, out _))
        {
            return new TypeClassification(22, false, true, true, false, false, 0);
        }

        if (TryGetSetElementType(type, out _))
        {
            return new TypeClassification(23, false, true, true, false, false, 0);
        }

        if (TryGetMapTypeArguments(type, out _, out _))
        {
            return new TypeClassification(24, false, true, false, true, false, 0);
        }

        if (IsUnionType(type))
        {
            return new TypeClassification(33, false, false, false, false, false, 0);
        }

        return new TypeClassification(27, false, false, false, false, false, 0);
    }

    private static DynamicAnyKind ResolveDynamicAnyKind(ITypeSymbol type)
    {
        if (type.SpecialType == SpecialType.System_Object)
        {
            return DynamicAnyKind.AnyValue;
        }

        return DynamicAnyKind.None;
    }

    private static bool IsDateType(ITypeSymbol symbol)
    {
        return string.Equals(symbol.ToDisplayString(), "System.DateOnly", StringComparison.Ordinal);
    }

    private static bool IsTimestampType(ITypeSymbol symbol)
    {
        string name = symbol.ToDisplayString();
        return string.Equals(name, "System.DateTime", StringComparison.Ordinal) ||
               string.Equals(name, "System.DateTimeOffset", StringComparison.Ordinal);
    }

    private static bool IsDurationType(ITypeSymbol symbol)
    {
        return string.Equals(symbol.ToDisplayString(), "System.TimeSpan", StringComparison.Ordinal);
    }

    private static bool IsUnionType(ITypeSymbol symbol)
    {
        if (symbol is INamedTypeSymbol namedType &&
            GetForyAttributeKind(namedType) == ForyAttributeKind.Union)
        {
            return true;
        }

        INamedTypeSymbol? current = symbol as INamedTypeSymbol;
        while (current is not null)
        {
            if (string.Equals(current.ToDisplayString(), "Apache.Fory.Union", StringComparison.Ordinal))
            {
                return true;
            }

            current = current.BaseType;
        }

        return false;
    }

    private static bool IsTypeSealed(ITypeSymbol symbol)
    {
        if (symbol.TypeKind == TypeKind.TypeParameter)
        {
            return false;
        }

        return symbol.IsSealed;
    }

    private static bool TryGetListElementType(ITypeSymbol type, out ITypeSymbol? elementType)
    {
        elementType = null;
        if (type is IArrayTypeSymbol arrayType)
        {
            elementType = arrayType.ElementType;
            return true;
        }

        if (type is not INamedTypeSymbol named)
        {
            return false;
        }

        string genericName = named.ConstructedFrom.ToDisplayString();
        if (genericName is
            "System.Collections.Generic.List<T>" or
            "System.Collections.Generic.LinkedList<T>" or
            "System.Collections.Generic.Queue<T>" or
            "System.Collections.Generic.Stack<T>" or
            "System.Collections.Generic.IList<T>" or
            "System.Collections.Generic.IReadOnlyList<T>")
        {
            elementType = named.TypeArguments[0];
            return true;
        }

        return false;
    }

    private static bool TryGetSetElementType(ITypeSymbol type, out ITypeSymbol? elementType)
    {
        elementType = null;
        if (type is not INamedTypeSymbol named)
        {
            return false;
        }

        string genericName = named.ConstructedFrom.ToDisplayString();
        if (genericName is
            "System.Collections.Generic.HashSet<T>" or
            "System.Collections.Generic.SortedSet<T>" or
            "System.Collections.Immutable.ImmutableHashSet<T>" or
            "System.Collections.Generic.ISet<T>" or
            "System.Collections.Generic.IReadOnlySet<T>" or
            "System.Collections.Immutable.IImmutableSet<T>")
        {
            elementType = named.TypeArguments[0];
            return true;
        }

        return false;
    }

    private static bool TryGetMapTypeArguments(ITypeSymbol type, out ITypeSymbol? keyType, out ITypeSymbol? valueType)
    {
        keyType = null;
        valueType = null;
        if (type is not INamedTypeSymbol named)
        {
            return false;
        }

        string genericName = named.ConstructedFrom.ToDisplayString();
        if (genericName is
            "System.Collections.Generic.Dictionary<TKey, TValue>" or
            "System.Collections.Generic.SortedDictionary<TKey, TValue>" or
            "System.Collections.Generic.SortedList<TKey, TValue>" or
            "System.Collections.Concurrent.ConcurrentDictionary<TKey, TValue>" or
            "System.Collections.Generic.IDictionary<TKey, TValue>" or
            "System.Collections.Generic.IReadOnlyDictionary<TKey, TValue>" or
            "Apache.Fory.NullableKeyDictionary<TKey, TValue>")
        {
            keyType = named.TypeArguments[0];
            valueType = named.TypeArguments[1];
            return true;
        }

        return false;
    }

    private static uint? TryResolvePackedArrayTypeIdForElement(ITypeSymbol elementType)
    {
        (bool isNullable, ITypeSymbol unwrapped) = UnwrapNullable(elementType);
        if (isNullable)
        {
            return null;
        }

        uint elementTypeId = ClassifyType(unwrapped).TypeId;
        return elementTypeId switch
        {
            9 => 41,  // byte -> binary
            1 => 43,  // bool -> bool array
            2 => 44,  // sbyte -> int8 array
            3 => 45,  // short -> int16 array
            5 => 46,  // int -> int32 array
            7 => 47,  // long -> int64 array
            10 => 49, // ushort -> uint16 array
            12 => 50, // uint -> uint32 array
            14 => 51, // ulong -> uint64 array
            17 => 53, // Half -> float16 array
            18 => 54, // BFloat16 -> bfloat16 array
            19 => 55, // float -> float32 array
            20 => 56, // double -> float64 array
            _ => null,
        };
    }

    private static (bool, ITypeSymbol) UnwrapNullable(ITypeSymbol type)
    {
        if (type is INamedTypeSymbol named &&
            named.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T)
        {
            return (true, named.TypeArguments[0]);
        }

        if (type.IsReferenceType && type.NullableAnnotation == NullableAnnotation.Annotated)
        {
            return (true, type.WithNullableAnnotation(NullableAnnotation.NotAnnotated));
        }

        return (false, type);
    }

    private static string BoolLiteral(bool value) => value ? "true" : "false";

    private static string EscapeString(string value) => value.Replace("\\", "\\\\").Replace("\"", "\\\"");

    private static string EscapeIdentifier(string value)
    {
        return SyntaxFacts.GetKeywordKind(value) != SyntaxKind.None
            || SyntaxFacts.GetContextualKeywordKind(value) != SyntaxKind.None
                ? "@" + value
                : value;
    }

    private static string ToSnakeCase(string name)
    {
        if (string.IsNullOrEmpty(name))
        {
            return name;
        }

        StringBuilder sb = new(name.Length + 4);
        for (int i = 0; i < name.Length; i++)
        {
            char c = name[i];
            if (char.IsUpper(c))
            {
                if (i > 0)
                {
                    bool prevUpper = char.IsUpper(name[i - 1]);
                    bool nextUpperOrEnd = i + 1 >= name.Length || char.IsUpper(name[i + 1]);
                    bool leadingPascalBoundary = i == 1 && prevUpper && !nextUpperOrEnd;
                    if ((!prevUpper || !nextUpperOrEnd) && !leadingPascalBoundary)
                    {
                        sb.Append('_');
                    }
                }

                sb.Append(char.ToLowerInvariant(c));
            }
            else
            {
                sb.Append(c);
            }
        }

        return sb.ToString();
    }

    private static string GeneratedSerializerName(ITypeSymbol target)
    {
        return "__ForySerializer_" + BuildRuntimeTypeKey(target);
    }

    private static string BuildRuntimeTypeKey(ITypeSymbol type)
    {
        List<byte> bytes = [];
        AppendRuntimeTypeKey(bytes, type);
        StringBuilder result = new(bytes.Count * 2);
        const string hex = "0123456789ABCDEF";
        foreach (byte value in bytes)
        {
            result.Append(hex[value >> 4]);
            result.Append(hex[value & 0x0F]);
        }

        return result.ToString();
    }

    private static void AppendRuntimeTypeKey(List<byte> bytes, ITypeSymbol type)
    {
        if (type.TypeKind == TypeKind.Dynamic)
        {
            AppendKeyComponent(bytes, "dynamic-object");
            return;
        }

        switch (type)
        {
            case IArrayTypeSymbol array:
                AppendKeyComponent(bytes, "array");
                AppendKeyComponent(bytes, array.Rank.ToString(CultureInfo.InvariantCulture));
                AppendKeyComponent(bytes, array.IsSZArray ? "1" : "0");
                AppendRuntimeTypeKey(bytes, array.ElementType);
                return;
            case IPointerTypeSymbol pointer:
                AppendKeyComponent(bytes, "pointer");
                AppendRuntimeTypeKey(bytes, pointer.PointedAtType);
                return;
            case INamedTypeSymbol named:
                named = named.TupleUnderlyingType ?? named;
                if (named.IsNativeIntegerType &&
                    named.NativeIntegerUnderlyingType is INamedTypeSymbol nativeUnderlying)
                {
                    named = nativeUnderlying;
                }

                AppendKeyComponent(bytes, "named");
                AppendAssemblyKey(bytes, named.OriginalDefinition.ContainingAssembly.Identity);
                AppendKeyComponent(bytes, FullMetadataName(named.OriginalDefinition));
                if (named.ContainingType is null)
                {
                    AppendKeyComponent(bytes, "no-containing-type");
                }
                else
                {
                    AppendKeyComponent(bytes, "containing-type");
                    AppendRuntimeTypeKey(bytes, named.ContainingType);
                }

                AppendKeyComponent(
                    bytes,
                    named.TypeArguments.Length.ToString(CultureInfo.InvariantCulture));
                foreach (ITypeSymbol typeArgument in named.TypeArguments)
                {
                    AppendRuntimeTypeKey(bytes, typeArgument);
                }

                return;
            default:
                AppendKeyComponent(bytes, type.TypeKind.ToString());
                AppendKeyComponent(bytes, type.ToDisplayString(SymbolDisplayFormat.FullyQualifiedFormat));
                return;
        }
    }

    private static void AppendAssemblyKey(List<byte> bytes, AssemblyIdentity identity)
    {
        AppendKeyComponent(bytes, identity.Name);
        AppendKeyComponent(bytes, identity.CultureName ?? string.Empty);
        StringBuilder token = new(identity.PublicKeyToken.Length * 2);
        const string hex = "0123456789ABCDEF";
        foreach (byte value in identity.PublicKeyToken)
        {
            token.Append(hex[value >> 4]);
            token.Append(hex[value & 0x0F]);
        }

        AppendKeyComponent(bytes, token.ToString());
        AppendKeyComponent(
            bytes,
            ((int)identity.ContentType).ToString(CultureInfo.InvariantCulture));
        AppendKeyComponent(bytes, identity.IsRetargetable ? "1" : "0");
    }

    private static void AppendKeyComponent(List<byte> bytes, string value)
    {
        byte[] valueBytes = Encoding.UTF8.GetBytes(value);
        uint length = checked((uint)valueBytes.Length);
        bytes.Add((byte)(length >> 24));
        bytes.Add((byte)(length >> 16));
        bytes.Add((byte)(length >> 8));
        bytes.Add((byte)length);
        bytes.AddRange(valueBytes);
    }

    private static string FullMetadataName(INamedTypeSymbol type)
    {
        if (type.ContainingType is not null)
        {
            return $"{FullMetadataName(type.ContainingType)}+{type.MetadataName}";
        }

        string namespaceName = type.ContainingNamespace.IsGlobalNamespace
            ? string.Empty
            : type.ContainingNamespace.ToDisplayString();
        return string.IsNullOrEmpty(namespaceName)
            ? type.MetadataName
            : $"{namespaceName}.{type.MetadataName}";
    }

    private static string Sanitize(string name)
    {
        StringBuilder sb = new(name.Length + 8);
        foreach (char c in name)
        {
            sb.Append(char.IsLetterOrDigit(c) ? c : '_');
        }

        return sb.ToString();
    }

    private sealed class TypeResolution
    {
        public TypeResolution(bool supported, TypeClassification classification)
        {
            Supported = supported;
            Classification = classification;
        }

        public bool Supported { get; }
        public TypeClassification Classification { get; }
    }

    private sealed class TypeClassification
    {
        public TypeClassification(
            uint typeId,
            bool isPrimitive,
            bool isBuiltIn,
            bool isCollection,
            bool isMap,
            bool isCompressedNumeric,
            int primitiveSize)
        {
            TypeId = typeId;
            IsPrimitive = isPrimitive;
            IsBuiltIn = isBuiltIn;
            IsCollection = isCollection;
            IsMap = isMap;
            IsCompressedNumeric = isCompressedNumeric;
            PrimitiveSize = primitiveSize;
        }

        public uint TypeId { get; }
        public bool IsPrimitive { get; }
        public bool IsBuiltIn { get; }
        public bool IsCollection { get; }
        public bool IsMap { get; }
        public bool IsCompressedNumeric { get; }
        public int PrimitiveSize { get; }
    }

    private sealed class TypeMetaFieldTypeModel
    {
        public TypeMetaFieldTypeModel(
            string typeIdExpr,
            bool nullable,
            bool trackRefByContext,
            ImmutableArray<TypeMetaFieldTypeModel> generics)
        {
            TypeIdExpr = typeIdExpr;
            Nullable = nullable;
            TrackRefByContext = trackRefByContext;
            Generics = generics;
        }

        public string TypeIdExpr { get; }
        public bool Nullable { get; }
        public bool TrackRefByContext { get; }
        public ImmutableArray<TypeMetaFieldTypeModel> Generics { get; }
    }

    private sealed class SchemaTypeModel
    {
        public SchemaTypeModel(
            uint typeId,
            SchemaTypeKind kind,
            ImmutableArray<SchemaTypeModel> generics,
            bool hasExplicitScalarEncoding = false)
        {
            TypeId = typeId;
            Kind = kind;
            Generics = generics;
            HasExplicitScalarEncoding = hasExplicitScalarEncoding;
        }

        public uint TypeId { get; }
        public SchemaTypeKind Kind { get; }
        public ImmutableArray<SchemaTypeModel> Generics { get; }
        public bool HasExplicitScalarEncoding { get; }
    }

    private sealed class FieldCodecModel
    {
        public FieldCodecModel(
            FieldCodecKind kind,
            uint typeId,
            string typeName,
            bool nullable,
            bool nullableValueType,
            CarrierKind carrierKind,
            ImmutableArray<FieldCodecModel> generics)
        {
            Kind = kind;
            TypeId = typeId;
            TypeName = typeName;
            Nullable = nullable;
            NullableValueType = nullableValueType;
            CarrierKind = carrierKind;
            Generics = generics;
        }

        public FieldCodecKind Kind { get; }
        public uint TypeId { get; }
        public string TypeName { get; }
        public bool Nullable { get; }
        public bool NullableValueType { get; }
        public CarrierKind CarrierKind { get; }
        public ImmutableArray<FieldCodecModel> Generics { get; }
    }

    private sealed class RuntimeTypeComparer : IEqualityComparer<ITypeSymbol>
    {
        // CLR registration erases source-only distinctions such as tuple names,
        // dynamic, and native-integer aliases; equality and hashing must erase
        // the same distinctions before selecting one generated owner.
        public static readonly RuntimeTypeComparer Instance = new();

        public bool Equals(ITypeSymbol? left, ITypeSymbol? right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left is null || right is null)
            {
                return false;
            }

            if (IsDynamicOrObject(left) || IsDynamicOrObject(right))
            {
                return IsDynamicOrObject(left) && IsDynamicOrObject(right);
            }

            if (left is IArrayTypeSymbol || right is IArrayTypeSymbol)
            {
                if (left is not IArrayTypeSymbol leftArray ||
                    right is not IArrayTypeSymbol rightArray)
                {
                    return false;
                }

                return leftArray.Rank == rightArray.Rank &&
                       leftArray.IsSZArray == rightArray.IsSZArray &&
                       Equals(leftArray.ElementType, rightArray.ElementType);
            }

            if (left is INamedTypeSymbol leftNamed &&
                right is INamedTypeSymbol rightNamed)
            {
                return NamedEquals(
                    NormalizeNamed(leftNamed),
                    NormalizeNamed(rightNamed));
            }

            return SymbolEqualityComparer.Default.Equals(left, right);
        }

        public int GetHashCode(ITypeSymbol type)
        {
            if (IsDynamicOrObject(type))
            {
                return (int)SpecialType.System_Object;
            }

            if (type is IArrayTypeSymbol array)
            {
                int hash = CombineHash(17, (int)TypeKind.Array);
                hash = CombineHash(hash, array.Rank);
                hash = CombineHash(hash, array.IsSZArray ? 1 : 0);
                return CombineHash(hash, GetHashCode(array.ElementType));
            }

            if (type is INamedTypeSymbol named)
            {
                return NamedHash(NormalizeNamed(named));
            }

            return SymbolEqualityComparer.Default.GetHashCode(type);
        }

        private bool NamedEquals(
            INamedTypeSymbol left,
            INamedTypeSymbol right)
        {
            if (!SymbolEqualityComparer.Default.Equals(
                    left.OriginalDefinition,
                    right.OriginalDefinition) ||
                left.TypeArguments.Length != right.TypeArguments.Length)
            {
                return false;
            }

            INamedTypeSymbol? leftContaining = left.ContainingType;
            INamedTypeSymbol? rightContaining = right.ContainingType;
            if ((leftContaining is null) != (rightContaining is null) ||
                leftContaining is not null &&
                !Equals(leftContaining, rightContaining))
            {
                return false;
            }

            for (int i = 0; i < left.TypeArguments.Length; i++)
            {
                if (!Equals(left.TypeArguments[i], right.TypeArguments[i]))
                {
                    return false;
                }
            }

            return true;
        }

        private int NamedHash(INamedTypeSymbol type)
        {
            int hash = CombineHash(
                23,
                SymbolEqualityComparer.Default.GetHashCode(type.OriginalDefinition));
            if (type.ContainingType is not null)
            {
                hash = CombineHash(hash, GetHashCode(type.ContainingType));
            }

            foreach (ITypeSymbol typeArgument in type.TypeArguments)
            {
                hash = CombineHash(hash, GetHashCode(typeArgument));
            }

            return hash;
        }

        private static INamedTypeSymbol NormalizeNamed(INamedTypeSymbol type)
        {
            type = NormalizeTuple(type);

            if (type.IsNativeIntegerType &&
                type.NativeIntegerUnderlyingType is INamedTypeSymbol nativeUnderlying)
            {
                return nativeUnderlying;
            }

            return type;
        }

        private static INamedTypeSymbol NormalizeTuple(INamedTypeSymbol type)
        {
            return type.TupleUnderlyingType ?? type;
        }

        private static bool IsDynamicOrObject(ITypeSymbol type)
        {
            return type.TypeKind == TypeKind.Dynamic ||
                   type.SpecialType == SpecialType.System_Object;
        }

        private static int CombineHash(int current, int value)
        {
            return unchecked(current * 31 + value);
        }
    }

    private sealed class TypeModel
    {
        public TypeModel(
            string declarationName,
            string targetTypeName,
            ITypeSymbol targetType,
            string serializerName,
            DeclKind kind,
            bool evolving,
            Location? declarationLocation,
            ImmutableArray<MemberModel> members,
            ImmutableArray<MemberModel> sortedMembers,
            ImmutableArray<Diagnostic> diagnostics,
            ImmutableArray<UnionCaseModel> unionCases = default,
            ImmutableArray<MemberModel> declaredMembers = default,
            ShallowStorageModel? shallowStorage = null,
            bool isOrdinary = false,
            bool isExternal = false,
            bool emitSerializerBody = true,
            bool registerSerializer = true,
            string providerVisibility = "internal")
        {
            DeclarationName = declarationName;
            TargetTypeName = targetTypeName;
            TargetType = targetType;
            SerializerName = serializerName;
            Kind = kind;
            Evolving = evolving;
            DeclarationLocation = declarationLocation;
            Members = members;
            SortedMembers = sortedMembers;
            Diagnostics = diagnostics;
            UnionCases = unionCases.IsDefault
                ? ImmutableArray<UnionCaseModel>.Empty
                : unionCases;
            DeclaredMembers = declaredMembers.IsDefault ? members : declaredMembers;
            if (kind == DeclKind.Class && shallowStorage is null)
            {
                throw new ArgumentException(
                    "Class type models require an explicit shallow-storage model.",
                    nameof(shallowStorage));
            }

            ShallowStorage = shallowStorage ?? ShallowStorageModel.Empty;
            IsOrdinary = isOrdinary;
            IsExternal = isExternal;
            EmitSerializerBody = emitSerializerBody;
            RegisterSerializer = registerSerializer;
            ProviderVisibility = providerVisibility;
        }

        public string DeclarationName { get; }
        public string TargetTypeName { get; }
        public ITypeSymbol TargetType { get; }
        public string SerializerName { get; }
        public DeclKind Kind { get; }
        public bool Evolving { get; }
        public Location? DeclarationLocation { get; }
        public ImmutableArray<MemberModel> Members { get; }
        public ImmutableArray<MemberModel> SortedMembers { get; }
        public ImmutableArray<Diagnostic> Diagnostics { get; }
        public ImmutableArray<UnionCaseModel> UnionCases { get; }
        public ImmutableArray<MemberModel> DeclaredMembers { get; }
        public ShallowStorageModel ShallowStorage { get; }
        public bool IsOrdinary { get; }
        public bool IsExternal { get; }
        public bool EmitSerializerBody { get; }
        public bool RegisterSerializer { get; }
        public string ProviderVisibility { get; }

        public TypeModel WithHierarchy(
            ImmutableArray<MemberModel> members,
            ImmutableArray<MemberModel> sortedMembers,
            string? parentProviderTypeName)
        {
            return new TypeModel(
                DeclarationName,
                TargetTypeName,
                TargetType,
                SerializerName,
                Kind,
                Evolving,
                DeclarationLocation,
                members,
                sortedMembers,
                Diagnostics,
                UnionCases,
                DeclaredMembers,
                Kind == DeclKind.Class
                    ? ShallowStorage.WithParent(parentProviderTypeName)
                    : ShallowStorage,
                IsOrdinary,
                IsExternal,
                EmitSerializerBody,
                RegisterSerializer,
                ProviderVisibility);
        }
    }

    private sealed class ShallowStorageModel
    {
        public static readonly ShallowStorageModel Empty = new(
            null,
            ImmutableArray<ShallowFieldModel>.Empty);

        public ShallowStorageModel(
            string? parentProviderTypeName,
            ImmutableArray<ShallowFieldModel> declaredFields)
        {
            ParentProviderTypeName = parentProviderTypeName;
            DeclaredFields = declaredFields.IsDefault
                ? ImmutableArray<ShallowFieldModel>.Empty
                : declaredFields;
        }

        public string? ParentProviderTypeName { get; }
        public ImmutableArray<ShallowFieldModel> DeclaredFields { get; }

        public ShallowStorageModel WithParent(string? parentProviderTypeName)
        {
            return new ShallowStorageModel(parentProviderTypeName, DeclaredFields);
        }
    }

    private sealed class ShallowFieldModel
    {
        public ShallowFieldModel(
            string identity,
            string name,
            string typeName,
            string memoryExpression,
            Location? location)
        {
            Identity = identity;
            Name = name;
            TypeName = typeName;
            MemoryExpression = memoryExpression;
            Location = location;
        }

        public string Identity { get; }
        public string Name { get; }
        public string TypeName { get; }
        public string MemoryExpression { get; }
        public Location? Location { get; }
    }

    private sealed class ExternalMemberMapping
    {
        public ExternalMemberMapping(
            bool ignore,
            INamedTypeSymbol? declaringType,
            string targetMemberName,
            ExternalTargetMemberKind memberKind)
        {
            Ignore = ignore;
            DeclaringType = declaringType;
            TargetMemberName = targetMemberName;
            MemberKind = memberKind;
        }

        public bool Ignore { get; }
        public INamedTypeSymbol? DeclaringType { get; }
        public string TargetMemberName { get; }
        public ExternalTargetMemberKind MemberKind { get; }
    }

    private sealed class ResolvedProvider
    {
        public ResolvedProvider(
            string providerTypeName,
            ImmutableArray<MemberModel> wireMembers)
        {
            ProviderTypeName = providerTypeName;
            WireMembers = wireMembers;
        }

        public string ProviderTypeName { get; }
        public ImmutableArray<MemberModel> WireMembers { get; }
    }

    private sealed class MemberModel
    {
        public MemberModel(
            string name,
            string fieldIdentifier,
            string typeName,
            bool isNullable,
            bool isNullableValueType,
            short? fieldId,
            TypeClassification classification,
            int group,
            bool isCollection,
            bool useDictionaryTypeInfoCache,
            bool isRefType,
            bool needsFieldTypeInfo,
            DynamicAnyKind dynamicAnyKind,
            TypeMetaFieldTypeModel typeMeta,
            FieldCodecModel? fieldCodec,
            bool hasSchemaType = false,
            ITypeSymbol? memberType = null,
            INamedTypeSymbol? declaringType = null,
            string? targetMemberName = null,
            WireMemberKind memberKind = WireMemberKind.Field,
            string? slotKey = null,
            string? accessorProviderTypeName = null,
            string? fieldAccessorName = null,
            string? getterAccessorName = null,
            string? setterAccessorName = null,
            string? publishedFieldAccessorName = null,
            string? publishedGetterAccessorName = null,
            string? publishedSetterAccessorName = null,
            string? codeKey = null,
            ITypeSymbol? schemaDescriptorType = null,
            int declarationOrdinal = 0,
            ImmutableArray<byte> nullableShape = default,
            bool useDeclaringCast = false)
        {
            Name = name;
            FieldIdentifier = fieldIdentifier;
            TypeName = typeName;
            IsNullable = isNullable;
            IsNullableValueType = isNullableValueType;
            FieldId = fieldId;
            Classification = classification;
            Group = group;
            IsCollection = isCollection;
            UseDictionaryTypeInfoCache = useDictionaryTypeInfoCache;
            IsRefType = isRefType;
            NeedsFieldTypeInfo = needsFieldTypeInfo;
            DynamicAnyKind = dynamicAnyKind;
            TypeMeta = typeMeta;
            FieldCodec = fieldCodec;
            HasSchemaType = hasSchemaType;
            MemberType = memberType;
            DeclaringType = declaringType;
            TargetMemberName = targetMemberName ?? name;
            MemberKind = memberKind;
            SlotKey = slotKey;
            AccessorProviderTypeName = accessorProviderTypeName;
            FieldAccessorName = fieldAccessorName;
            GetterAccessorName = getterAccessorName;
            SetterAccessorName = setterAccessorName;
            PublishedFieldAccessorName = publishedFieldAccessorName ?? fieldAccessorName;
            PublishedGetterAccessorName = publishedGetterAccessorName ?? getterAccessorName;
            PublishedSetterAccessorName = publishedSetterAccessorName ?? setterAccessorName;
            CodeKey = codeKey ?? "M0";
            SchemaDescriptorType = schemaDescriptorType;
            DeclarationOrdinal = declarationOrdinal;
            NullableShape = nullableShape.IsDefault ? ImmutableArray<byte>.Empty : nullableShape;
            UseDeclaringCast = useDeclaringCast;
        }

        public string Name { get; }
        public string FieldIdentifier { get; }
        public string TypeName { get; }
        public bool IsNullable { get; }
        public bool IsNullableValueType { get; }
        public short? FieldId { get; }
        public TypeClassification Classification { get; }
        public int Group { get; }
        public bool IsCollection { get; }
        public bool UseDictionaryTypeInfoCache { get; }
        public bool IsRefType { get; }
        public bool NeedsFieldTypeInfo { get; }
        public DynamicAnyKind DynamicAnyKind { get; }
        public TypeMetaFieldTypeModel TypeMeta { get; }
        public FieldCodecModel? FieldCodec { get; }
        public bool HasSchemaType { get; }
        public ITypeSymbol? MemberType { get; }
        public INamedTypeSymbol? DeclaringType { get; }
        public string TargetMemberName { get; }
        public WireMemberKind MemberKind { get; }
        public string? SlotKey { get; }
        public string? AccessorProviderTypeName { get; }
        public string? FieldAccessorName { get; }
        public string? GetterAccessorName { get; }
        public string? SetterAccessorName { get; }
        public string? PublishedFieldAccessorName { get; }
        public string? PublishedGetterAccessorName { get; }
        public string? PublishedSetterAccessorName { get; }
        public string CodeKey { get; }
        public ITypeSymbol? SchemaDescriptorType { get; }
        public int DeclarationOrdinal { get; }
        public ImmutableArray<byte> NullableShape { get; }
        public bool UseDeclaringCast { get; }

        public string ReadExpression(string valueExpression)
        {
            if (FieldAccessorName is not null)
            {
                return $"{AccessorProviderTypeName}.{FieldAccessorName}({valueExpression})";
            }

            if (GetterAccessorName is not null)
            {
                return $"{AccessorProviderTypeName}.{GetterAccessorName}({valueExpression})";
            }

            string receiver = !UseDeclaringCast || DeclaringType is null
                ? valueExpression
                : $"(({DeclaringType.ToDisplayString(FullNameFormat)}){valueExpression})";
            return $"{receiver}.{EscapeIdentifier(TargetMemberName)}";
        }

        public string AssignmentTarget(string valueExpression)
        {
            if (FieldAccessorName is not null)
            {
                return $"{AccessorProviderTypeName}.{FieldAccessorName}({valueExpression})";
            }

            string receiver = !UseDeclaringCast || DeclaringType is null
                ? valueExpression
                : $"(({DeclaringType.ToDisplayString(FullNameFormat)}){valueExpression})";
            return $"{receiver}.{EscapeIdentifier(TargetMemberName)}";
        }

        public MemberModel WithCodeKey(string codeKey)
        {
            return Copy(
                declaringType: DeclaringType,
                accessorProviderTypeName: AccessorProviderTypeName,
                fieldAccessorName: FieldAccessorName,
                getterAccessorName: GetterAccessorName,
                setterAccessorName: SetterAccessorName,
                publishedFieldAccessorName: PublishedFieldAccessorName,
                publishedGetterAccessorName: PublishedGetterAccessorName,
                publishedSetterAccessorName: PublishedSetterAccessorName,
                codeKey: codeKey,
                useDeclaringCast: UseDeclaringCast);
        }

        public MemberModel WithAccess(
            INamedTypeSymbol? declaringType,
            string? accessorProviderTypeName,
            string? fieldAccessorName,
            string? getterAccessorName,
            string? setterAccessorName,
            bool useDeclaringCast = false)
        {
            return Copy(
                declaringType,
                accessorProviderTypeName,
                fieldAccessorName,
                getterAccessorName,
                setterAccessorName,
                PublishedFieldAccessorName,
                PublishedGetterAccessorName,
                PublishedSetterAccessorName,
                CodeKey,
                useDeclaringCast);
        }

        public MemberModel WithPublishedAccessors(
            string? fieldAccessorName,
            string? getterAccessorName,
            string? setterAccessorName)
        {
            return Copy(
                DeclaringType,
                AccessorProviderTypeName,
                FieldAccessorName,
                GetterAccessorName,
                SetterAccessorName,
                fieldAccessorName,
                getterAccessorName,
                setterAccessorName,
                CodeKey,
                UseDeclaringCast);
        }

        public MemberModel WithDeclaration(
            ITypeSymbol memberType,
            INamedTypeSymbol declaringType,
            string targetMemberName,
            WireMemberKind memberKind,
            string? slotKey,
            string? accessorProviderTypeName,
            string? fieldAccessorName,
            string? getterAccessorName,
            string? setterAccessorName,
            ITypeSymbol? schemaDescriptorType,
            int declarationOrdinal,
            ImmutableArray<byte> nullableShape)
        {
            return new MemberModel(
                Name,
                FieldIdentifier,
                TypeName,
                IsNullable,
                IsNullableValueType,
                FieldId,
                Classification,
                Group,
                IsCollection,
                UseDictionaryTypeInfoCache,
                IsRefType,
                NeedsFieldTypeInfo,
                DynamicAnyKind,
                TypeMeta,
                FieldCodec,
                HasSchemaType,
                memberType,
                declaringType,
                targetMemberName,
                memberKind,
                slotKey,
                accessorProviderTypeName,
                fieldAccessorName,
                getterAccessorName,
                setterAccessorName,
                publishedFieldAccessorName: fieldAccessorName,
                publishedGetterAccessorName: getterAccessorName,
                publishedSetterAccessorName: setterAccessorName,
                CodeKey,
                schemaDescriptorType,
                declarationOrdinal,
                nullableShape);
        }

        private MemberModel Copy(
            INamedTypeSymbol? declaringType,
            string? accessorProviderTypeName,
            string? fieldAccessorName,
            string? getterAccessorName,
            string? setterAccessorName,
            string? publishedFieldAccessorName,
            string? publishedGetterAccessorName,
            string? publishedSetterAccessorName,
            string codeKey,
            bool useDeclaringCast)
        {
            return new MemberModel(
                Name,
                FieldIdentifier,
                TypeName,
                IsNullable,
                IsNullableValueType,
                FieldId,
                Classification,
                Group,
                IsCollection,
                UseDictionaryTypeInfoCache,
                IsRefType,
                NeedsFieldTypeInfo,
                DynamicAnyKind,
                TypeMeta,
                FieldCodec,
                HasSchemaType,
                MemberType,
                declaringType,
                TargetMemberName,
                MemberKind,
                SlotKey,
                accessorProviderTypeName,
                fieldAccessorName,
                getterAccessorName,
                setterAccessorName,
                publishedFieldAccessorName,
                publishedGetterAccessorName,
                publishedSetterAccessorName,
                codeKey,
                SchemaDescriptorType,
                DeclarationOrdinal,
                NullableShape,
                useDeclaringCast);
        }
    }

    private sealed class UnionCaseModel
    {
        public UnionCaseModel(int? caseId, string typeName, bool isUnknown, MemberModel? valueMember)
        {
            CaseId = caseId;
            TypeName = typeName;
            IsUnknown = isUnknown;
            ValueMember = valueMember;
        }

        public int? CaseId { get; }
        public int KnownCaseId => CaseId ?? throw new InvalidOperationException("unknown union carrier has no schema case id");
        public string TypeName { get; }
        public bool IsUnknown { get; }
        public MemberModel? ValueMember { get; }
    }

    private enum DeclKind
    {
        Unknown,
        Class,
        Struct,
        Enum,
        Union,
    }

    private enum ForyAttributeKind
    {
        None,
        Struct,
        Enum,
        Union,
    }

    private enum DynamicAnyKind
    {
        None,
        AnyValue,
    }

    private enum WireMemberKind
    {
        Field,
        Property,
    }

    private enum ExternalTargetMemberKind
    {
        Auto = 0,
        Field = 1,
        Property = 2,
    }

    private enum ForyProviderKind
    {
        Ordinary = 0,
        External = 1,
    }

    private enum SchemaTypeKind
    {
        Scalar,
        PackedArray,
        List,
        Set,
        Map,
    }

    private enum FieldCodecKind
    {
        Scalar,
        PackedArray,
        List,
        Set,
        Map,
    }

    private enum CarrierKind
    {
        Value,
        Array,
        List,
        HashSet,
        Dictionary,
        NullableKeyDictionary,
    }
}
