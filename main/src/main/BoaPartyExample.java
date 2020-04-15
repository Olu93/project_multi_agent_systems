package group10_strategy;

import genius.core.boaframework.*;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.utility.AbstractUtilitySpace;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This example shows how BOA components can be made into an independent
 * negotiation party and which can handle preference uncertainty.
 * 
 * Note that this is equivalent to adding a BOA party via the GUI by selecting
 * the components and parameters. However, this method gives more control over
 * the implementation, as the agent designer can choose to override behavior
 * (such as handling preference uncertainty).
 * <p>
 * For more information, see: Baarslag T., Hindriks K.V., Hendrikx M.,
 * Dirkzwager A., Jonker C.M. Decoupling Negotiating Agents to Explore the Space
 * of Negotiation Strategies. Proceedings of The Fifth International Workshop on
 * Agent-based Complex Automated Negotiations (ACAN 2012), 2012.
 * https://homepages.cwi.nl/~baarslag/pub/Decoupling_Negotiating_Agents_to_Explore_the_Space_of_Negotiation_Strategies_ACAN_2012.pdf
 * 
 * @author Tim Baarslag
 */
@SuppressWarnings("serial")
public class BoaPartyExample extends BoaParty 
{
	@Override
	public void init(NegotiationInfo info) 
	{
		// The choice for each component is made here
		AcceptanceStrategy 	ac  = new SmartAcceptanceStrategy();
		OpponentModel 		om  = new FreqOpponentPrefModel();
		OMStrategy			oms = new SmartOpponentOfferingModel();
		OfferingStrategy 	os  = new MCTSStrategy(ac,oms);
		
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

	/**
	 * Specific functionality, such as the estimate of the utility space in the
	 * face of preference uncertainty, can be specified by overriding the
	 * default behavior.
	 * 
	 * This example estimator sets all weights and all evaluator values randomly.
	 */
	@Override
	public AbstractUtilitySpace estimateUtilitySpace() 
	{
		AdditiveUtilitySpaceFactory additiveUtilitySpaceFactory = new AdditiveUtilitySpaceFactory(getDomain());
		List<IssueDiscrete> issues = additiveUtilitySpaceFactory.getIssues();
		for (IssueDiscrete i : issues)
		{
			additiveUtilitySpaceFactory.setWeight(i, rand.nextDouble());
			for (ValueDiscrete v : i.getValues())
				additiveUtilitySpaceFactory.setUtility(i, v, rand.nextDouble());
		}
		
		// Normalize the weights, since we picked them randomly in [0, 1]
		additiveUtilitySpaceFactory.normalizeWeights();
		
		// The factory is done with setting all parameters, now return the estimated utility space
		return additiveUtilitySpaceFactory.getUtilitySpace();
	}
	
	@Override
	public String getDescription() 
	{
		return "Crazy boa party";
	}

	// All the rest of the agent functionality is defined by the components selected above, using the BOA framework
}
