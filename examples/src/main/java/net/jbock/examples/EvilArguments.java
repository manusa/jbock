package net.jbock.examples;

import net.jbock.CommandLineArguments;

@CommandLineArguments(allowGrouping = true)
abstract class EvilArguments {

  abstract String fancy();

  abstract String fAncy();

  abstract String f_ancy();

  abstract String blub();

  abstract String Blub();
}