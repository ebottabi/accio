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

package io.accio.sqlrewrite;

import io.accio.base.AccioMDL;
import io.accio.base.SessionContext;
import io.accio.sqlrewrite.analyzer.Analysis;
import io.accio.sqlrewrite.analyzer.MetricRollupInfo;
import io.accio.sqlrewrite.analyzer.StatementAnalyzer;
import io.trino.sql.tree.AliasedRelation;
import io.trino.sql.tree.FunctionRelation;
import io.trino.sql.tree.Identifier;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.NodeRef;
import io.trino.sql.tree.Query;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.TableSubquery;

import java.util.List;

import static io.accio.sqlrewrite.Utils.parseMetricRollupSql;

public class MetricRollupRewrite
        implements AccioRule
{
    public static final MetricRollupRewrite METRIC_ROLLUP_REWRITE = new MetricRollupRewrite();

    @Override
    public Statement apply(Statement root, SessionContext sessionContext, AccioMDL accioMDL)
    {
        return apply(root, sessionContext, StatementAnalyzer.analyze(root, sessionContext, accioMDL), accioMDL);
    }

    @Override
    public Statement apply(Statement root, SessionContext sessionContext, Analysis analysis, AccioMDL accioMDL)
    {
        return (Statement) new Rewriter(analysis).process(root);
    }

    private static class Rewriter
            extends BaseRewriter<Void>
    {
        private final Analysis analysis;

        Rewriter(Analysis analysis)
        {
            this.analysis = analysis;
        }

        @Override
        protected Node visitFunctionRelation(FunctionRelation node, Void context)
        {
            if (analysis.getMetricRollups().containsKey(NodeRef.of(node))) {
                MetricRollupInfo info = analysis.getMetricRollups().get(NodeRef.of(node));
                Query query = parseMetricRollupSql(info);
                return new AliasedRelation(new TableSubquery(query), new Identifier(info.getMetric().getName()), List.of());
            }
            // this should not happen, every MetricRollup node should be captured and syntax checked in StatementAnalyzer
            throw new IllegalArgumentException("MetricRollup node is not replaced");
        }
    }
}
