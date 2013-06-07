package org.jetbrains.plugins.clojure.psi.stubs.api;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;

/**
 * @author ilyas
 */
public class ClNsStub extends StubBase<ClNs> implements NamedStub<ClNs> {
  private final StringRef myName;
  private final int myTextOffset;
  private final boolean classDefinition;

  public ClNsStub(StubElement parent, StringRef name, final IStubElementType elementType, int textOffset, boolean classDefinition) {
    super(parent, elementType);
    myName = name;
    myTextOffset = textOffset;
    this.classDefinition = classDefinition;
  }

  public int getTextOffset() {
    return myTextOffset;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  public boolean isClassDefinition() {
    return classDefinition;
  }
}
