package main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import genius.core.bidding.BidDetails;
import genius.core.boaframework.Actions;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.boaframework.OutcomeSpace;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.misc.Range;
import main.mcts.GameTree;
import main.mcts.Node;

public class CarlosBiddingStrategy extends OfferingStrategy {
	/**
	 *
	 */
	// private Bid opponentBestBid;

	private static final double UPPER_BOUND = 1.0;
	CarlosAcceptanceStrategy ac;
	OMStrategy om;
	GameTree tree = new GameTree();
	OutcomeSpace outcomeSpace;
	private final Double DISCOUNT_FACTOR = 0.90;
	private Double lowerBound = 1.0;
	private final Boolean IS_VERBOSE = false;
	BidDetails lastSetBid;
	private final Integer SIMULATION_FREQUENCY = 40;
	private final Integer SIMULATION_DEPTH = 5;
	private Double prevBestBidUtility = 0.0;

	// #endregion

	@Override
	public void init(NegotiationSession negotiationSession, OpponentModel opponentModel, OMStrategy omStrategy,
			Map<String, Double> parameters) throws Exception {
		super.init(negotiationSession, opponentModel, omStrategy, parameters);
		outcomeSpace = new SortedOutcomeSpace(negotiationSession.getUtilitySpace());
		negotiationSession.setOutcomeSpace(outcomeSpace);
		ac = new CarlosAcceptanceStrategy(negotiationSession, this, opponentModel, parameters);
	}

	@Override
	public BidDetails determineOpeningBid() {
		BidDetails lastSetBid = negotiationSession.getOutcomeSpace().getMaxBidPossible();
		return lastSetBid;
	}

	@Override
	public BidDetails determineNextBid() {
		BidDetails nextBid = this.getNextBid();
		if (nextBid != null && !nextBid.equals(lastSetBid)) {
			lastSetBid = nextBid;
			this.setNextBid(null);
			return lastSetBid;
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
		for (int i = 0; i < SIMULATION_FREQUENCY; i++) {

			final Node selectedNode = selectNode(node);


			if (selectedNode.getNoVisits() >= selectedNode.getChildren().size()) {
				expandNode(selectedNode);
			}

			Node explorationCandidate = selectedNode;
			if (explorationCandidate.getChildren().size() > 0) {
				explorationCandidate = selectedNode.getRandomChild();
			}
			rolloutSimBackprop(explorationCandidate);

		}

		final Node bestChoiceNode = node.getBestChild();
		tree.setRoot(bestChoiceNode);
		System.out.println("===========> Best choice: " + bestChoiceNode.getId());

//		lowerBound = didConcede() ? updateLowerBound() : lowerBound;
		lowerBound = updateLowerBound();
		return bestChoiceNode.getBid();
	}

	private Boolean didConcede() {
		BidDetails bestBid = this.negotiationSession.getOpponentBidHistory().getBestBidDetails();
		Double bestBidUtility = bestBid.getMyUndiscountedUtil();
		double newBar = this.prevBestBidUtility + 0.01;
		boolean tmp = bestBidUtility > newBar;
		if (tmp)
			this.prevBestBidUtility = bestBidUtility;
		return tmp;
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
			currNode = CarlosBiddingStrategy.getBestNode(currNode);
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
		node.addChild(new Node().setParent(node).setBid(chooseBid()))
				.addChild(new Node().setParent(node).setBid(chooseBid()))
				.addChild(new Node().setParent(node).setBid(chooseBid()));

	}

	// rollout and backpropagation
	private void rolloutSimBackprop(final Node node) {
		Node iterNodeCopy = node;
		final List<BidDetails> oppHistory = new ArrayList<BidDetails>();
		final List<BidDetails> agentHistory = new ArrayList<BidDetails>();
		oppHistory.add(this.negotiationSession.getOpponentBidHistory().getLastBidDetails());
		agentHistory.add(this.negotiationSession.getOpponentBidHistory().getLastBidDetails());

		int count = 0;
		BidDetails nextOpponentBid, agentCurrentBid;
		final List<Double> scores = new ArrayList<>();
		Double avgScore = 0.0;
		BidDetails agentNextBid;
		do {
			// if (negotiationSession.getTime() > 0.25) {
			nextOpponentBid = this.omStrategy instanceof CarlosOpponentBiddingStrategy
					? ((CarlosOpponentBiddingStrategy) this.omStrategy).getBidbyHistory(oppHistory, agentHistory)
					: this.omStrategy.getBid(oppHistory);

			// System.out.println(nextOpponentBid.getBid());

			agentCurrentBid = chooseBid();
			oppHistory.add(nextOpponentBid);
			agentHistory.add(agentCurrentBid);
			double opponentUtility = negotiationSession.getUtilitySpace().getUtility(nextOpponentBid.getBid());
			scores.add(opponentUtility * Math.pow(DISCOUNT_FACTOR, count));
			count++;
			double agentUtility = this.negotiationSession.getUtilitySpace()
					.getUtility(this.negotiationSession.getOwnBidHistory().getLastBidDetails().getBid());
			agentNextBid = this.outcomeSpace.getBidNearUtility(agentUtility);
		} while (ac.determineAcceptabilityBid(nextOpponentBid, agentNextBid) != Actions.Accept
				&& count <= SIMULATION_DEPTH);

		avgScore = scores.parallelStream().mapToDouble(val -> val).average().getAsDouble();
		while (iterNodeCopy != null) {
			double backpropVal = (iterNodeCopy.getScore() * iterNodeCopy.getNoVisits() + avgScore)
					/ (iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy.setScore(backpropVal);
			iterNodeCopy.setNoVisits(iterNodeCopy.getNoVisits() + 1);
			iterNodeCopy = iterNodeCopy.getParent();
		}
	}


	public BidDetails chooseBid() {
		return getBidInRange(lowerBound, UPPER_BOUND);
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
				.sorted((a, b) -> compareBids(a, b))
				.collect(Collectors.toList());

		Integer size = candidateBids.size() > 5 ? 5 : candidateBids.size();
		BidDetails result = candidateBids.size() > 0 ? candidateBids.subList(0, size).get(rand.nextInt(size))
				: negotiationSession.getMaxBidinDomain();
		return result;
	}

	private int compareBids(BidDetails a, BidDetails b) {
		return Double.compare(opponentModel.getBidEvaluation(a.getBid()),
				opponentModel.getBidEvaluation(b.getBid())) > 1 ? -1 : 1;
	}

	@Override
	public String getName() {
		return CarlosComponentNames.SMART_BIDDING_STRATEGY.toString();
	}


}
