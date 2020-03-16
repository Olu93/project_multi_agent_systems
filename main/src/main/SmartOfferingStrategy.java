package main;

import java.util.Random;

import genius.core.Bid;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.OfferingStrategy;

/**
 * SmartOfferingStrategy
 */
public class SmartOfferingStrategy extends OfferingStrategy {
	private Bid opponentBestBid;

    public BidDetails getOpponentBidPrediction() {
        Bid predictedBid = negotiationSession.getDomain().getRandomBid(new Random());
        Double utility = negotiationSession.getUtilitySpace().getUtility(predictedBid);
		return new BidDetails(predictedBid, utility);
	}
	
	public void setOpponentBestBid(Bid bestBid) {
		this.opponentBestBid = bestBid;
	}
	
	public Bid getBestOpponentBid() {
		Bid tmp = opponentBestBid;
		opponentBestBid = null;
		return tmp;
    }
    
    @Override
    public String getName() {
        return SmartComponentNames.SMART_BIDDING_STRATEGY.name();
    }

    @Override
    public BidDetails determineOpeningBid() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BidDetails determineNextBid() {
        // TODO Auto-generated method stub
        return null;
    }

    
}