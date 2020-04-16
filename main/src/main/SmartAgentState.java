package main;

import genius.core.Bid;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.SharedAgentState;
import genius.core.utility.AdditiveUtilitySpace;

public class SmartAgentState extends SharedAgentState {
	private NegotiationSession session;
	private UncertaintyUtilityEstimator uncertaintyEstimator;
	
	public SmartAgentState(NegotiationSession session){
		this.session = session;
		// this.uncertaintyEstimator = new UncertaintyUtilityEstimator(this.session);
	}

	public UncertaintyUtilityEstimator getUncertaintyEstimator() {
		return uncertaintyEstimator;
	}

	public void setUncertaintyEstimator(UncertaintyUtilityEstimator uncertaintyEstimator) {
		this.uncertaintyEstimator = uncertaintyEstimator;
	}

	
}