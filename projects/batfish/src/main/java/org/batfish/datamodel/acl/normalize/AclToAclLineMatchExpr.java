package org.batfish.datamodel.acl.normalize;

import static org.batfish.datamodel.acl.AclLineMatchExprs.FALSE;
import static org.batfish.datamodel.acl.AclLineMatchExprs.and;
import static org.batfish.datamodel.acl.AclLineMatchExprs.not;
import static org.batfish.datamodel.acl.AclLineMatchExprs.or;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableSortedSet.Builder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.batfish.common.Pair;
import org.batfish.common.util.NonRecursiveSupplier;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.datamodel.acl.AndMatchExpr;
import org.batfish.datamodel.acl.FalseExpr;
import org.batfish.datamodel.acl.GenericAclLineMatchExprVisitor;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.acl.MatchSrcInterface;
import org.batfish.datamodel.acl.NotMatchExpr;
import org.batfish.datamodel.acl.OrMatchExpr;
import org.batfish.datamodel.acl.OriginatingFromDevice;
import org.batfish.datamodel.acl.PermittedByAcl;
import org.batfish.datamodel.acl.TrueExpr;
import org.batfish.datamodel.acl.explanation.ConjunctsBuilder;
import org.batfish.datamodel.acl.explanation.DisjunctsBuilder;
import org.batfish.symbolic.bdd.AclLineMatchExprToBDD;

/** Reduce an {@link org.batfish.datamodel.IpAccessList} to a single {@link AclLineMatchExpr}. */
public final class AclToAclLineMatchExpr
    implements GenericAclLineMatchExprVisitor<AclLineMatchExpr> {
  private final Map<String, Supplier<AclLineMatchExpr>> _namedAclThunks;

  private AclLineMatchExprToBDD _aclMatchExprToBDD;

  AclToAclLineMatchExpr(AclLineMatchExprToBDD aclMatchExprToBDD, Map<String, IpAccessList> namedAcls) {
    _aclMatchExprToBDD = aclMatchExprToBDD;
    _namedAclThunks = createThunks(namedAcls);
  }

  Map<String, Supplier<AclLineMatchExpr>> createThunks(Map<String, IpAccessList> namedAcls) {
    ImmutableMap.Builder<String, Supplier<AclLineMatchExpr>> thunks = ImmutableMap.builder();
    namedAcls.forEach(
        (name, acl) ->
            thunks.put(name, new NonRecursiveSupplier<>(() -> this.computeAclLineMatchExpr(acl))));
    return thunks.build();
  }

  AclLineMatchExpr computeAclLineMatchExpr(IpAccessList acl) {
    List<AclLineMatchExpr> rejects = new ArrayList<>();
    DisjunctsBuilder disjunctsBuilder = new DisjunctsBuilder(_aclMatchExprToBDD);
        new Builder<>(Comparator.naturalOrder());
    for (IpAccessListLine line : acl.getLines()) {
      if (line.getMatchCondition() == FALSE) {
        continue;
      }
      AclLineMatchExpr expr = line.getMatchCondition().accept(this);
      if (line.getAction() == LineAction.PERMIT) {
        if (rejects.isEmpty()) {
          disjunctsBuilder.add(expr);
        } else {
          ConjunctsBuilder conjunctsBuilder = new ConjunctsBuilder(_aclMatchExprToBDD);
          conjunctsBuilder.add(expr);
          rejects.forEach(conjunctsBuilder::add);
          if (!conjunctsBuilder.unsat()) {
            disjunctsBuilder.add(conjunctsBuilder.build());
          }
        }
      } else {
        rejects.add(not(expr));
      }
    }
    return disjunctsBuilder.build();
  }

  public static AclLineMatchExpr toAclLineMatchExpr(
          AclLineMatchExprToBDD aclMatchExprToBDD, IpAccessList acl, Map<String, IpAccessList> namedAcls) {
    AclLineMatchExpr result = new AclToAclLineMatchExpr(aclMatchExprToBDD, namedAcls).computeAclLineMatchExpr(acl);
    return result;
  }

  public static List<Pair<LineAction, AclLineMatchExpr>> aclLines(AclLineMatchExprToBDD aclMatchExprToBDD, IpAccessList acl, Map<String, IpAccessList> namedAcls) {
    AclToAclLineMatchExpr toMatchExpr = new AclToAclLineMatchExpr(aclMatchExprToBDD, namedAcls);
    return acl.getLines().stream().map(ln -> new Pair<>(ln.getAction(), ln.getMatchCondition().accept(toMatchExpr))).collect(ImmutableList.toImmutableList());
  }

  @Override
  public AclLineMatchExpr visitAndMatchExpr(AndMatchExpr andMatchExpr) {
    return and(
        andMatchExpr
            .getConjuncts()
            .stream()
            .map(this::visit)
            .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder())));
  }

  @Override
  public AclLineMatchExpr visitFalseExpr(FalseExpr falseExpr) {
    return falseExpr;
  }

  @Override
  public AclLineMatchExpr visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
    return matchHeaderSpace;
  }

  @Override
  public AclLineMatchExpr visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
    return matchSrcInterface;
  }

  @Override
  public AclLineMatchExpr visitNotMatchExpr(NotMatchExpr notMatchExpr) {
    return new NotMatchExpr(notMatchExpr.getOperand().accept(this));
  }

  @Override
  public AclLineMatchExpr visitOriginatingFromDevice(OriginatingFromDevice originatingFromDevice) {
    return originatingFromDevice;
  }

  @Override
  public AclLineMatchExpr visitOrMatchExpr(OrMatchExpr orMatchExpr) {
    return or(
        orMatchExpr
            .getDisjuncts()
            .stream()
            .map(this::visit)
            .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder())));
  }

  @Override
  public AclLineMatchExpr visitPermittedByAcl(PermittedByAcl permittedByAcl) {
    return _namedAclThunks.get(permittedByAcl.getAclName()).get();
  }

  @Override
  public AclLineMatchExpr visitTrueExpr(TrueExpr trueExpr) {
    return trueExpr;
  }
}
