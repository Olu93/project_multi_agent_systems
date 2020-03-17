package main;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.ISSUETYPE;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
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
    public void init(NegotiationSession negotiationSession, OpponentModel model, Map<String, Double> parameters) {
        super.init(negotiationSession, model, parameters);
        this.myBiddingHistory = negotiationSession.getOwnBidHistory();
        this.opponentBiddingHistory = negotiationSession.getOpponentBidHistory();
        this.domainIssues = negotiationSession.getIssues();
        this.issueTypes = this.domainIssues.stream().map(issue -> new Map.Entry<Issue, ISSUETYPE>(issue, issue.getType())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
    }

    @Override
    public BidDetails getBid(List<BidDetails> bidsInRange) {
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
        double[] tmp = model.getIssueWeights();
        return new Matrix(tmp, 1);
        // }
    }

    private Matrix converHistoryToMatrix(BidHistory bidHistory){
        List<Bid> lBids = bidHistory.getHistory().parallelStream().map(bd -> bd.getBid()).collect(Collectors.toList());
        HashMap<Issue, List> values = new HashMap<>();
        for (Issue issue : this.domainIssues) {
            values.put(issue, lBids.stream().map(bid -> bid.getValue(issue)).collect(Collectors.toList()));
        }

        

        
        
        return null;
    }

    private Matrix dummyEncode(Integer columnNumber, List<ValueDiscrete> issueValues){
        // Matrix containerMatrix = new Matrix(1, issueValues.get(0).);
        IssueDiscrete tmp;
        tmp. 
        return null;
    }

}