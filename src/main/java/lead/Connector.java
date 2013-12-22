package lead;

import java.util.List;

public interface Connector {
  List<TreeNode> find(String pattern);

  List<Series> load(List<String> targets, LoadOptions options);
}
