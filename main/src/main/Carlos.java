package main;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.BoaParty;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import main.helper.UncertaintyUtilitySpace;

/**
 * SmartAgent
 */
public class Carlos extends BoaParty {

	private NegotiationInfo info;

	@Override
	public String getDescription() {
		return "Monte Carlo Tree Search";
	}

	@Override
	public void init(NegotiationInfo info) {
		// The choice for each component is made here
		AcceptanceStrategy ac = new Group10_AS();
		OpponentModel om = new Group10_OM();
		OMStrategy oms = new Group10_OMS();
		OfferingStrategy os = new Group10_BS(); // TODO remove params

		// All component parameters can be set below.
		Map<String, Double> noparams = Collections.emptyMap();
		Map<String, Double> osParams = new HashMap<String, Double>();
		// Set the concession parameter "e" for the offering strategy to yield
		// Boulware-like behavior
		osParams.put("e", 0.2);

		// Initialize all the components of this party to the choices defined above
		configure(ac, noparams, os, osParams, om, noparams, oms, noparams);

		this.info = info;
		super.init(info);

	}

	@Override
	public AbstractUtilitySpace estimateUtilitySpace() {
		Boolean isUncertain = info.getUserModel() == null;
		return (AbstractUtilitySpace) (isUncertain ? info.getUtilitySpace()
				: new UncertaintyUtilitySpace(info.getUserModel()));
	}
}