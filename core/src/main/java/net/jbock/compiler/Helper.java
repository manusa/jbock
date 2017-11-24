package net.jbock.compiler;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static net.jbock.com.squareup.javapoet.TypeName.BOOLEAN;
import static net.jbock.com.squareup.javapoet.TypeName.INT;
import static net.jbock.compiler.Analyser.LONG_NAME;
import static net.jbock.compiler.Analyser.SHORT_NAME;
import static net.jbock.compiler.Analyser.STRING;
import static net.jbock.compiler.Analyser.STRING_ITERATOR;
import static net.jbock.compiler.Analyser.STRING_LIST;
import static net.jbock.compiler.Analyser.repetitionError;
import static net.jbock.compiler.Analyser.throwRepetitionErrorInGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import net.jbock.com.squareup.javapoet.ClassName;
import net.jbock.com.squareup.javapoet.CodeBlock;
import net.jbock.com.squareup.javapoet.FieldSpec;
import net.jbock.com.squareup.javapoet.MethodSpec;
import net.jbock.com.squareup.javapoet.ParameterSpec;
import net.jbock.com.squareup.javapoet.TypeName;
import net.jbock.com.squareup.javapoet.TypeSpec;

final class Helper {

  private final ClassName optionClass;
  private final ClassName optionTypeClass;
  private final ClassName keysClass;
  private final FieldSpec longNamesField;
  private final FieldSpec shortNamesField;

  final FieldSpec optMap;
  final FieldSpec sMap;
  final FieldSpec flags;
  final Option option;
  final MethodSpec addFlagMethod;
  final MethodSpec addMethod;

  final MethodSpec readMethod;

  final MethodSpec readRegularOptionMethod;

  private Helper(
      ClassName optionClass,
      ClassName optionTypeClass,
      ClassName keysClass,
      FieldSpec longNamesField,
      FieldSpec shortNamesField,
      FieldSpec optMap,
      FieldSpec sMap,
      FieldSpec flags,
      Option option,
      MethodSpec addFlagMethod,
      MethodSpec addMethod,
      MethodSpec readMethod,
      MethodSpec readRegularOptionMethod) {
    this.optionClass = optionClass;
    this.optionTypeClass = optionTypeClass;
    this.keysClass = keysClass;
    this.longNamesField = longNamesField;
    this.shortNamesField = shortNamesField;
    this.optMap = optMap;
    this.sMap = sMap;
    this.flags = flags;
    this.option = option;
    this.addFlagMethod = addFlagMethod;
    this.addMethod = addMethod;
    this.readMethod = readMethod;
    this.readRegularOptionMethod = readRegularOptionMethod;
  }

  static Helper create(
      JbockContext context,
      MethodSpec readArgumentMethod,
      MethodSpec chopOffShortFlagMethod,
      TypeName optMapType,
      TypeName sMapType,
      TypeName flagsType,
      Option option,
      ClassName helperClass,
      FieldSpec longNamesField,
      FieldSpec shortNamesField) {
    FieldSpec optMap = FieldSpec.builder(optMapType, "optMap", FINAL).build();
    FieldSpec sMap = FieldSpec.builder(sMapType, "sMap", FINAL).build();
    FieldSpec flags = FieldSpec.builder(flagsType, "flags", FINAL).build();
    MethodSpec addFlagMethod = addFlagMethod(option.optionClass, option.optionType, flags);
    MethodSpec addMethod = addMethod(option.optionClass, option.optionType,
        optMap, sMap, option.isBindingMethod);
    MethodSpec readRegularOptionMethod = readRegularOptionMethod(
        helperClass, longNamesField, shortNamesField, option.optionClass);

    MethodSpec read = readMethod(
        context,
        helperClass,
        readArgumentMethod,
        readRegularOptionMethod,
        optMapType,
        sMapType,
        flagsType,
        option.optionClass,
        option.optionTypeField,
        option.optionType,
        chopOffShortFlagMethod,
        addMethod,
        addFlagMethod);

    return new Helper(
        option.optionClass,
        option.optionType,

        helperClass,
        longNamesField,
        shortNamesField,
        optMap,
        sMap,
        flags,
        option,
        addFlagMethod,
        addMethod,
        read, readRegularOptionMethod);
  }

  TypeSpec define() {
    return TypeSpec.classBuilder(keysClass)
        .addFields(Arrays.asList(
            longNamesField,
            shortNamesField,
            optMap.toBuilder()
                .initializer("new $T<>($T.class)", EnumMap.class, optionClass)
                .build(),
            sMap.toBuilder()
                .initializer("new $T<>($T.class)", EnumMap.class, optionClass)
                .build(),
            flags.toBuilder()
                .initializer("$T.noneOf($T.class)", EnumSet.class, optionClass)
                .build()))
        .addModifiers(PRIVATE, STATIC, FINAL)
        .addMethod(privateConstructor())
        .addMethod(readMethod)
        .addMethod(readRegularOptionMethod)
        .addMethod(addMethod)
        .addMethod(addFlagMethod)
        .build();
  }

