package main;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import agents.anac.y2019.harddealer.math3.optim.MaxIter;
import agents.anac.y2019.harddealer.math3.optim.PointValuePair;
import agents.anac.y2019.harddealer.math3.optim.linear.LinearConstraint;
import agents.anac.y2019.harddealer.math3.optim.linear.LinearConstraintSet;
import agents.anac.y2019.harddealer.math3.optim.linear.LinearObjectiveFunction;
import agents.anac.y2019.harddealer.math3.optim.linear.NonNegativeConstraint;
import agents.anac.y2019.harddealer.math3.optim.linear.Relationship;
import agents.anac.y2019.harddealer.math3.optim.linear.SimplexSolver;
import agents.anac.y2019.harddealer.math3.optim.nonlinear.scalar.GoalType;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Objective;
import genius.core.issue.ValueDiscrete;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.CustomUtilitySpace;
import genius.core.utility.Evaluator;
import genius.core.utility.UtilitySpace;
import genius.core.xml.SimpleElement;
import math.Matrix;
import misc.Utils;
import simplex.Simplex;
import simplex.TwoPhaseSimplex;

/**
 * Test
 */
public class UncertaintyUtilitySpace extends AdditiveUtilitySpace {

    /**
     *
     */
    private static final long serialVersionUID = 8769632363299414230L;
    private final BidRanking rankings;
    // private final HashMap<IssueDiscrete, Double> weights;
    private final Matrix weightsMatrix;
    private Simplex simplex;
    private final Long numberOfUnknowns; // Count of every individual issue value
    private static final Double EPSILON = 0.0001;
    private final Matrix oneHotBids;
    private final Matrix oneHotEqualityBids;
    private final UserModel userModel;
    private final List<Issue> issues;
    private final Boolean isVerbose = false;
    private final Boolean hasStrongConstraints = true;
    private final Boolean hasEpsilon = true;

    public UncertaintyUtilitySpace(final UserModel userModel) {
        super(userModel.getDomain());
		// this.fEvaluators = fEvaluators;
		// normalizeWeights();
        this.userModel = userModel;
        this.rankings = userModel.getBidRanking();
        this.issues = this.userModel.getDomain().getIssues();
        this.numberOfUnknowns = this.issues.stream().mapToLong(issue -> ((IssueDiscrete) issue).getNumberOfValues())
                .sum();
        this.oneHotBids = Utils.getDummyEncoding(this.issues, rankings.getBidOrder());
        this.oneHotEqualityBids = Utils.getDummyEncoding(this.issues,
                Arrays.asList(rankings.getMaximalBid(), rankings.getMinimalBid()));
        // system.out.println("Number of unknowns: " + numberOfUnknowns);

        this.weightsMatrix = this.init();
        this.evaluatePerformance(this.rankings.getBidOrder());
    }

    private Matrix init() {
        final Matrix comparisonMatrix = getMatrixOfPairWiseComparisons(this.rankings.getBidOrder());
        final Matrix simplexMatrix = getSimplexMatrix(comparisonMatrix, this.oneHotBids, this.oneHotEqualityBids);
        return computeSimplexMatrix(simplexMatrix, this.oneHotBids);
    }

