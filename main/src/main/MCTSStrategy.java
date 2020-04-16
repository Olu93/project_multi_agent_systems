package main;

import genius.core.Bid;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MCTSStrategy extends OfferingStrategy {
	SmartAcceptanceStrategy ac;
	OMStrategy om;
	GameTree tree;

	public MCTSStrategy() {
		if (this.omStrategy instanceof SmartOpponentOfferingModel) {
			om = (SmartOpponentOfferingModel) this.omStrategy;
		}
	}

	// TODO: I need both the opponent model strategy and the acceptance strategy in
	// here
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
		return enhanceTree(tree.getRoot());
	}

	public BidDetails enhanceTree(Node node) {

		System.out.println("Starting Simulation");
		for (int i = 0; i < 5; i++) {

			System.out.println(i);
			Node selectedNode = selectNode(node);

			// Node 1 (0) -> Node 2
			// Node 1 (3) [1] -> Keep Node 1 -> No expansion
			if (selectedNode.getNoVisits() >= selectedNode.getChildren().size()) {
				System.err.println("Expansion: " + selectedNode.getId() + " - " + selectedNode.getNoVisits());
				expandNode(selectedNode);
			}

			Node explorationCandidate = selectedNode;
			// No expansion -> Take randomly Node 2
			// No expansion -> Take randomly Node 3
			if (explorationCandidate.getChildren().size() > 0) {
				explorationCandidate = selectedNode.getRandomChild();
			}
			rolloutSimBackprop(explorationCandidate);

		}

		Node bestChoiceNode = node.getBestChild();
		tree.setRoot(bestChoiceNode);
		System.out.println("===========> Best choice: " + bestChoiceNode.getBid());
		System.out.println("===========> Best choice: " + bestChoiceNode.getId());
		return bestChoiceNode.getBid();
	}

	// node selection
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

		Double finalParentVisits = node.getParent() != null ? node.getParent().getNoVisits() : 0.0;
		if (node.getChildren().size() == 0) {
			return node;
		}

		return Collections.max(node.getChildren(),
				Comparator.comparing(c -> calculateUCB1(c.getNoVisits(), finalParentVisits, c.getScore())));
	}

	// node expansion
	private void expandNode(Node node) {
		// System.out.println("I run from expandNode");
		// TODO: beautify

		node.addChild(new Node().setParent(node)).addChild(new Node().setParent(node))
				.addChild(new Node().setParent(node));

		// for (int i = 0; i < 3; i++) {
		// Node newNode = new Node();
		// newNode.setParent(node);
		// node.addChild(newNode);
		// }

	}

	// rollout and backpropagation
	private void rolloutSimBackprop(Node node) {
		// TODO: make reset method
		// TODO: Actions might be strategies
		Node iterNodeCopy = node;
		List<BidDetails> oppHistory = new ArrayList<BidDetails>();
		List<BidDetails> agentHistory = new ArrayList<BidDetails>();
		oppHistory.addAll(negotiationSession.getOpponentBidHistory().getHistory());
		agentHistory.addAll(negotiationSession.getOwnBidHistory().getHistory());
		// if (biddingHistory.size() <= 1) {
		// biddingHistory.add(negotiationSession.getMinBidinDomain());
		// biddingHistory.add(negotiationSession.getMinBidinDomain());
		// }

		// BidDetails nextOpponentBid = om.getBidbyHistory(oppHistory, agentHistory);
		// Double score = nextOpponentBid.getMyUndiscountedUtil();
		// biddingHistory.add(nextOpponentBid);
		// BidDetails agentBid = generateRandomBidDetails();
		// node.setBid(agentBid);

		// TODO: change this with the time that we need till the end
		int count = 0;
		BidDetails nextOpponentBid, agentBid;
		Double score;
		do {

			nextOpponentBid = this.om instanceof SmartOpponentOfferingModel
					? ((SmartOpponentOfferingModel) this.om).getBidbyHistory(oppHistory, agentHistory)
					: this.om.getBid(oppHistory);
			oppHistory.add(nextOpponentBid);
			// TODO: use getBid
			// TODO: Change representation of the bid
			agentBid = generateRandomBidDetails();
			agentHistory.add(agentBid);
			// TODO: maybe do a an average on scores
			// TODO: add perturbations and do a rollout multiple times
			score = nextOpponentBid.getMyUndiscountedUtil();
			count++;
		} while (ac.determineAcceptabilityBid(nextOpponentBid) != Actions.Accept && count <= 5);

		System.out.println(score);
		while (iterNodeCopy != null) {
			iterNodeCopy.setScore(iterNodeCopy.getScore() + score);
			iterNodeCopy.setNoVisits(iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy = iterNodeCopy.getParent();
		}
	}

	private BidDetails generateRandomBidDetails() {
		Bid bid = negotiationSession.getDomain().getRandomBid(new Random());
		BidDetails agentBid = new BidDetails(bid, negotiationSession.getUtilitySpace().getUtility(bid));
		return agentBid;
	}

	// Nearest Bid
	// Different Strategy
	// Discretization

	@Override
	public String getName() {
		return "MCTS Strategy";
	}

}
