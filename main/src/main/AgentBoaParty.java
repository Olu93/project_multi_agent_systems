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
import negotiator.boaframework.offeringstrategy.anac2011.NiceTitForTat_Offering;

/**
 * SmartAgent
 */
public class AgentBoaParty extends BoaParty{

    @Override
    public String getDescription() {
        return "MCSTParty";
    }

    @Override
	public void init(NegotiationInfo info) 
	{
		// The choice for each component is made here
		AcceptanceStrategy 	ac  = new SmartAcceptanceStrategy();
		OpponentModel 		om  = new FreqOpponentPrefModel();
		OMStrategy			oms = new SmartOpponentOfferingModel();
		OfferingStrategy 	os  = new MCTSStrategy(); // TODO remove params 
		
		// All component parameters can be set below.
		Map<String, Double> noparams = Collections.emptyMap();
		Map<String, Double> osParams = new HashMap<String, Double>();
		// Set the concession parameter "e" for the offering strategy to yield Boulware-like behavior
		osParams.put("e", 0.2);
		
		// Initialize all the components of this party to the choices defined above
		configure(ac, noparams, 
				os,	osParams, 
				om, noparams,
				oms, noparams);
		super.init(info);
	}
    
}