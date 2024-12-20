/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.accio.base.type;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

import java.nio.charset.StandardCharsets;

public class AnyType
        extends PGType<Integer>
{
    // we represent the any data type as integer,
    // because the postgresql typalign of any is integer.
    public static final AnyType ANY = new AnyType();
    private static final int TYPE_LEN = 4;
    static final int OID = 2276;

    private AnyType()
    {
        super(OID, TYPE_LEN, -1, "any");
    }

    @Override
    public int typArray()
    {
        return 0;
    }

    @Override
    public String type()
    {
        return Type.PSEUDO.code();
    }

    @Override
    public String typeCategory()
    {
        return TypeCategory.PSEUDO.code();
    }

    @Override
    public int writeAsBinary(ByteBuf buffer, @Nonnull Integer value)
    {
        buffer.writeInt(TYPE_LEN);
        buffer.writeInt(value);
        return INT32_BYTE_SIZE + TYPE_LEN;
    }

    @Override
    public byte[] encodeAsUTF8Text(@Nonnull Integer value)
    {
        return Integer.toString(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Integer readBinaryValue(ByteBuf buffer, int valueLength)
    {
        return buffer.readInt();
    }

    @Override
    public Integer decodeUTF8Text(byte[] bytes)
    {
        return Integer.parseInt(new String(bytes, StandardCharsets.UTF_8));
    }
}
