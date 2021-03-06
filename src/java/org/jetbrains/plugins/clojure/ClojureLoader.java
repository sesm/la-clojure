package org.jetbrains.plugins.clojure;

import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.containers.HashSet;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: peter
 * Date: Jan 16, 2009
 * Time: 4:34:18 PM
 * Copyright 2007, 2008, 2009 Red Shark Technology
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ClojureLoader implements ApplicationComponent {

  private final Logger LOG = Logger.getLogger(ClojureLoader.class);

  @NotNull
  public static final String CLOJURE_EXTENSION = "clj";

  @NotNull
  public static final Set<String> CLOJURE_EXTENSIONS = new HashSet<String>();

  private static final String INIT_CLOJURE = "org.jetbrains.plugins.clojure.init-clojure";

  static {
    adjustClojureCompilerLoader();

    CLOJURE_EXTENSIONS.add(CLOJURE_EXTENSION);
  }

  private static void adjustClojureCompilerLoader() {
    // Hack in order to adjust Clojure ClassLoaders with PluginClassLoader
    final Application application = ApplicationManager.getApplication();
    final ClassLoader loader = ClojureLoader.class.getClassLoader();

    final Runnable runnable = new Runnable() {
      public void run() {
        final Thread thread = new Thread() {
          @Override
          public void run() {
            application.invokeLater(new Runnable() {
              public void run() {
                Var.pushThreadBindings(RT.map(clojure.lang.Compiler.LOADER, loader));
              }
            });
          }
        };
        thread.setContextClassLoader(loader);
        thread.start();
      }
    };

    application.invokeLater(runnable);

  }


  public ClojureLoader() {
  }

  public void initComponent() {
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();

    try {
      ClassLoader loader = ClojureLoader.class.getClassLoader();
      Thread.currentThread().setContextClassLoader(loader);

      StringWriter writer = new StringWriter();

      Class.forName("clojure.lang.RT");

      Var.pushThreadBindings(RT.map(clojure.lang.Compiler.LOADER, loader,
          RT.var("clojure.core", "*warn-on-reflection*"), true,
          RT.ERR, writer));

      RT.var("clojure.core", "require").invoke(Symbol.intern(INIT_CLOJURE));
      Var.find(Symbol.intern(INIT_CLOJURE + "/init")).invoke();

      String result = writer.toString();
      LOG.error("Reflection warnings:\n" + result);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    } finally {
      Var.popThreadBindings();
      Thread.currentThread().setContextClassLoader(oldLoader);
    }
  }


  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "clojure.support.loader";
  }

}
