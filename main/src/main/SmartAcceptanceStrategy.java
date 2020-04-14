package main;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.rmi.CORBA.Util;

import genius.core.Bid;
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

public class SmartAcceptanceStrategy extends AcceptanceStrategy {

	private final ArrayList<BidDetails> bestBidProposals = new ArrayList<BidDetails>();
	private UncertaintyUtilityEstimator uncertaintyEstimator;

	@Override
	protected void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);
	}

	@Override
	public Actions determineAcceptability() {

		this.uncertaintyEstimator = new UncertaintyUtilityEstimator(negotiationSession); // TODO: Way to inefficient!!!

		final Boolean isSmartOffering = SmartComponentNames.SMART_BIDDING_STRATEGY.toString()
				.equalsIgnoreCase(offeringStrategy.getName());
		final UserModel userModel = negotiationSession.getUserModel();
		ExperimentalUserModel userModelExperimental = (ExperimentalUserModel) userModel;
		final boolean isUncertain = userModel == null;
		final BidDetails agentNextBid = offeringStrategy.getNextBid();
		final BidDetails opponentBid = negotiationSession.getOpponentBidHistory().getLastBidDetails();
		// TODO: Uncertainty makes it unclear whether that really is the best bid.
		final BidHistory opponentHistory = negotiationSession.getOpponentBidHistory();

		System.out.println("PRRRRIIIIIIIIIIIIINT");

		Matrix oneHotEncodedRankings = Utils.getDummyEncoding(negotiationSession.getIssues(),
				userModel.getBidRanking().getBidOrder());

		System.out.println("Encoded ranking");
		Utils.printMatrix(oneHotEncodedRankings);
		System.out.println("Weights");
		Utils.printMatrix(this.uncertaintyEstimator.getWeights());
		Matrix prediction = oneHotEncodedRankings.times(this.uncertaintyEstimator.getWeights().transpose());
		int i = 0;
		List<Double> statsBuiltin = new ArrayList<Double>();
		List<Double> statsPredict = new ArrayList<Double>();
		for (Bid rankedBid : userModel.getBidRanking().getBidOrder()) {
			double realUtility = userModelExperimental.getRealUtility(rankedBid);
			double builtInDiff = Math.abs(negotiationSession.getUtilitySpace().getUtility(rankedBid)-realUtility);
			double predDiff = Math.abs(prediction.get(i,0)-realUtility);
			statsBuiltin.add(builtInDiff);
			statsPredict.add(predDiff);
			System.out.println("===> Bid: " + rankedBid);
			System.out.println("True  							Util: " + realUtility);
			System.out.println("Built-in 	distance to real utility: " + builtInDiff);
			System.out.println("Prediction 	distance to real utility: " + predDiff);
			i++;
		}
		System.out.println("Avg Distance of built in utility estimator: "+ statsBuiltin.stream().mapToDouble(a -> a).average().getAsDouble());
		System.out.println("Avg Distance of prediction utility estimator: "+ statsPredict.stream().mapToDouble(a -> a).average().getAsDouble());

		// opponentHistory.getHistory().removeIf(bid -> bestBidProposals.contains(bid));
		// <= Leads to permanent history change!!!
		final List<BidDetails> candidateBids = opponentHistory.getHistory().parallelStream()
				.filter(bid -> bestBidProposals.contains(bid)).collect(Collectors.toList());
		final BidHistory filteredOpponentHistory = new BidHistory(candidateBids);
		final BidDetails opponentBestBid = filteredOpponentHistory.getBestBidDetails();

		if (opponentBid == null || agentNextBid == null) {
			return Actions.Reject;
		}

		System.out.println("Checking availability of SmartOfferingStrategy... " + isSmartOffering);
		if (isUncertain ? agentNextBid.getMyUndiscountedUtil() <= opponentBid.getMyUndiscountedUtil()
				: new OutcomeComparison(agentNextBid, opponentBid).getComparisonResult() >= 0) {
			System.out.println("Next bid is going to be smaller than opponent bid!");

			if (isSmartOffering) {

				if (isUncertain ? opponentBid.getMyUndiscountedUtil() < opponentBestBid.getMyUndiscountedUtil()
						: new OutcomeComparison(opponentBid, opponentBestBid).getComparisonResult() > 0) {
					System.out.println("Opponent bid is worse than a bid in his history!");
					bestBidProposals.add(opponentBestBid);
					((SmartOfferingStrategy) offeringStrategy).setOpponentBestBid(opponentBestBid.getBid());
					return Actions.Reject;
				}

				System.out.println("Opponent bid is best bid so far!");
				final BidDetails opponentNextBid = ((SmartOfferingStrategy) offeringStrategy)
						.getOpponentBidPrediction();
				if (isUncertain ? opponentBid.getMyUndiscountedUtil() < opponentNextBid.getMyUndiscountedUtil()
						: new OutcomeComparison(opponentBid, opponentNextBid).getComparisonResult() > 0) {
					System.out.println("Opponent is going to bid better in the next!");
					return Actions.Reject;
				}

			}
			return Actions.Accept;
		}
		return Actions.Reject;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return SmartComponentNames.SMART_ACCEPTANCE_STRATEGY.toString();
	}

}
