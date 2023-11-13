package com.github.vlsi.mat.calcite.rules;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.tools.RelBuilderFactory;

public class DefaultRuleConfig implements RelRule.Config {
  public static final RelRule.Config EMPTY =
      new DefaultRuleConfig(RelFactories.LOGICAL_BUILDER, null, null);

  protected final RelBuilderFactory relBuilderFactory;
  protected final String description;
  protected final RelRule.OperandTransform operandTransform;

  protected DefaultRuleConfig(RelBuilderFactory relBuilderFactory, String description, RelRule.OperandTransform operandTransform) {
    this.relBuilderFactory = relBuilderFactory;
    this.description = description;
    this.operandTransform = operandTransform;
  }

  @Override
  public RelRule.Config withRelBuilderFactory(RelBuilderFactory relBuilderFactory) {
    return new DefaultRuleConfig(relBuilderFactory, description, operandTransform);
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public RelBuilderFactory relBuilderFactory() {
    return relBuilderFactory;
  }

  @Override
  public RelRule.Config withDescription(@org.checkerframework.checker.nullness.qual.Nullable String description) {
    return new DefaultRuleConfig(relBuilderFactory, description, operandTransform);
  }

  @Override
  public RelRule.OperandTransform operandSupplier() {
    return operandTransform;
  }

  @Override
  public RelRule.Config withOperandSupplier(RelRule.OperandTransform operandTransform) {
    return new DefaultRuleConfig(relBuilderFactory, description, operandTransform);
  }

  @Override
  public RelOptRule toRule() {
    return null;
  }
}
