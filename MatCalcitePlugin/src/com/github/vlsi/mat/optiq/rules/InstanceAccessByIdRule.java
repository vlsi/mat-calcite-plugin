package com.github.vlsi.mat.optiq.rules;

import org.eigenbase.rel.CorrelatorRel;
import org.eigenbase.relopt.RelOptRule;
import org.eigenbase.relopt.RelOptRuleCall;

public class InstanceAccessByIdRule extends RelOptRule {
    public static final InstanceAccessByIdRule INSTANCE = new InstanceAccessByIdRule();

    public InstanceAccessByIdRule() {
        super(operand(CorrelatorRel.class, any()));
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        System.out.println("InstanceAccessByIdRule fired for call " + call);
    }
}
