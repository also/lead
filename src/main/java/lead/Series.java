package lead;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.List;

public class Series {
  private String name;
  private DateTime start;
  private DateTime end;
  private Duration step;
  private List<Number> values;

  public Series(String name, DateTime start, DateTime end, Duration step, List<Number> values) {
    this.name = name;
    this.start = start;
    this.end = end;
    this.step = step;
    this.values = values;
  }

  public String getName() {
    return name;
  }

  public DateTime getStart() {
    return start;
  }

  public DateTime getEnd() {
    return end;
  }

  public Duration getStep() {
    return step;
  }

  public List<Number> getValues() {
    return values;
  }
}
