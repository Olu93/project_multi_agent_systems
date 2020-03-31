package main;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

/**
 * Test
 */
public class UncertaintyUtilityEstimator extends AdditiveUtilitySpace {

    private BidRanking rankings;
    private NegotiationSession session;
    private HashMap<IssueDiscrete, Double> weights;
    private Simplex simplex;

    public UncertaintyUtilityEstimator(NegotiationSession session) {
        this.session = session;
        this.rankings = session.getUserModel().getBidRanking();
        this.weights = this.init();
    }

    private HashMap<IssueDiscrete, Double> init() {
        List<Map<IssueDiscrete, Integer>> comparisons = getMatrixOfPairWiseComparisons();
        Matrix comparisonMatrix = convertPairwiseComparisonsToMatrix(comparisons);
        System.out.println("Comparison Matrix");
        Utils.printMatrix(comparisonMatrix);
        Matrix simplexMatrix = getFinalSimplex(Matrix.constructWithCopy(comparisonMatrix.getArrayCopy()));
        System.out.println("Simplex Matrix");
        Utils.printMatrix(simplexMatrix);
        this.simplex = computeSimplex(simplexMatrix.getArrayCopy(), simplexMatrix.getRowDimension() - 1,
                simplexMatrix.getColumnDimension() - 1);
        double[][] weights = this.simplex.getTable();
        System.out.println("Computed weights");
        Utils.printMatrix(weights);
        return null;
    }

    private Integer getIndex(IssueDiscrete issue, ValueDiscrete value) {
        return issue.getValueIndex(value);
    }

    private Map<IssueDiscrete, Integer> pairwiseComparison(Bid firstBid, Bid secondBid) {
        Map<IssueDiscrete, Integer> result = this.session.getIssues().stream().map(issue -> (IssueDiscrete) issue)
                .map(issue -> new SimpleEntry<IssueDiscrete, Integer>(issue,
                        issue.getValueIndex(firstBid.getValue(issue).toString())
                                - issue.getValueIndex(secondBid.getValue(issue).toString())))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
        return result;
    }

    private List<Map<IssueDiscrete, Integer>> getMatrixOfPairWiseComparisons() {
        List<Bid> bidOrder = this.rankings.getBidOrder();
        List<Map<IssueDiscrete, Integer>> result = IntStream.range(0, rankings.getSize() - 1)
                .mapToObj(idx -> pairwiseComparison(bidOrder.get(idx), bidOrder.get(idx + 1)))
                .collect(Collectors.toList());
        return result;
    }

    private Matrix convertPairwiseComparisonsToMatrix(List<Map<IssueDiscrete, Integer>> input) {
        System.out.println(input.size());
        Integer rowLength = input.size() - 1;
        Integer colLength = input.get(0).size();
        Matrix emptyMatrix = new Matrix(rowLength, colLength);
        Integer tmpCol;
        Map<IssueDiscrete, Integer> currentComparisonMap;

        for (int i = 0; i < rowLength; i++) {
            currentComparisonMap = input.get(i);
            for (Entry<IssueDiscrete, Integer> tmp : currentComparisonMap.entrySet()) {
                tmpCol = tmp.getKey().getNumber() - 1;
                emptyMatrix.set(i, tmpCol, tmp.getValue());
            }
        }
        return emptyMatrix;
    }

    private Matrix getFinalSimplex(Matrix comparisons) {
        Integer rowLength = comparisons.getRowDimension() - 1;
        Integer colLength = rowLength + comparisons.getColumnDimension();
        Integer splitAt = comparisons.getColumnDimension() - 1;
        Matrix slackVars = Matrix.identity(rowLength, rowLength);
        Matrix emptyMatrix = new Matrix(rowLength + 1, colLength + 1);

        for (int i = 0; i < rowLength; i++) {
            for (int j = 0; j < colLength; j++) {
                emptyMatrix.set(i, j, j > splitAt ? slackVars.get(i, j - splitAt - 1) : comparisons.get(i, j));
            }
        }

        emptyMatrix.set(0, emptyMatrix.getColumnDimension() - 1, rankings.getHighUtility());
        emptyMatrix.set(0, comparisons.getColumnDimension() - 1, 0);
        // emptyMatrix.set(emptyMatrix.getRowDimension()-1,
        // emptyMatrix.getColumnDimension()-1, rankings.getLowUtility()));
        for (int i = 0; i < colLength; i++) {
            emptyMatrix.set(rowLength, i, i > splitAt ? 1 : 0);
        }
        return emptyMatrix;
    }

    private Simplex computeSimplex(double[][] simplexMatrix, Integer rowNum, Integer colNum) {
        Simplex simplex = new Simplex(rowNum, colNum);
        boolean quit = false;

        simplex.fillTable(simplexMatrix);

        // print it out
        System.out.println("---Starting set---");
        simplex.print();
        
        // if table is not optimal re-iterate
        while(!quit){
            Simplex.ERROR err = simplex.compute();
            System.out.println("---Compute---: "+err);

            if(err == Simplex.ERROR.IS_OPTIMAL){
                System.out.println("---Continue---");
                simplex.print();
                quit = true;
            }
            else if(err == Simplex.ERROR.UNBOUNDED){
                System.out.println("---Solution is unbounded---");
                quit = true;
            }
        }
        return simplex;
    }

    @Override
    public double getUtility(Bid bid) {

        return 0.0;
    }

}