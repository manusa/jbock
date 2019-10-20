package net.jbock.coerce;

import net.jbock.coerce.either.Either;
import net.jbock.coerce.either.Left;
import net.jbock.coerce.either.Right;
import net.jbock.coerce.mapper.MapperType;
import net.jbock.coerce.mapper.ReferenceMapperType;
import net.jbock.coerce.reference.ReferenceTool;
import net.jbock.coerce.reference.ReferencedType;
import net.jbock.compiler.TypeTool;
import net.jbock.compiler.ValidationException;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static net.jbock.coerce.SuppliedClassValidator.commonChecks;
import static net.jbock.coerce.reference.ExpectedType.MAPPER;

final class MapperClassValidator {

  private final BasicInfo basicInfo;
  private final TypeMirror expectedReturnType;
  private final TypeElement mapperClass;

  MapperClassValidator(BasicInfo basicInfo, TypeMirror expectedReturnType, TypeElement mapperClass) {
    this.basicInfo = basicInfo;
    this.expectedReturnType = expectedReturnType;
    this.mapperClass = mapperClass;
  }

  ReferenceMapperType checkReturnType() {
    commonChecks(basicInfo, mapperClass, "mapper");
    ReferencedType<Function> functionType = new ReferenceTool<>(MAPPER, basicInfo, mapperClass)
        .getReferencedType();
    TypeMirror t = functionType.expectedType().typeArguments().get(0);
    TypeMirror r = functionType.expectedType().typeArguments().get(1);
    Optional<Map<String, TypeMirror>> t_result = tool().unify(tool().asType(String.class), t);
    if (!t_result.isPresent()) {
      throw boom(String.format("The supplied function must take a String argument, but takes %s", t));
    }
    Optional<Map<String, TypeMirror>> r_result = tool().unify(expectedReturnType, r);
    if (!r_result.isPresent()) {
      throw boom(String.format("The mapper should return %s but returns %s", expectedReturnType, r));
    }
    Either<List<TypeMirror>, String> solution = new Solver(basicInfo, mapperClass)
        .solve(Arrays.asList(t_result.get(), r_result.get()));
    if (solution instanceof Right) {
      throw boom(((Right<List<TypeMirror>, String>) solution).value());
    }
    return MapperType.create(basicInfo.tool(), functionType.isSupplier(), mapperClass,
        ((Left<List<TypeMirror>, String>) solution).value());
  }

  private ValidationException boom(String message) {
    return MAPPER.boom(basicInfo, message);
  }

  private TypeTool tool() {
    return basicInfo.tool();
  }
}
