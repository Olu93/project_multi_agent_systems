package main;

import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MCTSStrategy2 extends OfferingStrategy {
	SmartAcceptanceStrategy ac;
	SmartOpponentOfferingModel om;
	BinarySearchStrategy bs;
	GameTree tree;
	
	public MCTSStrategy2() {;}
	
	// TODO: I need both the opponent model strategy and the acceptance strategy in here	
	public MCTSStrategy2(AcceptanceStrategy acceptanceStrategy, OMStrategy opponentBidModel) {
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
		return enhanceTree(tree.getRoot());
	}

	public BidDetails enhanceTree(Node node) {

		System.out.println("Starting Simulation");
		for (int i=0; i<5; i++) {
			
			System.out.println(i);
			Node selectedNode = selectNode(node);
			if (selectedNode.getNoVisits() >= selectedNode.getChildren().size()) {
				System.err.println("Expansion: " + selectedNode.getId() + " - " + selectedNode.getNoVisits());
				expandNode(selectedNode);
			}
			Node exploredNode = selectedNode;
			if (exploredNode.getChildren().size() > 0) {
				exploredNode = selectedNode.getRandomChild();
			}
			rolloutSimBackprop(exploredNode);
		}
		
		Node bestChoiceNode = node.getBestChild();
		tree.setRoot(bestChoiceNode);
        return bestChoiceNode.getBid();
	}
	
	//	node selection
	private Node selectNode(Node rootNode) {
		Node currNode = rootNode;
		System.out.println(currNode);
		while (currNode.getChildren().size() != 0) {
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
		//TODO: bug in here; something is fucked		
		if(node.getChildren().size() == 0 || node.getParent() == null){
			return node;
		}
		
		Double parentVisits = node.getParent().getNoVisits();
		return Collections.max(node.getChildren(),
				Comparator.comparing(c -> calculateUCB1(c.getNoVisits(),
						parentVisits, c.getScore())));
	}
	
	// node expansion
	private void expandNode(Node node) {
//		System.out.println("I run from expandNode");
		Node newNode = new Node();
		System.err.println("Child: " + newNode.getId() + " - " + newNode.getNoVisits());
		newNode.setParent(node);
		node.getChildren().add(newNode);	
	}
	
	// rollout and backpropagation
	private void rolloutSimBackprop(Node node)  {
		// TODO: make reset method		
		bs = new BinarySearchStrategy();
		Node iterNodeCopy = node;
		List<BidDetails> biddingHistory = new ArrayList<BidDetails>();
		biddingHistory = negotiationSession.getOpponentBidHistory().getHistory();
		if(biddingHistory.size() <= 1) {
			biddingHistory.add(negotiationSession.getMinBidinDomain());
			biddingHistory.add(negotiationSession.getMinBidinDomain());
		}
		BidDetails nextOpponentBid = om.getBidbyHistory(biddingHistory);
		Double score = nextOpponentBid.getMyUndiscountedUtil();
		biddingHistory.add(nextOpponentBid);
		BidDetails agentBid = bs.determineNextBidFromInput(nextOpponentBid.getBid(), negotiationSession);
		node.setBid(agentBid);
		//TODO: change this with the time that we need till the end
		int count = 0;
		while (ac.determineAcceptabilityBid(nextOpponentBid) != Actions.Accept && count <= 5) {
			nextOpponentBid = om.getBidbyHistory(biddingHistory);
			biddingHistory.add(nextOpponentBid);
			// TODO: use getBid
			agentBid = bs.determineNextBidFromInput(nextOpponentBid.getBid(), negotiationSession);
			biddingHistory.add(agentBid);
			// TODO: maybe do a an average on scores
			// TODO: add perturbations and do a rollout multiple times
			score = nextOpponentBid.getMyUndiscountedUtil();
			count ++;
		}
		System.out.println(score);
		while (iterNodeCopy != null) {
			iterNodeCopy.setScore(iterNodeCopy.getScore() + score);
			iterNodeCopy.setNoVisits(iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy = iterNodeCopy.getParent();
		}
	}
	
	@Override
	public String getName() {
		return "MCTS Strategy";
	}
	
}
