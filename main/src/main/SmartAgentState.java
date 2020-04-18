package main;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.SharedAgentState;
import genius.core.utility.AdditiveUtilitySpace;

public class SmartAgentState extends SharedAgentState {
	private NegotiationSession session;
	private UncertaintyUtilitySpace uncertaintyEstimator;
	
	public SmartAgentState(NegotiationSession session){
		this.session = session;
		// this.uncertaintyEstimator = new UncertaintyUtilityEstimator(this.session);
	}

	public UncertaintyUtilitySpace getUncertaintyEstimator() {
		return uncertaintyEstimator;
	}

	public void setUncertaintyEstimator(UncertaintyUtilitySpace uncertaintyEstimator) {
		this.uncertaintyEstimator = uncertaintyEstimator;
	}

	
}