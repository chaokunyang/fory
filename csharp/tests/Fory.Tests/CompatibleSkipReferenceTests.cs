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

using Apache.Fory;
using ForyRuntime = Apache.Fory.Fory;

namespace Apache.Fory.Tests;

[ForyStruct]
public sealed class RemovedRefValue
{
    [ForyField(1)]
    public int Number { get; set; }

    [ForyField(2)]
    public string Text { get; set; } = string.Empty;
}

[ForyStruct]
public sealed class RemovedRefWriter
{
    [ForyField(1)]
    public RemovedRefValue Removed { get; set; } = null!;

    [ForyField(2)]
    public object? FirstAlias { get; set; }

    [ForyField(3)]
    public object? SecondAlias { get; set; }

    [ForyField(4)]
    public int Tail { get; set; }
}

[ForyStruct]
public sealed class RemovedRefReader
{
    [ForyField(2)]
    public object? FirstAlias { get; set; }

    [ForyField(3)]
    public object? SecondAlias { get; set; }

    [ForyField(4)]
    public int Tail { get; set; }
}

public sealed class CompatibleSkipReferenceTests
{
    [Fact]
    public void RemovedStructPublishesAlias()
    {
        ForyRuntime writer = ForyRuntime.Builder().Compatible(true).TrackRef(true).Build();
        writer.Register<RemovedRefWriter>(720);
        writer.Register<RemovedRefValue>(721);
        RemovedRefValue removed = new() { Number = 42, Text = "removed body" };
        byte[] bytes = writer.Serialize(new RemovedRefWriter
        {
            Removed = removed,
            FirstAlias = removed,
            SecondAlias = removed,
            Tail = 73,
        });

        ForyRuntime reader = ForyRuntime.Builder().Compatible(true).TrackRef(true).Build();
        reader.Register<RemovedRefReader>(720);
        RemovedRefReader decoded = reader.Deserialize<RemovedRefReader>(bytes);

        object skippedOwner = Assert.IsType<object>(decoded.FirstAlias);
        Assert.Same(skippedOwner, decoded.SecondAlias);
        Assert.Equal(73, decoded.Tail);

        long outerOwnerBytes = 2L * IntPtr.Size + sizeof(int) + 3L * sizeof(int);
        long skippedOwnerBytes = 2L * IntPtr.Size + sizeof(int);
        ForyRuntime constrainedReader = ForyRuntime.Builder()
            .Compatible(true)
            .TrackRef(true)
            .MaxGraphMemoryBytes(outerOwnerBytes + skippedOwnerBytes - 1)
            .Build();
        constrainedReader.Register<RemovedRefReader>(720);
        Assert.Throws<InvalidDataException>(
            () => constrainedReader.Deserialize<RemovedRefReader>(bytes));

        ForyRuntime exactReader = ForyRuntime.Builder()
            .Compatible(true)
            .TrackRef(true)
            .MaxGraphMemoryBytes(outerOwnerBytes + skippedOwnerBytes)
            .Build();
        exactReader.Register<RemovedRefReader>(720);
        Assert.Equal(73, exactReader.Deserialize<RemovedRefReader>(bytes).Tail);
    }
}
