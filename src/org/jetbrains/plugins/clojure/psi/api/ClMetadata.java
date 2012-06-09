package org.jetbrains.plugins.clojure.psi.api;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElement;

/**
 * @author ilyas
 */
public interface ClMetadata extends ClojurePsiElement {

  @Nullable
  ClojurePsiElement getValue(String key);

}
