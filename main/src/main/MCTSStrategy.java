package main;

import genius.core.Bid;
import genius.core.BidHistory;
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
import java.util.stream.Collectors;

public class MCTSStrategy extends OfferingStrategy {
	/**
	 *
	 */
//	private Bid opponentBestBid;

	private static final double UPPER_BOUND = 1.0;
	SmartAcceptanceStrategy ac;
	OMStrategy om;
	GameTree tree = new GameTree();
	OutcomeSpace outcomeSpace;
	private final Double DISCOUNT_FACTOR = 0.90;
	private Double lowerBound = 1.0;
	private final Boolean IS_VERBOSE = false;
	BidDetails lastSetBid;
//	private BinarySearchStrategy offerer;

//	public void setOpponentBestBid(Bid bestBid) {
//		this.opponentBestBid = bestBid;
//	}
//
//	public Bid getBestOpponentBid() {
//		Bid tmp = opponentBestBid;
//		opponentBestBid = null;
//		return tmp;
//	}

	// #endregion

	@Override
	public void init(NegotiationSession negotiationSession, OpponentModel opponentModel, OMStrategy omStrategy,
			Map<String, Double> parameters) throws Exception {
		super.init(negotiationSession, opponentModel, omStrategy, parameters);
		outcomeSpace = new SortedOutcomeSpace(negotiationSession.getUtilitySpace());
		negotiationSession.setOutcomeSpace(outcomeSpace);
		ac = new SmartAcceptanceStrategy(negotiationSession, this, opponentModel, parameters);
//		offerer = new BinarySearchStrategy();
//		offerer.init(negotiationSession, opponentModel, omStrategy, parameters);
	}

	@Override
	public BidDetails determineOpeningBid() {
		lastSetBid = negotiationSession.getMaxBidinDomain();
		return lastSetBid;
	}

	@Override
	public BidDetails determineNextBid() {
//		opponentModel.updateModel(negotiationSession.getOpponentBidHistory().getLastBid());
//		System.out.println("SEX");
		BidDetails nextBid = this.getNextBid();
		if (nextBid != null && !nextBid.equals(lastSetBid)) {
			lastSetBid = nextBid;
			this.setNextBid(null);
			return nextBid;
		}
//		System.out.println("TONIK");
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
		lowerBound = updateLowerBound();
		return bestChoiceNode.getBid();
	}

	private double updateLowerBound() {
		return 1 - (negotiationSession.getTime() / 5);
	}

	private double updateLowerBoundSigmoid() {
		Double x = negotiationSession.getTime() * 100;
		Double substract = 1 / (1 + Math.pow(Math.E, -0.1 * (x - 90)));
		if (true)
			System.out.println("Substracts: " + substract + " after " + Math.round(negotiationSession.getTime() * 100)
					+ "% of the time");
		System.out.println("Current lower bound: " + (1 - substract));
		return 1 - substract;
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
		node.addChild(new Node().setParent(node).setBid(chooseBid()))
				.addChild(new Node().setParent(node).setBid(chooseBid()))
				.addChild(new Node().setParent(node).setBid(chooseBid()));

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
			if (negotiationSession.getTime() > 0.1) {
				nextOpponentBid = this.omStrategy instanceof SmartOpponentOfferingModel
						? ((SmartOpponentOfferingModel) this.omStrategy).getBidbyHistory(oppHistory, agentHistory)
						: this.omStrategy.getBid(oppHistory);

			} else {
				nextOpponentBid = getBidInRange(0.0, 0.5);
			}
			// System.out.println(nextOpponentBid.getBid());

			oppHistory.add(nextOpponentBid);
			agentCurrentBid = chooseBid();
			agentHistory.add(agentCurrentBid);
			double opponentUtility = negotiationSession.getUtilitySpace().getUtility(nextOpponentBid.getBid());
			scores.add(opponentUtility * Math.pow(DISCOUNT_FACTOR, count));
			count++;
			agentNextBid = this.outcomeSpace.getBidNearUtility(
					this.negotiationSession.getOwnBidHistory().getLastBidDetails().getMyUndiscountedUtil());
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
	public BidDetails chooseBid() {
		return getBidInRange(lowerBound, UPPER_BOUND);
		// return getBestMutualBidInRange(lowerBound, UPPER_BOUND);
	}

	public BidDetails getBidInRange(Double lowerBound, Double upperBound) {
		Random rand = new Random();
		List<BidDetails> candidateBids = outcomeSpace.getBidsinRange(new Range(lowerBound, 1.0));
		BidDetails result = candidateBids.size() > 0 ? candidateBids.get(rand.nextInt(candidateBids.size()))
				: negotiationSession.getMaxBidinDomain();
		return result;
		// return offerer.determineNextBid();
	}

	public BidDetails getBestMutualBidInRange(Double lowerBound, Double upperBound) {
		Random rand = new Random();
		List<BidDetails> candidateBids = outcomeSpace.getBidsinRange(new Range(lowerBound, 1.0)).stream()
				.sorted((a,b) -> compareBids(a, b))
				// .peek(bid -> System.out.println(opponentModel.getBidEvaluation(bid.getBid())))
				.collect(Collectors.toList());

		Integer size = candidateBids.size() > 5 ? 5 : candidateBids.size();
		BidDetails result = candidateBids.size() > 0 ? candidateBids.subList(0, size).get(rand.nextInt(size))
				: negotiationSession.getMaxBidinDomain();
		return result;
	}

	private int compareBids(BidDetails a, BidDetails b){
		return Double.compare(opponentModel.getBidEvaluation(a.getBid()), opponentModel.getBidEvaluation(b.getBid())) > 1 ? -1 : 1;
	}

	@Override
	public String getName() {
		return "MCTS Strategy";
	}

	public BidDetails getNextOpponentBid() {
		return null;
	}

}
