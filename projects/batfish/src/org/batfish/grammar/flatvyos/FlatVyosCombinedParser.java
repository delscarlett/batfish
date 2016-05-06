package org.batfish.grammar.flatvyos;

import org.antlr.v4.runtime.ParserRuleContext;
import org.batfish.grammar.BatfishCombinedParser;
import org.batfish.main.Settings;

public class FlatVyosCombinedParser extends
      BatfishCombinedParser<FlatVyosParser, FlatVyosLexer> {

   public FlatVyosCombinedParser(String input, Settings settings) {
      super(FlatVyosParser.class, FlatVyosLexer.class, input, settings);
   }

   @Override
   public ParserRuleContext parse() {
      return _parser.flat_vyos_configuration();
   }

}
