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

package io.cml.wireprotocol;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.cml.spi.CatalogSchemaTableName;
import io.trino.sql.parser.SqlBaseLexer;
import io.trino.sql.tree.AstVisitor;
import io.trino.sql.tree.CurrentUser;
import io.trino.sql.tree.DereferenceExpression;
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.FunctionCall;
import io.trino.sql.tree.Identifier;
import io.trino.sql.tree.Join;
import io.trino.sql.tree.JoinCriteria;
import io.trino.sql.tree.JoinOn;
import io.trino.sql.tree.JoinUsing;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Select;
import io.trino.sql.tree.SelectItem;
import io.trino.sql.tree.SimpleGroupBy;
import io.trino.sql.tree.SingleColumn;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.Window;
import io.trino.sql.tree.WindowReference;
import io.trino.sql.tree.WindowSpecification;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.cml.pgcatalog.table.PgCatalogTableUtils.INFORMATION_SCHEMA;
import static io.trino.sql.QueryUtil.functionCall;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.toList;

public class PostgreSqlRewrite
{
    private static final String PGCATALOG_TABLE_PREFIX = "pg_";
    private static final String PGCATALOG_SCHEMA = "default";
    private static final String PGCATALOG = "pg_catalog";
    private static final List<String> SYSTEM_SCHEMAS = List.of(INFORMATION_SCHEMA, PGCATALOG);
    private static final List<String> NON_RESERVED = List.of("session_user", "user");

    private static final Set<String> KEYWORDS = ImmutableSet.copyOf(SqlBaseLexer.ruleNames);

    public Statement rewrite(Statement statement)
    {
        return (Statement) new Visitor().process(statement);
    }

    public Statement rewrite(
            Statement statement,
            @Nullable String catalogName,
            @Nullable String schemaName)
    {
        return (Statement) new Visitor().process(statement);
    }

