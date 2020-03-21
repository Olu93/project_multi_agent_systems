package main;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.math3.util.Pair;

import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.ISSUETYPE;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.IssueInteger;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.issue.ValueInteger;
import math.Matrix;

/**
 * SmartOpponentOfferingModel
 */
public class SmartOpponentOfferingModel extends OMStrategy {

    private BidHistory opponentBiddingHistory;
    private BidHistory myBiddingHistory;
    private HashMap<Issue, Matrix> issueStatistics;
    private Double[] issueWeights;
    private List<IssueDiscrete> domainIssues;
    private Map<Issue, ISSUETYPE> issueTypes;

    @Override
    public void init(final NegotiationSession negotiationSession, final OpponentModel model,
            final Map<String, Double> parameters) {
        super.init(negotiationSession, model, parameters);
        this.myBiddingHistory = negotiationSession.getOwnBidHistory();
        this.opponentBiddingHistory = negotiationSession.getOpponentBidHistory();
        this.domainIssues = negotiationSession.getIssues().parallelStream().map(value -> (IssueDiscrete) value).collect(Collectors.toList());
        // this.issueTypes = this.domainIssues.stream().map(issue -> new
        // AbstractMap.SimpleEntry<Issue, ISSUETYPE>(issue,
        // issue.getType())).collect(Collectors.toMap(Map.Entry::getKey,
        // Map.Entry::getValue));

    }

    @Override
    public BidDetails getBid(final List<BidDetails> bidsInRange) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean canUpdateOM() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getName() {
        return SmartComponentNames.SMART_OPPONENT_BIDDING_STRATEGY.toString();
    }

    private Matrix getIssueWeights() {
        // if(SmartComponentNames.SMART_OPPONENT_MODEL.toString().equals(model.getName())){
        final double[] tmp = model.getIssueWeights();
        return new Matrix(tmp, 1);
        // }
    }

    private Matrix converHistoryToMatrix(final BidHistory bidHistory) {
        final List<Bid> lBids = bidHistory.getHistory().parallelStream().map(bd -> bd.getBid())
            .collect(Collectors.toList());
        
        // Reduction 1
        // final HashMap<Issue, Matrix> oneHotMatrix = new HashMap<>();
        // Integer noOfValues = null;
        // List<Integer> issueValues = null;

        // for (final IssueDiscrete issue : this.domainIssues) {
        //     issueValues = lBids.stream().map(bid -> bid.getValue(issue))
        //             .map(value -> issue.getValueIndex((ValueDiscrete) value))
        //             .collect(Collectors.toList());
        //     noOfValues = issue.getNumberOfValues();
        //     oneHotMatrix.put(issue, dummyEncode(noOfValues, issueValues));
        // }

        // Reduction 2
        // Map<Issue,List<Integer>> valuesByIssue = this.domainIssues.stream()
        //     .map(issue -> new SimpleEntry<Issue,List<Integer>>(issue, extractAllValuesForIssue(lBids, issue)))
        //     .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        // Map<Issue,Matrix> oneHotEncodedMatrixByIssue = this.domainIssues.stream()
        //     .map(issue -> new SimpleEntry<Issue,Matrix>(issue,dummyEncode(issue.getNumberOfValues(), valuesByIssue.get(issue))))
        //     .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)); 

        final Map<Issue,Matrix> oneHotEncodedMatrixByIssue = this.domainIssues.stream()
            .map(issue -> new SimpleEntry<IssueDiscrete,List<Integer>>(issue, extractAllValuesForIssue(lBids, issue)))
            .map(entry -> new SimpleEntry<IssueDiscrete,Matrix>(entry.getKey(), dummyEncode(entry.getKey(), entry.getValue())))
            .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)); 
        
        final List<List<Double>> tmp = IntStream.range(0, lBids.size()).mapToObj(i -> extractFullRow(i, oneHotEncodedMatrixByIssue)).collect(Collectors.toList());
        final double[][] preFullMatrix = tmp.stream().map(arr -> arr.toArray()).toArray(double[][]::new);
        final Matrix fullMatrix = new Matrix(preFullMatrix);

        return fullMatrix;
    }

    private Matrix dummyEncode(final IssueDiscrete issue, final List<Integer> issueValues) {
        final Matrix containerMatrix = new Matrix(issueValues.size(), issue.getNumberOfValues());
        
        for (int row = 0; row < issueValues.size(); row++) {
            containerMatrix.set(row,issueValues.get(row), 1);
        }
        
        return containerMatrix;
    }

    private List<Double> extractFullRow(final Integer row, final Map<Issue, Matrix> oneHotMatrix) {
        return this.domainIssues.stream()
        .map(issue -> oneHotMatrix.get(issue).getArray()[row])
        .flatMapToDouble(Arrays::stream).boxed().collect(Collectors.toList());
    }

    private List<Integer> extractAllValuesForIssue(List<Bid> lBids, IssueDiscrete issue){
        return lBids.stream().map(bid -> (ValueDiscrete) bid.getValue(issue))
        .map(value -> issue.getValueIndex(value))
        .collect(Collectors.toList());
    }

}