  private MethodSpec privateConstructor() {
    CodeBlock.Builder builder = CodeBlock.builder();
    ParameterSpec longNames = ParameterSpec.builder(this.longNamesField.type, this.longNamesField.name)
        .build();
    ParameterSpec shortNames = ParameterSpec.builder(this.shortNamesField.type, this.shortNamesField.name)
        .build();
    ParameterSpec option = ParameterSpec.builder(optionClass, "option")
        .build();

    builder.add("\n");
    builder.addStatement("$T $N = new $T<>()",
        longNames.type, longNames, HashMap.class)
        .addStatement("$T $N = new $T<>()",
            shortNames.type, shortNames, HashMap.class);

    // begin iteration over options
    builder.beginControlFlow("for ($T $N : $T.values())", optionClass, option, optionClass);

    builder.beginControlFlow("if ($N.$N != null)", option, SHORT_NAME)
        .addStatement("$N.put($N.$N.toString(), $N)", shortNames, option, SHORT_NAME, option)
        .endControlFlow();

    builder.beginControlFlow("if ($N.$N != null)", option, LONG_NAME)
        .addStatement("$N.put($N.$N, $N)", longNames, option, LONG_NAME, option)
        .endControlFlow();

    // end iteration over options
    builder.endControlFlow();

    builder.addStatement("this.$N = $T.unmodifiableMap($N)",
        longNames, Collections.class, longNames);

    builder.addStatement("this.$N = $T.unmodifiableMap($N)",
        shortNames, Collections.class, shortNames);

    return MethodSpec.constructorBuilder()
        .addCode(builder.build())
        .build();
  }

  private static MethodSpec addFlagMethod(
      ClassName optionClass,
      ClassName optionTypeClass,
      FieldSpec flags) {
    ParameterSpec option = ParameterSpec.builder(optionClass, "option").build();
    return MethodSpec.methodBuilder("add")
        .addStatement("assert $N.type == $T.$L", option, optionTypeClass, OptionType.FLAG)
        .addStatement("return $N.add($N)", flags, option)
        .addParameter(option)
        .returns(BOOLEAN)
        .build();
  }

  private static MethodSpec addMethod(
      ClassName optionClass,
      ClassName optionTypeClass,
      FieldSpec optMap,
      FieldSpec sMap,
      MethodSpec isBindingMethod) {
    ParameterSpec option = ParameterSpec.builder(optionClass, "option").build();
    ParameterSpec argument = ParameterSpec.builder(STRING, "argument").build();
    ParameterSpec bucket = ParameterSpec.builder(STRING_LIST, "bucket").build();

    MethodSpec.Builder builder = MethodSpec.methodBuilder("add");
    builder.addStatement("assert $N.$N()", option, isBindingMethod);

    // begin handle repeatable
    builder.beginControlFlow("if ($N.type == $T.$L)", option, optionTypeClass, OptionType.REPEATABLE);

    builder.addStatement("$T $N = $N.get($N)", bucket.type, bucket, optMap, option);
    builder.beginControlFlow("if ($N == null)", bucket)
        .addStatement("$N = new $T<>()", bucket, ArrayList.class)
        .addStatement("$N.put($N, $N)", optMap, option, bucket)
        .endControlFlow();
    builder.addStatement("$N.add($N)", bucket, argument);
    builder.addStatement("return $L", true);

    // done handling repeatable
    builder.endControlFlow();

    builder.beginControlFlow("if ($N.containsKey($N))", sMap, option)
        .addStatement("return $L", false)
        .endControlFlow()
        .addStatement("$N.put($N, $N)", sMap, option, argument);

    builder.addStatement("return $L", true);

    return builder.addParameters(Arrays.asList(option, argument))
        .returns(BOOLEAN)
        .build();
  }

