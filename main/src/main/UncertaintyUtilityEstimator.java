package main;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.rmi.CORBA.Util;

import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.uncertainty.UserModel;
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
    // private final HashMap<IssueDiscrete, Double> weights;
    private final Matrix weightsMatrix;
    private Simplex simplex;
    private final Long numberOfUnknowns; // Count of every individual issue value
    private static final Double EPSILON = 1.0E-8;
    private Matrix oneHotBids;
    private UserModel userModel;
    private List<Issue> issues;
    private final Boolean isVerbose = false;

    public UncertaintyUtilityEstimator(final UserModel userModel) {
        this.userModel = userModel;
        this.rankings = userModel.getBidRanking();
        this.issues = this.userModel.getDomain().getIssues();
        this.numberOfUnknowns = this.issues.stream().mapToLong(issue -> ((IssueDiscrete) issue).getNumberOfValues())
                .sum();
        this.oneHotBids = Utils.getDummyEncoding(this.issues, rankings.getBidOrder());
        System.out.println("Number of unknowns: " + numberOfUnknowns);
        
        this.weightsMatrix = this.init();
        this.evaluatePerformance();
    }

    private void evaluatePerformance() {
        if (this.userModel instanceof ExperimentalUserModel) {
            ExperimentalUserModel userModelExperimental = (ExperimentalUserModel) this.userModel;
            Matrix oneHotEncodedRankings = Utils.getDummyEncoding(this.issues, this.rankings.getBidOrder());

            if (this.isVerbose) {
                System.out.println("Encoded ranking");
                Utils.printMatrix(oneHotEncodedRankings);
            }
            System.out.println("Weights");
            Utils.printMatrix(this.getWeights());
            Matrix prediction = oneHotEncodedRankings.times(this.getWeights().transpose());
            int i = 0;
            List<Double> statsBuiltin = new ArrayList<Double>();
            List<Double> statsPredict = new ArrayList<Double>();
            for (Bid rankedBid : this.rankings.getBidOrder()) {
                double realUtility = userModelExperimental.getRealUtility(rankedBid);
                double builtInDiff = Math.abs(this.getUtility(rankedBid) - realUtility);
                double predDiff = Math.abs(prediction.get(i, 0) - realUtility);
                statsBuiltin.add(builtInDiff);
                statsPredict.add(predDiff);
                if (this.isVerbose) {
                    System.out.println("===> Bid: " + rankedBid);
                    System.out.println("True  Util: " + realUtility);
                    System.out.println("Built-in 	distance to real utility: " + builtInDiff);
                    System.out.println("Prediction 	distance to real utility: " + predDiff);
                }
                i++;
            }
            System.out.println("Avg Distance of built in utility estimator: "
                    + statsBuiltin.stream().mapToDouble(a -> a).average().getAsDouble());
            System.out.println("Avg Distance of prediction utility estimator: "
                    + statsPredict.stream().mapToDouble(a -> a).average().getAsDouble());
        }

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

        return computeSimplex8(simplexMatrix);
    }

    private Integer getIndex(final IssueDiscrete issue, final ValueDiscrete value) {
        return issue.getValueIndex(value);
    }

    private Map<IssueDiscrete, Integer> pairwiseComparison2(final Bid firstBid, final Bid secondBid) {
        final Map<IssueDiscrete, Integer> result = this.issues.stream().map(issue -> (IssueDiscrete) issue)
                .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
                        issue.getValueIndex(firstBid.getValue(issue).toString())
                                - issue.getValueIndex(secondBid.getValue(issue).toString())))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
        return result;
    }

    public Matrix getWeights() {
        return weightsMatrix;
    }

    private Matrix pairwiseComparison(final Bid firstBid, final Bid secondBid) {
        final Matrix dummyEncodedBids = Utils.getDummyEncoding(this.issues, Arrays.asList(firstBid, secondBid));
        Integer numCol = dummyEncodedBids.getColumnDimension() - 1;
        Matrix firstRow = dummyEncodedBids.getMatrix(new int[] { 0 }, 0, numCol);
        Matrix secondRow = dummyEncodedBids.getMatrix(new int[] { 1 }, 0, numCol);
        Matrix comparisonRow = secondRow.minus(firstRow);

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
        final double[][] result = disjointComparisons.stream().map(row -> row.getRowPackedCopy())
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

    // private Matrix convertPairwiseComparisonsToMatrix(final
    // List<Map<IssueDiscrete, Integer>> input) {
    // System.out.println(input.size());
    // final Integer rowLength = input.size() - 1;
    // final Integer colLength = input.get(0).size();
    // final Matrix emptyMatrix = new Matrix(rowLength, colLength);
    // Integer tmpCol;
    // Map<IssueDiscrete, Integer> currentComparisonMap;

    // for (int i = 0; i < rowLength; i++) {
    // currentComparisonMap = input.get(i);
    // for (final Entry<IssueDiscrete, Integer> tmp :
    // currentComparisonMap.entrySet()) {
    // tmpCol = tmp.getKey().getNumber() - 1;
    // emptyMatrix.set(i, tmpCol, tmp.getValue());
    // }
    // }
    // return emptyMatrix;
    // }

    private Matrix getFinalSimplex(final Matrix comparisons) {

        List<Bid> hardConstraints = Arrays.asList(rankings.getMaximalBid(), rankings.getMinimalBid());

        final Integer endOfPairwiseComparisonIdx = comparisons.getRowDimension() - 1;
        final Integer lastColComparisonIdx = endOfPairwiseComparisonIdx + comparisons.getColumnDimension();
        final Integer splitAt = comparisons.getColumnDimension() - 1;

        final Integer lastRowRankingsIdx = this.oneHotBids.getRowDimension() - 1;
        final Integer lastColRankingsIdx = this.oneHotBids.getColumnDimension() - 1;

        int startOfMinUtilitiesIdx = endOfPairwiseComparisonIdx;
        int endOfMinUtilitiesIdx = endOfPairwiseComparisonIdx + lastRowRankingsIdx - 1;
        int startOfMaxUtilitiesIdx = endOfMinUtilitiesIdx + 1;
        int endOfMaxUtilitiesIdx = endOfPairwiseComparisonIdx + lastRowRankingsIdx * 2 - 1;
        int thirdToLastIdx = endOfMaxUtilitiesIdx + 1;
        int secondToLastIdx = thirdToLastIdx + 1;

        final Matrix slackVars = Matrix.identity(endOfPairwiseComparisonIdx, endOfPairwiseComparisonIdx);
        final Matrix emptyMatrix = new Matrix(
                endOfPairwiseComparisonIdx + lastRowRankingsIdx * 2 + hardConstraints.size() + 1,
                lastColComparisonIdx + 1);
        int finalColIdx = emptyMatrix.getColumnDimension() - 1;

        for (int i = 0; i < startOfMinUtilitiesIdx; i++) {
            for (int j = 0; j < lastColComparisonIdx; j++) {
                emptyMatrix.set(i, j, j > splitAt ? slackVars.get(i, j - splitAt - 1) : comparisons.get(i, j));
            }
        }
        emptyMatrix.setMatrix(startOfMinUtilitiesIdx, endOfMinUtilitiesIdx, 0, lastColRankingsIdx, oneHotBids);
        for (int i = startOfMinUtilitiesIdx; i < startOfMaxUtilitiesIdx; i++) {
            emptyMatrix.set(i, lastColComparisonIdx, rankings.getLowUtility());
        }

        emptyMatrix.setMatrix(startOfMaxUtilitiesIdx, endOfMaxUtilitiesIdx, 0, lastColRankingsIdx, oneHotBids);
        for (int i = startOfMaxUtilitiesIdx; i < thirdToLastIdx; i++) {
            emptyMatrix.set(i, lastColComparisonIdx, rankings.getHighUtility());
        }
        // if (isDebug) {
        // System.out.println("Almost done");
        // Utils.printMatrix(emptyMatrix);
        // }
        Matrix dummyEncodedHardConstraints = Utils.getDummyEncoding(this.issues, hardConstraints);

        int[] range = IntStream.range(thirdToLastIdx, thirdToLastIdx + hardConstraints.size()).toArray();
        emptyMatrix.setMatrix(range, 0, dummyEncodedHardConstraints.getColumnDimension() - 1,
                dummyEncodedHardConstraints);
        emptyMatrix.set(thirdToLastIdx, finalColIdx, rankings.getHighUtility());
        if (range.length > 1) {
            emptyMatrix.set(secondToLastIdx, finalColIdx, rankings.getLowUtility());
        }

        for (int i = 0; i < lastColComparisonIdx; i++) {
            emptyMatrix.set(thirdToLastIdx + hardConstraints.size(), i, i > splitAt ? 1 : 0);
        }

        if (this.isVerbose == true) {
            System.out.println("Done");
            Utils.printMatrix(emptyMatrix);
        }

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
        Integer lastRowIdx = convenience.getRowDimension() - 1;
        Integer lastColIdx = convenience.getColumnDimension() - 1;
        Integer numberOfUnknowns = this.numberOfUnknowns.intValue();
        double[][] A = convenience.getMatrix(0, lastRowIdx - 1, 0, numberOfUnknowns - 1).getArrayCopy();
        System.out.println("Input for two-phase:");
        Utils.printMatrix(A);
        double[] b = convenience.getMatrix(0, lastRowIdx - 1, lastColIdx, lastColIdx).getRowPackedCopy();
        double[] c = convenience.getMatrix(lastRowIdx, lastRowIdx, 0, numberOfUnknowns - 1).getRowPackedCopy();

        TwoPhaseSimplex lp;
        try {
            lp = new TwoPhaseSimplex(A, b, c);
        } catch (ArithmeticException e) {
            System.out.println(e);
            return null;
        }
        System.out.println("value = " + lp.value());
        double[] x = lp.primal();
        return new Matrix(x, 1);
    }

    private Matrix computeSimplex8(Matrix convenience) {
        Integer lastRowIdx = convenience.getRowDimension() - 1;
        Integer lastColIdx = convenience.getColumnDimension() - 1;
        Integer lastRowRankingsIdx = this.oneHotBids.getRowDimension() - 1;
        Integer lastColRankingsIdx = this.oneHotBids.getRowDimension() - 1;
        Integer numberOfUnknowns = this.numberOfUnknowns.intValue();
        double[][] inputComparisonPart = convenience
                .getMatrix(0, lastRowIdx - lastRowRankingsIdx * 2 - 3, 0, lastColIdx - 1).getArrayCopy();
        double[][] inputMinumumUtilityPart = convenience.getMatrix(lastRowIdx - lastRowRankingsIdx * 2 - 2,
                lastRowIdx - lastRowRankingsIdx - 3, 0, lastColIdx - 1).getArrayCopy();
        double[][] inputMaximumUtilityPart = convenience
                .getMatrix(lastRowIdx - lastRowRankingsIdx - 2, lastRowIdx - 3, 0, lastColIdx - 1).getArrayCopy();
        double[][] inputEqualityPart = convenience.getMatrix(lastRowIdx - 2, lastRowIdx - 1, 0, lastColIdx - 1)
                .getArrayCopy();
                
                
        double[] b = convenience.getMatrix(0, lastRowIdx - 1, lastColIdx, lastColIdx).getRowPackedCopy();
        b = Arrays.stream(b).boxed().map(val -> Utils.round(val, 3)).mapToDouble(val -> val).toArray();
        double[] c = convenience.getMatrix(lastRowIdx, lastRowIdx, 0, lastColIdx - 1).getRowPackedCopy();
        if (this.isVerbose) {
            System.out.println("========");
            Utils.printMatrix(inputComparisonPart);
            System.out.println("========");
            Utils.printMatrix(inputMinumumUtilityPart);
            System.out.println("========");
            Utils.printMatrix(inputMaximumUtilityPart);
            System.out.println("========");
            Utils.printMatrix(inputEqualityPart);
            System.out.println("Input b for two-phase:");
            System.out.println(Arrays.toString(b));
            System.out.println("Input c for two-phase:");
            System.out.println(Arrays.toString(c));
        }

        LinearObjectiveFunction oFunc = new LinearObjectiveFunction(c, 0);
        Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        int i = 0;
        System.out.println("Constraints");
        for (double[] row : inputComparisonPart) {
            constraints.add(new LinearConstraint(row, Relationship.GEQ, b[i]));
            System.out.println(Arrays.toString(row) + " >= " + b[i]);
            i++;
        }
        for (double[] row : inputMinumumUtilityPart) {
            constraints.add(new LinearConstraint(row, Relationship.GEQ, b[i]));
            System.out.println(Arrays.toString(row) + " >= " + b[i]);
            i++;
        }
        for (double[] row : inputMaximumUtilityPart) {
            constraints.add(new LinearConstraint(row, Relationship.LEQ, b[i]));
            System.out.println(Arrays.toString(row) + " <= " + b[i]);
            i++;
        }
        for (double[] row : inputEqualityPart) {
            constraints.add(new LinearConstraint(row, Relationship.EQ, b[i]));
            System.out.println(Arrays.toString(row) + "  = " + b[i]);
            i++;
        }

        SimplexSolver solver = new SimplexSolver();
        PointValuePair solution = solver.optimize(new MaxIter(100), oFunc, new LinearConstraintSet(constraints),
                GoalType.MINIMIZE, new NonNegativeConstraint(true));
        System.out.println("Pred Weights:");
        System.out.println(Arrays.toString(solution.getPoint()) + " : " + solution.getSecond());

        return new Matrix(Arrays.copyOfRange(solution.getPoint(), 0, numberOfUnknowns), 1);
    }

    @Override
    public double getUtility(Bid bid) {
        Matrix oneHotEncodedRankings = Utils.getDummyEncoding(this.issues, Arrays.asList(bid));
        Matrix prediction = oneHotEncodedRankings.times(this.getWeights().transpose());
        return prediction.get(0, 0);
    }

}