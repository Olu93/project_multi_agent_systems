package main;

import genius.core.Bid;
import genius.core.boaframework.OpponentModel;

public class SmartOpponentModel extends OpponentModel {

	@Override
	protected void updateModel(Bid bid, double time) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		return SmartComponentNames.SMART_OPPONENT_MODEL.toString();
	}

}
