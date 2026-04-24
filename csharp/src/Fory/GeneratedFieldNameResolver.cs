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

internal static class GeneratedFieldNameResolver
{
    private const string GeneratedSuffix = "Value";

    public static string GetCanonicalFieldName(Type declaringType, string memberName, Type memberType)
    {
        if (TryGetGeneratedAlias(declaringType, memberName, memberType, out string alias))
        {
            return alias;
        }

        return TypeMetaUtils.LowerCamelToLowerUnderscore(memberName);
    }

    public static bool TryGetGeneratedAlias(
        Type declaringType,
        string memberName,
        Type memberType,
        out string alias)
    {
        alias = string.Empty;
        if (!memberName.EndsWith(GeneratedSuffix, StringComparison.Ordinal) ||
            memberName.Length == GeneratedSuffix.Length)
        {
            return false;
        }

        Type normalizedMemberType = Nullable.GetUnderlyingType(memberType) ?? memberType;
        if (!normalizedMemberType.IsNested ||
            normalizedMemberType.DeclaringType != declaringType)
        {
            return false;
        }

        string nestedTypeName = normalizedMemberType.Name;
        string candidateName = memberName[..^GeneratedSuffix.Length];
        if (!string.Equals(candidateName, nestedTypeName, StringComparison.Ordinal))
        {
            return false;
        }

        alias = TypeMetaUtils.LowerCamelToLowerUnderscore(candidateName);
        return true;
    }
}
