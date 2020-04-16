package misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.boaframework.NegotiationSession;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import math.Matrix;

/**
 * LookUpTable
 */
public class BidEncoder {

    private List<IssueDiscrete> domainIssues;
    private NegotiationSession session;
    private HashMap<ValueDiscrete, Integer> mappingValToInt = new HashMap<>();
    private HashMap<Integer, IssueDiscrete> mappingIntToIssue = new HashMap<>();
    private HashMap<Integer, ValueDiscrete> mappingIntToVal = new HashMap<>();

    public BidEncoder(NegotiationSession session) {
        this.session = session;
        this.domainIssues = this.session.getIssues().parallelStream().map(value -> (IssueDiscrete) value)
                .collect(Collectors.toList());
        Integer cnt = 0;
        for (int i = 0; i < this.domainIssues.size(); i++) {
            IssueDiscrete issue = (IssueDiscrete) this.domainIssues.get(i);
            for (int j = 0; j < issue.getNumberOfValues(); j++) {
                mappingValToInt.put(issue.getValue(j), cnt);
                mappingIntToVal.put(cnt, issue.getValue(j));
                mappingIntToIssue.put(cnt, issue);
                cnt++;
            }
        }
    }

    public Integer getIndexByValue(Value val) {
        return this.mappingValToInt.get(val);
    }

    public Value getValueByIndex(Integer idx) {
        return this.mappingIntToVal.get(idx);
    }

    public IssueDiscrete getIssueByIndex(Integer idx) {
        return this.mappingIntToIssue.get(idx);
    }

    public Matrix encode(final BidHistory bidHistory) {
        List<Bid> lBids = bidHistory.getHistory().stream().map(b -> b.getBid()).collect(Collectors.toList());
        Matrix fullMatrix = new Matrix(lBids.size(), mappingValToInt.size());
        for (int i = 0; i < lBids.size(); i++) {
            for (int j = 0; j < lBids.get(i).getIssues().size(); j++) {
                IssueDiscrete currIssue = (IssueDiscrete) lBids.get(i).getIssues().get(j);
                ValueDiscrete val = (ValueDiscrete) lBids.get(i).getValue(currIssue);
                Integer pos = this.getIndexByValue(val);
                fullMatrix.set(i, pos, 1);
            }
        }
        return fullMatrix;
    }

    public Bid decode(final Matrix oneHotRowMatrix) {
        double[] oneHotRow = oneHotRowMatrix.getRowPackedCopy();
        List<Integer> idxOnes = new ArrayList<>();
        HashMap<Integer, Value> bidValues = new HashMap<>();
        for (int i = 0; i < oneHotRow.length; i++) {
            if (oneHotRow[i] == 1) {
                idxOnes.add(i);
            }
        }
        for (Integer idx : idxOnes) {
            IssueDiscrete issue = this.getIssueByIndex(idx);
            Value val = this.getValueByIndex(idx);
            bidValues.put(issue.getNumber(), val);
        }
        Bid result = new Bid(this.session.getDomain(), bidValues);
        return result;
    }

}