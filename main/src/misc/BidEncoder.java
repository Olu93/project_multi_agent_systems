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
	private HashMap<IssueValuePair, Integer> mappingIVPToInt = new HashMap<>();
	private HashMap<Integer, IssueDiscrete> mappingIntToIssue = new HashMap<>();
	private HashMap<Integer, IssueValuePair> mappingIntToIVP = new HashMap<>();

	public BidEncoder(NegotiationSession session) {
		this.session = session;
		if (this.session.getUserModel() == null) {
			System.out.println(this.session.getUserModel().getBidRanking().toString());
			System.out.println(this.session.getUserModel().getBidRanking().getMaximalBid().toString());
			System.out.println(this.session.getUserModel().getBidRanking().getMaximalBid().getIssues().toString());
//			System.out.println(this.session.getUserModel());
			this.domainIssues = this.session.getUserModel().getBidRanking().getMaximalBid().getIssues().parallelStream()
					.map(value -> (IssueDiscrete) value).collect(Collectors.toList());
		} else {
			this.domainIssues = this.session.getIssues().parallelStream().map(value -> (IssueDiscrete) value)
					.collect(Collectors.toList());
		}

		System.out.println("SEX2");
		System.out.println(this.domainIssues.toString());
		IssueValuePair ivp = null;
		Integer cnt = 0;
		for (int i = 0; i < this.domainIssues.size(); i++) {
			IssueDiscrete issue = (IssueDiscrete) this.domainIssues.get(i);
			for (int j = 0; j < issue.getNumberOfValues(); j++) {
				ivp = new IssueValuePair(issue, issue.getValue(j));
				mappingIVPToInt.put(ivp, cnt);
				mappingIntToIVP.put(cnt, ivp);
				mappingIntToIssue.put(cnt, issue);
				cnt++;
				System.out.println("TONIK22");
				System.out.println(ivp.toString());
			}
		}
	}

	public Integer getIndexByIVP(IssueValuePair val) {
		IssueValuePair key = this.mappingIVPToInt.keySet().stream().filter(pair -> pair.equals(val)).findFirst()
				.orElse(null);
		return this.mappingIVPToInt.get(key);
	}

	public IssueValuePair getIVPByIndex(Integer idx) {
		return this.mappingIntToIVP.get(idx);
	}

	public IssueDiscrete getIssueByIndex(Integer idx) {
		return this.mappingIntToIssue.get(idx);
	}

	public Matrix encode(final BidHistory bidHistory) {
		List<Bid> lBids = bidHistory.getHistory().stream().map(b -> b.getBid()).collect(Collectors.toList());
		Matrix fullMatrix = new Matrix(lBids.size(), mappingIVPToInt.size());
		for (int i = 0; i < lBids.size(); i++) {
			for (int j = 0; j < lBids.get(i).getIssues().size(); j++) {
				IssueDiscrete currIssue = (IssueDiscrete) lBids.get(i).getIssues().get(j);
				ValueDiscrete val = (ValueDiscrete) lBids.get(i).getValue(currIssue);
				Integer pos = this.getIndexByIVP(new IssueValuePair(currIssue, val));
				System.out.println(currIssue.toString() + " " + val.toString());
				System.out.println(pos);
				// if (pos > 21) {
				// System.out.println("");
				// }
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
			Value val = this.getIVPByIndex(idx).getValue();
			bidValues.put(issue.getNumber(), val);
		}
		Bid result = new Bid(this.session.getDomain(), bidValues);
		return result;
	}

}