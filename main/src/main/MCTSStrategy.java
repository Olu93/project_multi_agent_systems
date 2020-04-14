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
	SmartAcceptanceStrategy ac;
	SmartOpponentOfferingModel om;
	BinarySearchStrategy bs;
	GameTree tree;
	
	public MCTSStrategy() {;}
	
	public MCTSStrategy(AcceptanceStrategy acceptanceStrategy, OMStrategy opponentBidModel) {
		ac = (SmartAcceptanceStrategy) acceptanceStrategy;
		om = (SmartOpponentOfferingModel) opponentBidModel;
		tree = new GameTree();
	}

	@Override
	public BidDetails determineOpeningBid() {
		return negotiationSession.getMaxBidinDomain();
	}

	@Override
	public BidDetails determineNextBid() {
		// TODO Auto-generated method stub
		enhanceTree(tree.getRoot());
		return null;
	}

	public BidDetails enhanceTree(Node node) {
		
		for (int i=0; i<100; i++) {
			Node selectedNode = selectNode(node);
			if (selectedNode.getNoVisits() >= selectedNode.getChildren().size()) {
				expandNode(selectedNode);
			}
			Node exploredNode = selectedNode;
			if (exploredNode.getChildren().size() > 0) {
				exploredNode = selectedNode.getRandomChild();
			}
			try {
				rolloutSimBackprop(exploredNode);
			} catch (Exception e) {
				System.out.println("Something went wrong in the rollout");
				e.printStackTrace();
			}
		}
		
		Node bestChoiceNode = node.getBestChild();
        return bestChoiceNode.getBid();
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
	
	// rollout and backpropagation
	private void rolloutSimBackprop(Node node) throws Exception {
		bs = new BinarySearchStrategy();
		Node iterNodeCopy = node;
		BidDetails nextOpponentBid = om.getBid(negotiationSession.getOutcomeSpace(), new Range(negotiationSession.getUtilitySpace().getUtility(negotiationSession.getUtilitySpace().getMinUtilityBid()),
				negotiationSession.getUtilitySpace().getUtility(negotiationSession.getUtilitySpace().getMaxUtilityBid())));
		Double score = nextOpponentBid.getMyUndiscountedUtil();
		List<BidDetails> biddingHistory = new ArrayList<BidDetails>();
		biddingHistory.add(nextOpponentBid);
		BidDetails agentBid = bs.determineNextBidFromInput(nextOpponentBid.getBid());
		node.setBid(agentBid);
		// run until the rest of timesteps
		while (ac.determineAcceptabilityBid(nextOpponentBid) != Actions.Accept) {
			nextOpponentBid = om.getBidbyHistory(biddingHistory);
			biddingHistory.add(nextOpponentBid);
			agentBid = bs.determineNextBidFromInput(nextOpponentBid.getBid());
			biddingHistory.add(agentBid);
			score = nextOpponentBid.getMyUndiscountedUtil();
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
