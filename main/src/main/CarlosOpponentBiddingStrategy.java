package main;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.IntStream;

import agents.anac.y2011.TheNegotiator.Pair;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.NegotiationResult;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.boaframework.OutcomeSpace;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.issue.IssueDiscrete;
import genius.core.misc.Range;
import main.helper.BidEncoder;
import agents.Jama.Matrix;

/**
 * SmartOpponentOfferingModel
 */
public class CarlosOpponentBiddingStrategy extends OMStrategy {

    private List<BidDetails> opponentBiddingHistory;
    private List<BidDetails> myBiddingHistory;
    private Matrix K_stable;
    private BidEncoder encoder;
    private final Boolean IS_VERBOSE = false;
    private DataSet ds = new DataSet();
    private Matrix K_inv;
    private SortedOutcomeSpace outcomeSpace;
    private Random random;
    private final Integer START_PREDICTING = 11;
    private final Integer UPDATE_PERIOD = 11;
    private int updCnt;
    private boolean wasRecentlyUpdated = false;
    private HashMap<double[], double[]> statContainer = new HashMap<>();

    @Override
    public void init(final NegotiationSession negotiationSession, final OpponentModel model,
            final Map<String, Double> parameters) {
        super.init(negotiationSession, model, parameters);
        this.encoder = new BidEncoder(negotiationSession);
        this.opponentBiddingHistory = negotiationSession.getOpponentBidHistory().getHistory();
        this.myBiddingHistory = negotiationSession.getOwnBidHistory().getHistory();
        this.outcomeSpace = new SortedOutcomeSpace(negotiationSession.getUtilitySpace());
        this.random = new Random();
    }

    public void updateModel() {
        if ((this.opponentBiddingHistory.size() + 1) % UPDATE_PERIOD == 0) {
            if (wasRecentlyUpdated)
                return;
            this.wasRecentlyUpdated = true;
            int osize = this.opponentBiddingHistory.size() - 1;
            int asize = this.myBiddingHistory.size() - 1;
            List<BidDetails> oppBidList = this.opponentBiddingHistory.subList(0, osize - 1);
            List<BidDetails> agBidList = this.myBiddingHistory.subList(0, osize > asize ? asize : asize - 1);

            Pair<Matrix, Matrix> data = getMatrixRepresentation(oppBidList, agBidList);
            Matrix observedX = this.ds.setX(data.getFirst()).setY(data.getSecond()).getX();
            Matrix K = computeCovarianceMatrix(observedX, observedX);
            Matrix K_stable = K.plus(Matrix.identity(K.getRowDimension(), K.getColumnDimension()).times(0.00001));
            this.K_stable = K_stable;
            this.K_inv = this.K_stable.inverse();

        } else {
            if ((this.opponentBiddingHistory.size() + 1) % (UPDATE_PERIOD+5) == 0) {
                evaluatePrediction();
            }
                
            
            this.wasRecentlyUpdated = false;
        }
    }

    @Override
    public BidDetails getBid(final List<BidDetails> bidsInRange) {
        return getBid();
    }

    public BidDetails getBid() {
        return getBidbyHistory(this.opponentBiddingHistory, this.myBiddingHistory);
    }

    public void evaluatePrediction() {
        Matrix observedX = ds.getX();
        Matrix observedY = ds.getY().transpose(); // TODO: Consider gaussian regression per issue.
        Integer unObservedStart = observedX.getRowDimension() + 1;
        Integer unObservedEnd = this.opponentBiddingHistory.size() - 1;
        List<BidDetails> unObservedBids = this.opponentBiddingHistory.subList(unObservedStart, unObservedEnd);
        Matrix unObservedX = getMatrixRepresentation(unObservedBids,
                this.myBiddingHistory.subList(unObservedStart, unObservedEnd)).getFirst();

        // Using the Gaussian process regression method to generate the prediction.
        Matrix[] prediction = predictGaussianProcess(observedX, observedY, unObservedX);
        double[] predictedUtilities = prediction[0].getRowPackedCopy();
        double[] actualUtilities = unObservedBids.stream().mapToDouble(bid -> bid.getMyUndiscountedUtil()).toArray();
        this.statContainer.put(actualUtilities, predictedUtilities);
    }

    @Override
    public BidDetails getBid(OutcomeSpace space, Range range) {
        BidDetails tmp = super.getBid(space, range);
        return tmp == null ? this.getBid() : tmp;
    }

    public Double getMSE() {
        Double MSE = this.statContainer.entrySet().parallelStream()
                .flatMapToDouble(entry -> IntStream.range(0, entry.getKey().length - 1)
                        .mapToDouble(myInt -> Math.abs(entry.getKey()[myInt] - entry.getValue()[myInt])))
                .average().getAsDouble();
        System.out.println("MSE of the prediction: "+ MSE);
        return MSE;
    }

    @Override
    public BidDetails getBid(SortedOutcomeSpace space, double targetUtility) {
        BidDetails tmp = super.getBid(space, targetUtility);
        return tmp == null ? this.getBid() : tmp;
    }

