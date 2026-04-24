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

public class PrimitiveArraySerializer<T, TCodec> : Serializer<T[]>
    where TCodec : struct, IPrimitiveDictionaryCodec<T>
{
    public override T[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in T[] value, bool hasGenerics)
    {
        PrimitiveCollectionCodec.WriteValues<T, TCodec>(context, value ?? [], hasGenerics);
    }

    public override T[] ReadData(ReadContext context)
    {
        return PrimitiveCollectionCodec.ReadValues<T, TCodec>(context).ToArray();
    }
}

internal sealed class BoolArraySerializer : Serializer<bool[]>
{
    public override bool[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in bool[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        bool[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)safe.Length);
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteUInt8(safe[i] ? (byte)1 : (byte)0);
        }
    }

    public override bool[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        bool[] values = new bool[payloadSize];
        for (int i = 0; i < payloadSize; i++)
        {
            values[i] = context.Reader.ReadUInt8() != 0;
        }

        return values;
    }
}

internal sealed class Int8ArraySerializer : Serializer<sbyte[]>
{
    public override sbyte[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in sbyte[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        sbyte[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)safe.Length);
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteInt8(safe[i]);
        }
    }

    public override sbyte[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        sbyte[] values = new sbyte[payloadSize];
        for (int i = 0; i < payloadSize; i++)
        {
            values[i] = context.Reader.ReadInt8();
        }

        return values;
    }
}

internal sealed class Int16ArraySerializer : Serializer<short[]>
{
    public override short[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in short[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        short[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 2));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteInt16(safe[i]);
        }
    }

    public override short[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 1) != 0)
        {
            throw new InvalidDataException("int16 array payload size mismatch");
        }

        short[] values = new short[payloadSize / 2];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadInt16();
        }

        return values;
    }
}

public class FixedInt32ArraySerializer : Serializer<int[]>
{
    public override int[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in int[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        int[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 4));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteInt32(safe[i]);
        }
    }

    public override int[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 3) != 0)
        {
            throw new InvalidDataException("int32 array payload size mismatch");
        }

        int[] values = new int[payloadSize / 4];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadInt32();
        }

        return values;
    }
}

internal sealed class Int32ArraySerializer : FixedInt32ArraySerializer { }

public sealed class VarInt32ArraySerializer : PrimitiveArraySerializer<int, VarInt32PrimitiveDictionaryCodec> { }

public class FixedInt64ArraySerializer : Serializer<long[]>
{
    public override long[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in long[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        long[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 8));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteInt64(safe[i]);
        }
    }

    public override long[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 7) != 0)
        {
            throw new InvalidDataException("int64 array payload size mismatch");
        }

        long[] values = new long[payloadSize / 8];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadInt64();
        }

        return values;
    }
}

internal sealed class Int64ArraySerializer : FixedInt64ArraySerializer { }

public sealed class VarInt64ArraySerializer : PrimitiveArraySerializer<long, VarInt64PrimitiveDictionaryCodec> { }

public sealed class TaggedInt64ArraySerializer : PrimitiveArraySerializer<long, TaggedInt64PrimitiveDictionaryCodec> { }

public class FixedUInt16ArraySerializer : Serializer<ushort[]>
{
    public override ushort[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in ushort[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        ushort[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 2));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteUInt16(safe[i]);
        }
    }

    public override ushort[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 1) != 0)
        {
            throw new InvalidDataException("uint16 array payload size mismatch");
        }

        ushort[] values = new ushort[payloadSize / 2];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadUInt16();
        }

        return values;
    }
}

internal sealed class UInt16ArraySerializer : FixedUInt16ArraySerializer { }

public class FixedUInt32ArraySerializer : Serializer<uint[]>
{
    public override uint[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in uint[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        uint[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 4));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteUInt32(safe[i]);
        }
    }

    public override uint[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 3) != 0)
        {
            throw new InvalidDataException("uint32 array payload size mismatch");
        }

        uint[] values = new uint[payloadSize / 4];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadUInt32();
        }

        return values;
    }
}

