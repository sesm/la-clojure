package org.jetbrains.plugins.clojure.config;

import clojure.lang.AFn;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.plugins.clojure.ClojureBundle;

import static org.jetbrains.plugins.clojure.utils.ClojureUtils.CLOJURE_NOTIFICATION_GROUP;

/**
 * @author ilyas
 */
public class ClojureConfigUtil {

  public static final String CLOJURE_MAIN_CLASS_FILE = "clojure/main.class";

  public static String CLOJURE_SDK = PathUtil.getJarPathForClass(AFn.class);
  public static String NREPL_LIB = PathUtil.getJarPathForClass(clojure.tools.nrepl.main.class);

  public static boolean isClojureConfigured(final Module module) {
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    for (OrderEntry entry : manager.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry) entry).getLibrary();
        if (library != null) {
          for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
            String path = file.getPath();
            if (path.endsWith(".jar!/")) {
              if (file.findFileByRelativePath(CLOJURE_MAIN_CLASS_FILE) != null) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static void warningDefaultClojureJar(Module module) {
    Notifications.Bus.notify(new Notification(CLOJURE_NOTIFICATION_GROUP,
        "",
        ClojureBundle.message("clojure.jar.from.plugin.used"),
        NotificationType.WARNING), module.getProject());
  }

  public static class RunConfigurationParameters extends JavaParameters {
    private boolean defaultClojureJarUsed = false;

    public boolean isDefaultClojureJarUsed() {
      return defaultClojureJarUsed;
    }

    public void setDefaultClojureJarUsed(boolean defaultClojureJarUsed) {
      this.defaultClojureJarUsed = defaultClojureJarUsed;
    }
  }
}
