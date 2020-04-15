package group10_strategy;

import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
		return enhanceTree(tree.getRoot());
	}

	public BidDetails enhanceTree(Node node) {

		System.out.println("Starting Simulation");
		for (int i=0; i<2; i++) {
			System.out.println(i);
			System.out.println("Nema");
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
		Double parentVisits = 1.0;
		if(node.getParent() != null){
			parentVisits = node.getParent().getNoVisits();
		}
		Double finalParentVisits = parentVisits;

		if(node.getChildren().size() == 0){
			return node;
		}
		return Collections.max(node.getChildren(),
				Comparator.comparing(c -> calculateUCB1(c.getNoVisits(),
						finalParentVisits, c.getScore())));
	}
	
	// node expansion
	private void expandNode(Node node) {
//		System.out.println("I run from expandNode");
		Node newNode = new Node();
		newNode.setParent(node);
		node.getChildren().add(newNode);	
	}
	
	// rollout and backpropagation
	private void rolloutSimBackprop(Node node) throws Exception {
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
		//todo change this with the time that we need till the end
		int count = 0;
		while (ac.determineAcceptabilityBid(nextOpponentBid) != Actions.Accept && count <= 60) {
			nextOpponentBid = om.getBidbyHistory(biddingHistory);
			biddingHistory.add(nextOpponentBid);
			agentBid = bs.determineNextBidFromInput(nextOpponentBid.getBid(), negotiationSession);
			biddingHistory.add(agentBid);
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
		return "MCTS Strateg";
	}
	
}
