package main;

import genius.core.Bid;
import genius.core.BidIterator;
import genius.core.analysis.BidPoint;
import genius.core.analysis.BidSpace;
import genius.core.analysis.ParetoFrontier;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import agents.ParetoFrontierPlus;

public class BinarySearchStrategy extends OfferingStrategy {
    Random rand;
    double maxUtilityForBinary;
    double ourMaxBidThatWeGotFromOpponent;
    SmartOpponentOfferingModel oms;

    @Override
    public void init(NegotiationSession negotiationSession, OpponentModel opponentModel, OMStrategy omStrategy,
            Map<String, Double> parameters) throws Exception {
        super.init(negotiationSession, opponentModel, omStrategy, parameters);
        rand = new Random();
        maxUtilityForBinary = 1;
        ourMaxBidThatWeGotFromOpponent = 0.4;
        oms = (SmartOpponentOfferingModel) omStrategy;
    }

    @Override
    public BidDetails determineOpeningBid() {
        System.out.println("======================START 1st ROUND=============================");
        try {
            BidDetails maxBid = negotiationSession.getMaxBidinDomain();
            System.out.println("maxBid: " + maxBid);
            return maxBid;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public BidDetails determineNextBid() {
        Bid opponentLastBid = negotiationSession.getOpponentBidHistory().getLastBidDetails().getBid();
        // try {
        //     BidSpace tmp = new BidSpace(opponentModel.getOpponentUtilitySpace(),
        //             this.negotiationSession.getUtilitySpace(),true);
        //     List<BidPoint> tmp3 = tmp.getParetoFrontier();
        //     BidPoint tmp2 = tmp.getKalaiSmorodinsky();
        //     System.out.println("KALAI: "+tmp2.getBid());;
        // } catch (Exception e) {
        //     // TODO Auto-generated catch block
        //     e.printStackTrace();
        // }
        return determineNextBidFromInput(opponentLastBid, negotiationSession);
        
    }

    public BidDetails determineNextBidFromInput(Bid opponentLastBid, NegotiationSession negotiationSession) {
        System.out.println("negotiationSession " + negotiationSession);

        double myUtilityOfOpponentLastBid = negotiationSession.getUtilitySpace().getUtility(opponentLastBid);
        System.out.println(negotiationSession.getOpponentBidHistory().getHistory());
        System.out.println(negotiationSession.getOpponentBidHistory().getLastBidDetails().getMyUndiscountedUtil());

        if (negotiationSession.getOpponentBidHistory().getHistory().size() > 5) {
            // opponentModel.updateModel(opponentLastBid, negotiationSession.getTime());
            opponentModel.updateModel(opponentLastBid);
            System.out.println("Issue all 0 " + opponentLastBid.getIssues().get(0));
            System.out.println("Issue all 1 " + opponentLastBid.getIssues().get(1));
            System.out.println("Issue all " + opponentLastBid.getIssues());
            System.out.println("Issue Weight " + opponentModel.getWeight(opponentLastBid.getIssues().get(0)));

            // tmp.
        }

        double proposeUtility;
        if(myUtilityOfOpponentLastBid > ourMaxBidThatWeGotFromOpponent){
            System.out.println("Binary Tactic");
            ourMaxBidThatWeGotFromOpponent = myUtilityOfOpponentLastBid;
            maxUtilityForBinary = maxUtilityForBinary - (maxUtilityForBinary - myUtilityOfOpponentLastBid) / 2;
            proposeUtility = maxUtilityForBinary;
        }
        else{
            double targetUtility = maxUtilityForBinary - (maxUtilityForBinary - ourMaxBidThatWeGotFromOpponent) / 2;
            System.out.println("Counter the hardheaded");
            System.out.println("targetUtility: " + targetUtility);
            System.out.println("ourMaxBidThatWeGotFromOpponent: " + ourMaxBidThatWeGotFromOpponent);
            proposeUtility = maxUtilityForBinary - (maxUtilityForBinary - targetUtility) * negotiationSession.getTime();
        }
        ArrayList<BidDetails> myBids = new ArrayList<BidDetails>(getBidsOfUtility(proposeUtility, negotiationSession));
        Bid myBid = getBestBidForSelf(myBids, negotiationSession);
        System.out.println("maxUtilityForBinary: " + maxUtilityForBinary);
        System.out.println("proposeUtility: " + proposeUtility);
        System.out.println("myUtilityOfOpponentLastBid: " + myUtilityOfOpponentLastBid);
        nextBid = new BidDetails(myBid, negotiationSession.getUtilitySpace().getUtility(myBid),
                negotiationSession.getTime());
        System.out.println("nextBid: " + nextBid);
        System.out.println("myBid: " + myBid);
        System.out.println("=============================== " + negotiationSession.getTime() + " ===============================");
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
