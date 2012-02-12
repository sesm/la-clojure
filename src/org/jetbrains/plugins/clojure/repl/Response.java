package org.jetbrains.plugins.clojure.repl;

import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.Seqable;
import clojure.tools.nrepl.Connection;
import clojure.tools.nrepl.SafeFn;
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
  public static final Keyword NAMESPACE_KEYWORD = Keyword.intern("ns");
  @NonNls
  public static final Keyword OUTPUT_KEYWORD = Keyword.intern("out");
  @NonNls
  public static final Keyword ERROR_KEYWORD = Keyword.intern("err");
  @NonNls
  public static final Keyword VALUE_KEYWORD = Keyword.intern("value");

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

  public List<Object> values()
  {
    List<Object> ret = new ArrayList<Object>();
    Seqable values = (Seqable) combinedResponse().get(VALUE_KEYWORD);
    if (values != null) {
      for (ISeq seq = values.seq(); seq != null; seq = seq.next()) {
        String next = (String) seq.first();
        try {
          ret.add(READ_STRING.sInvoke(next));
        } catch (Exception ignore) {
          ret.add(new UnreadableForm(next));
        }
      }
    }
    return ret;
  }

  public String namespace()
  {
    return (String) combinedResponse().get(NAMESPACE_KEYWORD);
  }

  public String errorOutput()
  {
    return (String) combinedResponse().get(ERROR_KEYWORD);
  }

  public String stdOutput()
  {
    return (String) combinedResponse().get(OUTPUT_KEYWORD);
  }

  public static class UnreadableForm {
      public final String form;

      public UnreadableForm(String form) {
          this.form = form;
      }
  }
}
