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

package io.accio.main.pgcatalog.builder;

import io.accio.base.AccioException;
import io.accio.main.metadata.Metadata;
import io.accio.main.pgcatalog.function.PgFunction;

import javax.inject.Inject;

import static io.accio.base.metadata.StandardErrorCode.GENERIC_INTERNAL_ERROR;

public class PostgresPgFunctionBuilder
        extends PgFunctionBuilder
{
    @Inject
    public PostgresPgFunctionBuilder(Metadata connector)
    {
        super(connector);
    }

    @Override
    protected String generateCreateFunction(PgFunction pgFunction)
    {
        throw new AccioException(GENERIC_INTERNAL_ERROR, "Postgres no need to invoke this method");
    }
}
