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

import com.google.common.collect.ImmutableList;
import io.accio.base.AccioMDL;
import io.accio.base.dto.Column;
import io.accio.base.dto.Manifest;
import io.accio.base.dto.Model;
import io.accio.base.dto.Relationship;
import io.accio.testing.AbstractTestFramework;
import org.testng.annotations.Test;

import java.util.List;

import static io.accio.base.AccioTypes.BIGINT;
import static io.accio.base.AccioTypes.DATE;
import static io.accio.base.AccioTypes.INTEGER;
import static io.accio.base.AccioTypes.VARCHAR;
import static io.accio.base.dto.Column.caluclatedColumn;
import static io.accio.base.dto.Column.column;
import static io.accio.base.dto.JoinType.MANY_TO_ONE;
import static io.accio.base.dto.JoinType.ONE_TO_MANY;
import static io.accio.base.dto.Model.model;
import static io.accio.base.dto.Relationship.relationship;
import static io.accio.sqlrewrite.AccioSqlRewrite.ACCIO_SQL_REWRITE;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class TestModel
        extends AbstractTestFramework
{
    private final Model customer;
    private final Model orders;
    private final Model lineitem;
    private final Relationship ordersCustomer;
    private final Relationship ordersLineitem;

    public TestModel()
    {
        customer = model("Customer",
                "select * from main.customer",
                List.of(
                        column("custkey", INTEGER, null, true),
                        column("name", VARCHAR, null, true),
                        column("address", VARCHAR, null, true),
                        column("nationkey", INTEGER, null, true),
                        column("phone", VARCHAR, null, true),
                        column("acctbal", INTEGER, null, true),
                        column("mktsegment", VARCHAR, null, true),
                        column("comment", VARCHAR, null, true)),
                "custkey");
        orders = model("Orders",
                "select * from main.orders",
                List.of(
                        column("orderkey", INTEGER, null, true),
                        column("custkey", INTEGER, null, true),
                        column("orderstatus", VARCHAR, null, true),
                        column("totalprice", INTEGER, null, true),
                        column("orderdate", DATE, null, true),
                        column("orderpriority", VARCHAR, null, true),
                        column("clerk", VARCHAR, null, true),
                        column("shippriority", INTEGER, null, true),
                        column("comment", VARCHAR, null, true),
                        column("lineitem", "Lineitem", "OrdersLineitem", true)),
                "orderkey");
        lineitem = model("Lineitem",
                "select * from main.lineitem",
                List.of(
                        column("orderkey", INTEGER, null, true),
                        column("partkey", INTEGER, null, true),
                        column("suppkey", INTEGER, null, true),
                        column("linenumber", INTEGER, null, true),
                        column("quantity", INTEGER, null, true),
                        column("extendedprice", INTEGER, null, true),
                        column("discount", INTEGER, null, true),
                        column("tax", INTEGER, null, true),
                        column("returnflag", VARCHAR, null, true),
                        column("linestatus", VARCHAR, null, true),
                        column("shipdate", DATE, null, true),
                        column("commitdate", DATE, null, true),
                        column("receiptdate", DATE, null, true),
                        column("shipinstruct", VARCHAR, null, true),
                        column("shipmode", VARCHAR, null, true),
                        column("comment", VARCHAR, null, true),
                        column("orderkey_linenumber", VARCHAR, null, true, "concat(orderkey, '-', linenumber)")),
                "orderkey_linenumber");
        ordersCustomer = relationship("OrdersCustomer", List.of("Orders", "Customer"), MANY_TO_ONE, "Orders.custkey = Customer.custkey");
        ordersLineitem = relationship("OrdersLineitem", List.of("Orders", "Lineitem"), ONE_TO_MANY, "Orders.orderkey = Lineitem.orderkey");
    }

    @Override
    protected void prepareData()
    {
        String orders = requireNonNull(getClass().getClassLoader().getResource("tiny-orders.parquet")).getPath();
        exec("create table orders as select * from '" + orders + "'");
        String customer = requireNonNull(getClass().getClassLoader().getResource("tiny-customer.parquet")).getPath();
        exec("create table customer as select * from '" + customer + "'");
        String lineitem = requireNonNull(getClass().getClassLoader().getResource("tiny-lineitem.parquet")).getPath();
        exec("create table lineitem as select * from '" + lineitem + "'");
    }

    @Test
    public void testToManyCalculated()
    {
        // TODO: add this to test case, currently this won't work
        // caluclatedColumn("col_3", BIGINT, "concat(address, sum(orders.lineitem.discount * orders.lineitem.extendedprice))");

        Model newCustomer = addColumnsToModel(
                customer,
                column("orders", "Orders", "OrdersCustomer", true),
                caluclatedColumn("totalprice", BIGINT, "sum(orders.totalprice)"),
                caluclatedColumn("buy_item_count", BIGINT, "count(distinct orders.lineitem.orderkey_linenumber)"),
                caluclatedColumn("lineitem_totalprice", BIGINT, "sum(orders.lineitem.discount * orders.lineitem.extendedprice)"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, orders, lineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(manifest);

        assertThat(query(rewrite("SELECT totalprice FROM Customer WHERE custkey = 370", mdl)))
                .isEqualTo(query("SELECT sum(totalprice) FROM customer c LEFT JOIN orders o ON c.custkey = o.custkey WHERE c.custkey = 370"));
        assertThat(query(rewrite("SELECT custkey, buy_item_count FROM Customer WHERE custkey = 370", mdl)))
                .isEqualTo(query(
                        "SELECT c.custkey, count(*) FROM customer c " +
                                "LEFT JOIN orders o ON c.custkey = o.custkey " +
                                "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                                "WHERE c.custkey = 370 " +
                                "GROUP BY 1"));
        assertThat(query(rewrite("SELECT custkey, lineitem_totalprice FROM Customer WHERE custkey = 370", mdl)))
                .isEqualTo(query(
                        "SELECT c.custkey, sum(l.extendedprice * l.discount) FROM customer c " +
                                "LEFT JOIN orders o ON c.custkey = o.custkey " +
                                "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                                "WHERE c.custkey = 370 " +
                                "GROUP BY 1"));
    }

    @Test
    public void testToOneCalculated()
    {
        Model newLineitem = addColumnsToModel(
                lineitem,
                column("orders", "Orders", "OrdersLineitem", true),
                caluclatedColumn("col_1", BIGINT, "orders.totalprice + orders.totalprice"),
                caluclatedColumn("col_2", BIGINT, "concat(orders.orderkey, '#', orders.customer.custkey)"));
        Model newOrders = addColumnsToModel(
                orders,
                column("customer", "Customer", "OrdersCustomer", true));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AccioMDL mdl = AccioMDL.fromManifest(manifest);

        assertThat(query(rewrite("SELECT col_1 FROM Lineitem WHERE orderkey = 44995", mdl)))
                .isEqualTo(query(
                        "SELECT (totalprice + totalprice) AS col_1\n" +
                                "FROM lineitem l\n" +
                                "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                                "WHERE l.orderkey = 44995"));
        assertThat(query(rewrite("SELECT col_2 FROM Lineitem WHERE orderkey = 44995", mdl)))
                .isEqualTo(query(
                        "SELECT concat(l.orderkey, '#', c.custkey) AS col_2\n" +
                                "FROM lineitem l\n" +
                                "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                                "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                                "WHERE l.orderkey = 44995"));
    }

    private String rewrite(String sql, AccioMDL accioMDL)
    {
        return AccioPlanner.rewrite(sql, DEFAULT_SESSION_CONTEXT, accioMDL, List.of(ACCIO_SQL_REWRITE));
    }

    private static Model addColumnsToModel(Model model, Column... columns)
    {
        return model(
                model.getName(),
                model.getRefSql(),
                ImmutableList.<Column>builder()
                        .addAll(model.getColumns())
                        .add(columns)
                        .build(),
                model.getPrimaryKey());
    }
}
