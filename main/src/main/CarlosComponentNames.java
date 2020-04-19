package main;

/**
 * SmartConstants
 */
public enum CarlosComponentNames {

    SMART_OPPONENT_MODEL, SMART_BIDDING_STRATEGY, SMART_OPPONENT_BIDDING_STRATEGY, SMART_ACCEPTANCE_STRATEGY;

    public String toString() {
        switch (this) {
            case SMART_OPPONENT_MODEL:
                return "Smart Opponent Model";
            case SMART_BIDDING_STRATEGY:
                return "Smart Bidding Strategy";
            case SMART_OPPONENT_BIDDING_STRATEGY:
                return "Smart Opponent Bidding Strategy";
            case SMART_ACCEPTANCE_STRATEGY:
                return "Smart Acceptance Bidding Strategy";
        }
        return null;
    }

}