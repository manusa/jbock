package net.jbock.coerce.collectorabsent.mapperabsent;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import net.jbock.coerce.BasicInfo;
import net.jbock.coerce.Coercion;
import net.jbock.coerce.ParameterType;
import net.jbock.coerce.collector.AbstractCollector;
import net.jbock.coerce.collector.DefaultCollector;
import net.jbock.coerce.mapper.MapperType;

import javax.lang.model.type.TypeMirror;
import java.util.Optional;
import java.util.function.Function;

class Attempt {

  private final TypeMirror expectedReturnType;
  private final Function<ParameterSpec, CodeBlock> extractExpr;
  private final TypeMirror constructorParamType;
  private final ParameterType parameterType;
  private final BasicInfo basicInfo;

  Attempt(TypeMirror expectedReturnType, Function<ParameterSpec, CodeBlock> extractExpr, TypeMirror constructorParamType, ParameterType parameterType, BasicInfo basicInfo) {
    this.expectedReturnType = expectedReturnType;
    this.extractExpr = extractExpr;
    this.constructorParamType = constructorParamType;
    this.parameterType = parameterType;
    this.basicInfo = basicInfo;
  }

  Optional<Coercion> findCoercion() {
    Optional<CodeBlock> mapExpr = basicInfo.findMapExpr(expectedReturnType);
    if (!mapExpr.isPresent()) {
      return Optional.empty();
    }
    MapperType mapperType = MapperType.create(mapExpr.get());
    Optional<AbstractCollector> collector = parameterType.isRepeatable() ? Optional.of(new DefaultCollector(expectedReturnType)) : Optional.empty();
    return Optional.of(Coercion.getCoercion(basicInfo, collector, mapperType, extractExpr, constructorParamType, parameterType));
  }
}