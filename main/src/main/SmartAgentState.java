package main;

import genius.core.Bid;
import genius.core.boaframework.SharedAgentState;

public class SmartAgentState extends SharedAgentState {
	private Bid opponentBestBid;
	private Bid opponentPredictedBid;
	
	public void setOpponentBidPrediction(Bid predictedBid) {
		opponentPredictedBid = predictedBid;
	}
	
	public Bid getOpponentBidPrediction() {
		return opponentPredictedBid;
	}
	
	public void setOpponentBestBid(Bid bestBid) {
		this.opponentBestBid = bestBid;
	}
	
	public Bid getBestOpponentBid() {
		Bid tmp = opponentBestBid;
		opponentBestBid = null;
		return tmp;
	}
	
}
