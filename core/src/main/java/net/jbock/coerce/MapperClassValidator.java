package net.jbock.coerce;

import net.jbock.compiler.TypeTool;
import net.jbock.compiler.ValidationException;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.jbock.coerce.SuppliedClassValidator.commonChecks;
import static net.jbock.compiler.TypeTool.asDeclared;

final class MapperClassValidator {

  private final BasicInfo basicInfo;
  private final TypeMirror expectedReturnType;
  private final TypeElement mapperClass;

  private boolean isInvalidT(TypeParameterElement typeParameter) {
    TypeMirror stringType = tool().asType(String.class);
    for (TypeMirror bound : typeParameter.getBounds()) {
      if (!tool().isAssignable(stringType, bound)) {
        return true;
      }
    }
    return false;
  }

  private boolean isInvalidR(TypeParameterElement typeParameter) {
    for (TypeMirror bound : typeParameter.getBounds()) {
      if (!tool().isAssignable(expectedReturnType, bound)) {
        return true;
      }
    }
    return false;
  }

  MapperClassValidator(BasicInfo basicInfo, TypeMirror expectedReturnType, TypeElement mapperClass) {
    this.basicInfo = basicInfo;
    this.expectedReturnType = expectedReturnType;
    this.mapperClass = mapperClass;
  }

  MapperType checkReturnType() {
    commonChecks(basicInfo, mapperClass, "mapper");
    AbstractFunctionType functionType = getMapperType();
    TypeMirror string = tool().asType(String.class);
    TypeMirror t = asDeclared(functionType.functionType).getTypeArguments().get(0);
    TypeMirror r = asDeclared(functionType.functionType).getTypeArguments().get(1);
    Optional<Map<String, TypeMirror>> t_result = tool().unify(string, t);
    if (!t_result.isPresent()) {
      throw boom(String.format("The supplied function must take a String argument, but takes %s", t));
    }
    Optional<Map<String, TypeMirror>> r_result = tool().unify(expectedReturnType, r);
    if (!r_result.isPresent()) {
      throw boom(String.format("The mapper should return %s but returns %s", expectedReturnType, r));
    }
    return new Solver(functionType, t_result.get(), r_result.get()).solve();
  }

  private AbstractFunctionType getMapperType() {
    Optional<DeclaredType> supplier = typecheck(Supplier.class, mapperClass);
    if (supplier.isPresent()) {
      DeclaredType supplierType = supplier.get();
      List<? extends TypeMirror> typeArgs = asDeclared(supplierType).getTypeArguments();
      if (typeArgs.isEmpty()) {
        throw boom("raw Supplier type");
      }
      if (tool().isSameErasure(typeArgs.get(0), Function.class)) {
        if (tool().isRawType(typeArgs.get(0))) {
          throw boom("the function type must be parameterized");
        }
        Map<String, TypeParameterElement> typevarMapping = new HashMap<>();
        mapperClass.getTypeParameters().forEach(p -> typevarMapping.put(p.toString(), p));
        return new SupplierType(typevarMapping, supplierType, asDeclared(typeArgs.get(0)));
      }
      if (typeArgs.get(0).getKind() != TypeKind.DECLARED) {
        throw boom("could not infer type parameters");
      }
      DeclaredType suppliedType = asDeclared(typeArgs.get(0));
      TypeElement suppliedTypeElement = tool().asTypeElement(suppliedType);
      Map<String, TypeParameterElement> typevarMapping = createTypevarMapping(suppliedType, suppliedTypeElement);
      DeclaredType functionType = typecheck(Function.class, suppliedTypeElement).orElseThrow(() ->
          boom("not a Function or Supplier<Function>"));
      return new SupplierType(typevarMapping, supplierType, functionType);
    }
    TypeMirror mapper = typecheck(Function.class, mapperClass)
        .orElseThrow(() -> boom("not a Function or Supplier<Function>"));
    if (tool().isRawType(mapper)) {
      throw boom("the function type must be parameterized");
    }
    return new FunctionType(asDeclared(mapper));
  }

