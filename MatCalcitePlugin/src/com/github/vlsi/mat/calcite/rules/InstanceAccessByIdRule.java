package com.github.vlsi.mat.calcite.rules;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.core.Correlate;

public class InstanceAccessByIdRule extends RelRule<InstanceAccessByIdRule.Config> {
  public static final InstanceAccessByIdRule INSTANCE = Config.DEFAULT.toRule();

  public interface Config extends RelRule.Config {
    InstanceAccessByIdRule.Config DEFAULT = EMPTY
        .withOperandSupplier(b0 ->
            b0.operand(Correlate.class)
                .anyInputs())
        .as(InstanceAccessByIdRule.Config.class);

    @Override
    default InstanceAccessByIdRule toRule() {
      return new InstanceAccessByIdRule(this);
    }
  }

  public InstanceAccessByIdRule(Config config) {
    super(config);
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    System.out.println("InstanceAccessByIdRule fired for call " + call);
  }
}