internal sealed class UInt32ArraySerializer : FixedUInt32ArraySerializer { }

public sealed class VarUInt32ArraySerializer : PrimitiveArraySerializer<uint, VarUInt32PrimitiveDictionaryCodec> { }

public class FixedUInt64ArraySerializer : Serializer<ulong[]>
{
    public override ulong[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in ulong[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        ulong[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 8));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteUInt64(safe[i]);
        }
    }

    public override ulong[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 7) != 0)
        {
            throw new InvalidDataException("uint64 array payload size mismatch");
        }

        ulong[] values = new ulong[payloadSize / 8];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadUInt64();
        }

        return values;
    }
}

internal sealed class UInt64ArraySerializer : FixedUInt64ArraySerializer { }

public sealed class VarUInt64ArraySerializer : PrimitiveArraySerializer<ulong, VarUInt64PrimitiveDictionaryCodec> { }

public sealed class TaggedUInt64ArraySerializer : PrimitiveArraySerializer<ulong, TaggedUInt64PrimitiveDictionaryCodec> { }

public class FixedFloat16ArraySerializer : Serializer<Half[]>
{
    public override Half[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in Half[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        Half[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 2));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteUInt16(BitConverter.HalfToUInt16Bits(safe[i]));
        }
    }

    public override Half[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 1) != 0)
        {
            throw new InvalidDataException("float16 array payload size mismatch");
        }

        Half[] values = new Half[payloadSize / 2];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = BitConverter.UInt16BitsToHalf(context.Reader.ReadUInt16());
        }

        return values;
    }
}

internal sealed class Float16ArraySerializer : FixedFloat16ArraySerializer { }

public class FixedBFloat16ArraySerializer : Serializer<BFloat16[]>
{
    public override BFloat16[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in BFloat16[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        BFloat16[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 2));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteUInt16(safe[i].ToBits());
        }
    }

    public override BFloat16[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 1) != 0)
        {
            throw new InvalidDataException("bfloat16 array payload size mismatch");
        }

        BFloat16[] values = new BFloat16[payloadSize / 2];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = BFloat16.FromBits(context.Reader.ReadUInt16());
        }

        return values;
    }
}

internal sealed class BFloat16ArraySerializer : FixedBFloat16ArraySerializer { }

public class FixedFloat32ArraySerializer : Serializer<float[]>
{
    public override float[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in float[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        float[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 4));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteFloat32(safe[i]);
        }
    }

    public override float[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 3) != 0)
        {
            throw new InvalidDataException("float32 array payload size mismatch");
        }

        float[] values = new float[payloadSize / 4];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadFloat32();
        }

        return values;
    }
}

internal sealed class Float32ArraySerializer : FixedFloat32ArraySerializer { }

public class FixedFloat64ArraySerializer : Serializer<double[]>
{
    public override double[] DefaultValue => null!;

    public override void WriteData(WriteContext context, in double[] value, bool hasGenerics)
    {
        _ = hasGenerics;
        double[] safe = value ?? [];
        context.Writer.WriteVarUInt32((uint)(safe.Length * 8));
        for (int i = 0; i < safe.Length; i++)
        {
            context.Writer.WriteFloat64(safe[i]);
        }
    }

    public override double[] ReadData(ReadContext context)
    {
        int payloadSize = checked((int)context.Reader.ReadVarUInt32());
        if ((payloadSize & 7) != 0)
        {
            throw new InvalidDataException("float64 array payload size mismatch");
        }

        double[] values = new double[payloadSize / 8];
        for (int i = 0; i < values.Length; i++)
        {
            values[i] = context.Reader.ReadFloat64();
        }

        return values;
    }
}

internal sealed class Float64ArraySerializer : FixedFloat64ArraySerializer { }
