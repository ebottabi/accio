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
import io.accio.base.dto.Column;
import io.accio.base.dto.JoinType;
import io.accio.base.dto.Model;
import io.accio.base.dto.Relationable;
import io.accio.base.dto.Relationship;
import io.accio.sqlrewrite.analyzer.ExpressionRelationshipInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.accio.sqlrewrite.Utils.parseQuery;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public abstract class RelationableSqlRender
{
    protected final Relationable relationable;
    protected final AccioMDL mdl;
    protected final String refSql;
    // collect dependent models
    protected final Set<String> requiredObjects;
    // key is alias_name.column_name, value is column name, this map is used to compose select items in model sql
    protected final List<String> selectItems = new ArrayList<>();
    // calculatedRequiredRelationshipInfos collects all join condition needed in model calculated field and the original column name.
    // It is used to compose join conditions in model sql.
    protected final List<CalculatedFieldRelationshipInfo> calculatedRequiredRelationshipInfos = new ArrayList<>();
    // key is column name in model, value is column expression, this map store columns not use relationships
    protected final Map<String, String> columnWithoutRelationships = new LinkedHashMap<>();

    public RelationableSqlRender(Relationable relationable, AccioMDL mdl)
    {
        this.relationable = requireNonNull(relationable);
        this.mdl = requireNonNull(mdl);
        this.refSql = initRefSql(relationable);
        this.requiredObjects = new HashSet<>();
        if (relationable.getBaseObject() != null) {
            requiredObjects.add(relationable.getBaseObject());
        }
    }

    protected abstract String initRefSql(Relationable relationable);

    public abstract RelationInfo render();

    protected RelationInfo render(Model baseModel)
    {
        requireNonNull(baseModel, "baseModel is null");
        relationable.getColumns().stream()
                .filter(column -> column.getRelationship().isEmpty() && column.getExpression().isEmpty())
                .forEach(column -> {
                    selectItems.add(getSelectItemsExpression(column, Optional.empty()));
                    columnWithoutRelationships.put(column.getName(), format("\"%s\".\"%s\"", relationable.getName(), column.getName()));
                });

        relationable.getColumns().stream()
                .filter(column -> column.getRelationship().isEmpty() && column.getExpression().isPresent())
                .forEach(column -> collectRelationship(column, baseModel));
        String modelSubQuerySelectItemsExpression = getModelSubQuerySelectItemsExpression(columnWithoutRelationships);

        String modelSubQuery = format("(SELECT %s FROM (%s) AS \"%s\") AS \"%s\"",
                modelSubQuerySelectItemsExpression,
                refSql,
                baseModel.getName(),
                baseModel.getName());

        StringBuilder tableJoinsSql = new StringBuilder(modelSubQuery);
        if (!calculatedRequiredRelationshipInfos.isEmpty()) {
            tableJoinsSql.append(
                    getCalculatedSubQuery(baseModel, calculatedRequiredRelationshipInfos).stream()
                            .map(info -> format("\nLEFT JOIN (%s) AS \"%s\" ON %s", info.getSql(), info.getSubqueryAlias(), info.getJoinCriteria()))
                            .collect(joining("")));
        }
        tableJoinsSql.append("\n");

        return new RelationInfo(
                relationable,
                requiredObjects,
                parseQuery(getQuerySql(relationable, join(", ", selectItems), tableJoinsSql.toString())));
    }

    protected static String getRelationableAlias(String baseModelName)
    {
        return baseModelName + "_relationsub";
    }

    protected abstract List<SubQueryJoinInfo> getCalculatedSubQuery(Model baseModel, List<CalculatedFieldRelationshipInfo> calculatedFieldRelationshipInfo);

    protected abstract void collectRelationship(Column column, Model baseModel);

    protected abstract String getQuerySql(Relationable relationable, String selectItemsSql, String tableJoinsSql);

    protected abstract String getModelSubQuerySelectItemsExpression(Map<String, String> columnWithoutRelationships);

    protected abstract String getSelectItemsExpression(Column column, Optional<String> relationalBase);

    public static class CalculatedFieldRelationshipInfo
    {
        private final Column column;
        private final List<ExpressionRelationshipInfo> expressionRelationshipInfo;
        private final boolean isAggregated;

        public CalculatedFieldRelationshipInfo(Column column, List<ExpressionRelationshipInfo> expressionRelationshipInfo)
        {
            this.column = requireNonNull(column);
            this.expressionRelationshipInfo = requireNonNull(expressionRelationshipInfo);
            this.isAggregated = expressionRelationshipInfo.stream()
                    .map(ExpressionRelationshipInfo::getRelationships)
                    .flatMap(List::stream)
                    .map(Relationship::getJoinType)
                    .anyMatch(JoinType::isToMany);
        }

        public String getAlias()
        {
            return column.getName();
        }

        public Column getColumn()
        {
            return column;
        }

        public List<ExpressionRelationshipInfo> getExpressionRelationshipInfo()
        {
            return expressionRelationshipInfo;
        }

        public boolean isAggregated()
        {
            return isAggregated;
        }
    }

    public static class SubQueryJoinInfo
    {
        private final String sql;
        private final String subqueryAlias;
        private final String joinCriteria;

        public SubQueryJoinInfo(String sql, String subqueryAlias, String joinCriteria)
        {
            this.sql = sql;
            this.subqueryAlias = subqueryAlias;
            this.joinCriteria = joinCriteria;
        }

        public String getSql()
        {
            return sql;
        }

        public String getSubqueryAlias()
        {
            return subqueryAlias;
        }

        public String getJoinCriteria()
        {
            return joinCriteria;
        }
    }
}
