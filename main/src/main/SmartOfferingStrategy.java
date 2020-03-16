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

    public Bid getOpponentBidPrediction() {
		return negotiationSession.getDomain().getRandomBid(new Random());
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
        // TODO Auto-generated method stub
        return "SmartOfferingStrategy";
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