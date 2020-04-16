package main;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import agents.anac.y2019.harddealer.HardDealer_AS;
import genius.core.boaframework.AcceptanceStrategy;
import genius.core.boaframework.BoaParty;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OfferingStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import negotiator.boaframework.acceptanceconditions.anac2010.AC_IAMHaggler2010;
import negotiator.boaframework.acceptanceconditions.anac2011.AC_HardHeaded;
import negotiator.boaframework.acceptanceconditions.anac2012.AC_BRAMAgent2;
import negotiator.boaframework.offeringstrategy.anac2011.NiceTitForTat_Offering;
import negotiator.boaframework.offeringstrategy.anac2010.IAMhaggler2010_Offering;

/**
 * SmartAgent
 */
public class SmartAgent extends BoaParty {
	// TODO: Fix setTilSpace not working (or remove this code)
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private NegotiationInfo info;
	
	@Override
	public String getDescription() {
		return "SmartAgentParty";
	}

	@Override
	public void init(NegotiationInfo info) {
		// The choice for each component is made here
		AcceptanceStrategy ac = new SmartAcceptanceStrategy();
		OfferingStrategy os = new NiceTitForTat_Offering();
		//OfferingStrategy os = new IAMhaggler2010_Offering();
		OpponentModel om = new FreqOpponentPrefModel();
		OMStrategy oms = new SmartOpponentOfferingModel();

		// All component parameters can be set below.
		Map<String, Double> noparams = Collections.emptyMap();
		Map<String, Double> osParams = new HashMap<String, Double>();
		// Set the concession parameter "e" for the offering strategy to yield
		// Boulware-like behavior
		osParams.put("e", 0.2);

		// Initialize all the components of this party to the choices defined above
		configure(ac, noparams, os, osParams, om, noparams, oms, noparams);

		System.out.println("!!!!!!!!!!!!!!START!!!!!!!!!!!!");
		Boolean isUncertain = info.getUserModel() == null;
		System.out.println(isUncertain ? "Preferences are certain!" : "Uncertain preferences detected!");
		info.setUtilSpace((AbstractUtilitySpace) (isUncertain ? info.getUtilitySpace() : new UncertaintyUtilityEstimator(info.getUserModel()))); 
		this.info = info;
		super.init(info);

	}

	 @Override
	 public AbstractUtilitySpace estimateUtilitySpace() {
		Boolean isUncertain = info.getUserModel() == null;
	 	return (AbstractUtilitySpace) (isUncertain ? info.getUtilitySpace() : new UncertaintyUtilityEstimator(info.getUserModel()));
	 }

}