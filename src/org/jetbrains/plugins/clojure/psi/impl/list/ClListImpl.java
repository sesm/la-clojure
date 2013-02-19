package org.jetbrains.plugins.clojure.psi.impl.list;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.psi.api.ClList;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;

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

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return ResolveUtil.processDeclarations(this, processor, state, lastParent, place);
  }
}
