package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How often an athlete coming on random days misses a pattern axis (lot D', D181). Pure and exact:
 * each week offers weighted choices of days (every set of k days, or of 3, 4 or 5 days), the weeks
 * are independent, and the probability is counted over every combination of the window's weeks,
 * without drawing at random.
 *
 * <p>A share under a limit is linear once written as {@code axis - limit x total < 0}, so each
 * choice becomes one number and the window is a sum of independent numbers; the weeks are split in
 * two halves whose sums are sorted and matched (meet in the middle), a few thousand values instead
 * of every combination.
 */
public final class RandomAttendance {

  /**
   * One way to attend a week.
   *
   * @param weight Its probability within the week.
   * @param axes Sets per axis ({@link Axis} ordinal) of the days chosen.
   */
  public record Choice(double weight, double[] axes) {}

  private RandomAttendance() {}

  /**
   * The probability that an axis's share over the window falls under a limit.
   *
   * @param weeks The choices of each week of the window.
   * @param axis The axis.
   * @param limit The limit, in percent of the sets of every axis.
   * @return The probability, 0 to 1.
   */
  public static double missProbability(List<List<Choice>> weeks, Axis axis, double limit) {
    double fraction = limit / 100;
    List<List<double[]>> values = new ArrayList<>();
    for (List<Choice> week : weeks) {
      List<double[]> own = new ArrayList<>();
      for (Choice choice : week) {
        double total = Arrays.stream(choice.axes()).sum();
        own.add(new double[] {choice.axes()[axis.ordinal()] - fraction * total, choice.weight()});
      }
      values.add(own);
    }
    int half = values.size() / 2;
    double[][] left = combine(values.subList(0, half));
    double[][] right = combine(values.subList(half, values.size()));
    // right sorted by value, with the cumulated weight of the values below each index
    Integer[] order = new Integer[right[0].length];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Arrays.sort(order, (a, b) -> Double.compare(right[0][a], right[0][b]));
    double[] sorted = new double[order.length];
    double[] cumulated = new double[order.length + 1];
    for (int i = 0; i < order.length; i++) {
      sorted[i] = right[0][order[i]];
      cumulated[i + 1] = cumulated[i] + right[1][order[i]];
    }
    double miss = 0;
    for (int i = 0; i < left[0].length; i++) {
      // right values strictly under -left[i] (a tolerance keeps an exact tie out)
      int below = countAtMost(sorted, -left[0][i] - 1e-9);
      miss += left[1][i] * cumulated[below];
    }
    return miss;
  }

  /** Every sum of one value per week, with the product of their weights: {values, weights}. */
  private static double[][] combine(List<List<double[]>> weeks) {
    double[] sums = {0};
    double[] weights = {1};
    for (List<double[]> week : weeks) {
      double[] nextSums = new double[sums.length * week.size()];
      double[] nextWeights = new double[nextSums.length];
      int k = 0;
      for (int i = 0; i < sums.length; i++) {
        for (double[] value : week) {
          nextSums[k] = sums[i] + value[0];
          nextWeights[k] = weights[i] * value[1];
          k++;
        }
      }
      sums = nextSums;
      weights = nextWeights;
    }
    return new double[][] {sums, weights};
  }

  /** How many sorted values are at most the key. */
  private static int countAtMost(double[] sorted, double key) {
    int low = 0;
    int high = sorted.length;
    while (low < high) {
      int mid = (low + high) >>> 1;
      if (sorted[mid] <= key) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }
}
