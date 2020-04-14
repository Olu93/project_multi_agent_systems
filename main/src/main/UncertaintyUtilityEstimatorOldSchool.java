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
import genius.core.issue.Value;
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
public class UncertaintyUtilityEstimatorOldSchool extends AdditiveUtilitySpace {

    private final BidRanking rankings;
    // private final HashMap<IssueDiscrete, Double> weights;
    private final Matrix weightsMatrix;
    private Simplex simplex;
    private final Long numberOfUnknowns; // Count of every individual issue value
    private static final Double EPSILON = 1.0E-8;
    private Matrix oneHotBids;
    private UserModel userModel;
    private List<Issue> issues;
    private final Boolean isVerbose = true;

    public UncertaintyUtilityEstimatorOldSchool(final UserModel userModel) {
        this.userModel = userModel;
        this.rankings = userModel.getBidRanking();
        this.issues = this.userModel.getDomain().getIssues();
        this.numberOfUnknowns = new Long(this.issues.size());
        this.oneHotBids = Utils.getDummyEncoding(this.issues, rankings.getBidOrder());
        System.out.println("Number of unknowns: " + numberOfUnknowns);

        this.weightsMatrix = this.init();
        this.evaluatePerformance();
    }

    private Matrix init() {
        final List<Map<IssueDiscrete, Integer>> comparisons = getMatrixOfPairWiseComparisons2();
        final List<Map<IssueDiscrete, Integer>> bidValues = getMatrixOfValues2(this.rankings.getBidOrder());
        final Matrix comparisonMatrix = convertPairwiseComparisonsToMatrix(comparisons);
        final Matrix valueMatrix = convertBidToMatrix(bidValues);
        this.oneHotBids = valueMatrix;
        System.out.println("Comparison Matrix");
        Utils.printMatrix(comparisonMatrix);
        System.out.println("Value Matrix");
        Utils.printMatrix(valueMatrix);
        final Matrix simplexMatrix = getFinalSimplex(Matrix.constructWithCopy(comparisonMatrix.getArrayCopy()));

        return computeSimplex8(simplexMatrix);
    }

    private Integer getIndexDifference(final IssueDiscrete issue, final Value higherValue, final Value lowerValue) {
        return issue.getValueIndex(higherValue.toString()) - issue.getValueIndex(lowerValue.toString());
    }

