package net.jbock.coerce.collectorpresent;

import net.jbock.coerce.Flattener;
import net.jbock.coerce.FlattenerResult;
import net.jbock.coerce.collectors.CustomCollector;
import net.jbock.coerce.reference.ReferenceTool;
import net.jbock.coerce.reference.ReferencedType;
import net.jbock.compiler.TypeTool;
import net.jbock.compiler.TypevarMapping;
import net.jbock.compiler.ValidationException;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.function.Function;
import java.util.stream.Collector;

import static net.jbock.coerce.SuppliedClassValidator.commonChecks;
import static net.jbock.coerce.Util.checkNotAbstract;
import static net.jbock.coerce.reference.ExpectedType.COLLECTOR;

public class CollectorClassValidator {

  private final Function<String, ValidationException> errorHandler;
  private final TypeTool tool;
  private final TypeElement collectorClass;
  private final TypeMirror originalReturnType;

  public CollectorClassValidator(Function<String, ValidationException> errorHandler, TypeTool tool, TypeElement collectorClass, TypeMirror originalReturnType) {
    this.errorHandler = errorHandler;
    this.tool = tool;
    this.collectorClass = collectorClass;
    this.originalReturnType = originalReturnType;
  }

  // visible for testing
  public CustomCollector getCollectorInfo() {
    commonChecks(collectorClass);
    checkNotAbstract(collectorClass);
    ReferencedType<Collector> collectorType = new ReferenceTool<>(COLLECTOR, errorHandler, tool, collectorClass)
        .getReferencedType();
    TypeMirror inputType = collectorType.typeArguments().get(0);
    TypeMirror outputType = collectorType.typeArguments().get(2);
    TypevarMapping rightSolution = tool.unify(originalReturnType, outputType)
        .orElseThrow(this::boom);
    TypevarMapping leftSolution = TypevarMapping.empty(tool); // left side is currently ignored
    FlattenerResult result = new Flattener(errorHandler, tool, collectorClass)
        .getTypeParameters(leftSolution, rightSolution)
        .orElseThrow(this::boom);
    return new CustomCollector(tool, result.substitute(inputType).orElseThrow(f -> boom(f.getMessage())),
        collectorClass, collectorType.isSupplier(), result.getTypeParameters());
  }

  private ValidationException boom(String message) {
    return errorHandler.apply(COLLECTOR.boom(message));
  }
}
