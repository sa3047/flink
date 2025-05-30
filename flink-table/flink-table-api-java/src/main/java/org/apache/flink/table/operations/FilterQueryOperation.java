/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.operations;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.expressions.SqlFactory;
import org.apache.flink.table.operations.utils.OperationExpressionsUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Filters out rows of underlying relational operation that do not match given condition. */
@Internal
public class FilterQueryOperation implements QueryOperation {

    private static final String INPUT_ALIAS = "$$T_FILTER";
    private final ResolvedExpression condition;
    private final QueryOperation child;

    public FilterQueryOperation(ResolvedExpression condition, QueryOperation child) {
        this.condition = condition;
        this.child = child;
    }

    public ResolvedExpression getCondition() {
        return condition;
    }

    @Override
    public ResolvedSchema getResolvedSchema() {
        return child.getResolvedSchema();
    }

    @Override
    public String asSummaryString() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("condition", condition);

        return OperationUtils.formatWithChildren(
                "Filter", args, getChildren(), Operation::asSummaryString);
    }

    @Override
    public String asSerializableString(SqlFactory sqlFactory) {
        return String.format(
                "SELECT %s FROM (%s\n) %s WHERE %s",
                OperationUtils.formatSelectColumns(getResolvedSchema(), INPUT_ALIAS),
                OperationUtils.indent(child.asSerializableString(sqlFactory)),
                INPUT_ALIAS,
                OperationExpressionsUtils.scopeReferencesWithAlias(INPUT_ALIAS, condition)
                        .asSerializableString(sqlFactory));
    }

    @Override
    public List<QueryOperation> getChildren() {
        return Collections.singletonList(child);
    }

    @Override
    public <T> T accept(QueryOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
