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

package io.accio.testing;

import com.google.common.collect.ImmutableList;
import io.accio.base.SessionContext;
import io.accio.base.client.AutoCloseableIterator;
import io.accio.base.client.duckdb.DuckdbClient;
import io.accio.base.dto.Manifest;
import io.trino.sql.parser.ParsingOptions;
import io.trino.sql.parser.SqlParser;
import org.intellij.lang.annotations.Language;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.util.Arrays;
import java.util.List;

import static io.trino.sql.SqlFormatter.Dialect.DUCKDB;
import static io.trino.sql.SqlFormatter.formatSql;
import static io.trino.sql.parser.ParsingOptions.DecimalLiteralTreatment.AS_DECIMAL;

public abstract class AbstractTestFramework
{
    private static final SqlParser SQL_PARSER = new SqlParser();
    public static final SessionContext DEFAULT_SESSION_CONTEXT =
            SessionContext.builder().setCatalog("accio").setSchema("test").build();
    private DuckdbClient duckdbClient;

    public static Manifest.Builder withDefaultCatalogSchema()
    {
        return Manifest.builder()
                .setCatalog(DEFAULT_SESSION_CONTEXT.getCatalog().orElseThrow())
                .setSchema(DEFAULT_SESSION_CONTEXT.getSchema().orElseThrow());
    }

    @BeforeClass
    public void init()
    {
        duckdbClient = new DuckdbClient();
        prepareData();
    }

    @AfterClass(alwaysRun = true)
    public final void close()
    {
        cleanup();
    }

    protected void prepareData() {}

    protected void cleanup() {}

    protected List<List<Object>> query(@Language("SQL") String sql)
    {
        sql = formatSql(SQL_PARSER.createStatement(sql, new ParsingOptions(AS_DECIMAL)), DUCKDB);
        try (AutoCloseableIterator<Object[]> iterator = duckdbClient.query(sql)) {
            ImmutableList.Builder<List<Object>> builder = ImmutableList.builder();
            while (iterator.hasNext()) {
                builder.add(Arrays.asList(iterator.next()));
            }
            return builder.build();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void exec(@Language("SQL") String sql)
    {
        duckdbClient.executeDDL(sql);
    }

    protected static Manifest.Builder copyOf(Manifest manifest)
    {
        return Manifest.builder()
                .setCatalog(manifest.getCatalog())
                .setSchema(manifest.getSchema())
                .setModels(manifest.getModels())
                .setRelationships(manifest.getRelationships())
                .setMetrics(manifest.getMetrics())
                .setCumulativeMetrics(manifest.getCumulativeMetrics())
                .setViews(manifest.getViews())
                .setEnumDefinitions(manifest.getEnumDefinitions());
    }
}
