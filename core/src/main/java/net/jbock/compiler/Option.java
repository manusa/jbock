package net.jbock.compiler;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import net.jbock.Description;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static net.jbock.compiler.Analyser.IS_FLAG;
import static net.jbock.compiler.Analyser.LONG_NAME;
import static net.jbock.compiler.Analyser.SHORT_NAME;
import static net.jbock.compiler.Analyser.STRING;
import static net.jbock.compiler.Names.isFlag;

final class Option {

  private static final FieldSpec DESCRIPTION = FieldSpec.builder(
      Analyser.STRING_LIST, "description", PUBLIC, FINAL).build();

  private static final FieldSpec ARGUMENT_NAME = FieldSpec.builder(
      Analyser.STRING, "descriptionParameter", PUBLIC, FINAL).build();

  private static final String DD = "--";
  private static final String CS = ", ";
  private static final String NL = "\n";

  private final ExecutableElement constructor;
  private final ClassName argumentInfo;
  private final MethodSpec describeNamesMethod;
  private final MethodSpec descriptionBlockMethod;

  private Option(ExecutableElement constructor, ClassName argumentInfo) {
    this.constructor = constructor;
    this.argumentInfo = argumentInfo;
    this.describeNamesMethod = describeNamesMethod();
    this.descriptionBlockMethod = descriptionBlockMethod();
  }

  static Option create(ExecutableElement constructor, ClassName argumentInfo) {
    return new Option(constructor, argumentInfo);
  }

  TypeSpec define() {
    ClassName originalClass = (ClassName) TypeName.get(constructor.getEnclosingElement().asType());
    String originalClassName = originalClass.simpleName();
    TypeSpec.Builder builder = TypeSpec.enumBuilder(argumentInfo)
        .addJavadoc(String.format("Arguments of {@link %s#%s(\n  %s\n)}\n", originalClassName, originalClassName,
            constructor.getParameters().stream()
                .map(variableElement -> TypeName.get(variableElement.asType()).toString())
                .map(s -> s.replaceAll("^java.lang.", ""))
                .collect(Collectors.joining(",\n  "))));
    boolean needsSuffix = constructor.getParameters().stream()
        .map(Option::upcase)
        .collect(Collectors.toSet()).size() < constructor.getParameters().size();
    for (int i = 0; i < constructor.getParameters().size(); i++) {
      VariableElement variableElement = constructor.getParameters().get(i);
      Names names = Names.create(variableElement);
      boolean flag = isFlag(variableElement);
      Description description = variableElement.getAnnotation(Description.class);
      String[] desc = getText(description);
      String argumentName = flag ? null : getArgumentName(description);
      String suffix = needsSuffix ? String.format("_%d", i) : "";
      String enumConstant = upcase(variableElement) + suffix;
      String format = String.format("$S, $S, $L, $S, new $T[] {%s}",
          String.join(", ", Collections.nCopies(desc.length, "$S")));
      List<Comparable<? extends Comparable<?>>> fixArgs =
          Arrays.asList(names.longName, names.shortName, flag, argumentName, STRING);
      List<Object> args = new ArrayList<>(fixArgs.size() + desc.length);
      args.addAll(fixArgs);
      args.addAll(Arrays.asList(desc));
      builder.addEnumConstant(enumConstant, anonymousClassBuilder(format,
          args.toArray()).build());
    }
    ParameterSpec longName = ParameterSpec.builder(LONG_NAME.type, LONG_NAME.name).build();
    ParameterSpec shortName = ParameterSpec.builder(SHORT_NAME.type, SHORT_NAME.name).build();
    ParameterSpec isFlag = ParameterSpec.builder(IS_FLAG.type, IS_FLAG.name).build();
    ParameterSpec description = ParameterSpec.builder(ArrayTypeName.of(STRING), DESCRIPTION.name).build();
    ParameterSpec argumentName = ParameterSpec.builder(ARGUMENT_NAME.type, ARGUMENT_NAME.name).build();
    //@formatter:off
    return builder.addModifiers(PUBLIC)
        .addFields(Arrays.asList(LONG_NAME, SHORT_NAME, IS_FLAG, ARGUMENT_NAME, DESCRIPTION))
        .addMethod(describeMethod())
        .addMethod(describeNamesMethod)
        .addMethod(descriptionBlockMethod)
        .addMethod(MethodSpec.constructorBuilder()
            .addParameters(Arrays.asList(longName, shortName, isFlag, argumentName, description))
            .beginControlFlow("if ($N == null && $N == null)", longName, shortName)
              .addStatement("throw new $T($S)", NullPointerException.class, "both names are null")
              .endControlFlow()
            .beginControlFlow("if ($N == null)", description)
              .addStatement("throw new $T($S)", NullPointerException.class, "description")
              .endControlFlow()
            .beginControlFlow("if (!$N && $N == null)", isFlag, argumentName)
              .addStatement("throw new $T($S)", NullPointerException.class, "argumentName")
              .endControlFlow()
            .addStatement("this.$N = $N", LONG_NAME, longName)
            .addStatement("this.$N = $N", SHORT_NAME, shortName)
            .addStatement("this.$N = $N", IS_FLAG, isFlag)
            .addStatement("this.$N = $T.unmodifiableList($T.asList($N))", DESCRIPTION,
                Collections.class, Arrays.class, description)
            .addStatement("this.$N = $N", ARGUMENT_NAME, argumentName)
            .build())
        .build();
    //@formatter:on
  }

