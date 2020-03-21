package main;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private List<Issue> domainIssues;
    private Map<Issue, ISSUETYPE> issueTypes;

    @Override
    public void init(final NegotiationSession negotiationSession, final OpponentModel model,
            final Map<String, Double> parameters) {
        super.init(negotiationSession, model, parameters);
        this.myBiddingHistory = negotiationSession.getOwnBidHistory();
        this.opponentBiddingHistory = negotiationSession.getOpponentBidHistory();
        this.domainIssues = negotiationSession.getIssues();
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
        final HashMap<Issue, List<Value>> values = new HashMap<>();
        final HashMap<Issue, Matrix> oneHotMatrix = new HashMap<>();
        Integer noOfValues = null;
        List<Integer> issueValues = null;
        for (final Issue issue : this.domainIssues) {
            issueValues = lBids.stream().map(bid -> bid.getValue(issue))
                    .map(value -> value.getType() == ISSUETYPE.INTEGER ? ((ValueInteger) value).getValue()
                            : ((IssueDiscrete) issue).getValueIndex((ValueDiscrete) value))
                    .collect(Collectors.toList());
            final ISSUETYPE type = issue.getType();
            if (type == ISSUETYPE.INTEGER) {
                noOfValues = Math.toIntExact(((IssueInteger) issue).getNumberOfDiscretizationSteps());
            }
            if (type == ISSUETYPE.DISCRETE) {
                noOfValues = ((IssueDiscrete) issue).getNumberOfValues();
            }

            oneHotMatrix.put(issue, dummyEncode(noOfValues, type, issueValues));
        }


        final List<List<Double>> tmp = IntStream.range(0, lBids.size()).mapToObj(i -> extractFullRow(i, oneHotMatrix)).collect(Collectors.toList());
        final double[][] preFullMatrix = tmp.stream().map(arr -> arr.toArray()).toArray(double[][]::new);
        final Matrix fullMatrix = new Matrix(preFullMatrix);

        return fullMatrix;
    }

    private Matrix dummyEncode(final Integer columnNumber, final ISSUETYPE type, final List<Integer> issueValues) {
        final Matrix containerMatrix = new Matrix(issueValues.size(), columnNumber);
        for (int row = 0; row < issueValues.size(); row++) {
            containerMatrix.set(row,issueValues.get(row), 1);
        }
        
        return containerMatrix;
    }

    private List<Double> extractFullRow(final Integer row, final HashMap<Issue, Matrix> oneHotMatrix) {
        return this.domainIssues.stream()
        .map(issue -> oneHotMatrix.get(issue).getArray()[row])
        .flatMapToDouble(Arrays::stream).boxed().collect(Collectors.toList());
    }

}