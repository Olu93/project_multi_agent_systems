package main;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.BoaParty;
import genius.core.boaframework.NoModel;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import main.helper.UncertaintyUtilitySpace;
import negotiator.boaframework.omstrategy.BestBid;
import negotiator.boaframework.omstrategy.NullStrategy;

/**
 * SmartAgent
 */
public class CarlosRandomBid extends BoaParty {

	private NegotiationInfo info;

	@Override
	public String getDescription() {
		return "MCSTParty";
	}

	@Override
	public void init(NegotiationInfo info) {
		// The choice for each component is made here
		AcceptanceStrategy ac = new Group10_AS();
		OpponentModel om = new Group10_OM();
		OMStrategy oms = new NullStrategy();
		OfferingStrategy os = new Group10_BS(); // TODO remove params

		// All component parameters can be set below.
		Map<String, Double> noparams = Collections.emptyMap();
		Map<String, Double> osParams = new HashMap<String, Double>();
		// Set the concession parameter "e" for the offering strategy to yield
		// Boulware-like behavior
		osParams.put("e", 0.2);

		// Initialize all the components of this party to the choices defined above
		configure(ac, noparams, os, osParams, om, noparams, oms, noparams);

		System.out.println("!!!!!!!!!!!!!!START!!!!!!!!!!!!");
		this.info = info;
		super.init(info);

	}

	@Override
	public AbstractUtilitySpace estimateUtilitySpace() {
		Boolean isUncertain = info.getUserModel() == null;
		System.out.println(isUncertain ? "Preferences are certain!" : "Uncertain preferences detected!");
		return (AbstractUtilitySpace) (isUncertain ? info.getUtilitySpace()
				: new UncertaintyUtilitySpace(info.getUserModel()));
	}
}