  private static String upcase(VariableElement variableElement) {
    return variableElement.getSimpleName().toString()
        .toUpperCase(Locale.ENGLISH);
  }

  private static String[] getText(Description description) {
    if (description == null) {
      return new String[]{"--- description goes here ---"};
    }
    return description.lines();
  }

  private static String getArgumentName(Description description) {
    if (description == null) {
      return "VAL";
    }
    return description.argumentName();
  }

  private static MethodSpec descriptionBlockMethod() {
    ParameterSpec indent = ParameterSpec.builder(TypeName.INT, "indent").build();
    ParameterSpec sIndent = ParameterSpec.builder(STRING, "indentString").build();
    ParameterSpec aIndent = ParameterSpec.builder(ArrayTypeName.of(TypeName.CHAR), "a").build();
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.addStatement("$T $N = new $T[$N]", aIndent.type, aIndent,
        TypeName.CHAR, indent);
    builder.addStatement("$T.fill($N, ' ')", Arrays.class, aIndent);
    builder.addStatement("$T $N = new $T($N)", STRING, sIndent, STRING, aIndent);
    builder.addStatement("return $N + $T.join('\\n' + $N, $N)", sIndent, STRING, sIndent, DESCRIPTION);
    return MethodSpec.methodBuilder("descriptionBlock")
        .addModifiers(PUBLIC)
        .addParameter(indent)
        .returns(Analyser.STRING)
        .addCode(builder.build())
        .build();
  }


  private MethodSpec describeMethod() {
    ParameterSpec sb = ParameterSpec.builder(StringBuilder.class, "sb").build();
    ParameterSpec indent = ParameterSpec.builder(TypeName.INT, "indent").build();
    CodeBlock codeBlock = CodeBlock.builder()
        .addStatement("$T $N = new $T()", StringBuilder.class, sb, StringBuilder.class)
        .addStatement("$N.append($N())", sb, describeNamesMethod)
        .addStatement("$N.append($S)", sb, NL)
        .addStatement("$N.append($N($N))", sb, descriptionBlockMethod, indent)
        .addStatement("return $N.toString()", sb)
        .build();
    return MethodSpec.methodBuilder("describe")
        .addModifiers(PUBLIC)
        .returns(Analyser.STRING)
        .addParameter(indent)
        .addCode(codeBlock)
        .build();
  }

  private static MethodSpec describeNamesMethod() {
    ParameterSpec sb = ParameterSpec.builder(StringBuilder.class, "sb").build();
    //@formatter:off
    CodeBlock.Builder builder = CodeBlock.builder();
    builder.beginControlFlow("if ($N)", IS_FLAG)
      .addStatement("$T $N = new $T()", StringBuilder.class, sb, StringBuilder.class)
      .beginControlFlow("if ($N != null && $N != null)", LONG_NAME, SHORT_NAME)
        .addStatement("$N.append('-').append($N)", sb, SHORT_NAME)
        .addStatement("$N.append($S).append($S).append($N)", sb, CS, DD, LONG_NAME)
        .endControlFlow()
      .beginControlFlow("else if ($N != null)", LONG_NAME)
        .addStatement("$N.append($S).append($N)", sb, DD, LONG_NAME)
        .endControlFlow()
      .beginControlFlow("else")
        .addStatement("$N.append('-').append($N)", sb, SHORT_NAME)
        .endControlFlow()
      .addStatement("return $N.toString()", sb)
    .endControlFlow();
    builder.addStatement("$T $N = new $T()", StringBuilder.class, sb, StringBuilder.class)
        .beginControlFlow("if ($N != null && $N != null)", LONG_NAME, SHORT_NAME)
          .addStatement("$N.append('-').append($N)", sb, SHORT_NAME)
          .addStatement("$N.append($S).append($S).append($N).append('=').append($N)", sb, CS, DD, LONG_NAME, ARGUMENT_NAME)
          .endControlFlow()
        .beginControlFlow("else if ($N != null)", LONG_NAME)
          .addStatement("$N.append($S).append($N).append(' ').append($N)", sb, DD, LONG_NAME, ARGUMENT_NAME)
          .endControlFlow()
        .beginControlFlow("else")
          .addStatement("$N.append('-').append($N).append(' ').append($N)", sb, SHORT_NAME, ARGUMENT_NAME)
          .endControlFlow()
      .addStatement("return $N.toString()", sb);
    //@formatter:on
    return MethodSpec.methodBuilder("describeNames")
        .addModifiers(PUBLIC)
        .returns(Analyser.STRING)
        .addCode(builder.build())
        .build();

  }
}