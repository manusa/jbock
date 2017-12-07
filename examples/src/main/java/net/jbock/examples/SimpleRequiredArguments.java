package net.jbock.examples;


import java.util.List;
import net.jbock.CommandLineArguments;
import net.jbock.Positional;

@CommandLineArguments
abstract class SimpleRequiredArguments {

  abstract String dir();

  @Positional
  abstract List<String> otherTokens();
}