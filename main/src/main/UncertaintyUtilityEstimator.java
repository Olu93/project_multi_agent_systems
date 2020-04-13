package main;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.rmi.CORBA.Util;

import org.apache.commons.math3.optim.linear.SimplexSolver;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.UtilitySpace;
import genius.core.xml.SimpleElement;
import math.Matrix;
import misc.Utils;
import simplex.Simplex;
import simplex.TwoPhaseSimplex;

/**
 * Test
 */
public class UncertaintyUtilityEstimator extends AdditiveUtilitySpace {

    private final BidRanking rankings;
    private final NegotiationSession session;
    // private final HashMap<IssueDiscrete, Double> weights;
    private final Matrix weightsMatrix;
    private Simplex simplex;
    private final Long numberOfUnknowns; // Count of every individual issue value

    public UncertaintyUtilityEstimator(final NegotiationSession session) {
        this.session = session;
        this.rankings = session.getUserModel().getBidRanking();
        this.numberOfUnknowns = this.session.getIssues().stream()
                .mapToLong(issue -> ((IssueDiscrete) issue).getNumberOfValues()).sum();

        System.out.println("Number of unknowns: " + numberOfUnknowns);
        this.weightsMatrix = this.init();
    }

    private Matrix init() {
        // final List<Map<IssueDiscrete, Integer>> comparisons =
        // getMatrixOfPairWiseComparisons();
        // final Matrix comparisonMatrix =
        // convertPairwiseComparisonsToMatrix(comparisons);
        final Matrix comparisonMatrix = getMatrixOfPairWiseComparisons();
        // System.out.println("Comparison Matrix");
        // Utils.printMatrix(comparisonMatrix);
        final Matrix simplexMatrix = getFinalSimplex(Matrix.constructWithCopy(comparisonMatrix.getArrayCopy()));
        // System.out.println("Simplex Matrix");
        // Utils.printMatrix(simplexMatrix);

        // this.simplex = computeSimplex(simplexMatrix.getArrayCopy(), simplexMatrix.getRowDimension() - 1,
        //         simplexMatrix.getColumnDimension() - 1);
        // final double[][] weights = this.simplex.getTable();
        
        // System.out.println("Computed weights");
        // Utils.printMatrix(weights);
        
        // if (Arrays.deepEquals(weights, simplexMatrix.getArrayCopy())) 
        //     System.out.println("Same"); 
        // else
        //     System.out.println("Not same"); 
        

        ;    
        return computeSimplex2(simplexMatrix);
    }

    private Integer getIndex(final IssueDiscrete issue, final ValueDiscrete value) {
        return issue.getValueIndex(value);
    }

    // private Map<IssueDiscrete, Integer> pairwiseComparison(final Bid firstBid,
    // final Bid secondBid) {
    // final Map<IssueDiscrete, Integer> result =
    // this.session.getIssues().stream().map(issue -> (IssueDiscrete) issue)
    // .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
    // issue.getValueIndex(firstBid.getValue(issue).toString())
    // - issue.getValueIndex(secondBid.getValue(issue).toString())))
    // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
    // return result;
    // }

    public Matrix getWeights() {
        return weightsMatrix;
    }

    private Matrix pairwiseComparison(final Bid firstBid, final Bid secondBid) {
        final Matrix dummyEncodedBids = Utils.getDummyEncoding(this.session.getIssues(),
                Arrays.asList(firstBid, secondBid));
        Integer numCol = dummyEncodedBids.getColumnDimension() - 1;
        Matrix firstRow = dummyEncodedBids.getMatrix(new int[] { 0 }, 0, numCol);
        Matrix secondRow = dummyEncodedBids.getMatrix(new int[] { 1 }, 0, numCol);
        Matrix comparisonRow = firstRow.minus(secondRow);

        // .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
        // issue.getValueIndex(firstBid.getValue(issue).toString())
        // - issue.getValueIndex(secondBid.getValue(issue).toString())))
        return comparisonRow;
    }

    private Matrix getMatrixOfPairWiseComparisons() {
        final List<Bid> bidOrder = this.rankings.getBidOrder();
        final List<Matrix> disjointComparisons = IntStream.range(0, rankings.getSize() - 1)
                // .peek(idx -> Utils.printMatrix(pairwiseComparison2(bidOrder.get(idx),
                // bidOrder.get(idx + 1))))
                .mapToObj(idx -> pairwiseComparison(bidOrder.get(idx), bidOrder.get(idx + 1)))
                .collect(Collectors.toList());
        final double[][] result = disjointComparisons
                .stream()
                .map(row -> row.getRowPackedCopy())
                .toArray(double[][]::new);
        return new Matrix(result);
    }

    // private List<Map<IssueDiscrete, Integer>> getMatrixOfPairWiseComparisons() {
    // final List<Bid> bidOrder = this.rankings.getBidOrder();
    // final List<Map<IssueDiscrete, Integer>> result = IntStream.range(0,
    // rankings.getSize() - 1)
    // // .peek(idx -> Utils.printMatrix(pairwiseComparison2(bidOrder.get(idx),
    // bidOrder.get(idx + 1))))
    // .mapToObj(idx -> pairwiseComparison(bidOrder.get(idx), bidOrder.get(idx +
    // 1)))
    // .collect(Collectors.toList());
    // return result;
    // }

