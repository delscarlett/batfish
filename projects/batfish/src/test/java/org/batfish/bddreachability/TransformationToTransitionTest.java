package org.batfish.bddreachability;

import static org.batfish.datamodel.acl.AclLineMatchExprs.matchDst;
import static org.batfish.datamodel.acl.AclLineMatchExprs.matchSrc;
import static org.batfish.datamodel.transformation.Transformation.always;
import static org.batfish.datamodel.transformation.Transformation.when;
import static org.batfish.datamodel.transformation.TransformationStep.assignSourceIp;
import static org.batfish.datamodel.transformation.TransformationStep.shiftDestinationIp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.ImmutableMap;
import net.sf.javabdd.BDD;
import org.batfish.common.bdd.BDDPacket;
import org.batfish.common.bdd.IpAccessListToBDD;
import org.batfish.common.bdd.IpSpaceToBDD;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.transformation.Transformation;
import org.junit.Before;
import org.junit.Test;

/** Tests of {@link TransformationToTransition}. */
public class TransformationToTransitionTest {
  private BDDPacket _pkt;
  private IpSpaceToBDD _dstIpSpaceToBdd;
  private IpSpaceToBDD _srcIpSpaceToBdd;
  private TransformationToTransition _toTransition;
  private BDD _one;
  private BDD _zero;

  @Before
  public void setup() {
    _pkt = new BDDPacket();
    _one = _pkt.getFactory().one();
    _zero = _pkt.getFactory().zero();
    _dstIpSpaceToBdd = new IpSpaceToBDD(_pkt.getDstIp());
    _srcIpSpaceToBdd = new IpSpaceToBDD(_pkt.getSrcIp());
    _toTransition =
        new TransformationToTransition(
            _pkt, IpAccessListToBDD.create(_pkt, ImmutableMap.of(), ImmutableMap.of()));
  }

  @Test
  public void testShiftIpAddressIntoSubnet() {
    Prefix shiftIntoPrefix = Prefix.parse("5.5.0.0/16");
    Transformation transformation = always().apply(shiftDestinationIp(shiftIntoPrefix)).build();
    Transition transition = _toTransition.toTransition(transformation);

    // forward -- unconstrained
    BDD expectedOut = _dstIpSpaceToBdd.toBDD(shiftIntoPrefix);
    BDD actualOut = transition.transitForward(_one);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- outside prefix
    BDD in = _dstIpSpaceToBdd.toBDD(Prefix.parse("1.2.3.0/24"));
    expectedOut = _dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.3.0/24"));
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- inside prefix
    in = _dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.3.0/24"));
    expectedOut = in;
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // backward -- unconstrained
    BDD expectedIn = _one;
    BDD actualIn = transition.transitBackward(_one);
    assertThat(actualIn, equalTo(expectedIn));

    // backward -- constrained
    expectedIn = _dstIpSpaceToBdd.toBDD(new IpWildcard(new Ip("0.0.3.0"), new Ip("255.255.0.255")));
    actualIn = transition.transitBackward(expectedOut);
    assertThat(actualIn, equalTo(expectedIn));
  }

  @Test
  public void testShiftIpAddressIntoSubnet2() {
    Prefix shiftIntoPrefix = Prefix.parse("5.5.0.32/27");
    Transformation transformation = always().apply(shiftDestinationIp(shiftIntoPrefix)).build();
    Transition transition = _toTransition.toTransition(transformation);

    // forward -- unconstrained
    BDD expectedOut = _dstIpSpaceToBdd.toBDD(shiftIntoPrefix);
    BDD actualOut = transition.transitForward(_one);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- outside prefix
    BDD in = _dstIpSpaceToBdd.toBDD(Prefix.parse("1.2.3.12/30"));
    expectedOut = _dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.0.44/30"));
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- inside prefix
    actualOut = transition.transitForward(expectedOut);
    assertThat(actualOut, equalTo(expectedOut));

    // backward -- unconstrained
    BDD expectedIn = _one;
    BDD actualIn = transition.transitBackward(_one);
    assertThat(actualIn, equalTo(expectedIn));

    // backward -- constrained
    Ip address = new Ip("0.0.0.12");
    // all bits wild except 28,29,30
    Ip mask = new Ip("255.255.255.227");
    expectedIn = _dstIpSpaceToBdd.toBDD(new IpWildcard(address, mask));
    actualIn = transition.transitBackward(expectedOut);
    assertThat(actualIn, equalTo(expectedIn));
  }

