package org.batfish.bddreachability;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDInteger;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.IpAccessListToBDD;
import org.batfish.common.bdd.IpSpaceToBDD;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.transformation.AssignIpAddressFromPool;
import org.batfish.datamodel.transformation.IpField;
import org.batfish.datamodel.transformation.ShiftIpAddressIntoSubnet;
import org.batfish.datamodel.transformation.Transformation;
import org.batfish.datamodel.transformation.TransformationStep;
import org.batfish.datamodel.transformation.TransformationStepVisitor;

/** Convert a {@link Transformation} to a BDD reachability graph {@link Transition}. */
public class TransformationToTransition {
  private final BDDPacket _bddPacket;
  private final IdentityHashMap<Transformation, Transition> _cache;
  private final IpAccessListToBDD _ipAccessListToBDD;
  private final TransformationStepToTransition _stepToTransition;

  public TransformationToTransition(BDDPacket bddPacket, IpAccessListToBDD ipAccessListToBDD) {
    _bddPacket = bddPacket;
    _cache = new IdentityHashMap<>();
    _ipAccessListToBDD = ipAccessListToBDD;
    _stepToTransition = new TransformationStepToTransition();
  }

  private static EraseAndSetTransition assignIpFromPool(BDDInteger var, Ip poolStart, Ip poolEnd) {
    BDD erase = Arrays.stream(var.getBitvec()).reduce(var.getFactory().one(), BDD::and);
    BDD setValue =
        poolStart.equals(poolEnd)
            ? var.value(poolStart.asLong())
            : var.geq(poolStart.asLong()).and(var.leq(poolEnd.asLong()));
    return new EraseAndSetTransition(erase, setValue);
  }

  private static EraseAndSetTransition shiftIpIntoPrefix(BDDInteger var, Prefix prefix) {
    int len = prefix.getPrefixLength();
    BDD erase = Arrays.stream(var.getBitvec()).limit(len).reduce(var.getFactory().one(), BDD::and);
    BDD setValue = new IpSpaceToBDD(var).toBDD(prefix);
    return new EraseAndSetTransition(erase, setValue);
  }

  private class TransformationStepToTransition implements TransformationStepVisitor<Transition> {
    private BDDInteger ipField(IpField ipField) {
      switch (ipField) {
        case DESTINATION:
          return _bddPacket.getDstIp();
        case SOURCE:
          return _bddPacket.getSrcIp();
        default:
          throw new IllegalArgumentException("Unknown IpField: " + ipField);
      }
    }

    @Override
    public Transition visitAssignIpAddressFromPool(AssignIpAddressFromPool step) {
      return assignIpFromPool(ipField(step.getIpField()), step.getPoolStart(), step.getPoolEnd());
    }

    @Override
    public Transition visitShiftIpAddressIntoSubnet(ShiftIpAddressIntoSubnet step) {
      return shiftIpIntoPrefix(ipField(step.getIpField()), step.getSubnet());
    }
  }

  public Transition toTransition(Transformation transformation) {
    return _cache.computeIfAbsent(transformation, this::computeTransition);
  }

  private Transition computeTransition(Transformation transformation) {
    BDD guard = _ipAccessListToBDD.visit(transformation.getGuard());
    Transition steps = computeSteps(transformation.getTransformationSteps());

    Transition trueBranch =
        transformation.getAndThen() == null
            ? steps
            : new CompositeTransition(steps, toTransition(transformation.getAndThen()));
    Transition falseBranch =
        transformation.getOrElse() == null
            ? IdentityTransition.INSTANCE
            : toTransition(transformation.getOrElse());
    return new BranchTransition(guard, trueBranch, falseBranch);
  }

  private Transition computeSteps(List<TransformationStep> transformationSteps) {
    return transformationSteps
        .stream()
        .map(_stepToTransition::visit)
        .reduce(CompositeTransition::new)
        .orElse(IdentityTransition.INSTANCE);
  }
}
