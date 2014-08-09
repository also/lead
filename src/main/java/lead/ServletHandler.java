package lead;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ServletHandler {
  private static final IFn createHandler;
  private static final IFn handle;
  static {
    IFn require = Clojure.var("clojure.core", "require");
    require.invoke(Clojure.read("lead.servlet-handler"));
    createHandler = Clojure.var("lead.servlet-handler", "create-handler");
    handle = Clojure.var("lead.servlet-handler", "handle");
  }

  private final Object handler;

  public ServletHandler(String configFile) {
    handler = createHandler.invoke(configFile);
  }

  public void handle(HttpServletRequest req, HttpServletResponse res) {
    handle.invoke(handler, req, res);
  }
}
