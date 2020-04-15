package group10_strategy;

import java.util.HashMap;
import java.util.Map;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Issue;
import genius.core.issue.Value;

public class FreqOpponentPrefModel extends OpponentModel {
	private HashMap<Issue, Double> entropies = new HashMap<Issue, Double>();
	private HashMap<Issue, HashMap<Value, Integer>> frequencyHash = new HashMap<Issue, HashMap<Value,Integer>>(); 
	
	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
		//	TODO if fail, initialize HashMap here
	}
	
	private Double getEntropy(HashMap<Value, Integer> freq) {
		Double entropy = (double) 1;
		Integer sum = freq.values().stream().reduce(0, Integer::sum);
		
		for (Value val : freq.keySet()) {
			Double probability = (double) freq.get(val) / sum;
			entropy += -(probability * Math.log(probability));
		}
		
		return 1 - entropy;
	}
	
	private Double softmax(Double entropy, Double sumOfEntropies) {
	    return Math.exp(entropy) / sumOfEntropies;
	}
	
	@Override
	public double getWeight(Issue issue) {
		System.out.println("USEDGET_WHEIGHT HERE!");
		Double sumOfEntropies = this.entropies.values().stream().mapToDouble(Math::exp).sum();
		return softmax(this.entropies.get(issue), sumOfEntropies);
	}

	@Override
	protected void updateModel(Bid bid, double time) {
		for (Issue issue : bid.getIssues()) {
			if (!this.frequencyHash.containsKey(issue)) {
				this.frequencyHash.put(issue, new HashMap<Value, Integer>());
			}
			if (!this.frequencyHash.get(issue).containsKey(bid.getValue(issue))) {
				this.frequencyHash.get(issue).put(bid.getValue(issue), 0);
			}
			System.out.println("SEXXXXXXXXXXXXXXXXXXTONIK2:");
//			System.out.println(issue);
//			System.out.println(bid);
//			System.out.println(bid.getValue(issue));
//			System.out.println(this.frequencyHash.get(issue));
//			System.out.println(this.frequencyHash.get(issue).get(bid.getValue(issue)));
			Integer incrementedValue = this.frequencyHash.get(issue).get(bid.getValue(issue)) + 1;
			this.frequencyHash.get(issue).put(bid.getValue(issue), incrementedValue);
			this.entropies.put(issue, getEntropy(this.frequencyHash.get(issue)));
		}
		this.opponentUtilitySpace.setWeights(bid.getIssues(), getIssueWeights());
	}
	
	@Override
	public String getName() {
		return "FreqOpponentPrefModel";
	}

}