  private Map<String, TypeParameterElement> createTypevarMapping(
      DeclaredType declared,
      TypeElement typeElement) {
    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
    List<? extends TypeMirror> typeArguments = declared.getTypeArguments();
    if (declared.getTypeArguments().size() != typeParameters.size()) {
      throw boom("could not infer type parameters");
    }
    Map<String, TypeParameterElement> mapping = new HashMap<>();
    for (int i = 0; i < typeParameters.size(); i++) {
      TypeParameterElement p = typeParameters.get(i);
      TypeMirror typeArgument = typeArguments.get(i);
      if (typeArgument.getKind() == TypeKind.TYPEVAR) {
        mapping.put(typeArgument.toString(), p);
      }
    }
    return mapping;
  }

  private TypeTool tool() {
    return basicInfo.tool();
  }

  private Optional<DeclaredType> typecheck(Class<?> goal, TypeElement start) {
    return Resolver.typecheck(start, goal, tool());
  }

  private ValidationException boom(String message) {
    return basicInfo.asValidationException(String.format("There is a problem with the mapper class: %s.", message));
  }

  private abstract static class AbstractFunctionType {
    final DeclaredType functionType; // subtype of Function

    AbstractFunctionType(DeclaredType functionType) {
      this.functionType = functionType;
    }

    abstract TypeParameterElement getTypevar(TypeParameterElement typeParameterInMapperType);
  }

  private static class FunctionType extends AbstractFunctionType {

    FunctionType(DeclaredType functionType) {
      super(functionType);
    }

    @Override
    TypeParameterElement getTypevar(TypeParameterElement typeParameterInMapperType) {
      return typeParameterInMapperType;
    }
  }

  private static class SupplierType extends AbstractFunctionType {

    // mapper type typevar -> function type typevar
    final Map<String, TypeParameterElement> typevarMapping;

    final DeclaredType functionType; // subtype of Function
    final DeclaredType supplierType; // subtype of Supplier

    SupplierType(Map<String, TypeParameterElement> typevarMapping, DeclaredType supplierType, DeclaredType functionType) {
      super(functionType);
      this.typevarMapping = typevarMapping;
      this.functionType = functionType;
      this.supplierType = supplierType;
    }

    @Override
    TypeParameterElement getTypevar(TypeParameterElement typeParameterInMapperType) {
      return typevarMapping.get(typeParameterInMapperType.toString());
    }
  }

  private class Solver {

    final AbstractFunctionType functionType;
    final Map<String, TypeMirror> t_result;
    final Map<String, TypeMirror> r_result;

    Solver(AbstractFunctionType functionType, Map<String, TypeMirror> t_result, Map<String, TypeMirror> r_result) {
      this.functionType = functionType;
      this.t_result = t_result;
      this.r_result = r_result;
    }

    MapperType solve() {
      List<? extends TypeParameterElement> typeParameters = mapperClass.getTypeParameters();
      List<TypeMirror> solution = typeParameters.stream()
          .map(functionType::getTypevar)
          .map(this::getSolution)
          .collect(Collectors.toList());
      return MapperType.create(functionType instanceof SupplierType, mapperClass, solution);
    }

    TypeMirror getSolution(TypeParameterElement typeParameter) {
      TypeMirror t = t_result.get(typeParameter.toString());
      TypeMirror r = r_result.get(typeParameter.toString());
      if (t != null) {
        if (isInvalidT(typeParameter)) {
          throw boom("invalid bounds");
        }
        if (r != null && !tool().isSameType(t, r)) {
          throw boom("could not infer type parameters");
        }
      }
      if (r != null) {
        if (isInvalidR(typeParameter)) {
          throw boom("invalid bounds");
        }
      }
      return Stream.of(t, r)
          .filter(Objects::nonNull)
          .findFirst()
          .orElseThrow(() -> boom("could not infer all type parameters"));
    }
  }
}
