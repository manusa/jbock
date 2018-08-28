package net.jbock.examples.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test util
 */
public final class ParserTestFixture<E> {

  public interface TriFunction<A, B, D> {
    D apply(A a, B b, int c);
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final TriFunction<String[], PrintStream, Optional<E>> parseMethod;

  private ParserTestFixture(
      TriFunction<String[], PrintStream, Optional<E>> parseMethod) {
    this.parseMethod = parseMethod;
  }

  private static JsonNode readJson(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static JsonNode parseJson(Object... kvs) {
    ObjectNode node = MAPPER.createObjectNode();
    if (kvs.length % 2 != 0) {
      throw new IllegalArgumentException("length must be even: " + Arrays.toString(kvs));
    }
    Set<String> keys = new HashSet<>();
    for (int i = 0; i < kvs.length; i += 2) {
      String k = kvs[i].toString();
      if (!keys.add(k)) {
        throw new IllegalArgumentException("duplicate key: " + k);
      }
      Object v = kvs[i + 1];
      if (v == null) {
        node.put(k, (String) null);
      }
      if (v instanceof Integer) {
        node.put(k, (Integer) v);
      } else if (v instanceof String) {
        node.put(k, (String) v);
      } else if (v instanceof Boolean) {
        node.put(k, (Boolean) v);
      } else if (v instanceof List) {
        List list = (List) v;
        ArrayNode array = node.putArray(k);
        for (Object s : list) {
          array.add(s.toString());
        }
      }
    }
    return node;
  }

  public static <E> ParserTestFixture<E> create(
      TriFunction<String[], PrintStream, Optional<E>> fn) {
    return new ParserTestFixture<>(fn);
  }

  public JsonAssert<E> assertThat(String... args) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    Optional<E> parsed = parseMethod.apply(args, out, 2);
    return new JsonAssert<>(parsed, new String(baos.toByteArray()));
  }

  public E parse(String... args) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    return parseMethod.apply(args, out, 2).get();
  }

  public void assertPrints(String... expected) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Optional<E> result = parseMethod.apply(new String[]{"--help"}, new PrintStream(out), 2);
    assertFalse(result.isPresent());
    String[] actual = new String(out.toByteArray()).split("\\r?\\n", -1);
    assertArrayEquals(expected, actual, "Actual: " + Arrays.toString(actual));
  }

  public static final class JsonAssert<E> {

    private final Optional<E> parsed;

    private final String e;

    private JsonAssert(Optional<E> parsed, String e) {
      this.parsed = parsed;
      this.e = e;
    }

    public void failsWithLine1(String expectedMessage) {
      if (parsed.isPresent()) {
        fail("Expecting a failure" +
            " but parsing was successful");
      }
      assertTrue(e.startsWith("Usage:"));
      assertTrue(e.contains("\n"));
      String actualMessage = e.split("\\r?\\n", -1)[1];
      assertEquals(expectedMessage, actualMessage);
    }

    public void failsWithLines(String... expected) {
      if (parsed.isPresent()) {
        fail("Expecting a failure" +
            " but parsing was successful");
      }
      String[] actualMessage = e.split("\\r?\\n", -1);
      assertArrayEquals(expected, actualMessage, "Actual: " + Arrays.toString(actualMessage));
    }

    public void satisfies(Predicate<E> predicate) {
      assertTrue(parsed.isPresent(), "Parsing was not successful");
      assertTrue(predicate.test(parsed.get()));
    }

    public void succeeds(Object... expected) {
      assertTrue(parsed.isPresent(), "Parsing was not successful");
      String jsonString = parsed.get().toString();
      JsonNode actualJson = readJson(jsonString);
      JsonNode expectedJson = parseJson(expected);
      assertEquals(expectedJson, actualJson);
    }
  }
}
