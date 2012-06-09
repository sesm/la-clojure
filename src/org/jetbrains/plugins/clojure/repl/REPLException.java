package org.jetbrains.plugins.clojure.repl;

/**
 * @author Colin Fleming
 */
public class REPLException extends Exception
{
  public REPLException(String message)
  {
    super(message);
  }

  public REPLException(Throwable cause)
  {
    super(cause);
  }
}
