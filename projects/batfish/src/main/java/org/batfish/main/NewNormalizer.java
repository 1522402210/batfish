package org.batfish.main;

import com.google.common.collect.ImmutableList;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.datamodel.acl.AclLineMatchExprs;
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
import org.batfish.datamodel.acl.normalize.Negate;
import org.batfish.datamodel.routing_policy.expr.ConjunctionChain;
import org.batfish.symbolic.bdd.AclLineMatchExprToBDD;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NewNormalizer implements GenericAclLineMatchExprVisitor<Void> {
    private final AclLineMatchExprToBDD _aclLineMatchExprToBDD;
    private Set<ConjunctsBuilder> _conjunctsBuilders;

    public NewNormalizer(AclLineMatchExprToBDD aclLineMatchExprToBDD) {
        _aclLineMatchExprToBDD = aclLineMatchExprToBDD;
        _conjunctsBuilders = new HashSet<>();
        _conjunctsBuilders.add(new ConjunctsBuilder(_aclLineMatchExprToBDD));
    }

    public NewNormalizer(NewNormalizer other) {
       _aclLineMatchExprToBDD = other._aclLineMatchExprToBDD;
       _conjunctsBuilders = other._conjunctsBuilders.stream().map(ConjunctsBuilder::new).collect(Collectors.toSet());
    }


    public AclLineMatchExpr normalize(AclLineMatchExpr expr) {
        expr.accept(this);
        expandOrs();
        Set<AclLineMatchExpr> disjuncts = _conjunctsBuilders
                .stream()
                .filter(conjunctsBuilder -> !conjunctsBuilder.unsat())
                .map(ConjunctsBuilder::build)
                .collect(Collectors.toSet());
        return AclLineMatchExprs.or(disjuncts);
    }

    private void expandOrs() {
        int oldSize = _conjunctsBuilders.size();
        _conjunctsBuilders = _conjunctsBuilders.stream().flatMap(this::expandOrs).collect(Collectors.toSet());
        int newSize = _conjunctsBuilders.size();
        System.out.println(String.format("OrMatchExpr caused blowup from %d to %d", oldSize,newSize));
    }

  private Stream<ConjunctsBuilder> expandOrs(ConjunctsBuilder conjunctsBuilder) {
    if(!conjunctsBuilder.containsOrMatchExpr()) {
        return Stream.of(conjunctsBuilder);
    }

    AclLineMatchExpr expr = conjunctsBuilder.build();

    if (expr == FalseExpr.INSTANCE) {
        return Stream.of();
    }

    Iterable<AclLineMatchExpr> conjuncts =
            expr instanceof AndMatchExpr
                    ? ((AndMatchExpr) expr).getConjuncts()
                    : ImmutableList.of(expr);

    NewNormalizer normalizer = new NewNormalizer(_aclLineMatchExprToBDD);
    for (AclLineMatchExpr conj : conjuncts) {
      if (conj instanceof OrMatchExpr) {
        normalizer.visitDisjuncts(((OrMatchExpr) conj).getDisjuncts());
      } else {
        normalizer.visit(conj);
      }
    }

    return normalizer._conjunctsBuilders.stream().flatMap(this::expandOrs);
  }

    private void visitDisjuncts(SortedSet<AclLineMatchExpr> disjuncts) {
        _conjunctsBuilders = disjuncts.stream().flatMap(disjunct -> {
            NewNormalizer normalizer = new NewNormalizer(this);
            disjunct.accept(normalizer);
            return normalizer._conjunctsBuilders.stream().filter(conjunctsBuilder -> !conjunctsBuilder.unsat());
        }).collect(Collectors.toSet());
    }

    private void addConstraint(AclLineMatchExpr expr) {
        List<ConjunctsBuilder> unsat = new ArrayList<>();
        for (ConjunctsBuilder conjunctsBuilder : _conjunctsBuilders) {
            conjunctsBuilder.add(expr);
            if(conjunctsBuilder.unsat()) {
                unsat.add(conjunctsBuilder);
            }
        }
        if (unsat.size() > 0) {
            System.out.println(String.format("Removing %d of %d conjunctsBuilders", unsat.size(), _conjunctsBuilders.size()));
            _conjunctsBuilders.removeAll(unsat);
        }
    }

    @Override
    public Void visitAndMatchExpr(AndMatchExpr andMatchExpr) {
        andMatchExpr.getConjuncts().forEach(this::visit);
        return null;
    }

    @Override
    public Void visitFalseExpr(FalseExpr falseExpr) {
        _conjunctsBuilders = new HashSet<>();
        return null;
    }

    @Override
    public Void visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
        addConstraint(matchHeaderSpace);
        return null;
    }

    @Override
    public Void visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
        addConstraint(matchSrcInterface);
        return null;
    }

    @Override
    public Void visitNotMatchExpr(NotMatchExpr notMatchExpr) {
        AclLineMatchExpr negatedOperand = Negate.negate(notMatchExpr.getOperand());
        NewNormalizer normalizer = this;
        negatedOperand.accept(new GenericAclLineMatchExprVisitor<Void>() {
            @Override
            public Void visitAndMatchExpr(AndMatchExpr andMatchExpr) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitFalseExpr(FalseExpr falseExpr) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitMatchHeaderSpace(MatchHeaderSpace matchHeaderSpace) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitMatchSrcInterface(MatchSrcInterface matchSrcInterface) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitNotMatchExpr(NotMatchExpr notMatchExpr) {
                // negated leaf node. rather than recurse, just add to the conjuctsBuilders.
                addConstraint(notMatchExpr);
                return null;
            }

            @Override
            public Void visitOriginatingFromDevice(OriginatingFromDevice originatingFromDevice) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitOrMatchExpr(OrMatchExpr orMatchExpr) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitPermittedByAcl(PermittedByAcl permittedByAcl) {
                negatedOperand.accept(normalizer);
                return null;
            }

            @Override
            public Void visitTrueExpr(TrueExpr trueExpr) {
                negatedOperand.accept(normalizer);
                return null;
            }
        });
        return null;
    }

    @Override
    public Void visitOriginatingFromDevice(OriginatingFromDevice originatingFromDevice) {
        addConstraint(originatingFromDevice);
        return null;
    }

    @Override
    public Void visitOrMatchExpr(OrMatchExpr orMatchExpr) {
        addConstraint(orMatchExpr);
        return null;
    }

    @Override
    public Void visitPermittedByAcl(PermittedByAcl permittedByAcl) {
        throw new BatfishException("PermittedByAcl expressions must be inlined");
    }

    @Override
    public Void visitTrueExpr(TrueExpr trueExpr) {
        return null;
    }
}
