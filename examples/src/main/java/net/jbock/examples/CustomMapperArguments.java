package net.jbock.examples;

import net.jbock.CommandLineArguments;
import net.jbock.Parameter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@CommandLineArguments
abstract class CustomMapperArguments {

  /**
   * The mapper must be a Function from String to whatever-this-returns.
   * It must also have a package-visible no-arg constructor.
   */
  @Parameter(mappedBy = DateMapper.class)
  abstract Date date();

  @Parameter(mappedBy = DateMapper.class)
  abstract Optional<Date> optDate();

  @Parameter(mappedBy = DateMapper.class)
  abstract List<Date> dateList();

  @Parameter(mappedBy = CustomBigIntegerMapper.class)
  abstract Optional<BigInteger> verbosity();

  @Parameter(mappedBy = PositiveNumberMapper.class)
  abstract int anInt();

  @Parameter(mappedBy = PositiveNumberMapper.class)
  abstract OptionalInt anOptionalInt();

  @Parameter(mappedBy = ArrayMapper.class)
  abstract Optional<String[]> stringArray();

  @Parameter(mappedBy = IntegerListMapper.class)
  abstract Optional<List<Integer>> integerList();

  @Parameter(mappedBy = EnumSetMapper.class)
  abstract Optional<Set<MyEnum>> enumSet();

  static class DateMapper implements Function<String, Date> {

    @Override
    public Date apply(String s) {
      return new Date(Long.parseLong(s));
    }
  }

  static class PositiveNumberMapper implements Function<String, Integer> {

    @Override
    public Integer apply(String s) {
      Integer i = Integer.valueOf(s);
      if (i < 0) {
        throw new IllegalArgumentException("The value cannot be negative.");
      }
      return i;
    }
  }

  static class ArrayMapper implements Function<String, String[]> {

    @Override
    public String[] apply(String s) {
      return new String[]{s};
    }
  }

  static class IntegerListMapper implements Function<String, List<Integer>> {

    @Override
    public List<Integer> apply(String s) {
      return Arrays.stream(s.split(",", -1))
          .map(Integer::valueOf)
          .collect(Collectors.toList());
    }
  }

  static class EnumSetMapper implements Function<String, Set<MyEnum>> {

    @Override
    public Set<MyEnum> apply(String s) {
      return Arrays.stream(s.split(",", -1))
          .map(MyEnum::valueOf)
          .collect(Collectors.toSet());
    }
  }

  enum MyEnum {
    FOO, BAR
  }
}
