package org.batfish.question.routes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.question.routes.RoutesQuestion.RibProtocol;
import org.junit.Test;

/** Tests of {@link RoutesQuestion} */
public class RoutesQuestionTest {
  @Test
  public void testDefaultParams() {
    RoutesQuestion question = new RoutesQuestion();

    assertThat(question.getDataPlane(), equalTo(true));
    assertThat(question.getName(), equalTo("routes"));

    assertThat(question.getNodes(), equalTo(NodesSpecifier.ALL));
    assertThat(question.getVrfs(), equalTo(".*"));
    assertThat(question.getRib(), equalTo(RibProtocol.MAIN));
  }
}
