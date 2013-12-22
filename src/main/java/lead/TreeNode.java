package lead;

public class TreeNode {
  private String name;
  private boolean isLeaf;

  public TreeNode(String name, boolean isLeaf) {
    this.name = name;
    this.isLeaf = isLeaf;
  }

  public String getName() {
    return name;
  }

  public boolean isLeaf() {
    return isLeaf;
  }
}
