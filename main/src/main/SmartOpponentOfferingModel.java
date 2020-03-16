package main;

import java.util.List;

import genius.core.bidding.BidDetails;
import genius.core.boaframework.OMStrategy;

/**
 * SmartOpponentOfferingModel
 */
public class SmartOpponentOfferingModel extends OMStrategy {

    @Override
    public BidDetails getBid(List<BidDetails> bidsInRange) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean canUpdateOM() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getName() {
        return SmartComponentNames.SMART_OPPONENT_BIDDING_STRATEGY.toString();
    }

    
}