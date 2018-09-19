package net.jbock.coerce;

import javax.lang.model.type.TypeMirror;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import static net.jbock.compiler.Util.QUALIFIED_NAME;

public enum CoercionKind {

  SIMPLE(false),
  LIST_COMBINATION(true),
  OPTIONAL_COMBINATION(true);

  final boolean combination;

  CoercionKind(boolean combination) {
    this.combination = combination;
  }

  Entry<CoercionKind, TypeMirror> of(TypeMirror mirror) {
    return new SimpleImmutableEntry<>(this, mirror);
  }

  public boolean isCombination() {
    return combination;
  }

  public static CoercionKind findKind(TypeMirror mirror) {
    String qname = mirror.accept(QUALIFIED_NAME, null);
    switch (qname) {
      case "java.util.Optional":
        return CoercionKind.OPTIONAL_COMBINATION;
      case "java.util.List":
        return CoercionKind.LIST_COMBINATION;
      default:
        return CoercionKind.SIMPLE;
    }
  }
}