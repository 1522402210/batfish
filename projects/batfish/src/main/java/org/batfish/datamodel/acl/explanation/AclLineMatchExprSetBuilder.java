package org.batfish.datamodel.acl.explanation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.acl.AclLineMatchExpr;
import org.batfish.symbolic.bdd.AclLineMatchExprToBDD;

/**
 * Represents a set of {@link AclLineMatchExpr AclLineMatchExprs} -- either a set of conjuncts or a
 * set of disjuncts. Removes
 */
public abstract class AclLineMatchExprSetBuilder {

  private final AclLineMatchExprToBDD _aclLineMatchExprToBDD;
  private final Map<AclLineMatchExpr, BDD> _exprs;
  private BDD _bdd;
  private BDD _orExprBDDs;

  protected AclLineMatchExprSetBuilder(AclLineMatchExprToBDD aclLineMatchExprToBDD, BDD identity) {
    _aclLineMatchExprToBDD = aclLineMatchExprToBDD;
    _exprs = new HashMap<>();
    _bdd = identity;
    _orExprBDDs = identity.getFactory().zero();
  }

  public AclLineMatchExprSetBuilder(AclLineMatchExprSetBuilder other) {
    _aclLineMatchExprToBDD = other._aclLineMatchExprToBDD;
    _exprs = new HashMap<>(other._exprs);
    _bdd = other._bdd;
    _orExprBDDs = other._orExprBDDs;
  }

  protected abstract BDD identity();

  protected abstract BDD combinator(BDD bdd1, BDD bdd2);

  protected abstract BDD shortCircuitBDD();

  public abstract AclLineMatchExpr build();

  public void add(AclLineMatchExpr expr) {
    if (_bdd.equals(shortCircuitBDD()) || _exprs.containsKey(expr)) {
      return;
    }

    BDD exprBdd = _aclLineMatchExprToBDD.visit(expr);
    BDD newBdd = combinator(_bdd, exprBdd);
    if (newBdd.equals(_bdd)) {
      // expr contributes nothing to the set; discard
      return;
    }

    if (!_exprs.isEmpty() && exprBdd.imp(_orExprBDDs).isOne()) {
      // can remove something
      List<AclLineMatchExpr> toRemove = new ArrayList<>();
      _exprs.forEach(
          (expr1, bdd1) -> {
            if (combinator(bdd1, exprBdd).equals(exprBdd)) {
              // bdd1 is now redundant; remove
              toRemove.add(expr1);
            }
          });
      assert !toRemove.isEmpty();
      toRemove.forEach(_exprs::remove);

      _orExprBDDs = _exprs.values().stream().reduce(_bdd.getFactory().zero(), BDD::or);
    }

    _exprs.put(expr, exprBdd);
    _bdd = newBdd;
    _orExprBDDs = _orExprBDDs.or(exprBdd);
  }

  protected BDD getBdd() {
    return _bdd;
  }

  protected Set<AclLineMatchExpr> getExprs() {
    return _exprs.keySet();
  }
}