    private static class Visitor
            extends AstVisitor<Node, Void>
    {
        private final List<Identifier> joinUsingCriteria = new ArrayList<>();
        private final Map<Identifier, Expression> selectItemsMap = new HashMap<>();

        @Override
        protected Node visitSelect(Select node, Void context)
        {
            List<SelectItem> selectItems = node.getSelectItems().stream()
                    .map(selectItem -> {
                        if (selectItem instanceof SingleColumn) {
                            return removeBaseIfBelongJoinUsingCriterias((SingleColumn) selectItem);
                        }
                        return selectItem;
                    }).collect(toImmutableList());

            if (node.getLocation().isPresent()) {
                return new Select(
                        node.getLocation().get(),
                        node.isDistinct(),
                        visitNodes(selectItems));
            }
            return new Select(
                    node.isDistinct(),
                    visitNodes(selectItems));
        }

        @Override
        protected Node visitSimpleGroupBy(SimpleGroupBy node, Void context)
        {
            ImmutableList.Builder<Expression> builder = ImmutableList.builder();
            node.getExpressions().forEach(expression -> {
                if (isColumnAlias(expression)) {
                    builder.add(selectItemsMap.get((Identifier) expression));
                    return;
                }
                builder.add(expression);
            });
            List<Expression> expressions = builder.build();
            return (node.getLocation().isPresent()) ?
                    new SimpleGroupBy(node.getLocation().get(), expressions) :
                    new SimpleGroupBy(expressions);
        }

        private boolean isColumnAlias(Expression expression)
        {
            return expression instanceof Identifier && selectItemsMap.containsKey(expression);
        }

        private SingleColumn removeBaseIfBelongJoinUsingCriterias(SingleColumn singleColumn)
        {
            if (singleColumn.getExpression() instanceof DereferenceExpression) {
                Identifier joinUsingColumn = ((DereferenceExpression) singleColumn.getExpression()).getField();
                if (joinUsingCriteria.contains(joinUsingColumn)) {
                    if (singleColumn.getLocation().isPresent()) {
                        return new SingleColumn(singleColumn.getLocation().get(), joinUsingColumn, singleColumn.getAlias());
                    }
                    return new SingleColumn(joinUsingColumn, singleColumn.getAlias());
                }
            }
            return singleColumn;
        }

        @Override
        protected Node visitSingleColumn(SingleColumn node, Void context)
        {
            Expression expression = node.getExpression();
            node.getAlias().ifPresent(identifier -> selectItemsMap.put(identifier, expression));

            Optional<Identifier> alias = rewriteAlias(node.getAlias(), expression);

            return (node.getLocation().isPresent()) ?
                    new SingleColumn(
                            node.getLocation().get(),
                            visitAndCast(expression),
                            alias) :
                    new SingleColumn(
                            visitAndCast(expression),
                            alias);
        }

        private Optional<Identifier> rewriteAlias(Optional<Identifier> alias, Expression expression)
        {
            if (alias.isPresent()) {
                return alias;
            }

            if (expression instanceof DereferenceExpression) {
                DereferenceExpression base = (DereferenceExpression) expression;
                if (base.getBase() instanceof FunctionCall) {
                    return Optional.of(new Identifier(base.getField().getValue()));
                }
                return Optional.empty();
            }
            else if (expression instanceof Identifier) {
                String value = ((Identifier) expression).getValue();
                if (isNonReservedLexer(value)) {
                    return Optional.of(new Identifier(value));
                }
                return Optional.empty();
            }
            else if (expression instanceof FunctionCall) {
                FunctionCall functionCallExpression = (FunctionCall) expression;
                return Optional.of(new Identifier(functionCallExpression.getName().getSuffix()));
            }
            return Optional.of(new Identifier("?column?"));
        }

        private static boolean isNonReservedLexer(String value)
        {
            return NON_RESERVED.contains(value);
        }

        @Override
        protected Node visitJoin(Join node, Void context)
        {
            node.getCriteria().filter(criteria -> criteria instanceof JoinUsing)
                    .map(criteria -> ((JoinUsing) criteria).getColumns())
                    .ifPresent(joinUsingCriteria::addAll);

            if (node.getLocation().isPresent()) {
                return new Join(
                        node.getLocation().get(),
                        node.getType(),
                        visitAndCast(node.getLeft()),
                        visitAndCast(node.getRight()),
                        node.getCriteria().map(this::visitJoinCriteria));
            }
            return new Join(
                    node.getType(),
                    visitAndCast(node.getLeft()),
                    visitAndCast(node.getRight()),
                    node.getCriteria().map(this::visitJoinCriteria));
        }

        @Override
        protected Node visitTable(Table node, Void context)
        {
            if (isBelongPgCatalog(node.getName().getParts())) {
                if (node.getLocation().isPresent()) {
                    return new Table(
                            node.getLocation().get(),
                            qualifiedName(toPgCatalogSchemaTableName(node.getName().getParts())));
                }
                return new Table(qualifiedName(toPgCatalogSchemaTableName(node.getName().getParts())));
            }
            if (node.getLocation().isPresent()) {
                return new Table(
                        node.getLocation().get(),
                        node.getName());
            }
            return new Table(node.getName());
        }

        @Override
        protected Node visitDereferenceExpression(DereferenceExpression node, Void context)
        {
            QualifiedName name = getQualifiedName(node.getBase());
            Expression base;
            if (name == null) {
                base = visitAndCast(node.getBase());
            }
            else if (isBelongPgCatalog(name.getParts())) {
                base = DereferenceExpression.from(qualifiedName(toPgCatalogSchemaTableName(name.getParts())));
            }
            else {
                base = DereferenceExpression.from(name);
            }
            if (node.getLocation().isPresent()) {
                return new DereferenceExpression(
                        node.getLocation().get(),
                        base,
                        node.getField());
            }
            return new DereferenceExpression(
                    base,
                    node.getField());
        }

        @Override
        protected Node visitFunctionCall(FunctionCall node, Void context)
        {
            switch (node.getName().getSuffix()) {
                case "version":
                    return functionCall("pg_version");
                case "to_date":
                    return functionCall("pg_to_date");
            }
            return new FunctionCall(
                    node.getLocation(),
                    removeNamespace(node.getName()),
                    node.getWindow().map(this::visitAndCast),
                    node.getFilter().map(this::visitAndCast),
                    node.getOrderBy().map(this::visitAndCast),
                    node.isDistinct(),
                    node.getNullTreatment(),
                    visitNodes(node.getArguments()));
        }

        @Override
        protected Node visitCurrentUser(CurrentUser node, Void context)
        {
            return functionCall("$current_user");
        }

        @Override
        protected Node visitIdentifier(Identifier node, Void context)
        {
            if (List.of("session_user", "user").contains(node.getValue().toLowerCase(Locale.ROOT))) {
                return functionCall("$current_user");
            }
            return super.visitIdentifier(node, context);
        }

        protected JoinCriteria visitJoinCriteria(JoinCriteria joinCriteria)
        {
            if (joinCriteria instanceof JoinOn) {
                JoinOn joinOn = (JoinOn) joinCriteria;
                return new JoinOn(visitAndCast(joinOn.getExpression()));
            }

            return joinCriteria;
        }

        private boolean isBelongPgCatalog(List<String> parts)
        {
            // sql submitted by pg jdbc will only like `pg_type` and `pg_catalog.pg_type`.
            if (parts.size() == 1) {
                return parts.get(0).startsWith(PGCATALOG_TABLE_PREFIX);
            }
            else if (parts.size() == 2) {
                return parts.get(0).equals(PGCATALOG) && parts.get(1).startsWith(PGCATALOG_TABLE_PREFIX);
            }
            return false;
        }

        private static CatalogSchemaTableName toPgCatalogSchemaTableName(List<String> parts)
        {
            return new CatalogSchemaTableName(PGCATALOG, PGCATALOG_SCHEMA, parts.get(parts.size() - 1));
        }

        private static QualifiedName removeNamespace(QualifiedName name)
        {
            if (SYSTEM_SCHEMAS.contains(name.getOriginalParts().get(0).getValue())) {
                return QualifiedName.of(name.getSuffix());
            }
            return name;
        }

        protected <T extends Node> List<T> visitNodes(List<T> nodes)
        {
            return nodes.stream()
                    .map(node -> (T) process(node))
                    .collect(toList());
        }

        protected <T extends Node> T visitAndCast(T node)
        {
            return (T) process(node);
        }

        protected <T extends Window> T visitAndCast(T window)
        {
            Node node = null;
            if (window instanceof WindowSpecification) {
                node = (WindowSpecification) window;
            }
            else if (window instanceof WindowReference) {
                node = (WindowReference) window;
            }
            return (T) process(node);
        }
    }

    protected static QualifiedName getQualifiedName(Expression expression)
    {
        if (expression instanceof DereferenceExpression) {
            return DereferenceExpression.getQualifiedName((DereferenceExpression) expression);
        }
        if (expression instanceof Identifier) {
            return QualifiedName.of(ImmutableList.of((Identifier) expression));
        }
        return null;
    }

    protected static QualifiedName qualifiedName(CatalogSchemaTableName table)
    {
        return QualifiedName.of(ImmutableList.of(
                new Identifier(table.getCatalogName()),
                identifier(table.getSchemaTableName().getSchemaName()),
                identifier(table.getSchemaTableName().getTableName())));
    }

    protected static Identifier identifier(String name)
    {
        if (KEYWORDS.contains(name.toUpperCase(ENGLISH))) {
            return new Identifier(name, true);
        }
        return new Identifier(name);
    }
}