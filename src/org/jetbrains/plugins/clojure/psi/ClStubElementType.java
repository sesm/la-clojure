package org.jetbrains.plugins.clojure.psi;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.file.ClojureFileType;

/**
 * @author ilyas
 */
public abstract class ClStubElementType<S extends StubElement, T extends ClojurePsiElement> extends IStubElementType<S, T> {

  public ClStubElementType(@NonNls @NotNull String debugName) {
    super(debugName, ClojureFileType.CLOJURE_LANGUAGE);
  }

  public void indexStub(final S stub, final IndexSink sink) {
  }

  public String getExternalId() {
    return "clj." + super.toString();
  }

}
