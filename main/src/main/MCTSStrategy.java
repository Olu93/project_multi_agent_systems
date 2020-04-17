package main;

import genius.core.Bid;
import genius.core.BidIterator;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.boaframework.OutcomeSpace;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.misc.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MCTSStrategy extends OfferingStrategy {
	/**
	 *
	 */
	private Bid opponentBestBid;

	private static final double UPPER_BOUND = 1.0;
	SmartAcceptanceStrategy ac;
	OMStrategy om;
	GameTree tree = new GameTree();
	OutcomeSpace outcomeSpace;
	private final Double DISCOUNT_FACTOR = 0.90;
	private Double lowerBound = 0.95;
	private final Boolean IS_VERBOSE = false;

	public void setOpponentBestBid(Bid bestBid) {
		this.opponentBestBid = bestBid;
	}

	public Bid getBestOpponentBid() {
		Bid tmp = opponentBestBid;
		opponentBestBid = null;
		return tmp;
	}

	// #endregion

	@Override
	public void init(NegotiationSession negotiationSession, OpponentModel opponentModel, OMStrategy omStrategy,
			Map<String, Double> parameters) throws Exception {
		super.init(negotiationSession, opponentModel, omStrategy, parameters);
		outcomeSpace = new SortedOutcomeSpace(negotiationSession.getUtilitySpace());
		negotiationSession.setOutcomeSpace(outcomeSpace);
		ac = new SmartAcceptanceStrategy(negotiationSession, this, opponentModel ,parameters);
	}

	@Override
	public BidDetails determineOpeningBid() {
		return negotiationSession.getMaxBidinDomain();
	}

	@Override
	public BidDetails determineNextBid() {
		BidDetails nextBid = this.getNextBid();
		if (nextBid != null) {
			this.setNextBid(null);
			return nextBid;
		}
		return enhanceTree(tree.getRoot());
	}

	@Override
	public BidDetails getNextBid() {
		BidDetails previousOpponentBestBid = this.nextBid;
		return previousOpponentBestBid;
	}

	public BidDetails enhanceTree(final Node node) {
		System.out.println("Starting Simulation");
		for (int i = 0; i < 10; i++) {

			// System.out.println(i);
			final Node selectedNode = selectNode(node);

			// Node 1 (0) -> Node 2
			// Node 1 (3) [1] -> Keep Node 1 -> No expansion
			if (selectedNode.getNoVisits() >= selectedNode.getChildren().size()) {
				// System.err.println("Expansion: " + selectedNode.getId() + " - " +
				// selectedNode.getNoVisits());
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

		final Node bestChoiceNode = node.getBestChild();
		tree.setRoot(bestChoiceNode);
		System.out.println("===========> Best choice: " + bestChoiceNode.getId());
		lowerBound = 1 - (negotiationSession.getTime() / 5);

		return bestChoiceNode.getBid();
	}

	// node selection
	private Node selectNode(final Node rootNode) {
		Node currNode = rootNode;

		if (IS_VERBOSE)
			System.out.println(currNode);
		while (currNode.getChildren().size() != 0) {
			currNode = MCTSStrategy.getBestNode(currNode);
		}

		return currNode;
	}

	public static Double calculateUCB1(final Double nodeVisits, final Double parentVisits, final Double score) {
		if (nodeVisits == 0) {
			return Double.MAX_VALUE;
		}
		return (score / nodeVisits) + Math.sqrt(2 * (Math.log(parentVisits) / nodeVisits));
	}

	public static Double calculateUCB1(final Double nodeVisits, final Double parentVisits, final Double score,
			final Double c) {
		if (nodeVisits == 0) {
			return Double.MAX_VALUE;
		}
		return (score / nodeVisits) + Math.sqrt(c * (Math.log(parentVisits) / nodeVisits));
	}

	public static Node getBestNode(final Node node) {

		final Double finalParentVisits = node.getParent() != null ? node.getParent().getNoVisits() : 0.0;
		if (node.getChildren().size() == 0) {
			return node;
		}

		return Collections.max(node.getChildren(),
				Comparator.comparing(c -> calculateUCB1(c.getNoVisits(), finalParentVisits, c.getScore())));
	}

	// node expansion
	private void expandNode(final Node node) {
		// System.out.println("I run from expandNode");
		// TODO: beautify
		node.addChild(new Node().setParent(node).setBid(getBidInRange(lowerBound, UPPER_BOUND)))
				.addChild(new Node().setParent(node).setBid(getBidInRange(lowerBound, UPPER_BOUND)))
				.addChild(new Node().setParent(node).setBid(getBidInRange(lowerBound, UPPER_BOUND)));

	}

	// rollout and backpropagation
	private void rolloutSimBackprop(final Node node) {
		// TODO: make reset method
		// TODO: Actions might be strategies
		Node iterNodeCopy = node;
		final List<BidDetails> oppHistory = new ArrayList<BidDetails>();
		final List<BidDetails> agentHistory = new ArrayList<BidDetails>();
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
		BidDetails nextOpponentBid, agentCurrentBid;
		final List<Double> scores = new ArrayList<>();
		Double avgScore = 0.0;
		BidDetails agentNextBid;
		do {
			// if (negotiationSession.getTime() > 0.25) {
			if (negotiationSession.getTime() > 0.10) {
				nextOpponentBid = this.omStrategy instanceof SmartOpponentOfferingModel
						? ((SmartOpponentOfferingModel) this.omStrategy).getBidbyHistory(oppHistory, agentHistory)
						: this.omStrategy.getBid(oppHistory);

			} else {
				nextOpponentBid = getBidInRange(0.0, 0.5);
			}
			// System.out.println(nextOpponentBid.getBid());

			oppHistory.add(nextOpponentBid);
			agentCurrentBid = getBidInRange(lowerBound, UPPER_BOUND);
			agentHistory.add(agentCurrentBid);
			scores.add(negotiationSession.getUtilitySpace().getUtility(nextOpponentBid.getBid()) * Math.pow(DISCOUNT_FACTOR, count));
			count++;
			agentNextBid = this.outcomeSpace.getBidNearUtility(this.negotiationSession.getOwnBidHistory().getLastBidDetails().getMyUndiscountedUtil());
		} while (ac.determineAcceptabilityBid(nextOpponentBid, agentNextBid) != Actions.Accept && count <= 5);

		avgScore = scores.parallelStream().mapToDouble(val -> val).average().getAsDouble();
		// TODO: Average across multiple simulations
		while (iterNodeCopy != null) {
			// (((X1+X2)/2*2)+X3)/3
			double backpropVal = (iterNodeCopy.getScore() * iterNodeCopy.getNoVisits() + avgScore)
					/ (iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy.setScore(backpropVal);
			iterNodeCopy.setNoVisits(iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy = iterNodeCopy.getParent();
		}
	}

	private BidDetails generateRandomBidDetails() {
		final Bid bid = negotiationSession.getDomain().getRandomBid(new Random());
		final BidDetails agentBid = new BidDetails(bid, negotiationSession.getUtilitySpace().getUtility(bid));
		return agentBid;
	}

	// Nearest Bid
	// Different Strategy
	// Discretization

	public BidDetails getBidInRange(Double lowerBound, Double upperBound) {
		Random rand = new Random();
		// List<BidDetails> candidateBids = new ArrayList<BidDetails>();
		// BidIterator myBidIterator = new
		// BidIterator(negotiationSession.getUtilitySpace().getDomain());
		// while (myBidIterator.hasNext()) {
		// Bid b = myBidIterator.next();
		// try {
		// double util = negotiationSession.getUtilitySpace().getUtility(b);
		// if (util >= lowerBound && util <= upperBound) {
		// candidateBids.add(new BidDetails(b, util));
		// }
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		List<BidDetails> candidateBids = outcomeSpace.getBidsinRange(new Range(lowerBound, 1.0));

		BidDetails result = candidateBids.size() > 0 ? candidateBids.get(rand.nextInt(candidateBids.size()))
				: negotiationSession.getMaxBidinDomain();
		return result;
	}

	@Override
	public String getName() {
		return "MCTS Strategy";
	}

	public BidDetails getNextOpponentBid() {
		return null;
	}

}
