package net.jbock.coerce.matching;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import net.jbock.coerce.BasicInfo;
import net.jbock.coerce.NonFlagCoercion;
import net.jbock.coerce.NonFlagSkew;
import net.jbock.compiler.TypeTool;

import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;

import static net.jbock.coerce.NonFlagSkew.OPTIONAL;
import static net.jbock.coerce.NonFlagSkew.REPEATABLE;
import static net.jbock.coerce.NonFlagSkew.REQUIRED;

public class AutoMatcher {

  private final BasicInfo basicInfo;

  public AutoMatcher(BasicInfo basicInfo) {
    this.basicInfo = basicInfo;
  }

  public NonFlagCoercion findCoercion() {
    TypeMirror returnType = basicInfo.returnType();
    Optional<Optionalish> opt = Optionalish.unwrap(returnType, tool());
    Optional<TypeMirror> listWrapped = tool().unwrap(List.class, returnType);
    if (opt.isPresent()) {
      Optionalish optional = opt.get();
      // optional match
      ParameterSpec param = basicInfo.constructorParam(optional.liftedType());
      return createCoercion(optional.wrappedType(), optional.extractExpr(param), param, OPTIONAL);
    }
    if (listWrapped.isPresent()) {
      // repeatable match
      ParameterSpec param = basicInfo.constructorParam(returnType);
      return createCoercion(listWrapped.get(), param, REPEATABLE);
    }
    // exact match (-> required)
    ParameterSpec param = basicInfo.constructorParam(returnType);
    return createCoercion(tool().box(returnType), param, REQUIRED);
  }

  private NonFlagCoercion createCoercion(TypeMirror testType, ParameterSpec constructorParam, NonFlagSkew skew) {
    return createCoercion(testType, CodeBlock.of("$N", constructorParam), constructorParam, skew);
  }

  private NonFlagCoercion createCoercion(TypeMirror testType, CodeBlock extractExpr, ParameterSpec constructorParam, NonFlagSkew skew) {
    return basicInfo.findAutoMapper(testType)
        .map(mapExpr -> new NonFlagCoercion(basicInfo, mapExpr, MatchingAttempt.autoCollectExpr(basicInfo, skew), extractExpr, skew, constructorParam))
        .orElseThrow(() -> basicInfo.failure(String.format("Unknown parameter type: %s. Try defining a custom mapper or collector.",
            basicInfo.returnType())));
  }

  private TypeTool tool() {
    return basicInfo.tool();
  }
}
