package group10_strategy;

import genius.core.Bid;
import genius.core.BidIterator;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OfferingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BinarySearchStrategy extends OfferingStrategy {
	Random rand;
    double maxUtilityForBinary;
    double ourMaxBidThatWeGotFromOpponent;
	
	public BinarySearchStrategy() {
		rand = new Random();
	    maxUtilityForBinary = 1;
	    ourMaxBidThatWeGotFromOpponent = 0.4;
	}
    

    @Override
    public BidDetails determineOpeningBid() {
        System.out.println("======================START 1st ROUND=============================");
        try {
            BidDetails maxBid = negotiationSession.getMaxBidinDomain();
            System.out.println("maxBid: " +  maxBid);
            return maxBid;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public BidDetails determineNextBid() {
        Bid opponentLastBid = negotiationSession.getOpponentBidHistory().getLastBidDetails().getBid();
        return determineNextBidFromInput(opponentLastBid, negotiationSession);
    }
    
    public BidDetails determineNextBidFromInput(Bid opponentLastBid, NegotiationSession negotiationSession) {
//	    System.out.println("negotiationSession "+ negotiationSession);
    	double myUtilityOfOpponentLastBid = negotiationSession.getUtilitySpace().getUtility(opponentLastBid);
//        System.out.println(negotiationSession.getOpponentBidHistory().getHistory());
//        System.out.println(negotiationSession.getOpponentBidHistory().getLastBidDetails().getMyUndiscountedUtil());
        double proposeUtility;
        if(myUtilityOfOpponentLastBid > ourMaxBidThatWeGotFromOpponent){
            ourMaxBidThatWeGotFromOpponent = myUtilityOfOpponentLastBid;
            maxUtilityForBinary = maxUtilityForBinary - (maxUtilityForBinary - myUtilityOfOpponentLastBid) / 2;
            proposeUtility = maxUtilityForBinary;
//            System.out.println("Binary Tactic");
        }
        else{
            double targetUtility = maxUtilityForBinary - (maxUtilityForBinary - ourMaxBidThatWeGotFromOpponent) / 2;
//            System.out.println("Counter the hardheaded");
//            System.out.println("targetUtility: " + targetUtility);
//            System.out.println("ourMaxBidThatWeGotFromOpponent: " + ourMaxBidThatWeGotFromOpponent);
            proposeUtility = maxUtilityForBinary - (maxUtilityForBinary - targetUtility) * negotiationSession.getTime();
        }
//        System.out.println(negotiationSession.getTime());

        ArrayList<BidDetails> myBids = new ArrayList<BidDetails>(getBidsOfUtility(proposeUtility, negotiationSession));
        Bid myBid = getBestBidForSelf(myBids, negotiationSession);
//        System.out.println("maxUtilityForBinary: " + maxUtilityForBinary);
//        System.out.println("proposeUtility: " + proposeUtility);
//        System.out.println("myUtilityOfOpponentLastBid: " + myUtilityOfOpponentLastBid);
        nextBid = new BidDetails(myBid, negotiationSession.getUtilitySpace().getUtility(myBid),
                negotiationSession.getTime());
//        System.out.println("nextBid: " + nextBid);
//        System.out.println("myBid: " + myBid);
//        System.out.println("===================================================");
        return nextBid;
    }

    private Bid getBestBidForSelf(ArrayList<BidDetails> bids, NegotiationSession negotiationSession) {
        Bid maxBid = bids.get(0).getBid();
        for (int i = 0; i < bids.size(); i++) {
            try {
                if (negotiationSession.getUtilitySpace().getUtility(bids.get(i).getBid()) > negotiationSession
                        .getUtilitySpace().getUtility(maxBid)) {
                    maxBid = bids.get(i).getBid();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return maxBid;
    }

    /**
     * Get all bids in a utility range.
     */
    private List<BidDetails> getBidsOfUtility(double target, NegotiationSession negotiationSession) {
        if (target > 1)
            target = 1;

        double min = target * 0.98;
        double max = target + 0.04;
        do {
            max += 0.01;
            List<BidDetails> bids = getBidsOfUtility(min, max, negotiationSession);
            int size = bids.size();
            // We need at least 2 bids. Or if max = 1, then we settle for 1 bid.
            if (size > 1 || (max >= 1 && size > 0)) {
                return bids;
            }
        } while (max <= 1);

        // Weird if this happens
        ArrayList<BidDetails> best = new ArrayList<BidDetails>();
        Bid maxBid = negotiationSession.getMaxBidinDomain().getBid();
        try {
            best.add(new BidDetails(maxBid, negotiationSession.getUtilitySpace().getUtility(maxBid)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return best;
    }

    private List<BidDetails> getBidsOfUtility(double lowerBound, double upperBound, NegotiationSession negotiationSession) {
        // In big domains, we need to find only this amount of bids around the
        // target. Should be at least 2.
        final int limit = 2;

        List<BidDetails> bidsInRange = new ArrayList<BidDetails>();
        BidIterator myBidIterator = new BidIterator(negotiationSession.getUtilitySpace().getDomain());
        while (myBidIterator.hasNext()) {
            Bid b = myBidIterator.next();
            try {
                double util = negotiationSession.getUtilitySpace().getUtility(b);
                if (util >= lowerBound && util <= upperBound) {
                    bidsInRange.add(new BidDetails(b, util));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return bidsInRange;
    }

    @Override
    public String getName() {
        return "Binary Search Offering Strategy";
    }
}
