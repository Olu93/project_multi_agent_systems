package main;

/**
 * SmartConstants
 */
public enum CarlosComponentNames {

    SMART_OPPONENT_MODEL, SMART_BIDDING_STRATEGY, SMART_OPPONENT_BIDDING_STRATEGY, SMART_ACCEPTANCE_STRATEGY;

    public String toString() {
        switch (this) {
            case SMART_OPPONENT_MODEL:
                return "Carlos Opponent Model - Frequency-Based";
            case SMART_BIDDING_STRATEGY:
                return "Carlos Bidding Strategy - MCTS";
            case SMART_OPPONENT_BIDDING_STRATEGY:
                return "Carlos Opponent Bidding Strategy - Gaussian Process";
            case SMART_ACCEPTANCE_STRATEGY:
                return "Carlos Acceptance Bidding Strategy - Heuristic";
        }
        return null;
    }

}