package main;

import java.util.List;

import genius.core.Bid;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.Actions;
import genius.core.boaframework.SharedAgentState;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.OutcomeComparison;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;

public class SmartAcceptanceStrategy extends AcceptanceStrategy {

	@Override
	public Actions determineAcceptability() {
		final Boolean isSmartOffering = offeringStrategy.getName() == "SmartOfferingStrategy";
		System.out.println("Checking availability of SmartOfferingStrategy... " + isSmartOffering);
		final BidDetails agentNextBid = offeringStrategy.getNextBid();
		final BidDetails opponentBid = negotiationSession.getOpponentBidHistory().getLastBidDetails();
		final BidDetails opponentBestBid = negotiationSession.getOpponentBidHistory().getBestBidDetails();

		if (opponentBid == null || agentNextBid == null) {
			return Actions.Reject;
		}
		final UserModel userModel = negotiationSession.getUserModel();
		if (userModel != null) {
			return acceptanceUnderUncertainty(agentNextBid, opponentBid, opponentBestBid ,isSmartOffering);
			// return acceptanceUnderCertainty(agentNextBid.getBid(), opponentBid.getBid(), opponentBestBid.getBid() ,isSmartOffering);
		} else {
			return acceptanceUnderCertainty(agentNextBid.getBid(), opponentBid.getBid(), opponentBestBid.getBid() ,isSmartOffering);
		}

	}

	private Actions acceptanceUnderCertainty(final Bid agentNextBid, final Bid opponentBid, final Bid opponentBestBid,
			final Boolean isSmartOffering) {
		final AbstractUtilitySpace utilitySpace = negotiationSession.getUtilitySpace();
		final Double rankOwnBid = (Double) utilitySpace.getUtility(agentNextBid);
		final Double rankOppenentBid = (Double) utilitySpace.getUtility(opponentBid);
		final Double rankBestOpponentBid = (Double) utilitySpace.getUtility(opponentBestBid);
		System.out.println("Ranks: " + rankOwnBid);
		System.out.println("Ranks: " + rankOppenentBid);
		System.out.println("Ranks: " + rankBestOpponentBid);
		if (rankOwnBid <= rankOppenentBid) {
			System.out.println("Next bid is going to be smaller than opponent bid!");
			// TODO: Requires a copy of the history
			if (rankOppenentBid < rankBestOpponentBid) {
				// TODO: pop bid
				return saveOpponentBestBidFromHistory(isSmartOffering, opponentBestBid);
			}
			System.out.println("Opponent bid is best bid so far!");
			if (isSmartOffering && rankOppenentBid < utilitySpace
					.getUtility(((SmartOfferingStrategy) offeringStrategy).getOpponentBidPrediction())) {
				System.out.println("Opponent is going to bid better in the next!");
				return Actions.Reject;
			}
			return Actions.Accept;
		}
		return Actions.Reject;
	}

	private Actions acceptanceUnderUncertainty(final BidDetails agentNextBid, final BidDetails opponentBid, final BidDetails opponentBestBid, final Boolean isSmartOffering) {

		final OutcomeComparison rankOwnBid = new OutcomeComparison(agentNextBid, opponentBid);
		final OutcomeComparison rankOppenentBid = new OutcomeComparison(opponentBid, opponentBestBid);
		// System.out.println("Ranks: " + rankOwnBid);
		// System.out.println("Ranks: " + rankOppenentBid);
		System.out.println("Ranks MyB: " + rankOwnBid.getComparisonResult());
		System.out.println("Ranks OpB: " + rankOppenentBid.getComparisonResult());
		if (rankOwnBid.getComparisonResult() >= 0) {
			System.out.println("Next bid is going to be smaller than opponent bid!");
			// TODO: Requires a copy of the history
			if (rankOppenentBid.getComparisonResult() > 0) {
				return saveOpponentBestBidFromHistory(isSmartOffering, opponentBestBid.getBid());
			}
			System.out.println("Opponent bid is best bid so far!");
			if (isSmartOffering) {
				final Bid opponenNextBid = ((SmartOfferingStrategy) offeringStrategy).getOpponentBidPrediction();
				final BidDetails opponentNextBidDetails = new BidDetails(opponenNextBid, negotiationSession.getUtilitySpace().getUtility(opponenNextBid));
				if (opponentBid.compareTo(opponentNextBidDetails) > 0) {
					System.out.println("Opponent is going to bid better in the next!");
					return Actions.Reject;
				}
			}
			return Actions.Accept;
		}
		return Actions.Reject;
	}

	private Actions saveOpponentBestBidFromHistory(final Boolean isSmartOffering, final Bid opponentBestBid) {
		System.out.println("Opponent bid is worse than a bid in his history!");
		if (isSmartOffering)
			((SmartOfferingStrategy) offeringStrategy).setOpponentBestBid(opponentBestBid);
		return Actions.Reject;
	}

	

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Smart Acceptance Strategy: This strategy employs a number of smart heuristics for acceptance";
	}

}
