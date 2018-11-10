package net.jbock.coerce;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import net.jbock.compiler.TypeTool;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Coercion {

  // only for repeatable
  private final Optional<ParameterSpec> collectorParam;

  // helper.build
  private final Optional<CodeBlock> mapExpr;

  // helper.build
  private final CodeBlock initMapper;

  // helper.build
  private final Optional<CodeBlock> initCollector;

  // impl constructor param
  private final ParameterSpec constructorParam;

  // impl
  private final FieldSpec field;

  private final Function<ParameterSpec, CodeBlock> extractExpr;

  private final boolean isDefaultCollector;

  private Coercion(
      Optional<ParameterSpec> collectorParam,
      Optional<CodeBlock> mapExpr,
      CodeBlock initMapper,
      Optional<CodeBlock> initCollector,
      ParameterSpec constructorParam,
      FieldSpec field,
      Function<ParameterSpec, CodeBlock> extractExpr,
      boolean isDefaultCollector) {
    this.collectorParam = collectorParam;
    this.mapExpr = mapExpr;
    this.initMapper = initMapper;
    this.initCollector = initCollector;
    this.constructorParam = constructorParam;
    this.field = field;
    this.extractExpr = extractExpr;
    this.isDefaultCollector = isDefaultCollector;
  }

  public static Coercion create(
      Optional<ParameterSpec> collectorParam,
      Optional<CodeBlock> mapExpr,
      CodeBlock initMapper,
      TypeMirror mapperReturnType,
      Optional<CodeBlock> initCollector,
      TypeMirror constructorParamType,
      BasicInfo basicInfo) {
    boolean isDefaultCollector = isDefaultCollector(initCollector, constructorParamType, mapperReturnType);
    ParameterSpec constructorParam = ParameterSpec.builder(
        TypeName.get(constructorParamType), basicInfo.paramName()).build();
    return new Coercion(collectorParam, mapExpr, initMapper, initCollector, constructorParam, basicInfo.fieldSpec(), basicInfo.extractExpr(), isDefaultCollector);
  }

  /**
   * Maps from String to mapperReturnType
   */
  public Optional<CodeBlock> mapExpr() {
    return mapExpr;
  }

  public CodeBlock initMapper() {
    return initMapper;
  }

  public Optional<CodeBlock> initCollector() {
    if (skipMapCollect()) {
      return Optional.empty();
    }
    return initCollector;
  }

  public ParameterSpec constructorParam() {
    return constructorParam;
  }

  public FieldSpec field() {
    return field;
  }

  public Optional<ParameterSpec> collectorParam() {
    if (skipMapCollect()) {
      return Optional.empty();
    }
    return collectorParam;
  }

  public Optional<CodeBlock> collectExpr() {
    if (skipMapCollect()) {
      return Optional.empty();
    }
    if (isDefaultCollector) {
      return Optional.of(CodeBlock.of("$T.toList()", Collectors.class));
    }
    if (!collectorParam.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(CodeBlock.of("$N", collectorParam.get()));
  }

  public boolean skipMapCollect() {
    return !mapExpr.isPresent() && isDefaultCollector;
  }

  private static boolean isDefaultCollector(
      Optional<CodeBlock> initCollector,
      TypeMirror paramType,
      TypeMirror mapperReturnType) {
    if (initCollector.isPresent()) {
      return false;
    }
    if (mapperReturnType.getKind() != TypeKind.DECLARED) {
      return false;
    }
    TypeTool tool = TypeTool.get();
    return tool.isSameType(paramType, tool.listOf(mapperReturnType));
  }

  public CodeBlock extractExpr() {
    return extractExpr.apply(constructorParam);
  }
}
