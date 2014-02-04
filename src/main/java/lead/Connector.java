package lead;

import java.util.List;

public interface Connector {
  List<TreeNode> find(String pattern) throws Exception;

  List<Series> load(String target, LoadOptions options) throws Exception;
}
