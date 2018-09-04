package org.batfish.symbolic.bdd;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.IpSpace;

import java.util.IdentityHashMap;
import java.util.Map;

public class MemoizedIpSpaceToBDD extends IpSpaceToBDD {
    private final Map<IpSpace,BDD> _cache = new IdentityHashMap<>();

    public MemoizedIpSpaceToBDD(BDDFactory factory, BDDInteger var) {
        super(factory, var);
    }

    public MemoizedIpSpaceToBDD(BDDFactory factory, BDDInteger var, Map<String, IpSpace> namedIpSpaces) {
        super(factory, var, namedIpSpaces);
    }

    @Override
    public BDD visit(IpSpace ipSpace) {
        return _cache.computeIfAbsent(ipSpace, super::visit);
    }
}
