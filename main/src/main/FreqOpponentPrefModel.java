package main;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import misc.IssueValuePair;

public class FreqOpponentPrefModel extends OpponentModel {
	private HashMap<IssueValuePair, Double> frequencies = new HashMap<IssueValuePair, Double>();
	private HashMap<IssueDiscrete, Double> invEntropies = new HashMap<IssueDiscrete, Double>();
	private NegotiationSession session;
	private List<IssueDiscrete> domainIssues;

	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
		System.out.println("USING - SmartFreqOpponentModel");
		this.session = negotiationSession;
		this.domainIssues = negotiationSession.getIssues().stream().map(issue -> (IssueDiscrete) issue)
				.collect(Collectors.toList());
		for (IssueDiscrete issueDiscrete : this.domainIssues) {
			issueDiscrete.getValues().forEach(value -> frequencies.put(new IssueValuePair(issueDiscrete, value), 0.0));
			invEntropies.put(issueDiscrete, 0.0);
		}
	}

	private Double getEntropy(IssueDiscrete issue, HashMap<IssueValuePair, Double> frequencyHash) {
		List<IssueValuePair> allValuePairs = frequencyHash.keySet().stream().filter(ivp -> issue == ivp.getIssue())
				.collect(Collectors.toList());
		Double entropy = 0.0;
		Double sum = frequencyHash.values().stream().reduce(0.0, Double::sum);

		for (IssueValuePair ivp : allValuePairs) {
			Double probability = new Double(frequencyHash.get(ivp)) / sum;
			Double tmp = probability > 0.0 ? (-(probability * Math.log(probability))) : 0.0;
			entropy += tmp;
		}

		return 1-entropy;
	}

	private Double softmax(Double entropy, Double sumOfEntropies) {
		return Math.exp(entropy) / sumOfEntropies;
	}

	@Override
	public double getWeight(Issue issue) {
		// System.out.println("USEDGET_WHEIGHT HERE!");
		// Double sumOfEntropies = this.entropies.values().stream().mapToDouble(Math::exp).sum();
		return this.invEntropies.get(issue);
		// return this.entropies.get(issue);
	}

	@Override
	public void updateModel(Bid opponentBid) {
		// TODO Auto-generated method stub
		this.updateModel(opponentBid, 0);
	}

	@Override
	protected void updateModel(Bid bid, double time) {
		for (IssueValuePair ivp : this.frequencies.keySet()) {
			IssueDiscrete issue = ivp.getIssue();
			if (bid.containsValue(issue, ivp.getValue())) {
				frequencies.put(ivp, frequencies.get(ivp) + (1000*session.getTime()));
				this.invEntropies.put(issue, getEntropy(issue, frequencies));
			}

		}
		this.opponentUtilitySpace.setWeights(bid.getIssues(), this.getIssueWeights());
	}

	@Override
	public double[] getIssueWeights() {
		List<Issue> issues = this.session.getUtilitySpace().getDomain().getIssues();
		double estimatedIssueWeights[] = new double[issues.size()];
		int i = 0;
		for (Issue issue : issues) {
			estimatedIssueWeights[i] = getWeight(issue);
			i++;
		}
		// System.out.println(Arrays.toString(estimatedIssueWeights));
		return estimatedIssueWeights;
	}

	@Override
	public double getBidEvaluation(Bid bid) {
		try {
			return this.opponentUtilitySpace.getUtility(bid);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public String getName() {
		return "FreqOpponentPrefModel";
	}

}
