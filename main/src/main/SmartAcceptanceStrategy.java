package main;

import java.util.List;

import genius.core.Bid;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.SharedAgentState;
import genius.core.uncertainty.OutcomeComparison;
import genius.core.uncertainty.UserModel;

public class SmartAcceptanceStrategy extends AcceptanceStrategy {
	
	@Override
	public Actions determineAcceptability() {
		Bid agentNextBid = offeringStrategy.getNextBid(). getBid();
		Bid opponentBid = negotiationSession.getOpponentBidHistory().getLastBid();
		
		if (opponentBid == null || agentNextBid == null) {
			return Actions.Reject;
		}
		UserModel userModel = negotiationSession.getUserModel();
		List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();
		if (bidOrder.indexOf(agentNextBid) <= bidOrder.indexOf(opponentBid)) {	
			Bid opponentBestBid = negotiationSession.getOpponentBidHistory().getBestBidDetails().getBid(); // Requires a copy of the history
			if(bidOrder.indexOf(opponentBid) < bidOrder.indexOf(opponentBestBid)){
				// TODO: pop bid
				if(helper.getName() == "SmartAgentState"){
					SmartAgentState myhelper = (SmartAgentState) helper;
					myhelper.setOpponentBestBid(opponentBestBid);
				}
				return Actions.Reject;
			} 
			if(helper.getName() == "SmartAgentState"){
				SmartAgentState myhelper = (SmartAgentState) helper;
				if (bidOrder.indexOf(agentNextBid) < bidOrder.indexOf(myhelper.getOpponentBidPrediction())) {					
					return Actions.Reject;
				}
			}
			return Actions.Accept;
		}
		return Actions.Reject;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Smart Acceptance Strategy: This strategy employs a number of smart heuristics for acceptance";
	}

}
