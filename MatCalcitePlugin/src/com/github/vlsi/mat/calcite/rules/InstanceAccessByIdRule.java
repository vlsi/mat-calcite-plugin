package com.github.vlsi.mat.calcite.rules;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.core.Correlate;

public class InstanceAccessByIdRule extends RelOptRule
{
    public static final InstanceAccessByIdRule INSTANCE = new InstanceAccessByIdRule();

    public InstanceAccessByIdRule() {
        super(operand(Correlate.class, any()));
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        System.out.println("InstanceAccessByIdRule fired for call " + call);
    }
}
