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
    private final Matrix oneHotBids;
    private final UserModel userModel;
    private final List<Issue> issues;
    private final Boolean isVerbose = true;
    private final Boolean hasStrongConstraints = false;

    public UncertaintyUtilityEstimator(final UserModel userModel) {
        this.userModel = userModel;
        this.rankings = userModel.getBidRanking();
        this.issues = this.userModel.getDomain().getIssues();
        this.numberOfUnknowns = this.issues.stream().mapToLong(issue -> ((IssueDiscrete) issue).getNumberOfValues())
                .sum();
        this.oneHotBids = Utils.getDummyEncoding(this.issues, rankings.getBidOrder());
        System.out.println("Number of unknowns: " + numberOfUnknowns);

        this.weightsMatrix = this.init();
        this.evaluatePerformance(this.rankings.getBidOrder());
    }

    private Matrix init() {
        final Matrix comparisonMatrix = getMatrixOfPairWiseComparisons(this.rankings.getBidOrder());
        final Matrix simplexMatrix = getSimplexMatrix(comparisonMatrix, this.oneHotBids);
        return computeSimplexMatrix(simplexMatrix, this.oneHotBids);
    }

    private Matrix getSimplexMatrix(final Matrix comparisons, final Matrix oneHotEncodedBids) {

        final List<Bid> hardConstraints = Arrays.asList(rankings.getMaximalBid(), rankings.getMinimalBid());

        final Integer endOfPairwiseComparisonIdx = comparisons.getRowDimension() - 1;
        final Integer lastRowRankingsIdx = oneHotEncodedBids.getRowDimension() - 1;
        final Integer lastColRankingsIdx = oneHotEncodedBids.getColumnDimension() - 1;
        final Integer splitAt = comparisons.getColumnDimension() - 1;

        final int startOfMinUtilitiesIdx = endOfPairwiseComparisonIdx + 1;
        final int endOfMinUtilitiesIdx = startOfMinUtilitiesIdx + lastRowRankingsIdx;
        final int startOfMaxUtilitiesIdx = endOfMinUtilitiesIdx + 1;
        final int endOfMaxUtilitiesIdx = startOfMaxUtilitiesIdx + lastRowRankingsIdx;
        final int startOfEqualities = endOfMaxUtilitiesIdx + 1;
        final int thirdToLastIdx = startOfEqualities;
        final int secondToLastIdx = startOfEqualities + 1;

        final Integer lastColComparisonIdx = comparisons.getRowDimension() + comparisons.getColumnDimension();
        final Matrix slackVars = Matrix.identity(startOfEqualities, startOfEqualities);
        final Matrix emptyMatrix = new Matrix(startOfEqualities + hardConstraints.size() + 1, lastColComparisonIdx + 1);
        final int finalColIdx = emptyMatrix.getColumnDimension() - 1;

        emptyMatrix.setMatrix(0, endOfPairwiseComparisonIdx, 0, splitAt, comparisons);
        emptyMatrix.setMatrix(0, endOfPairwiseComparisonIdx, splitAt + 1, finalColIdx - 1, slackVars);

        emptyMatrix.setMatrix(startOfMinUtilitiesIdx, endOfMinUtilitiesIdx, 0, lastColRankingsIdx, oneHotBids);
        for (int i = startOfMinUtilitiesIdx; i < startOfMaxUtilitiesIdx; i++) {
            emptyMatrix.set(i, lastColComparisonIdx, rankings.getLowUtility());
        }

        emptyMatrix.setMatrix(startOfMaxUtilitiesIdx, endOfMaxUtilitiesIdx, 0, lastColRankingsIdx, oneHotBids);
        for (int i = startOfMaxUtilitiesIdx; i < thirdToLastIdx; i++) {
            emptyMatrix.set(i, lastColComparisonIdx, rankings.getHighUtility());
        }
        // System.out.println("Almost done");
        // Utils.printMatrix(emptyMatrix);

        final Matrix dummyEncodedHardConstraints = Utils.getDummyEncoding(this.issues, hardConstraints);

        final int[] range = IntStream.range(thirdToLastIdx, thirdToLastIdx + hardConstraints.size()).toArray();
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

    private Matrix computeSimplexMatrix(final Matrix convenience, final Matrix oneHotEncodedBids) {
        final Integer lastRowIdx = convenience.getRowDimension() - 1;
        final Integer lastColIdx = convenience.getColumnDimension() - 1;
        final Integer lastRowRankingsIdx = oneHotEncodedBids.getRowDimension() - 1;
        final Integer lastColRankingsIdx = oneHotEncodedBids.getRowDimension() - 1;

        final Integer finalRowIdx = lastRowIdx;
        final Integer startOfEqualityIdx = finalRowIdx - 2;
        final Integer endOfMaxUtitiliesIdx = startOfEqualityIdx - 1;
        final Integer startOfMaxUtilitiesIdx = endOfMaxUtitiliesIdx - lastRowRankingsIdx;
        final Integer endOfMinUtitiliesIdx = startOfMaxUtilitiesIdx - 1;
        final Integer startOfMinUtilitiesIdx = endOfMinUtitiliesIdx - lastRowRankingsIdx;
        final Integer endOfComparisonsIdx = startOfMinUtilitiesIdx - 1;
        final Integer startIdx = 0;

        final Integer numberOfUnknowns = this.numberOfUnknowns.intValue();
        final double[][] inputComparisonPart = convenience.getMatrix(startIdx, endOfComparisonsIdx, 0, lastColIdx - 1)
                .getArrayCopy();
        final double[][] inputMinumumUtilityPart = convenience
                .getMatrix(startOfMinUtilitiesIdx, endOfMinUtitiliesIdx, 0, lastColIdx - 1).getArrayCopy();
        final double[][] inputMaximumUtilityPart = convenience
                .getMatrix(startOfMaxUtilitiesIdx, endOfMaxUtitiliesIdx, 0, lastColIdx - 1).getArrayCopy();
        final double[][] inputEqualityPart = convenience
                .getMatrix(startOfEqualityIdx, finalRowIdx - 1, 0, lastColIdx - 1).getArrayCopy();

        double[] b = convenience.getMatrix(0, lastRowIdx - 1, lastColIdx, lastColIdx).getRowPackedCopy();
        b = Arrays.stream(b).boxed().map(val -> Utils.round(val, 2)).mapToDouble(val -> val).toArray();
        final double[] c = convenience.getMatrix(lastRowIdx, lastRowIdx, 0, lastColIdx - 1).getRowPackedCopy();
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

        final LinearObjectiveFunction oFunc = new LinearObjectiveFunction(c, 0);
        final Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        int i = 0;
        System.out.println("Constraints");
        for (final double[] row : inputComparisonPart) {
            constraints.add(new LinearConstraint(row, Relationship.GEQ, b[i]));
            System.out.println(Arrays.toString(row) + " >= " + b[i]);
            i++;
        }
        for (final double[] row : inputMinumumUtilityPart) {
            constraints.add(new LinearConstraint(row, Relationship.GEQ, b[i]));
            System.out.println(Arrays.toString(row) + " >= " + b[i]);
            i++;
        }
        for (final double[] row : inputMaximumUtilityPart) {
            constraints.add(new LinearConstraint(row, Relationship.LEQ, b[i]));
            System.out.println(Arrays.toString(row) + " <= " + b[i]);
            i++;
        }
        for (final double[] row : inputEqualityPart) {
            constraints.add(new LinearConstraint(row, Relationship.EQ, b[i]));
            System.out.println(Arrays.toString(row) + "  = " + b[i]);
            i++;
        }

        final SimplexSolver solver = new SimplexSolver();
        final PointValuePair solution = solver.optimize(new MaxIter(100), oFunc, new LinearConstraintSet(constraints),
                GoalType.MINIMIZE, new NonNegativeConstraint(true));
        System.out.println("Pred Weights:");
        System.out.println(Arrays.toString(solution.getPoint()) + " : " + solution.getSecond());

        return new Matrix(Arrays.copyOfRange(solution.getPoint(), 0, numberOfUnknowns), 1);
    }



    public Matrix getWeights() {
        return weightsMatrix;
    }

    private Matrix pairwiseComparison(final Bid firstBid, final Bid secondBid) {
        final Matrix dummyEncodedBids = Utils.getDummyEncoding(this.issues, Arrays.asList(firstBid, secondBid));
        final Integer numCol = dummyEncodedBids.getColumnDimension() - 1;
        final Matrix firstRow = dummyEncodedBids.getMatrix(new int[] { 0 }, 0, numCol);
        final Matrix secondRow = dummyEncodedBids.getMatrix(new int[] { 1 }, 0, numCol);
        final Matrix comparisonRow = secondRow.minus(firstRow);
        return comparisonRow;
    }

    private Matrix getMatrixOfPairWiseComparisons(final List<Bid> bidOrder) {
        final List<Matrix> disjointComparisons = IntStream.range(0, rankings.getSize() - 1)
                .mapToObj(idx -> pairwiseComparison(bidOrder.get(idx), bidOrder.get(idx + 1)))
                .collect(Collectors.toList());
        final double[][] result = disjointComparisons.stream().map(row -> row.getRowPackedCopy())
                .toArray(double[][]::new);
        return new Matrix(result);
    }

    private void evaluatePerformance(final List<Bid> evaluationBids) {
        if (this.userModel instanceof ExperimentalUserModel) {
            final ExperimentalUserModel userModelExperimental = (ExperimentalUserModel) this.userModel;
            final Matrix oneHotEncodedRankings = Utils.getDummyEncoding(this.issues, evaluationBids);
            final Matrix weights = this.getWeights();

            if (this.isVerbose) {
                System.out.println("Encoded ranking");
                Utils.printMatrix(oneHotEncodedRankings);
            }
            System.out.println("Weights");
            Utils.printMatrix(weights);

            final List<Double> statsPredictDiff = new ArrayList<Double>();
            final List<Double> statsPredictMSE = new ArrayList<Double>();
            for (final Bid rankedBid : evaluationBids) {
                final double realUtility = userModelExperimental.getRealUtility(rankedBid);
                final double predUtility = this.getUtility(rankedBid);
                final double predMSE = Math.pow(predUtility - realUtility, 2);
                final double predDiff = Math.abs(predUtility - realUtility);
                statsPredictMSE.add(predMSE);
                statsPredictDiff.add(predDiff);
                if (this.isVerbose) {
                    System.out.println("===>   Bid: " + rankedBid);
                    System.out.println("True  Util: " + realUtility);
                    System.out.println("Pred  Util: " + predUtility);
                    System.out.println("       MSE: " + predMSE);
                    System.out.println("Difference: " + predDiff);
                }
            }
            System.out.println("Avg MSE of prediction utility estimator: "
                    + statsPredictMSE.stream().mapToDouble(a -> a).average().getAsDouble());
            System.out.println("Avg Difference of prediction utility estimator: "
                    + statsPredictDiff.stream().mapToDouble(a -> a).average().getAsDouble());
        }

    }

    @Override
    public double getUtility(final Bid bid) {
        final Matrix oneHotEncodedRankings = Utils.getDummyEncoding(this.issues, Arrays.asList(bid));
        final Matrix prediction = oneHotEncodedRankings.times(this.getWeights().transpose());
        return prediction.get(0, 0);
    }

}