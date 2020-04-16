package main;

import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.NegotiationSession;
import genius.core.uncertainty.ExperimentalUserModel;
import genius.core.uncertainty.OutcomeComparison;
import genius.core.uncertainty.UserModel;
import math.Matrix;
import misc.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SmartAcceptanceStrategy extends AcceptanceStrategy {

	private final ArrayList<BidDetails> bestBidProposals = new ArrayList<BidDetails>();
	private UncertaintyUtilityEstimator uncertaintyEstimator;


	@Override
	protected void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);

	}

	@Override
	public Actions determineAcceptability() {
		// TODO: time based epsilon function
		// TODO: Use only utility function. Uncertainty estimation is handled elsewhere
		
		// this.uncertaintyEstimator = new UncertaintyUtilityEstimator(negotiationSession); // TODO: Way to inefficient!!!

		System.out.println("OVERWRITE SMALLER DETERMINE ACCEPTABILITY");
		final BidDetails opponentBid = negotiationSession.getOpponentBidHistory().getLastBidDetails();
		// TODO: Uncertainty makes it unclear whether that really is the best bid.
		return determineAcceptabilityBid(opponentBid);
	}
	
	public Actions determineAcceptabilityBid(BidDetails opponentBid) {
		System.out.println("START DETERMINE ACCEPTABILITY: ");
		final Boolean isSmartOffering = offeringStrategy instanceof SmartOfferingStrategy;
		final UserModel userModel = negotiationSession.getUserModel();
		final boolean isUncertain = userModel == null;
		final BidDetails agentNextBid = offeringStrategy.getNextBid();
		
		final BidHistory opponentHistory = negotiationSession.getOpponentBidHistory();
		// opponentHistory.getHistory().removeIf(bid -> bestBidProposals.contains(bid));
		// <= Leads to permanent history change!!!
		final List<BidDetails> candidateBids = opponentHistory.getHistory().parallelStream()
				.filter(bid -> bestBidProposals.contains(bid)).collect(Collectors.toList());
		final BidHistory filteredOpponentHistory = new BidHistory(candidateBids);
		final BidDetails opponentBestBid = filteredOpponentHistory.getBestBidDetails();
		final double timeRatio = negotiationSession.getTimeline().getCurrentTime() / negotiationSession.getTimeline().getTotalTime();
		final double epsilon = (0.02/(1.05-timeRatio));
		
		System.out.println("epsilon value");
		System.out.println(epsilon);
		System.out.println("Agent next bid: " + negotiationSession.getUtilitySpace().getUtility(agentNextBid.getBid()));
		System.out.println("Opponent bid: " + negotiationSession.getUtilitySpace().getUtility(opponentBid.getBid()));

		
		if (opponentBid == null || agentNextBid == null) {
			System.out.println("END REACHED");
			return Actions.Reject;
		}
		System.out.println("Checking availability of SmartOfferingStrategy... " + isSmartOffering);
		// Possibly temporary addition of extra requirement to proceed. Until smartOfferingStrategy is done, accepts way too easily
		if (negotiationSession.getUtilitySpace().getUtility(agentNextBid.getBid()) <= negotiationSession.getUtilitySpace().getUtility(opponentBid.getBid()) + epsilon
				&& negotiationSession.getUtilitySpace().getUtility(agentNextBid.getBid()) > epsilon) {
			System.out.println("Next bid is going to be smaller than opponent bid!");
			if (isSmartOffering) {

				if (opponentBid.getMyUndiscountedUtil() < opponentBestBid.getMyUndiscountedUtil()) {
					System.out.println("Opponent bid is worse than a bid in his history!");
					bestBidProposals.add(opponentBestBid);
					((SmartOfferingStrategy) offeringStrategy).setOpponentBestBid(opponentBestBid.getBid());
					System.out.println("END REACHED");
					return Actions.Reject;
				}

				System.out.println("Opponent bid is best bid so far!");
				final BidDetails opponentNextBid = ((SmartOfferingStrategy) offeringStrategy)
						.getOpponentBidPrediction();
				if (opponentBid.getMyUndiscountedUtil() < opponentNextBid.getMyUndiscountedUtil()) {
					System.out.println("Opponent is going to bid better in the next!");
					System.out.println("END REACHED");
					return Actions.Reject;
				}

			}
			System.out.println("Accepting bid!");
			return Actions.Accept;
		}
		System.out.println("END REACHED");
		return Actions.Reject;	
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return SmartComponentNames.SMART_ACCEPTANCE_STRATEGY.toString();
	}

}
