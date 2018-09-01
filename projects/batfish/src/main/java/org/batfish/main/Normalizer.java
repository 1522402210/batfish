package org.batfish.main;

import static org.batfish.datamodel.acl.normalize.Negate.negate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

/**
 * Normalize {@link AclLineMatchExpr AclLineMatchExprs} to a DNF-style form: a single or at the
 * root, all ands as immediate children of the or.
 */
public final class Normalizer implements GenericAclLineMatchExprVisitor<AclLineMatchExpr> {
  AclLineMatchExprToBDD _aclLineMatchExprToBDD;

  public Normalizer(AclLineMatchExprToBDD aclLineMatchExprToBDD) {
    _aclLineMatchExprToBDD = aclLineMatchExprToBDD;
  }

  public AclLineMatchExpr normalize(AclLineMatchExpr expr) {
    return expr.accept(this);
  }

  @Override
  public AclLineMatchExpr visitAndMatchExpr(AndMatchExpr andMatchExpr) {
    // Normalize subexpressions, combine all OR subexpressions, and then distribute the AND over
    // the single OR.
    Set<ConjunctsBuilder> disjuncts = new HashSet<>();
    disjuncts.add(new ConjunctsBuilder(_aclLineMatchExprToBDD));

    BiConsumer<Set<ConjunctsBuilder>, AclLineMatchExpr> addToEachDisjunct =
        (conjunctsBuilders, expr) -> {
          List<ConjunctsBuilder> unsatDisjuncts = new ArrayList<>();
          conjunctsBuilders.forEach(
              conjunctsBuilder -> {
                conjunctsBuilder.add(expr);
                if (conjunctsBuilder.unsat()) {
                  unsatDisjuncts.add(conjunctsBuilder);
                }
              });
          System.out.println(String.format("Removing %d of %d conjunctBuilders", unsatDisjuncts.size(), conjunctsBuilders.size()));
          conjunctsBuilders.removeAll(unsatDisjuncts);
        };

    int counter = 0;
    for (AclLineMatchExpr conjunct : andMatchExpr.getConjuncts()) {
      counter++;
      AclLineMatchExpr conjunctNf = visit(conjunct);
      if (conjunctNf instanceof AndMatchExpr) {
        for (AclLineMatchExpr expr : ((AndMatchExpr) conjunctNf).getConjuncts()) {
          addToEachDisjunct.accept(disjuncts, expr);
        }
      } else if (conjunctNf instanceof OrMatchExpr) {
        /* concatenate each AND with each disjunct, multiplying the number of ANDs in orOfAnds
         * by the number of disjuncts within the OrMatchExpr. i.e. this is where the blow-up caused
         * by normalization happens.
         */
        OrMatchExpr orMatchExpr = (OrMatchExpr) conjunctNf;
        final Set<ConjunctsBuilder> oldDisjuncts = disjuncts;
        disjuncts =
            orMatchExpr
                .getDisjuncts()
                .stream()
                .flatMap(
                    expr ->
                        oldDisjuncts
                            .stream()
                            .map(ConjunctsBuilder::new)
                            .peek(
                                conjunctsBuilder -> {
                                  if (expr instanceof AndMatchExpr) {
                                    ((AndMatchExpr) expr)
                                        .getConjuncts()
                                        .forEach(conjunctsBuilder::add);
                                  } else {
                                    conjunctsBuilder.add(expr);
                                  }
                                }))
                .filter(conjunctsBuilder -> !conjunctsBuilder.unsat())
                .collect(Collectors.toSet());
      } else {
        // add it to each AND
        addToEachDisjunct.accept(disjuncts, conjunctNf);
      }

      if(disjuncts.isEmpty()) {
        // everything unsat.
        System.out.println(String.format("Detected unsat after %s of %s iterations", counter, andMatchExpr.getConjuncts().size()));
        break;
      }
    }

    DisjunctsBuilder disjunctsBuilder = new DisjunctsBuilder(_aclLineMatchExprToBDD);
    disjuncts.stream().map(ConjunctsBuilder::build).forEach(disjunctsBuilder::add);
    return disjunctsBuilder.build();
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
    AclLineMatchExpr negated = negate(notMatchExpr.getOperand());
    if (negated instanceof NotMatchExpr) {
      // hit a leaf
      return negated;
    }
    return negated.accept(this);
  }

  @Override
  public AclLineMatchExpr visitOriginatingFromDevice(OriginatingFromDevice originatingFromDevice) {
    return originatingFromDevice;
  }

  @Override
  public AclLineMatchExpr visitOrMatchExpr(OrMatchExpr orMatchExpr) {
    DisjunctsBuilder disjunctsBuilder = new DisjunctsBuilder(_aclLineMatchExprToBDD);
    orMatchExpr
        .getDisjuncts()
        .stream()
        // normalize
        .map(this::visit)
        // expand nested OrMatchExprs
        .flatMap(
            expr ->
                (expr instanceof OrMatchExpr)
                    ? ((OrMatchExpr) expr).getDisjuncts().stream()
                    : Stream.of(expr))
        .filter(expr -> expr != FalseExpr.INSTANCE)
        .forEach(disjunctsBuilder::add);
    return disjunctsBuilder.build();
  }

  @Override
  public AclLineMatchExpr visitPermittedByAcl(PermittedByAcl permittedByAcl) {
    return permittedByAcl;
  }

  @Override
  public AclLineMatchExpr visitTrueExpr(TrueExpr trueExpr) {
    return trueExpr;
  }
}
