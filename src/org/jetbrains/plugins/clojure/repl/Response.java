package org.jetbrains.plugins.clojure.repl;

import clojure.lang.*;
import clojure.tools.nrepl.Connection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Colin Fleming
 */
public class Response
{
  @NonNls
  public static final String NAMESPACE = "ns";
  public static final Keyword NAMESPACE_KEYWORD = Keyword.intern("ns");
  @NonNls
  public static final String OUTPUT = "out";
  public static final Keyword OUTPUT_KEYWORD = Keyword.intern("out");
  @NonNls
  public static final String ERROR = "err";
  public static final Keyword ERROR_KEYWORD = Keyword.intern("err");
  @NonNls
  public static final String VALUE = "value";
  public static final Keyword VALUE_KEYWORD = Keyword.intern("value");

  private static final Var READ_STRING = RT.var(ClojureUtils.CORE_NAMESPACE, "read-string");

  private final Connection.Response response;

  public Response(Connection.Response response)
  {
    this.response = response;
  }

  protected Map<String, Object> combinedResponse()
  {
    return response.combinedResponse();
  }

  public List<Object> values()
  {
    List<Object> ret = new ArrayList<Object>();
    Seqable values = (Seqable) tryBoth(VALUE, VALUE_KEYWORD);
    if (values != null) {
      for (ISeq seq = values.seq(); seq != null; seq = seq.next()) {
        String next = (String) seq.first();
        try {
          ret.add(READ_STRING.invoke(next));
        } catch (Exception ignore) {
          ret.add(new UnreadableForm(next));
        }
      }
    }
    return ret;
  }

  public String namespace()
  {
    return (String) tryBoth(NAMESPACE, NAMESPACE_KEYWORD);
  }

  public String errorOutput()
  {
    return (String) tryBoth(ERROR, ERROR_KEYWORD);
  }

  public String stdOutput()
  {
    return (String) tryBoth(OUTPUT, OUTPUT_KEYWORD);
  }

  private Object tryBoth(String stringKey, Keyword keywordKey) {
    Object ret = combinedResponse().get(stringKey);
    if (ret == null) {
      ret = combinedResponse().get(keywordKey);
    }
    return ret;
  }

  public static class UnreadableForm {
      public final String form;

      public UnreadableForm(String form) {
          this.form = form;
      }
  }
}
