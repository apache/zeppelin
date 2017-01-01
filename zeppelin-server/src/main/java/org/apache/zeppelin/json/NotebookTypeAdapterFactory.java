package org.apache.zeppelin.json;

import java.io.IOException;

import org.apache.zeppelin.socket.NotebookServer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Custom adapter type factory
 * Modify the jsonObject before serailaization/deserialization
 * Check sample implementation at  {@link NotebookServer}
 * @param <C> the type whose json is to be customized for serialization/deserialization
 */
public class NotebookTypeAdapterFactory<C> implements TypeAdapterFactory {
  private final Class<C> customizedClass;

  public NotebookTypeAdapterFactory(Class<C> customizedClass) {
    this.customizedClass = customizedClass;
  }

  @SuppressWarnings("unchecked")
  // we use a runtime check to guarantee that 'C' and 'T' are equal
  public final <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    return type.getRawType() == customizedClass ? (TypeAdapter<T>) customizeTypeAdapter(gson,
        (TypeToken<C>) type) : null;
  }

  private TypeAdapter<C> customizeTypeAdapter(Gson gson, TypeToken<C> type) {
    final TypeAdapter<C> delegate = gson.getDelegateAdapter(this, type);
    final TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
    return new TypeAdapter<C>() {
      @Override
      public void write(JsonWriter out, C value) throws IOException {
        JsonElement tree = delegate.toJsonTree(value);
        beforeWrite(value, tree);
        elementAdapter.write(out, tree);
      }

      @Override
      public C read(JsonReader in) throws IOException {
        JsonElement tree = elementAdapter.read(in);
        afterRead(tree);
        return delegate.fromJsonTree(tree);
      }
    };
  }

  /**
   * Override this to change {@code toSerialize} before it is written to the
   * outgoing JSON stream.
   */
  protected void beforeWrite(C source, JsonElement toSerialize) {
  }

  /**
   * Override this to change {@code deserialized} before it parsed into the
   * application type.
   */
  protected void afterRead(JsonElement deserialized) {
  }
}
