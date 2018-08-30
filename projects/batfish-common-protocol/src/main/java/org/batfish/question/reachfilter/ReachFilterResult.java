package org.batfish.question.reachfilter;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.acl.AclLineMatchExpr;

/**
 * A result from the ReachFilter question: a description of the headerspace expressed as an {@link
 * AclLineMatchExpr} and an example flow in the headerspace.
 */
@ParametersAreNonnullByDefault
public class ReachFilterResult {
  private final AclLineMatchExpr _headerSpaceDescription;
  private final Flow _exampleFlow;

  public ReachFilterResult(AclLineMatchExpr headerSpaceDescription, Flow exampleFlow) {
    _headerSpaceDescription = headerSpaceDescription;
    _exampleFlow = exampleFlow;
  }

  public @Nonnull AclLineMatchExpr getHeaderSpaceDescription() {
    return _headerSpaceDescription;
  }

  public @Nonnull Flow getExampleFlow() {
    return _exampleFlow;
  }
}