    // Predict the next opponent bid from the opponent bid history and the agent bid
    // history.
    public BidDetails getBidbyHistory(List<BidDetails> oppBidList, List<BidDetails> agBidList) {
        updateModel();
        int currentSize = this.opponentBiddingHistory.size();
        if (currentSize <= START_PREDICTING)
            return currentSize > 0 ? this.opponentBiddingHistory.get(currentSize - 1)
                    : this.outcomeSpace.getMinBidPossible();
        Matrix observedX = ds.getX();
        Matrix observedY = ds.getY().transpose(); // TODO: Consider gaussian regression per issue.
        Matrix unObservedX = getMatrixRepresentation(oppBidList, agBidList).getFirst();

        // Using the Gaussian process regression method to generate the prediction.
        Matrix[] prediction = predictGaussianProcess(observedX, observedY, unObservedX);
        Matrix predictedUtilities = prediction[0];
        Matrix variances = prediction[1];
        double mean = predictedUtilities.getRowPackedCopy()[predictedUtilities.getRowDimension() - 1];
        double var = variances.get(variances.getRowDimension() - 1, variances.getColumnDimension() - 1);

        double sampleUtility = mean;
        BidDetails sampledBid = this.negotiationSession.getOutcomeSpace().getBidNearUtility(sampleUtility);

        if (IS_VERBOSE)
            System.out.println("Predicted Bid: " + sampledBid + "\n" + sampledBid.getBid());
        return sampledBid;
    }

    public Pair<Matrix, Matrix> getMatrixRepresentation(List<BidDetails> oppBidList, List<BidDetails> agBidList) {

        BidHistory bidHistoryOpp = new BidHistory(oppBidList);
        BidHistory bidHistoryAg = new BidHistory(agBidList);
        Matrix observedXOpponent = this.encoder.encode(bidHistoryOpp);
        Matrix observedXAgent = this.encoder.encode(bidHistoryAg);
        Integer numRow = observedXOpponent.getRowDimension();
        Integer shift = observedXOpponent.getColumnDimension();
        Integer numCol = observedXOpponent.getColumnDimension() + observedXAgent.getColumnDimension();

        Matrix X = new Matrix(numRow, numCol + 1);
        X.setMatrix(0, numRow - 1, 0, shift - 1, observedXOpponent);
        X.setMatrix(0, numRow - 1, shift, numCol - 1, observedXAgent);
        for (int i = 0; i < numRow; i++) {
            double bidTime = oppBidList.get(i).getTime();
            double currTime = this.negotiationSession.getTime();
            double timePerBid = currTime / this.opponentBiddingHistory.size();
            X.set(i, numCol, bidTime > 0 ? bidTime : currTime + i * timePerBid);
        }

        Matrix Y = new Matrix(1, numRow);
        for (int i = 0; i < numRow; i++) {
            Y.set(0, i, this.opponentBiddingHistory.get(i + 1).getMyUndiscountedUtil());
        }

        return new Pair<Matrix, Matrix>(X, Y);
    }

    @Override
    public boolean canUpdateOM() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getName() {
        return CarlosComponentNames.SMART_OPPONENT_BIDDING_STRATEGY.toString();
    }

    private Matrix[] predictGaussianProcess(Matrix observedX, Matrix observedY, Matrix unObservedX) {
        Matrix K_star = computeCovarianceMatrix(unObservedX, observedX);
        Matrix K_star_star = computeCovarianceMatrix(unObservedX, unObservedX);

        Matrix predictedY = K_star.times(this.K_inv).times(observedY);
        Matrix variances = K_star_star.minus(K_star.times(this.K_inv).times(K_star.transpose()));
        Matrix[] results = { predictedY, variances };

        return results;
    }

    private Matrix computeCovarianceMatrix(Matrix A, Matrix B) {
        Integer rowLenA = A.getRowDimension();
        Integer rowLenB = B.getRowDimension();
        Integer colLenA = A.getColumnDimension() - 1;
        Integer colLenB = B.getColumnDimension() - 1;
        Matrix covMatrix = new Matrix(rowLenA, rowLenB);
        for (Integer i = 0; i < rowLenA; i++) {
            for (Integer j = 0; j < rowLenB; j++) {
                int[] rowIdxA = { i };
                int[] rowIdxB = { j };
                covMatrix.set(i, j, getRbfDistance(A.getMatrix(rowIdxA, 0, colLenA), B.getMatrix(rowIdxB, 0, colLenB)));
            }
        }

        return covMatrix;
    }

    private Double getRbfDistance(Matrix A, Matrix B) {
        Double distance = getEuclidianDistance(A, B);
        return Math.exp(-distance / 2);
    }

    private Double getRbfDistance(Matrix A, Matrix B, Double gamma) {
        Double distance = getEuclidianDistance(A, B);
        return Math.exp(-distance / (2 * Math.pow(gamma, 2)));
    }

    private Double getEuclidianDistance(Matrix A, Matrix B) {
        Matrix aSquared = A.times(A.transpose());
        Matrix bSquared = B.times(B.transpose());
        Matrix interaction = A.times(B.transpose()).times(2);
        double distance = aSquared.plus(bSquared).minus(interaction).get(0, 0);
        return distance;
    }

    private class DataSet {
        Matrix X;
        Matrix Y;
        Matrix X_star;
        Bid predictedResult;

        public DataSet() {
        }

        public Matrix getX() {
            return X;
        }

        public DataSet setX(Matrix x) {
            X = x;
            return this;
        }

        public Matrix getY() {
            return Y;
        }

        public DataSet setY(Matrix y) {
            Y = y;
            return this;
        }

        public Matrix getX_star() {
            return X_star;
        }

        public void setX_star(Matrix x_star) {
            X_star = x_star;
        }

        public Bid getPredictedResult() {
            return predictedResult;
        }

        public DataSet setPredictedResult(Bid predictedResult) {
            this.predictedResult = predictedResult;
            return this;
        }

        @Override
        public String toString() {
            return predictedResult + "";
        }

    }

}