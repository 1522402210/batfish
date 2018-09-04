package org.batfish.datamodel.acl.normalize;

import static org.batfish.datamodel.acl.AclLineMatchExprs.and;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchSrcInterface;
import static org.batfish.datamodel.acl.AclLineMatchExprs.not;
import static org.batfish.datamodel.acl.AclLineMatchExprs.or;
import static org.batfish.datamodel.acl.AclLineMatchExprs.permittedByAcl;
import static org.batfish.datamodel.acl.normalize.AclToAclLineMatchExpr.toAclLineMatchExpr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessList.Builder;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.junit.Test;

public class AclToAclLineMatchExprTest {
  private static final AclLineMatchExpr EXPR_A = matchSrcInterface("A");
  private static final AclLineMatchExpr EXPR_B = matchSrcInterface("B");
  private static final AclLineMatchExpr EXPR_C = matchSrcInterface("C");
  private static final AclLineMatchExpr EXPR_D = matchSrcInterface("D");
  private static final AclLineMatchExpr EXPR_E = matchSrcInterface("E");

  private static final IpAccessListLine ACCEPT_A =
      IpAccessListLine.accepting().setMatchCondition(EXPR_A).build();
  private static final IpAccessListLine REJECT_A =
      IpAccessListLine.rejecting().setMatchCondition(EXPR_A).build();
  private static final IpAccessListLine ACCEPT_B =
      IpAccessListLine.accepting().setMatchCondition(EXPR_B).build();
  private static final IpAccessListLine REJECT_B =
      IpAccessListLine.rejecting().setMatchCondition(EXPR_B).build();
  private static final IpAccessListLine ACCEPT_C =
      IpAccessListLine.accepting().setMatchCondition(EXPR_C).build();
  private static final IpAccessListLine REJECT_C =
      IpAccessListLine.rejecting().setMatchCondition(EXPR_C).build();
  private static final IpAccessListLine ACCEPT_D =
      IpAccessListLine.accepting().setMatchCondition(EXPR_D).build();
  private static final IpAccessListLine REJECT_D =
      IpAccessListLine.rejecting().setMatchCondition(EXPR_D).build();
  private static final IpAccessListLine ACCEPT_E =
      IpAccessListLine.accepting().setMatchCondition(EXPR_E).build();
  private static final IpAccessListLine REJECT_E =
      IpAccessListLine.rejecting().setMatchCondition(EXPR_E).build();

  @Test
  public void testSimple() {
    IpAccessList acl =
        IpAccessList.builder()
            .setName("acl")
            .setLines(ImmutableList.of(ACCEPT_A, REJECT_B, ACCEPT_C, REJECT_C, ACCEPT_D, REJECT_E))
            .build();
    assertThat(
        toAclLineMatchExpr(null, acl, ImmutableMap.of()),
        equalTo(or(EXPR_A, and(not(EXPR_B), EXPR_C), and(not(EXPR_B), not(EXPR_C), EXPR_D))));
  }

  @Test
  public void testReference() {
    Builder builder = IpAccessList.builder().setName("acl");
    IpAccessList acl1 =
        builder.setLines(ImmutableList.of(ACCEPT_A, REJECT_A, ACCEPT_B, REJECT_B)).build();
    Map<String, IpAccessList> namedAcls = ImmutableMap.of("acl1", acl1);
    IpAccessListLine permitIfAcl1Permits =
        IpAccessListLine.accepting().setMatchCondition(permittedByAcl("acl1")).build();
    IpAccessList acl2 =
        builder
            .setLines(ImmutableList.of(ACCEPT_C, REJECT_C, permitIfAcl1Permits, ACCEPT_D, REJECT_E))
            .build();
    AclLineMatchExpr expr = toAclLineMatchExpr(null, acl2, namedAcls);
    return;
  }
}
