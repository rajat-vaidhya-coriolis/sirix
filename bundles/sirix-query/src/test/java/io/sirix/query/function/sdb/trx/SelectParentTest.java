package io.sirix.query.function.sdb.trx;

import io.sirix.JsonTestHelper;
import io.sirix.query.SirixCompileChain;
import io.sirix.query.SirixQueryContext;
import io.sirix.query.json.BasicJsonDBStore;
import io.brackit.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SelectParentTest {

  @BeforeEach
  protected void setUp() {
    JsonTestHelper.deleteEverything();
  }

  @AfterEach
  protected void tearDown() {
    JsonTestHelper.closeEverything();
  }

  @Test
  public void test() throws IOException {
    // Initialize query context and store.
    try (final BasicJsonDBStore store = BasicJsonDBStore.newBuilder()
                                                        .location(JsonTestHelper.PATHS.PATH1.getFile().getParent())
                                                        .build();
         final SirixQueryContext ctx = SirixQueryContext.createWithJsonStore(store);
         final SirixCompileChain chain = SirixCompileChain.createWithJsonStore(store)) {

      // Use Query to store a JSON string into the store.
      final String storeQuery = "jn:store('json-path1','mydoc.jn','[\"bla\", \"blubb\"]')";
      new Query(chain, storeQuery).evaluate(ctx);

      // Use Query to load a JSON database/resource.
      final String openQuery = "sdb:select-parent(jn:doc('json-path1','mydoc.jn')[1])";

      try (final var out = new ByteArrayOutputStream(); final var printWriter = new PrintWriter(out)) {
        new Query(chain, openQuery).serialize(ctx, printWriter);
        assertEquals("[\"bla\",\"blubb\"]", out.toString());
      }
    }
  }
}
