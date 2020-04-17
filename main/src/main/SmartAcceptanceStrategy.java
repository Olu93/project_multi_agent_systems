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

public class SmartAcceptanceStrategy extends AcceptanceStrategy {
	// NOTE: Does very poor if the own bid is weak.
	private final ArrayList<BidDetails> prevBestBidProposals = new ArrayList<BidDetails>();
	private OMStrategy omStrategy;
	private Boolean IS_VERBOSE = false;

	@Override
	public void init(NegotiationSession negotiationSession, OfferingStrategy offeringStrategy,
			OpponentModel opponentModel, Map<String, Double> parameters) throws Exception {
		super.init(negotiationSession, offeringStrategy, opponentModel, parameters);
	}

	public SmartAcceptanceStrategy(NegotiationSession session, OfferingStrategy offeringStrategy,
	OpponentModel opponentModel, Map<String, Double> parameters) {
		this.negotiationSession = session;
		this.omStrategy = ((MCTSStrategy) offeringStrategy).om;

		try {
			this.init(negotiationSession, offeringStrategy, opponentModel, parameters);
		} catch (Exception e) {
			e.printStackTrace();
		};
	}

	public SmartAcceptanceStrategy() {
		;
	}

	@Override
	public Actions determineAcceptability() {
		// TODO: time based epsilon function
		// TODO: Use only utility function. Uncertainty estimation is handled elsewhere

		// this.uncertaintyEstimator = new
		// UncertaintyUtilityEstimator(negotiationSession); // TODO: Way to
		// inefficient!!!

		final BidDetails opponentBid = negotiationSession.getOpponentBidHistory().getLastBidDetails();
		final BidDetails agentNextBid = offeringStrategy.determineNextBid();
		return determineAcceptabilityBid(opponentBid, agentNextBid);
	}

	public Actions determineAcceptabilityBid(BidDetails opponentBid, BidDetails agentNextBid) {
		if(IS_VERBOSE) System.out.println("START DETERMINE ACCEPTABILITY: ");
		final Boolean isSmartOffering = offeringStrategy instanceof SmartOfferingStrategy;
		final BidHistory opponentHistory = negotiationSession.getOpponentBidHistory();
		// opponentHistory.getHistory().removeIf(bid -> bestBidProposals.contains(bid));
		// <= Leads to permanent history change!!!
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
		if(IS_VERBOSE) System.out.println("Checking availability of SmartOfferingStrategy... " + isSmartOffering);
		// Possibly temporary addition of extra requirement to proceed. Until
		// smartOfferingStrategy is done, accepts way too easily
		double utilityAgentBid = negotiationSession.getUtilitySpace().getUtility(agentNextBid.getBid());
		double utilityOpponentBid = negotiationSession.getUtilitySpace().getUtility(opponentBid.getBid());

		// System.out.println("Agent: " + agentNextBid.getBid());
		// System.out.println("Opponent: " + opponentBid.getBid());
		if(IS_VERBOSE) System.out.println("Agent next bid: " + utilityAgentBid);
		if(IS_VERBOSE) System.out.println("Opponent bid: " + utilityOpponentBid);
		if (utilityAgentBid <= utilityOpponentBid + epsilon && utilityAgentBid > epsilon) { // TODO why the second?
			if(IS_VERBOSE) System.out.println("Next bid is going to be smaller than opponent bid!");
			if (utilityOpponentBid < opponentBestBid.getMyUndiscountedUtil()) {
				if(IS_VERBOSE) System.out.println("Opponent bid is worse than a bid in his history!");
				prevBestBidProposals.add(opponentBestBid);
				offeringStrategy.setNextBid(opponentBestBid);
				return reject();
			}

			if(IS_VERBOSE) System.out.println("Opponent bid is best bid so far!");
			
			if(this.omStrategy != null){
				double oppnentNextBid = this.omStrategy.getBid(negotiationSession.getOpponentBidHistory().getHistory()).getMyUndiscountedUtil();
				if (utilityOpponentBid < oppnentNextBid) {
					if(IS_VERBOSE) System.out.println("Opponent is going to bid better in the next!");
					return reject();
				}
			}			
			

			System.out.println("Accepting bid!");
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
		return SmartComponentNames.SMART_ACCEPTANCE_STRATEGY.toString();
	}

}
