package net.jbock.coerce.mappers;

import com.squareup.javapoet.CodeBlock;

import java.util.regex.Pattern;

class PatternCoercion extends CoercionFactory {

  PatternCoercion() {
    super(Pattern.class);
  }

  @Override
  CodeBlock map() {
    return CodeBlock.builder().add(".map($T::compile)", Pattern.class).build();
  }
}
