package org.batfish.symbolic.bdd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.acl.AclLineMatchExpr;

import javax.annotation.Nonnull;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MemoizedAclLineMatchExprToBDD extends AclLineMatchExprToBDD {
    private Map<AclLineMatchExpr,BDD> _cache = new IdentityHashMap<>();

    public MemoizedAclLineMatchExprToBDD(
            BDDFactory factory,
            BDDPacket packet,
            Map<String, Supplier<BDD>> aclEnv,
            Map<String, IpSpace> namedIpSpaces) {
        super(factory,packet,aclEnv,namedIpSpaces);
    }

    public MemoizedAclLineMatchExprToBDD(BDDFactory factory, BDDPacket packet, Map<String, Supplier<BDD>> aclEnv, Map<String, IpSpace> namedIpSpaces, @Nonnull BDDSourceManager bddSrcManager) {
        super(factory, packet, aclEnv, namedIpSpaces, bddSrcManager);
    }

    @Override
    public BDD visit(AclLineMatchExpr expr) {
        return _cache.computeIfAbsent(expr, super::visit);
    }
}
