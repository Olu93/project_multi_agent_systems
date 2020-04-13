package main;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import genius.core.Bid;
import genius.core.BidIterator;
import genius.core.NegoRound;
import genius.core.NegoTurn;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.misc.Range;

public class MCTSStrategy extends OfferingStrategy{
	AcceptanceStrategy ac;
	OMStrategy om;
	
	public MCTSStrategy() {;}
	
	public MCTSStrategy(AcceptanceStrategy acceptanceStrategy, OMStrategy opponentBidModel) {
		ac = acceptanceStrategy;
		om = opponentBidModel;
	}

	@Override
	public BidDetails determineOpeningBid() {
		return negotiationSession.getMaxBidinDomain();
	}

	@Override
	public BidDetails determineNextBid() {
		// TODO Auto-generated method stub
		return null;
	}
	
	private List<BidDetails> getBidinRange(double lowerBound, double upperBound) {

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

	public Bid simulateNextBid() {
		GameTree tree = new GameTree();
		Node root = tree.getRoot();
		
		return null;
	}
	
	//	node selection
	private Node selectNode(Node rootNode) {
		Node currNode = rootNode;
		while (!currNode.getChildren().isEmpty()) {
			currNode = MCTSStrategy.getBestNode(currNode);
		}
		return currNode;
	}
	
	public static Double calculateUCB1(Double nodeVisits, Double parentVisits, Double score) {
		if (nodeVisits == 0) {
			return Double.MAX_VALUE;
		}
		return (score / nodeVisits) + Math.sqrt(2 * (Math.log(parentVisits) / nodeVisits));
	}
	
	public static Double calculateUCB1(Double nodeVisits, Double parentVisits, Double score, Double c) {
		if (nodeVisits == 0) {
			return Double.MAX_VALUE;
		}
		return (score / nodeVisits) + Math.sqrt(c * (Math.log(parentVisits) / nodeVisits));
	}
	
	public static Node getBestNode(Node node) {
		Double parentVisits = node.getParent().getNoVisits();
		return Collections.max(node.getChildren(),
				Comparator.comparing(c -> calculateUCB1(c.getNoVisits(),
						parentVisits, c.getScore())));
	}
	
	// node expansion
	private void expandNode(Node node) {
		Node newNode = new Node();
		newNode.setParent(node);
		node.getChildren().add(newNode);	
	}
	
	private void rolloutSimBackprop(Node node) throws Exception {
		Node iterNodeCopy = node;
		// I need: method that would decide if a bid is acceptable or not
		// A way to update and reuse the opponent bidding strategy
		BidDetails nextOpponentBid = om.getBid(negotiationSession.getOutcomeSpace(), new Range(negotiationSession.getUtilitySpace().getUtility(negotiationSession.getUtilitySpace().getMinUtilityBid()),
				negotiationSession.getUtilitySpace().getUtility(negotiationSession.getUtilitySpace().getMaxUtilityBid())));
		Double score = nextOpponentBid.getMyUndiscountedUtil();
		while (ac.determineAcceptability() != Actions.Accept) {
			nextOpponentBid = om.getBid(negotiationSession.getOutcomeSpace(), new Range(negotiationSession.getUtilitySpace().getUtility(negotiationSession.getUtilitySpace().getMinUtilityBid()),
					negotiationSession.getUtilitySpace().getUtility(negotiationSession.getUtilitySpace().getMaxUtilityBid())));
			score = nextOpponentBid.getMyUndiscountedUtil();
			// my bid too
		}
		while (iterNodeCopy != null) {
			iterNodeCopy.setScore(iterNodeCopy.getScore() + score);
			iterNodeCopy.setNoVisits(iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy = iterNodeCopy.getParent();
		}
	}
	
	@Override
	public String getName() {
		return "MCTS Strateg";
	}
	
}
