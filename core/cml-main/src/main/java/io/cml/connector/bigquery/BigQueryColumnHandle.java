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

package io.cml.connector.bigquery;

import io.cml.metadata.ColumnHandle;
import io.cml.spi.type.PGType;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public class BigQueryColumnHandle
        implements ColumnHandle
{
    private final String name;
    private final PGType<?> type;

    public BigQueryColumnHandle(String name, PGType<?> type)
    {
        this.name = name;
        this.type = type;
    }

    public String getName()
    {
        return name;
    }

    public PGType<?> getType()
    {
        return type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, type);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof BigQueryColumnHandle)) {
            return false;
        }

        BigQueryColumnHandle columnHandle = (BigQueryColumnHandle) obj;
        if (this == obj) {
            return true;
        }
        return this.name.equals(columnHandle.name) ||
                this.type.equals(columnHandle.type);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", name)
                .add("type", type)
                .toString();
    }
}