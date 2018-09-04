package net.jbock.examples;

import net.jbock.examples.fixture.ParserTestFixture;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;

class RequiredArgumentsTest {

  private ParserTestFixture<RequiredArguments> f =
      ParserTestFixture.create(RequiredArguments_Parser.create());

  @Test
  void success() {
    f.assertThat("--dir", "A").succeeds("otherTokens", emptyList(), "dir", "A");
  }

  @Test
  void errorDirMissing() {
    f.assertThat().failsWithLine1("Missing required option: DIR (--dir)");
  }

  @Test
  void errorRepeatedArgument() {
    f.assertThat("--dir", "A", "--dir", "B").failsWithLine1(
        "Option DIR (--dir) is not repeatable");
    f.assertThat("--dir=A", "--dir", "B").failsWithLine1(
        "Option DIR (--dir) is not repeatable");
    f.assertThat("--dir=A", "--dir=B").failsWithLine1(
        "Option DIR (--dir) is not repeatable");
    f.assertThat("--dir", "A", "--dir=B").failsWithLine1(
        "Option DIR (--dir) is not repeatable");
  }

  @Test
  void errorDetachedAttached() {
    f.assertThat("--dir", "A", "--dir=B").failsWithLine1("Option DIR (--dir) is not repeatable");
  }

  @Test
  void testPrint() {
    f.assertPrints(
        "NAME",
        "  RequiredArguments",
        "",
        "SYNOPSIS",
        "  RequiredArguments --dir=<DIR> [[--] <other_tokens...>]",
        "",
        "DESCRIPTION",
        "",
        "OTHER_TOKENS",
        "",
        "OPTIONS",
        "  --dir <DIR>",
        "",
        "  --help",
        "    Print this help page.",
        "    The help flag may only be passed as the first argument.",
        "    Any further arguments will be ignored.",
        "",
        "");
  }
}