  private static MethodSpec readRegularOptionMethod(
      ClassName keysClass,
      FieldSpec longNames,
      FieldSpec shortNames,
      ClassName optionClass) {
    ParameterSpec token = ParameterSpec.builder(STRING, "token").build();
    ParameterSpec index = ParameterSpec.builder(INT, "index").build();
    CodeBlock.Builder builder = CodeBlock.builder();

    builder.beginControlFlow("if ($N.length() <= 1 || $N.charAt(0) != '-')", token, token)
        .add("// not an option\n")
        .addStatement("return null")
        .endControlFlow();

    builder.beginControlFlow("if ($N.charAt(1) != '-')", token)
        .add("// short option\n")
        .addStatement("return $N.get($N.substring(1, 2))", shortNames, token)
        .endControlFlow();

    builder.addStatement("$T $N = $N.indexOf('=')", INT, index, token);

    builder.beginControlFlow("if ($N < 0)", index)
        .addStatement("return $N.get($N.substring(2))", longNames, token)
        .endControlFlow();

    builder.addStatement("return $N.get($N.substring(2, $N))", longNames, token, index);

    return MethodSpec.methodBuilder("readRegularOption")
        .addParameter(token)
        .returns(optionClass)
        .addCode(builder.build())
        .build();
  }

  /**
   * <p>The read method takes one token. If the token is
   * <em>known</em>, its argument is  also consumed (unless it's a flag),
   * and this information is added to one of
   * {@code sMap}, {@code optMap}, {@code flags}, and {@code true} is returned.
   * Otherwise, none of these collections are modified, and {@code false}
   * is returned.</p>
   *
   * <p>If the token is an <em>option group</em>, then all tokens of this
   * group are consumed, along with its argument (unless the group ends with
   * a flag).</p>
   *
   * <p>After this method returns, the next token in the iterator will be
   * a free token, if any. </p>
   */
  private static MethodSpec readMethod(
      JbockContext context,
      ClassName keysClass,
      MethodSpec readArgumentMethod,
      MethodSpec readRegularOptionMethod,
      TypeName optionMapType,
      TypeName sMapType,
      TypeName flagsType,
      ClassName optionClass,
      FieldSpec optionType,
      ClassName optionTypeClass,
      MethodSpec chopOffShortFlagMethod,
      MethodSpec addMethod,
      MethodSpec addFlagMethod) {

    ParameterSpec optMap = ParameterSpec.builder(optionMapType, "optMap").build();
    ParameterSpec sMap = ParameterSpec.builder(sMapType, "sMap").build();
    ParameterSpec flags = ParameterSpec.builder(flagsType, "flags").build();
    ParameterSpec otherTokens = ParameterSpec.builder(STRING_LIST, "otherTokens").build();
    ParameterSpec it = ParameterSpec.builder(STRING_ITERATOR, "it").build();
    ParameterSpec bucket = ParameterSpec.builder(STRING_LIST, "bucket").build();
    ParameterSpec argument = ParameterSpec.builder(STRING, "argument").build();

    ParameterSpec freeToken = ParameterSpec.builder(STRING, "freeToken").build();
    ParameterSpec token = ParameterSpec.builder(STRING, "token").build();
    ParameterSpec option = ParameterSpec.builder(optionClass, "option").build();
    ParameterSpec ignore = ParameterSpec.builder(optionClass, "__").build();

    CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement("$T $N = $N($N)", option.type, option, readRegularOptionMethod, freeToken);

    // unknown token
    builder.beginControlFlow("if ($N == null)", option)
        .addStatement("return $L", false)
        .endControlFlow();

    builder.add("\n");
    builder.add("// handle flags\n");
    builder.addStatement("$T $N = $N", STRING, token, freeToken);

    // begin option group loop
    builder.beginControlFlow("while ($N.$N == $T.$L)", option, optionType, optionTypeClass, OptionType.FLAG);

    builder.beginControlFlow("if (!$N($N))", addFlagMethod, option)
        .add(throwRepetitionErrorInGroup(option, freeToken))
        .endControlFlow();
    builder.addStatement("$N = $N($N)", token, chopOffShortFlagMethod, token);
    builder.beginControlFlow("if ($N == null)", token)
        .add("// done reading flags\n")
        .addStatement("return $L", true)
        .endControlFlow();
    builder.addStatement("$N = $N($N)", option, readRegularOptionMethod, token);
    builder.beginControlFlow("if ($N == null)", option)
        .addStatement("throw new $T($S + $N)", IllegalArgumentException.class,
            "Unknown token in option group: ", freeToken)
        .endControlFlow();

    // end option group loop
    builder.endControlFlow();

    // if we got here, the option must be binding, so read next token
    builder.add("\n");
    builder.add("// option is now binding\n");
    builder.addStatement("$T $N = $N($N, $N)", argument.type, argument, readArgumentMethod, token, it);

    builder.beginControlFlow("if (!$N($N, $N))", addMethod, option, argument)
        .add(repetitionError(option, token))
        .endControlFlow();

    builder.addStatement("return $L", true);

    return MethodSpec.methodBuilder("read")
        .addParameters(Arrays.asList(freeToken, it))
        .addCode(builder.build())
        .returns(BOOLEAN)
        .build();
  }
}