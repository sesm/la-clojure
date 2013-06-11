package org.jetbrains.plugins.clojure.psi.impl.list;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
*/
public class ClListImpl extends ClListBaseImpl<EmptyStub> {

  public ClListImpl(ASTNode node) {
    super(node);
  }

  public ClListImpl(EmptyStub stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public String toString() {
    return "ClList"; 
  }
}
