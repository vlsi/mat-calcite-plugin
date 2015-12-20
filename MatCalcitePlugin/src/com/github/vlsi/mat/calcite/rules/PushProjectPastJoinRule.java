/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
package com.github.vlsi.mat.calcite.rules;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.rules.PushProjector;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexNode;

import java.util.ArrayList;
import java.util.List;

/**
 * PushProjectPastJoinRule implements the rule for pushing a projection past a
 * join by splitting the projection into a projection on top of each child of
 * the join.
 * Note: this is a copy from Apache Calcite with just ExprCondition fix.
 * Unfortunately the constructor is private, so we can't extend reuse Calcite's class.
 */
public class PushProjectPastJoinRule extends RelOptRule
{
    public static final PushProjectPastJoinRule INSTANCE =
            new PushProjectPastJoinRule(new PushProjector.ExprCondition() {
                @Override
                public boolean test(RexNode rexNode) {
                    return true;
                }
            });

    //~ Instance fields --------------------------------------------------------

    /**
     * Condition for expressions that should be preserved in the projection.
     */
    private final PushProjector.ExprCondition preserveExprCondition;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a PushProjectPastJoinRule with an explicit condition.
     *
     * @param preserveExprCondition Condition for expressions that should be
     *                              preserved in the projection
     */
    private PushProjectPastJoinRule(
            PushProjector.ExprCondition preserveExprCondition) {
        super(
                operand(LogicalProject.class,
                        operand(LogicalJoin.class, any())));
        this.preserveExprCondition = preserveExprCondition;
    }

    //~ Methods ----------------------------------------------------------------

    // implement RelOptRule
    public void onMatch(RelOptRuleCall call) {
        LogicalProject origProj = call.rel(0);
        final LogicalJoin join = call.rel(1);

        // locate all fields referenced in the projection and join condition;
        // determine which inputs are referenced in the projection and
        // join condition; if all fields are being referenced and there are no
        // special expressions, no point in proceeding any further
        PushProjector pushProject =
                new PushProjector(
                        origProj,
                        join.getCondition(),
                        join,
                        preserveExprCondition);
        if (pushProject.locateAllRefs()) {
            return;
        }

        // create left and right projections, projecting only those
        // fields referenced on each side
        RelNode leftProjRel =
                pushProject.createProjectRefsAndExprs(
                        join.getLeft(),
                        true,
                        false);
        RelNode rightProjRel =
                pushProject.createProjectRefsAndExprs(
                        join.getRight(),
                        true,
                        true);

        // convert the join condition to reference the projected columns
        RexNode newJoinFilter = null;
        int[] adjustments = pushProject.getAdjustments();
        if (join.getCondition() != null) {
            List<RelDataTypeField> projJoinFieldList =
                    new ArrayList<RelDataTypeField>();
            projJoinFieldList.addAll(
                    join.getSystemFieldList());
            projJoinFieldList.addAll(
                    leftProjRel.getRowType().getFieldList());
            projJoinFieldList.addAll(
                    rightProjRel.getRowType().getFieldList());
            newJoinFilter =
                    pushProject.convertRefsAndExprs(
                            join.getCondition(),
                            projJoinFieldList,
                            adjustments);
        }

        // create a new join with the projected children
        LogicalJoin newJoinRel =
                join.copy(
                        join.getTraitSet(),
                        newJoinFilter,
                        leftProjRel,
                        rightProjRel,
                        join.getJoinType(),
                        join.isSemiJoinDone());

        // put the original project on top of the join, converting it to
        // reference the modified projection list
        RelNode topProject =
                pushProject.createNewProject(newJoinRel, adjustments);

        call.transformTo(topProject);
    }
}
