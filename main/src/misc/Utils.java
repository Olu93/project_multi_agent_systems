package misc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import genius.core.Bid;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import math.Matrix;

/**
 * Utils
 */
public class Utils {

    public static void printMatrix(Matrix m) {
        for (double[] row : Arrays.asList(m.getArrayCopy())) {
            writeRow(row);
        }
    }

    public static void printMatrix(double[][] m) {
        for (double[] row : m) {
            writeRow(row);
        }
    }

    private static void writeRow(double[] row) {
        for (double ds : row) {
            double ss = (Math.round(ds*100));
            System.out.print((ds < 0 ? ss/100 : " " + ss/100) + "\t");
        }
        System.out.print("\n");
    }

    public static Matrix getDummyEncoding(List<Issue> domainIssues, List<Bid> lBids) {
        final Map<Issue, Matrix> oneHotEncodedMatrixByIssue = domainIssues.stream().map(issue -> (IssueDiscrete) issue)
                .map(issue -> new SimpleEntry<IssueDiscrete, List<Integer>>(issue,
                        extractAllValuesForIssue(lBids, issue)))
                .map(entry -> new SimpleEntry<IssueDiscrete, Matrix>(entry.getKey(),
                        dummyEncode(entry.getKey(), entry.getValue())))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        final List<List<Double>> tmp = IntStream.range(0, lBids.size())
                .mapToObj(i -> extractFullRow(domainIssues, i, oneHotEncodedMatrixByIssue))
                .collect(Collectors.toList());

        final double[][] preFullMatrix = tmp.stream()
                .map(arr -> arr.stream().mapToDouble(Double::doubleValue).toArray()).collect(Collectors.toList())
                .stream().toArray(double[][]::new);

        final Matrix fullMatrix = new Matrix(preFullMatrix);
        return fullMatrix;
    }

    private static Matrix dummyEncode(final IssueDiscrete issue, final List<Integer> issueValues) {
        final Matrix containerMatrix = new Matrix(issueValues.size(), issue.getNumberOfValues());
        // System.out.println("============> " + issue.getName());
        for (int row = 0; row < issueValues.size(); row++) {
            // System.out.println(row + ": "+ issueValues.get(row) + " - "+
            // issue.getStringValue(issueValues.get(row)));
            containerMatrix.set(row, issueValues.get(row), 1);
        }

        return containerMatrix;
    }

    private static List<Integer> extractAllValuesForIssue(List<Bid> lBids, IssueDiscrete issue) {
        return lBids.stream().map(bid -> (ValueDiscrete) bid.getValue(issue))
                .mapToInt(value -> issue.getValueIndex(value)).boxed().collect(Collectors.toList());
    }

    private static List<Double> extractFullRow(List<Issue> domainIssues, final Integer row,
            final Map<Issue, Matrix> oneHotMatrix) {
        return domainIssues.stream().map(issue -> oneHotMatrix.get(issue).getArray()[row])
                .flatMapToDouble(Arrays::stream).boxed().collect(Collectors.toList());
    }

    public static Matrix getRow(Matrix m, Integer startRow, Integer endRow) {
        Integer numCol = m.getColumnDimension();
        return m.getMatrix(startRow, endRow, 0, numCol);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
    
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

}