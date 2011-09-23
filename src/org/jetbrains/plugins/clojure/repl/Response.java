package org.jetbrains.plugins.clojure.repl;

import clojure.lang.Keyword;
import clojure.tools.nrepl.Connection;
import clojure.tools.nrepl.SafeFn;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Colin Fleming
 */
public class Response
{
  @NonNls
  public static final Keyword NAMESPACE_KEYWORD = Keyword.intern("ns");
  @NonNls
  public static final Keyword OUTPUT_KEYWORD = Keyword.intern("out");
  @NonNls
  public static final Keyword ERROR_KEYWORD = Keyword.intern("err");
  @NonNls
  public static final Keyword VALUE_KEYWORD = Keyword.intern("value");

  private static final SafeFn MAP = SafeFn.find(ClojureUtils.CORE_NAMESPACE, "map");
  private static final SafeFn READ_STRING = SafeFn.find(ClojureUtils.CORE_NAMESPACE, "read-string");

  private final Connection.Response response;

  public Response(Connection.Response response)
  {
    this.response = response;
  }

  protected Map<Keyword, Object> combinedResponse()
  {
    return response.combinedResponse();
  }

  public final List<Object> values()
  {
    return (List<Object>) MAP.sInvoke(READ_STRING, combinedResponse().get(VALUE_KEYWORD));
  }

  public final String namespace()
  {
    return (String) combinedResponse().get(NAMESPACE_KEYWORD);
  }

  public final String errorOutput()
  {
    return (String) combinedResponse().get(ERROR_KEYWORD);
  }

  public final String stdOutput()
  {
    return (String) combinedResponse().get(OUTPUT_KEYWORD);
  }
}
