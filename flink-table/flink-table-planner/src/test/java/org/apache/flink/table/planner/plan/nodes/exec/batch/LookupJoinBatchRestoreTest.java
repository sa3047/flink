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

package org.apache.flink.table.planner.plan.nodes.exec.batch;

import org.apache.flink.table.planner.plan.nodes.exec.common.LookupJoinTestPrograms;
import org.apache.flink.table.planner.plan.nodes.exec.testutils.BatchRestoreTestBase;
import org.apache.flink.table.test.program.TableTestProgram;

import java.util.Arrays;
import java.util.List;

/** Batch Compiled Plan tests for {@link BatchExecLookupJoin}. */
public class LookupJoinBatchRestoreTest extends BatchRestoreTestBase {

    public LookupJoinBatchRestoreTest() {
        super(BatchExecLookupJoin.class);
    }

    @Override
    public List<TableTestProgram> programs() {
        return Arrays.asList(
                LookupJoinTestPrograms.LOOKUP_JOIN_FILTER_PUSHDOWN,
                LookupJoinTestPrograms.LOOKUP_JOIN_LEFT_JOIN,
                LookupJoinTestPrograms.LOOKUP_JOIN_PRE_FILTER);
    }
}