  @Test
  public void testGuardAndTransformSameField() {
    Prefix guardPrefix = Prefix.parse("1.0.0.0/8");
    Prefix shiftIntoPrefix = Prefix.parse("5.5.0.0/16");
    Transformation transformation =
        when(matchDst(guardPrefix)).apply(shiftDestinationIp(shiftIntoPrefix)).build();
    Transition transition = _toTransition.toTransition(transformation);

    BDD guardBdd = _dstIpSpaceToBdd.toBDD(guardPrefix);

    // forward -- unconstrained
    BDD expectedOut = guardBdd.not();
    BDD actualOut = transition.transitForward(_one);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- matching guard
    BDD in = _dstIpSpaceToBdd.toBDD(Prefix.parse("1.2.3.0/24"));
    expectedOut = _dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.3.0/24"));
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- not matching guard
    in = _dstIpSpaceToBdd.toBDD(Prefix.parse("2.2.3.0/24"));
    expectedOut = in;
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // backward -- unconstrained
    BDD expectedIn = _one;
    BDD actualIn = transition.transitBackward(_one);
    assertThat(actualIn, equalTo(expectedIn));

    // backward -- matched and transformed or not matched
    BDD out = _dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.3.0/24"));
    expectedIn =
        out.or(_dstIpSpaceToBdd.toBDD(new IpWildcard(new Ip("1.0.3.0"), new Ip("0.255.0.255"))));
    actualIn = transition.transitBackward(out);
    assertThat(actualIn, equalTo(expectedIn));
  }

  @Test
  public void testGuardAndTransformDifferentFields() {
    Prefix guardPrefix = Prefix.parse("1.0.0.0/8");
    Prefix shiftIntoPrefix = Prefix.parse("5.5.0.0/16");
    Transformation transformation =
        when(matchSrc(guardPrefix)).apply(shiftDestinationIp(shiftIntoPrefix)).build();
    Transition transition = _toTransition.toTransition(transformation);

    BDD guardBdd = _srcIpSpaceToBdd.toBDD(guardPrefix);
    BDD shiftIntoBdd = _dstIpSpaceToBdd.toBDD(shiftIntoPrefix);

    // forward -- unconstrained
    BDD expectedOut = guardBdd.imp(shiftIntoBdd);
    BDD actualOut = transition.transitForward(_one);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- matching guard
    BDD in = _dstIpSpaceToBdd.toBDD(Prefix.parse("1.2.3.0/24"));
    expectedOut = guardBdd.ite(_dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.3.0/24")), in);
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- not matching guard
    in = _srcIpSpaceToBdd.toBDD(Prefix.parse("2.2.3.0/24"));
    expectedOut = in;
    actualOut = transition.transitForward(in);
    assertThat(actualOut, equalTo(expectedOut));

    // backward -- unconstrained
    BDD expectedIn = _one;
    BDD actualIn = transition.transitBackward(_one);
    assertThat(actualIn, equalTo(expectedIn));

    // backward -- matched and transformed or not matched
    BDD out = _dstIpSpaceToBdd.toBDD(Prefix.parse("5.5.3.0/24"));
    IpWildcard preTransformationDestIps =
        new IpWildcard(new Ip("0.0.3.0"), new Ip("255.255.0.255"));
    expectedIn = guardBdd.ite(_dstIpSpaceToBdd.toBDD(preTransformationDestIps), out);
    actualIn = transition.transitBackward(out);
    assertThat(actualIn, equalTo(expectedIn));
  }

  @Test
  public void testAssignFromPool() {
    Ip poolStart = new Ip("1.1.1.5");
    Ip poolEnd = new Ip("1.1.1.13");
    Transformation transformation = always().apply(assignSourceIp(poolStart, poolEnd)).build();
    Transition transition = _toTransition.toTransition(transformation);

    // the entire pool as a BDD
    BDD poolBdd =
        _pkt.getSrcIp().geq(poolStart.asLong()).and(_pkt.getSrcIp().leq(poolEnd.asLong()));
    // one IP in the pool as a BDD
    BDD poolIpBdd = _pkt.getSrcIp().value(poolStart.asLong() + 2);
    BDD nonPoolIpBdd = _pkt.getSrcIp().value(poolEnd.asLong() + 2);

    // forward -- unconstrainted
    BDD expectedOut = poolBdd;
    BDD actualOut = transition.transitForward(_one);
    assertThat(actualOut, equalTo(expectedOut));

    // forward -- already in pool
    expectedOut = poolBdd;
    actualOut = transition.transitForward(poolIpBdd);
    assertThat(actualOut, equalTo(expectedOut));

    // backward -- inside of pool
    BDD expectedIn = _one;
    BDD actualIn = transition.transitBackward(poolIpBdd);
    assertThat(actualIn, equalTo(expectedIn));

    // backward -- outside of pool
    expectedIn = _zero;
    actualIn = transition.transitBackward(nonPoolIpBdd);
    assertThat(actualIn, equalTo(expectedIn));
  }

  @Test
  public void testMultipleSteps() {}

  @Test
  public void testMultipleTrasnformations() {}
}
