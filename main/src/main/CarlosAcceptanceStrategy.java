package main;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;

public class CarlosAcceptanceStrategy extends AcceptanceStrategy {
	// NOTE: Does very poor if the own bid is weak.
	private final ArrayList<BidDetails> prevBestBidProposals = new ArrayList<BidDetails>();
	private OMStrategy omStrategy;
	private Boolean IS_VERBOSE = false;

	@Override
	public void init(NegotiationSession negotiationSession, OfferingStrategy offeringStrategy,
			OpponentModel opponentModel, Map<String, Double> parameters) throws Exception {
		super.init(negotiationSession, offeringStrategy, opponentModel, parameters);
	}

	public CarlosAcceptanceStrategy(NegotiationSession session, OfferingStrategy offeringStrategy,
	OpponentModel opponentModel, Map<String, Double> parameters) {
		this.negotiationSession = session;
		this.omStrategy = offeringStrategy instanceof CarlosBiddingStrategy ? ((CarlosBiddingStrategy) offeringStrategy).om : null;

		try {
			this.init(negotiationSession, offeringStrategy, opponentModel, parameters);
		} catch (Exception e) {
			e.printStackTrace();
		};
	}

	public CarlosAcceptanceStrategy() {
		;
	}

	@Override
	public Actions determineAcceptability() {
		final BidDetails opponentBid = negotiationSession.getOpponentBidHistory().getLastBidDetails();
		final BidDetails agentNextBid = offeringStrategy.determineNextBid();
		return determineAcceptabilityBid(opponentBid, agentNextBid);
	}

	// Receives a bid and returns whether the agent will accept the bid. 
	// First takes time concessions into account, then takes opponent bidding history into account,
	// and finally takes estimated opponent strategy into account.
	public Actions determineAcceptabilityBid(BidDetails opponentBid, BidDetails agentNextBid) {
		if(IS_VERBOSE) System.out.println("START DETERMINE ACCEPTABILITY: ");
		final BidHistory opponentHistory = negotiationSession.getOpponentBidHistory();
		final List<BidDetails> candidateBids = opponentHistory.getHistory().parallelStream()
				.filter(bid -> !prevBestBidProposals.contains(bid)).collect(Collectors.toList());
		final BidDetails opponentBestBid = new BidHistory(candidateBids).getBestBidDetails();

		final double timeRatio = negotiationSession.getTimeline().getCurrentTime()
				/ negotiationSession.getTimeline().getTotalTime();
		final double epsilon = (0.02 / (1.05 - timeRatio));

		if(IS_VERBOSE) System.out.println("epsilon value");
		if(IS_VERBOSE) System.out.println(epsilon);

		if (opponentBid == null || agentNextBid == null) {
			System.out.println("END REACHED");
			return Actions.Reject;
		}

		double utilityAgentBid = negotiationSession.getUtilitySpace().getUtility(agentNextBid.getBid());
		double utilityOpponentBid = negotiationSession.getUtilitySpace().getUtility(opponentBid.getBid());
		double utilityOpponentBestBid = negotiationSession.getUtilitySpace().getUtility(opponentBestBid.getBid());

		if(IS_VERBOSE) System.out.println("Agent next bid: " + utilityAgentBid);
		if(IS_VERBOSE) System.out.println("Opponent bid: " + utilityOpponentBid);
		
		// Factoring in whether the opponent bid is worse than our next bid, taking time concession into account through epsilon value.
		if (utilityAgentBid <= utilityOpponentBid + epsilon && utilityAgentBid > epsilon) {
			if(IS_VERBOSE) System.out.println("Next bid is going to be smaller than opponent bid!");
			
			// Factoring in whether there is a better opponent bid in their history
			if (utilityOpponentBid < utilityOpponentBestBid && opponentHistory.size() > 1) {
				if(IS_VERBOSE) System.out.println("Opponent bid is worse than a bid in his history!");
				prevBestBidProposals.add(opponentBestBid);
				offeringStrategy.setNextBid(opponentBestBid);
				return reject();
			}

			if(IS_VERBOSE) System.out.println("Opponent bid is best bid so far!");
			
			// Factoring in whether the opponent is predicted to produce a better next bid.
			if(this.omStrategy != null){
				double oppnentNextBid = this.omStrategy.getBid(negotiationSession.getOpponentBidHistory().getHistory()).getMyUndiscountedUtil();
				if (utilityOpponentBid < oppnentNextBid) {
					if(IS_VERBOSE) System.out.println("Opponent is going to bid better in the next!");
					return reject();
				}
			}			
			

			if(IS_VERBOSE) System.out.println("Accepting bid!" + opponentBid);
			return Actions.Accept;
		}
		return reject();
	}

	private Actions reject() {
		if(IS_VERBOSE) System.out.println("Reject - Time: " + negotiationSession.getTime());
		return Actions.Reject;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return CarlosComponentNames.SMART_ACCEPTANCE_STRATEGY.toString();
	}

}