    private Matrix getSimplexMatrix(final Matrix comparisons, final Matrix oneHotEncodedBids,
            final Matrix oneHotEqualityBids) {

        final Integer endOfPairwiseComparisonIdx = comparisons.getRowDimension() - 1;
        final Integer lastRowRankingsIdx = oneHotEncodedBids.getRowDimension() - 1;
        final Integer lastColRankingsIdx = oneHotEncodedBids.getColumnDimension() - 1;
        final Integer splitAt = comparisons.getColumnDimension() - 1;
        final int numberOfEqualityBids = oneHotEqualityBids.getRowDimension();

        final int startOfMinUtilitiesIdx = endOfPairwiseComparisonIdx + 1;
        final int endOfMinUtilitiesIdx = startOfMinUtilitiesIdx + lastRowRankingsIdx;
        final int startOfMaxUtilitiesIdx = endOfMinUtilitiesIdx + 1;
        final int endOfMaxUtilitiesIdx = startOfMaxUtilitiesIdx + lastRowRankingsIdx;
        final int startOfEqualities = (hasStrongConstraints ? endOfMaxUtilitiesIdx : endOfPairwiseComparisonIdx) + 1;
        final int thirdToLastIdx = startOfEqualities;
        final int secondToLastIdx = startOfEqualities + 1;

        final Integer lastColComparisonIdx = comparisons.getRowDimension() + comparisons.getColumnDimension();
        final Matrix slackVars = Matrix.identity(startOfEqualities, startOfEqualities);
        final Matrix emptyMatrix = new Matrix(startOfEqualities + numberOfEqualityBids + 1, lastColComparisonIdx + 1);
        final int finalColIdx = emptyMatrix.getColumnDimension() - 1;

        emptyMatrix.setMatrix(0, endOfPairwiseComparisonIdx, 0, splitAt, comparisons);
        emptyMatrix.setMatrix(0, endOfPairwiseComparisonIdx, splitAt + 1, finalColIdx - 1, slackVars);

        if (hasEpsilon) {
            Matrix tmp = new Matrix(startOfMaxUtilitiesIdx, 1, EPSILON);
            emptyMatrix.setMatrix(0, endOfPairwiseComparisonIdx, finalColIdx, finalColIdx, tmp);
        }

        if (hasStrongConstraints) {
            emptyMatrix.setMatrix(endOfPairwiseComparisonIdx + 1, endOfMinUtilitiesIdx, 0, lastColRankingsIdx,
                    oneHotBids);
            for (int i = startOfMinUtilitiesIdx; i < startOfMaxUtilitiesIdx; i++) {
                emptyMatrix.set(i, lastColComparisonIdx, rankings.getLowUtility());
            }

            emptyMatrix.setMatrix(startOfMaxUtilitiesIdx, endOfMaxUtilitiesIdx, 0, lastColRankingsIdx, oneHotBids);
            for (int i = startOfMaxUtilitiesIdx; i < thirdToLastIdx; i++) {
                emptyMatrix.set(i, lastColComparisonIdx, rankings.getHighUtility());
            }
        }
        // // system.out.println("Almost done");
        // Utils.printMatrix(emptyMatrix);

        final int[] range = IntStream.range(thirdToLastIdx, thirdToLastIdx + numberOfEqualityBids).toArray();
        emptyMatrix.setMatrix(range, 0, oneHotEqualityBids.getColumnDimension() - 1, oneHotEqualityBids);
        emptyMatrix.set(thirdToLastIdx, finalColIdx, rankings.getHighUtility());
        if (range.length > 1) {
            emptyMatrix.set(secondToLastIdx, finalColIdx, rankings.getLowUtility());
        }

        for (int i = 0; i < lastColComparisonIdx; i++) {
            emptyMatrix.set(thirdToLastIdx + numberOfEqualityBids, i, i > splitAt ? 1 : 0);
        }

        if (this.isVerbose == true) {
            // system.out.println("Done");
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
        final Integer endOfComparisonsIdx = hasStrongConstraints ? startOfMinUtilitiesIdx : startOfEqualityIdx - 1;
        final Integer startIdx = 0;

        final Integer numberOfUnknowns = this.numberOfUnknowns.intValue();
        List<Constraint> constraintsList = new ArrayList<>();

        final Matrix inputComparisonPart = convenience.getMatrix(startIdx, endOfComparisonsIdx, 0, lastColIdx);

        constraintsList.addAll(addRowsToConstraints(inputComparisonPart, Relationship.GEQ));

        if (hasStrongConstraints) {
            final Matrix inputMinumumUtilityPart = convenience.getMatrix(startOfMinUtilitiesIdx + 1,
                    endOfMinUtitiliesIdx, 0, lastColIdx);
            constraintsList.addAll(addRowsToConstraints(inputMinumumUtilityPart, Relationship.GEQ));
            final Matrix inputMaximumUtilityPart = convenience.getMatrix(startOfMaxUtilitiesIdx, endOfMaxUtitiliesIdx,
                    0, lastColIdx);
            constraintsList.addAll(addRowsToConstraints(inputMaximumUtilityPart, Relationship.LEQ));
        }

        final Matrix inputEqualityPart = convenience.getMatrix(startOfEqualityIdx, finalRowIdx - 1, 0, lastColIdx);
        constraintsList.addAll(addRowsToConstraints(inputEqualityPart, Relationship.EQ));

        // double[] b = convenience.getMatrix(0, lastRowIdx - 1, lastColIdx,
        // lastColIdx).getRowPackedCopy();
        // b = Arrays.stream(b).boxed().map(val -> Utils.round(val, 2)).mapToDouble(val
        // -> val).toArray();
        final double[] c = convenience.getMatrix(lastRowIdx, lastRowIdx, 0, lastColIdx - 1).getRowPackedCopy();

        if (this.isVerbose) {
            System.out.println("Input c for two-phase:");
            System.out.println(Arrays.toString(c));
        }

        final LinearObjectiveFunction oFunc = new LinearObjectiveFunction(c, 0);
        final Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>();
        int i = 0;
        // system.out.println("Constraints");
        for (Constraint entry : constraintsList) {
            constraints.add(new LinearConstraint(entry.getRow(), entry.getType(), entry.getValue()));
            // system.out.println(entry);
        }

        final SimplexSolver solver = new SimplexSolver();
        final PointValuePair solution = solver.optimize(new MaxIter(1000), oFunc, new LinearConstraintSet(constraints),
                GoalType.MINIMIZE, new NonNegativeConstraint(true));
        // system.out.println("Pred Weights:");
        // system.out.println(Arrays.toString(solution.getPoint()) + " : " + solution.getSecond());

        return new Matrix(Arrays.copyOfRange(solution.getPoint(), 0, numberOfUnknowns), 1);
    }

    private List<Constraint> addRowsToConstraints(Matrix rows, Relationship relationship) {
        List<Constraint> constraints = new ArrayList<>();
        int lastIndex = rows.getColumnDimension() - 1;
        for (int i = 0; i < rows.getRowDimension(); i++) {
            constraints.add(new Constraint(rows.getMatrix(i, i, 0, lastIndex), relationship));
        }
        return constraints;
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
    public double getUtility(Bid bid) {
//    	System.out.println("CONTENT OF BID");
//    	System.out.println(bid);
//    	System.out.println("number of issues" + bid.getIssues().size());
//    	System.out.println("number of values" + bid.getValues().size());
    	if (bid != null && bid.getValues().size() > 0) {
	        final Matrix oneHotEncodedRankings = Utils.getDummyEncoding(this.issues, Arrays.asList(bid));
	        final Matrix prediction = oneHotEncodedRankings.times(this.getWeights().transpose());
	        return prediction.get(0, 0);
    	}
//    	// default utility function in case of null bid
//    	// System.out.println("ENTERING DEFAULT METHOD");
//    	double utility = 0;
//    	Objective root = getDomain().getObjectivesRoot();
//    	Enumeration<Objective> issueEnum = root.getPreorderIssueEnumeration();
//    	while (issueEnum.hasMoreElements()) {
//	    	Objective is = issueEnum.nextElement();
//	    	Evaluator eval = getfEvaluators().get(is);
//	    	if (eval == null) {
//	    		throw new IllegalArgumentException("UtilitySpace does not contain evaluator for issue "+ is + ". ");
//	    	}
//	    	switch (eval.getType()) {
//		    	case DISCRETE:
//		    	case INTEGER:
//		    	case REAL:
//		    		utility += eval.getWeight() * getEvaluation(is.getNumber(), bid);
//		    		break;
//		    	case OBJECTIVE:
//		    	// we ignore OBJECTIVE. Not clear what it is and why.
//		    	break;
//	    	}
//    	}
//    	double result = utility;
//    	System.out.println("LEAVING DEFAULT METHOD WITH RESULT: " + result);
//    	if (result > 1)
//    		return 1;
    	return 0.0;
    }

    private class Constraint {
        final double[] row;
        final Relationship type;
        final double value;

        public Constraint(Matrix row, Relationship type) {
            int lastIndex = row.getColumnDimension() - 1;
            this.row = row.getMatrix(new int[] { 0 }, 0, lastIndex - 1).getRowPackedCopy();
            this.value = Utils.round(row.get(0, lastIndex), 4);
            this.type = type;
        }

        @Override
        public String toString() {
            return Utils.getRowString(row) + (type == Relationship.EQ ? "=" + type + " " : type + " ") + value;
        }

        public double[] getRow() {
            return row;
        }

        public Relationship getType() {
            return type;
        }

        public double getValue() {
            return value;
        }

    }

}