    private Map<IssueDiscrete, Integer> pairwiseComparison1(final Bid firstBid, final Bid secondBid) {
        final Map<IssueDiscrete, Integer> result = this.issues.stream().map(issue -> (IssueDiscrete) issue)
                .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
                        getIndexDifference(issue, secondBid.getValue(issue), firstBid.getValue(issue))))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
        return result;
    }


    // private Map<IssueDiscrete, Integer> pairwiseComparison3(final Bid firstBid,
    // final Bid secondBid) {
    // final Map<IssueDiscrete, Integer> result = this.issues.stream().map(issue ->
    // (IssueDiscrete) issue)
    // .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
    // secondBid.getValue(issue) - firstBid.getValue(issue)))
    // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
    // return result;
    // }

    public Matrix getWeights() {
        return weightsMatrix;
    }

    private List<Map<IssueDiscrete, Integer>> getMatrixOfPairWiseComparisons2() {
        final List<Bid> bidOrder = this.rankings.getBidOrder();
        final List<Map<IssueDiscrete, Integer>> result = IntStream.range(0, rankings.getSize() - 1)
                .mapToObj(idx -> pairwiseComparison1(bidOrder.get(idx), bidOrder.get(idx + 1)))
                .collect(Collectors.toList());
        return result;
    }

    private List<Map<IssueDiscrete, Integer>> getMatrixOfValues2(List<Bid> bidOrder) {
        final List<Map<IssueDiscrete, Integer>> result = bidOrder.stream()
                .map(bid -> this.issues.stream().map(issue -> (IssueDiscrete) issue)
                        .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
                                issue.getValueIndex(bid.getValue(issue).toString())))
                        .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)))
                .collect(Collectors.toList());
        return result;
    }

    private Matrix convertPairwiseComparisonsToMatrix(final List<Map<IssueDiscrete, Integer>> input) {
        System.out.println(input.size());
        final Integer rowLength = input.size() - 1;
        final Integer colLength = input.get(0).size();
        final Matrix emptyMatrix = new Matrix(rowLength, colLength);
        Integer tmpCol;
        Map<IssueDiscrete, Integer> currentComparisonMap;

        for (int i = 0; i < rowLength; i++) {
            currentComparisonMap = input.get(i);
            for (final Entry<IssueDiscrete, Integer> tmp : currentComparisonMap.entrySet()) {
                tmpCol = tmp.getKey().getNumber() - 1;
                emptyMatrix.set(i, tmpCol, tmp.getValue());
            }
        }
        return emptyMatrix;
    }

    private Matrix convertBidToMatrix(final List<Map<IssueDiscrete, Integer>> input) {
        System.out.println(input.size());
        final Integer rowLength = input.size();
        final Integer colLength = input.get(0).size();
        final Matrix emptyMatrix = new Matrix(rowLength, colLength);
        Integer tmpCol;
        Map<IssueDiscrete, Integer> currentComparisonMap;

        for (int i = 0; i < rowLength; i++) {
            currentComparisonMap = input.get(i);
            for (final Entry<IssueDiscrete, Integer> tmp : currentComparisonMap.entrySet()) {
                tmpCol = tmp.getKey().getNumber() - 1;
                emptyMatrix.set(i, tmpCol, tmp.getValue());
            }
        }
        return emptyMatrix;
    }

    private Matrix getFinalSimplex(final Matrix comparisons) {

        List<Bid> hardConstraints = Arrays.asList(rankings.getMaximalBid(), rankings.getMinimalBid());

        final Integer endOfPairwiseComparisonIdx = comparisons.getRowDimension() - 1;
        final Integer lastRowRankingsIdx = this.oneHotBids.getRowDimension() - 1;
        final Integer lastColRankingsIdx = this.oneHotBids.getColumnDimension() - 1;
        final Integer splitAt = comparisons.getColumnDimension() - 1;

        int startOfMinUtilitiesIdx = endOfPairwiseComparisonIdx + 1;
        int endOfMinUtilitiesIdx = startOfMinUtilitiesIdx + lastRowRankingsIdx;
        int startOfMaxUtilitiesIdx = endOfMinUtilitiesIdx + 1;
        int endOfMaxUtilitiesIdx = startOfMaxUtilitiesIdx + lastRowRankingsIdx;
        int startOfEqualities = endOfMaxUtilitiesIdx + 1;
        int thirdToLastIdx = startOfEqualities;
        int secondToLastIdx = startOfEqualities + 1;

        final Integer lastColComparisonIdx = comparisons.getRowDimension() + comparisons.getColumnDimension();
        final Matrix slackVars = Matrix.identity(startOfMinUtilitiesIdx, startOfMinUtilitiesIdx);
        final Matrix emptyMatrix = new Matrix(startOfEqualities + hardConstraints.size() + 1, lastColComparisonIdx + 1);
        int finalColIdx = emptyMatrix.getColumnDimension() - 1;

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
        System.out.println("Added all the additional constraints");
        Utils.printMatrix(emptyMatrix);

        List<Map<IssueDiscrete, Integer>> tmp = getMatrixOfValues2(hardConstraints);
        Matrix dummyEncodedHardConstraints = convertBidToMatrix(tmp);

        int[] range = { thirdToLastIdx, secondToLastIdx };
        emptyMatrix.setMatrix(range, 0, dummyEncodedHardConstraints.getColumnDimension() - 1,
                dummyEncodedHardConstraints);
        emptyMatrix.set(thirdToLastIdx, finalColIdx, rankings.getHighUtility());
        emptyMatrix.set(secondToLastIdx, finalColIdx, rankings.getLowUtility());

        System.out.println("Almost done");
        Utils.printMatrix(emptyMatrix);

        for (int i = 0; i < lastColComparisonIdx; i++) {
            emptyMatrix.set(thirdToLastIdx + hardConstraints.size(), i, i > splitAt ? 1 : 0);
        }

        if (this.isVerbose == true) {
            System.out.println("Done");
            Utils.printMatrix(emptyMatrix);
        }

        return emptyMatrix;
    }

    private Matrix computeSimplex8(Matrix convenience) {
        Integer lastRowIdx = convenience.getRowDimension() - 1;
        Integer lastColIdx = convenience.getColumnDimension() - 1;
        Integer lastRowRankingsIdx = this.oneHotBids.getRowDimension() - 1;
        Integer lastColRankingsIdx = this.oneHotBids.getRowDimension() - 1;

        Integer finalRowIdx = lastRowIdx;
        Integer startOfEqualityIdx = finalRowIdx - 2;
        Integer endOfMaxUtitiliesIdx = startOfEqualityIdx - 1;
        Integer startOfMaxUtilitiesIdx = endOfMaxUtitiliesIdx - lastRowRankingsIdx;
        Integer endOfMinUtitiliesIdx = startOfMaxUtilitiesIdx - 1;
        Integer startOfMinUtilitiesIdx = endOfMinUtitiliesIdx - lastRowRankingsIdx;
        Integer endOfComparisonsIdx = startOfMinUtilitiesIdx - 1;
        Integer startIdx = 0;

        Integer numberOfUnknowns = this.numberOfUnknowns.intValue();
        double[][] inputComparisonPart = convenience.getMatrix(startIdx, endOfComparisonsIdx, 0, lastColIdx - 1)
                .getArrayCopy();
        double[][] inputMinumumUtilityPart = convenience
                .getMatrix(startOfMinUtilitiesIdx, endOfMinUtitiliesIdx, 0, lastColIdx - 1).getArrayCopy();
        double[][] inputMaximumUtilityPart = convenience
                .getMatrix(startOfMaxUtilitiesIdx, endOfMaxUtitiliesIdx, 0, lastColIdx - 1).getArrayCopy();
        double[][] inputEqualityPart = convenience.getMatrix(startOfEqualityIdx, finalRowIdx - 1, 0, lastColIdx - 1)
                .getArrayCopy();

        double[] b = convenience.getMatrix(0, lastRowIdx - 1, lastColIdx, lastColIdx).getRowPackedCopy();
        b = Arrays.stream(b).boxed().map(val -> Utils.round(val, 3)).mapToDouble(val -> val).toArray();
        double[] c = convenience.getMatrix(lastRowIdx, lastRowIdx, 0, lastColIdx - 1).getRowPackedCopy();
        if (this.isVerbose) {
            System.out.println("======== Comparison");
            Utils.printMatrix(inputComparisonPart);
            System.out.println("======== Min");
            Utils.printMatrix(inputMinumumUtilityPart);
            System.out.println("======== Max");
            Utils.printMatrix(inputMaximumUtilityPart);
            System.out.println("======== Equal");
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
            List<Double> statsPredict = new ArrayList<Double>();
            for (Bid rankedBid : this.rankings.getBidOrder()) {
                double realUtility = userModelExperimental.getRealUtility(rankedBid);
                double predDiff = Math.abs(prediction.get(i, 0) - realUtility);
                statsPredict.add(predDiff);
                if (this.isVerbose) {
                    System.out.println("===>   Bid: " + rankedBid);
                    System.out.println("True  Util: " + realUtility);
                    System.out.println("Pred  Util: " + this.getUtility(rankedBid));
                    System.out.println("Difference: " + predDiff);
                }
                i++;
            }
            System.out.println("Avg Distance of prediction utility estimator: "
                    + statsPredict.stream().mapToDouble(a -> a).average().getAsDouble());
        }

    }
}