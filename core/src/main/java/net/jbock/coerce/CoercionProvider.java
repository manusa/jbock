package net.jbock.coerce;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import net.jbock.coerce.coercions.CoercionFactory;
import net.jbock.coerce.coercions.EnumCoercion;
import net.jbock.coerce.coercions.MapperCoercion;
import net.jbock.coerce.coercions.StandardCoercions;
import net.jbock.coerce.collector.AbstractCollector;
import net.jbock.coerce.collector.DefaultCollector;
import net.jbock.coerce.mapper.MapperType;
import net.jbock.compiler.ParamName;
import net.jbock.compiler.TypeTool;
import net.jbock.compiler.ValidationException;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static javax.lang.model.element.Modifier.FINAL;

public class CoercionProvider {

  private final BasicInfo basicInfo;

  private CoercionProvider(BasicInfo basicInfo) {
    this.basicInfo = basicInfo;
  }

  public static Coercion flagCoercion(ExecutableElement sourceMethod, ParamName paramName) {
    return new Coercion(
        Optional.empty(),
        CodeBlock.of("$T.identity()", Function.class),
        CodeBlock.of(""),
        ParameterSpec.builder(TypeName.get(sourceMethod.getReturnType()), paramName.snake(), FINAL).build(),
        FieldSpec.builder(TypeName.get(sourceMethod.getReturnType()), paramName.snake(), FINAL).build(),
        e -> CodeBlock.of("$N", e),
        false);
  }

  public static Coercion findCoercion(
      ExecutableElement sourceMethod,
      ParamName paramName,
      TypeElement mapperClass,
      TypeElement collectorClass,
      InferredAttributes attributes,
      TypeTool tool) {
    BasicInfo basicInfo = BasicInfo.create(
        mapperClass, collectorClass,
        attributes, paramName, sourceMethod, tool);
    CoercionProvider coercionProvider = new CoercionProvider(basicInfo);
    return coercionProvider.run();
  }

  private Coercion run() {
    if (basicInfo.isRepeatable()) {
      return handleRepeatable();
    } else {
      return handleNotRepeatable();
    }
  }

  private Coercion handleNotRepeatable() {
    if (basicInfo.mapperClass().isPresent()) {
      return handleExplicitMapperNotRepeatable(basicInfo.mapperClass().get());
    } else {
      return handleAutoMapperNotRepeatable();
    }
  }

  private Coercion handleRepeatable() {
    if (basicInfo.mapperClass().isPresent()) {
      return handleRepeatableExplicitMapper(basicInfo.mapperClass().get());
    } else {
      return handleRepeatableAutoMapper();
    }
  }

  private Coercion handleAutoMapperNotRepeatable() {
    CoercionFactory factory = findCoercion(basicInfo.optionalInfo().orElse(basicInfo.returnType()));
    Function<ParameterSpec, CodeBlock> extractExpr = basicInfo.extractExpr(); // TODO
    return factory.getCoercion(basicInfo, Optional.empty(), Optional.empty(), extractExpr, basicInfo.returnType());
  }

  private Coercion handleExplicitMapperNotRepeatable(TypeElement mapperClass) {
    Function<ParameterSpec, CodeBlock> extractExpr;
    MapperType mapperType;
    TypeMirror constructorParamType;
    try {
      mapperType = new MapperClassAnalyzer(basicInfo, basicInfo.originalReturnType(), mapperClass).checkReturnType();
      extractExpr = p -> CodeBlock.of("$N", p);
      constructorParamType = basicInfo.originalReturnType();
    } catch (ValidationException e) {
      mapperType = new MapperClassAnalyzer(basicInfo, basicInfo.returnType(), mapperClass).checkReturnType();
      extractExpr = basicInfo.extractExpr(); // TODO
      constructorParamType = basicInfo.returnType();
    }
    return MapperCoercion.create(Optional.empty(), mapperType, basicInfo, extractExpr, constructorParamType);
  }

  private Coercion handleRepeatableAutoMapper() {
    AbstractCollector collectorInfo = collectorInfo();
    CoercionFactory coercion = findCoercion(collectorInfo.inputType());
    MapperType mapperType = MapperType.create(collectorInfo.inputType(), coercion.createMapper(collectorInfo.inputType()));
    Function<ParameterSpec, CodeBlock> extractExpr = basicInfo.extractExpr(); // TODO
    return coercion.getCoercion(basicInfo, Optional.of(collectorInfo), Optional.of(mapperType), extractExpr, basicInfo.returnType());
  }

  private Coercion handleRepeatableExplicitMapper(
      TypeElement mapperClass) {
    AbstractCollector collectorInfo = collectorInfo();
    MapperType mapperType = new MapperClassValidator(basicInfo, collectorInfo.inputType(), mapperClass).checkReturnType();
    Function<ParameterSpec, CodeBlock> extractExpr = basicInfo.extractExpr(); // TODO
    return MapperCoercion.create(Optional.of(collectorInfo), mapperType, basicInfo, extractExpr, basicInfo.returnType());
  }

  private CoercionFactory findCoercion(TypeMirror innerType) {
    CoercionFactory standardCoercion = StandardCoercions.get(tool(), tool().box(innerType));
    if (standardCoercion != null) {
      return standardCoercion;
    }
    boolean isEnum = isEnumType(innerType);
    if (!isEnum) {
      throw basicInfo.asValidationException("Unknown parameter type. Define a custom mapper.");
    }
    return EnumCoercion.create();
  }

  private boolean isEnumType(TypeMirror mirror) {
    List<? extends TypeMirror> supertypes = tool().getDirectSupertypes(mirror);
    if (supertypes.isEmpty()) {
      // not an enum
      return false;
    }
    TypeMirror superclass = supertypes.get(0);
    if (!tool().isSameErasure(superclass, tool().asType(Enum.class))) {
      // not an enum
      return false;
    }
    if (tool().isPrivateType(mirror)) {
      throw basicInfo.asValidationException("The enum may not be private.");
    }
    return true;
  }

  private AbstractCollector collectorInfo() {
    if (basicInfo.collectorClass().isPresent()) {
      return new CollectorClassValidator(basicInfo, basicInfo.collectorClass().get()).getCollectorInfo();
    }
    if (!tool().isSameErasure(basicInfo.returnType(), List.class)) {
      throw basicInfo.asValidationException("Either define a custom collector, or return List.");
    }
    List<? extends TypeMirror> typeParameters = tool().typeargs(basicInfo.returnType());
    if (typeParameters.isEmpty()) {
      throw basicInfo.asValidationException("Add a type parameter.");
    }
    return new DefaultCollector(typeParameters.get(0));
  }

  private TypeTool tool() {
    return basicInfo.tool();
  }
}