    // private Matrix convertPairwiseComparisonsToMatrix(final List<Map<IssueDiscrete, Integer>> input) {
    //     System.out.println(input.size());
    //     final Integer rowLength = input.size() - 1;
    //     final Integer colLength = input.get(0).size();
    //     final Matrix emptyMatrix = new Matrix(rowLength, colLength);
    //     Integer tmpCol;
    //     Map<IssueDiscrete, Integer> currentComparisonMap;

    //     for (int i = 0; i < rowLength; i++) {
    //         currentComparisonMap = input.get(i);
    //         for (final Entry<IssueDiscrete, Integer> tmp : currentComparisonMap.entrySet()) {
    //             tmpCol = tmp.getKey().getNumber() - 1;
    //             emptyMatrix.set(i, tmpCol, tmp.getValue());
    //         }
    //     }
    //     return emptyMatrix;
    // }

    private Matrix getFinalSimplex(final Matrix comparisons) {
        
        List<Bid> hardConstraints = Arrays.asList(rankings.getMaximalBid(), rankings.getMinimalBid());


        final Integer rowLength = comparisons.getRowDimension() - 1;
        final Integer colLength = rowLength + comparisons.getColumnDimension();
        final Integer splitAt = comparisons.getColumnDimension() - 1;
        final Matrix slackVars = Matrix.identity(rowLength, rowLength).times(-1);
        final Matrix emptyMatrix = new Matrix(rowLength + hardConstraints.size() + 1, colLength + 1);
        int finalColIdx = emptyMatrix.getColumnDimension()-1;

        for (int i = 0; i < rowLength; i++) {
            for (int j = 0; j < colLength; j++) {
                emptyMatrix.set(i, j, j > splitAt ? slackVars.get(i, j - splitAt - 1) : comparisons.get(i, j));
            }
        }
        // System.out.println("Almost done");
        // Utils.printMatrix(emptyMatrix);
        Matrix dummyEncodedHardConstraints = Utils.getDummyEncoding(this.session.getIssues(), hardConstraints);

        // double[] positivesOnly = Arrays.stream(emptyMatrix.getMatrix(new int[]{0}, 0, finalColIdx - 1).copy().getRowPackedCopy())
        //     .map(val -> val > 0 ? val : 0)
        //     .toArray();// remove negatives
        // Matrix maxBidConstraint = new Matrix(positivesOnly, 1);
        // emptyMatrix.timesEquals(-1);
        int[] range = IntStream.range(rowLength, rowLength+hardConstraints.size()).toArray();
        emptyMatrix.setMatrix(range, 0, dummyEncodedHardConstraints.getColumnDimension() - 1, dummyEncodedHardConstraints);
        emptyMatrix.set(rowLength, finalColIdx, rankings.getHighUtility());
        if (range.length>1) {
            emptyMatrix.set(rowLength+1, finalColIdx, rankings.getLowUtility());
        }
        // emptyMatrix.set(rowLength, finalColIdx, 1);
        
        // System.out.println("Almost Almost done");
        // Utils.printMatrix(emptyMatrix);
        
        // emptyMatrix.set(0, splitAt, 0);
        // emptyMatrix.set(emptyMatrix.getRowDimension()-1,
        // emptyMatrix.getColumnDimension()-1, rankings.getLowUtility()));
        for (int i = 0; i < colLength; i++) {
            emptyMatrix.set(rowLength+hardConstraints.size(), i, i > splitAt ? 1 : 0);
        }
        System.out.println("Done");
        Utils.printMatrix(emptyMatrix);
        return emptyMatrix;
    }

    private Simplex computeSimplex(final double[][] simplexMatrix, final Integer rowNum, final Integer colNum) {
        final Simplex simplex = new Simplex(rowNum, colNum);
        boolean quit = false;

        simplex.fillTable(simplexMatrix);

        // print it out
        System.out.println("---Starting set---");
        simplex.print();

        // if table is not optimal re-iterate
        while (!quit) {
            final Simplex.ERROR err = simplex.compute();
            System.out.println("---Compute---: " + err);

            if (err == Simplex.ERROR.IS_OPTIMAL) {
                System.out.println("---Continue---");
                simplex.print();
                quit = true;
            } else if (err == Simplex.ERROR.UNBOUNDED) {
                System.out.println("---Solution is unbounded---");
                quit = true;
            }
        }
        return simplex;
    }

    private Matrix computeSimplex2(final Matrix convenience) {
        Integer lastRowIdx = convenience.getRowDimension()-1;
        Integer lastColIdx = convenience.getColumnDimension()-1;
        Integer numberOfUnknowns = this.numberOfUnknowns.intValue();
        double[][] A = convenience.getMatrix(0, lastRowIdx-1, 0, numberOfUnknowns-1).getArrayCopy();
        System.out.println("Input for two-phase:");
        Utils.printMatrix(A);
        double[] b = convenience.getMatrix(0, lastRowIdx-1, lastColIdx, lastColIdx).getRowPackedCopy(); 
        double[] c = convenience.getMatrix(lastRowIdx, lastRowIdx, 0, numberOfUnknowns-1).getRowPackedCopy(); 
        
        TwoPhaseSimplex lp;
        try {
            lp = new TwoPhaseSimplex(A, b, c);
        }
        catch (ArithmeticException e) { 
            System.out.println(e);
            return null;
        }
        System.out.println("value = " + lp.value());
        double[] x = lp.primal();
        return new Matrix(x, 1);
    }

    @Override
    public double getUtility(final Bid bid) {

        return 0.0;
    }

}