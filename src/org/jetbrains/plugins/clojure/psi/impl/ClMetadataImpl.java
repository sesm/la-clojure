package org.jetbrains.plugins.clojure.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElement;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElementImpl;
import org.jetbrains.plugins.clojure.psi.api.ClMap;
import org.jetbrains.plugins.clojure.psi.api.ClMetadata;

/**
 * @author ilyas
*/
public class ClMetadataImpl extends ClojurePsiElementImpl implements ClMetadata {
  public ClMetadataImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "ClMetadata";
  }

  private ClMap getUnderlyingMap() {
    final ClMap map = findChildByClass(ClMap.class);
    if (map == null) return null;
    return map;
  }

  public ClojurePsiElement getValue(String key) {
    final ClMap map = getUnderlyingMap();
    if (map == null) return null;
    return map.getValue(key);
  }
}
