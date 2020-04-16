package main;

import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.*;
import math.Matrix;
import misc.Utils;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * SmartOpponentOfferingModel
 */
public class SmartOpponentOfferingModel extends OMStrategy {

    private BidHistory opponentBiddingHistory;
    private BidHistory myBiddingHistory;
    private HashMap<Issue, Matrix> issueStatistics;
    private Double[] issueWeights;
    private List<IssueDiscrete> domainIssues;
    private Map<Issue, ISSUETYPE> issueTypes;

    @Override
    public void init(final NegotiationSession negotiationSession, final OpponentModel model,
            final Map<String, Double> parameters) {
        super.init(negotiationSession, model, parameters);
        this.myBiddingHistory = negotiationSession.getOwnBidHistory();
        this.opponentBiddingHistory = negotiationSession.getOpponentBidHistory();
        this.domainIssues = negotiationSession.getIssues().parallelStream().map(value -> (IssueDiscrete) value)
                .collect(Collectors.toList());
        // this.issueTypes = this.domainIssues.stream().map(issue -> new
        // AbstractMap.SimpleEntry<Issue, ISSUETYPE>(issue,
        // issue.getType())).collect(Collectors.toMap(Map.Entry::getKey,
        // Map.Entry::getValue));

    }

    @Override
    public BidDetails getBid(final List<BidDetails> bidsInRange) {
        if (this.opponentBiddingHistory.size() == 0)
            return null;
        // System.out.println("Starting the prediction process");
        List<BidDetails> o = this.opponentBiddingHistory.getHistory();
        List<BidDetails> a = this.myBiddingHistory.getHistory();
        return getBidbyHistory(o,a);
    }

    public DataSet getMatrixRepresentation(List<BidDetails> oppBidList, List<BidDetails> agBidList) {
        List<BidDetails> opponent = oppBidList; // Xo + Xo*
        List<BidDetails> agent = agBidList; // Xa + Xa*
        // TODOs: Use skip instead
        int osize = opponent.size();
        int asize = agent.size();
        if (asize < osize) {
            agent.add(0, agent.get(0));
        }


        BidHistory slicedXOpponent = new BidHistory(opponent.subList(0, osize > 1 ? osize - 1 : osize));  // Xo 
        BidHistory shiftedOpponentBidHistory = new BidHistory(opponent.subList(osize > 1 ? 1 : 0, osize)); // Y

        BidHistory newXOpponent = new BidHistory(opponent.subList(osize - 1, osize)); // X*
        BidHistory slicedXAgent = new BidHistory(agent.subList(0, asize > 1 ? asize - 1 : asize)); // Xa
        // BidHistory shiftedAgentBidHistory = new BidHistory(agent.subList(1,
        // agent.size()));
        // BidHistory newXAgent = new BidHistory(agent.subList(agent.size()-1,
        // agent.size()));

        Matrix observedXOpponent = converHistoryToMatrix(slicedXOpponent);
        Matrix observedXAgent = converHistoryToMatrix(slicedXAgent);
        Integer numRow = observedXOpponent.getRowDimension();
        Integer shift = observedXOpponent.getColumnDimension();
        Integer numCol = observedXOpponent.getColumnDimension() + observedXAgent.getColumnDimension();

        Matrix X = new Matrix(numRow, numCol);
        X.setMatrix(0, numRow - 1, 0, shift - 1, observedXOpponent);
        X.setMatrix(0, numRow - 1, shift, numCol - 1, observedXAgent);
        Utils.printMatrix(X);
        Matrix Y = converHistoryToMatrix(shiftedOpponentBidHistory);
        Matrix X_star = converHistoryToMatrix(newXOpponent);
        // TODO: Consider doing gaussian regression per issue.
        // Matrix observedYAgent = converHistoryToMatrix(shiftedAgentBidHistory); //
        // Matrix unObservedXAgent = converHistoryToMatrix(newXAgent);

        return new DataSet(X, Y, X_star);
    }

    public BidDetails getBidbyHistory(List<BidDetails> oppBidList, List<BidDetails> agBidList) {
        // BidHistory shiftedOpponentBidHistory = new BidHistory(tmp.subList(1, tmp.size()));
        // BidHistory slicedX = new BidHistory(tmp.subList(0, tmp.size() - 1));
        // BidHistory newX = new BidHistory(tmp.subList(tmp.size() - 1, tmp.size()));
        DataSet ds = getMatrixRepresentation(oppBidList, agBidList);
        AtomicInteger ai = new AtomicInteger();
        List<Integer> sizes = this.domainIssues.stream().map(issue -> issue.getNumberOfValues()).map(ai::addAndGet)
                .collect(Collectors.toList());
        sizes.add(0, 0);

        // Matrix observedXa = converHistoryToMatrix(slicedX);
        // Matrix observedYa = converHistoryToMatrix(shiftedOpponentBidHistory);
        // Matrix unObservedXa = converHistoryToMatrix(newX);

        Matrix observedX = ds.getX();
        Matrix observedY = ds.getY(); // TODO: Consider gaussian regression per issue.
        Matrix unObservedX = ds.getX_star();

        Matrix[] prediction = predictGaussianProcess(observedX, observedY, unObservedX);

        // System.out.println("Predictions:
        // "+Arrays.toString(prediction[0].getRowPackedCopy()));

        Map<IssueDiscrete, Matrix> splittedPredictions = IntStream.range(0, sizes.size() - 1)
                .mapToObj(idx -> new SimpleEntry<IssueDiscrete, Matrix>(this.domainIssues.get(idx),
                        prediction[0].getMatrix(0, prediction[0].getRowDimension() - 1, sizes.get(idx),
                                sizes.get(idx) - 1)))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        Map<IssueDiscrete, List<Integer>> tmpList = splittedPredictions.entrySet().stream()
                .map(e -> new SimpleEntry<IssueDiscrete, List>(e.getKey(),
                        simplifiedClosestIssueValues(e.getKey(), e.getValue())))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        Bid nextBid = constructBid(tmpList);
        BidDetails result = new BidDetails(nextBid, negotiationSession.getUtilitySpace().getUtility(nextBid)); // TODO:
                                                                                                               // Deal
                                                                                                               // with
                                                                                                               // preference
                                                                                                               // uncertainty!
        // System.out.println(nextBid.toStringCSV());
        return result;
    }

    private Bid constructBid(Map<IssueDiscrete, List<Integer>> tmpList) {
        HashMap<Integer, Value> issueValues = (HashMap<Integer, Value>) tmpList.entrySet().stream()
                .map(e -> new SimpleEntry<IssueDiscrete, Integer>(e.getKey(), e.getValue().get(0)))
                .map(e -> new SimpleEntry<Integer, Value>(e.getKey().getNumber(), e.getKey().getValue(e.getValue())))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)); // new ValueDiscrete(name)
        Bid result = new Bid(negotiationSession.getDomain(), issueValues);
        return result;
    }

    @Override
    public boolean canUpdateOM() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getName() {
        return SmartComponentNames.SMART_OPPONENT_BIDDING_STRATEGY.toString();
    }

    private Matrix getIssueWeights() {
        // if(SmartComponentNames.SMART_OPPONENT_MODEL.toString().equals(model.getName())){
        final double[] tmp = model.getIssueWeights();
        return new Matrix(tmp, 1);
        // }
    }

    // private List<Bid> mapDummyEncodingToClosestBid(List<Matrix>
    // dummyEncodedPredictions){

    // return null;
    // }

    private List<Integer> closestIssueValues(IssueDiscrete issue, Matrix issuePredictions) {
        Integer numberOfDifferentValues = issue.getNumberOfValues();
        Matrix allPossibleIssueValues = Matrix.identity(numberOfDifferentValues, numberOfDifferentValues);
        Matrix normalizationConstantsByRow = new Matrix(Stream.of(issuePredictions.getArrayCopy())
                .mapToDouble(row -> Arrays.stream(row).parallel().sum()).toArray(), 1);
        Matrix normalizedIssuePredictions = issuePredictions.arrayRightDivide(normalizationConstantsByRow);
        Matrix cosineSimilarity = normalizedIssuePredictions.times(allPossibleIssueValues);
        List<Integer> highestIndexPerRow = Stream.of(cosineSimilarity.getArrayCopy())
                .mapToInt(row -> IntStream.range(0, row.length).boxed().reduce((a, b) -> row[a] < row[b] ? b : a).get())
                .boxed().collect(Collectors.toList());
        // TODO: Calculate cosine distance and take the lowest between each row in
        // allpossible issues and dummyEncodedIssues.

        return highestIndexPerRow;
    }

    private List<Integer> simplifiedClosestIssueValues(IssueDiscrete issue, Matrix issuePredictions) {
        List<Integer> highestIndexPerRow = Stream.of(issuePredictions.getArrayCopy()).mapToInt(
                row -> IntStream.range(0, row.length).boxed().reduce((a, b) -> row[a] < row[b] ? b : a).orElse(0))
                .boxed().collect(Collectors.toList());
        // TODO: Calculate cosine distance and take the lowest between each row in
        // allpossible issues and dummyEncodedIssues.

        return highestIndexPerRow;
    }

    private Matrix converHistoryToMatrix(final BidHistory bidHistory) {
        final List<Bid> lBids = bidHistory.getHistory().stream().map(bd -> bd.getBid()).collect(Collectors.toList());

        // Reduction 1
        // final HashMap<Issue, Matrix> oneHotMatrix = new HashMap<>();
        // Integer noOfValues = null;
        // List<Integer> issueValues = null;

        // for (final IssueDiscrete issue : this.domainIssues) {
        // issueValues = lBids.stream().map(bid -> bid.getValue(issue))
        // .map(value -> issue.getValueIndex((ValueDiscrete) value))
        // .collect(Collectors.toList());
        // noOfValues = issue.getNumberOfValues();
        // oneHotMatrix.put(issue, dummyEncode(noOfValues, issueValues));
        // }

        // Reduction 2
        // Map<Issue,List<Integer>> valuesByIssue = this.domainIssues.stream()
        // .map(issue -> new SimpleEntry<Issue,List<Integer>>(issue,
        // extractAllValuesForIssue(lBids, issue)))
        // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        // Map<Issue,Matrix> oneHotEncodedMatrixByIssue = this.domainIssues.stream()
        // .map(issue -> new
        // SimpleEntry<Issue,Matrix>(issue,dummyEncode(issue.getNumberOfValues(),
        // valuesByIssue.get(issue))))
        // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        final Map<Issue, Matrix> oneHotEncodedMatrixByIssue = this.domainIssues.stream() // Order might be lost
                .map(issue -> new SimpleEntry<IssueDiscrete, List<Integer>>(issue,
                        extractAllValuesForIssue(lBids, issue)))
                .map(entry -> new SimpleEntry<IssueDiscrete, Matrix>(entry.getKey(),
                        dummyEncode(entry.getKey(), entry.getValue())))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        final List<List<Double>> tmp = IntStream.range(0, lBids.size())
                .mapToObj(i -> extractFullRow(i, oneHotEncodedMatrixByIssue)).collect(Collectors.toList());
        final double[][] preFullMatrix = tmp.stream()
                .map(arr -> arr.stream().mapToDouble(Double::doubleValue).toArray()).collect(Collectors.toList())
                .stream().toArray(double[][]::new);
        final Matrix fullMatrix = new Matrix(preFullMatrix);

        return fullMatrix;
    }

    private Matrix dummyEncode(final IssueDiscrete issue, final List<Integer> issueValues) {
        final Matrix containerMatrix = new Matrix(issueValues.size(), issue.getNumberOfValues());
        // System.out.println("============> " + issue.getName());
        for (int row = 0; row < issueValues.size(); row++) {
            // System.out.println(row + ": "+ issueValues.get(row) + " - "+
            // issue.getStringValue(issueValues.get(row)));
            containerMatrix.set(row, issueValues.get(row), 1);
        }

        return containerMatrix;
    }

    private List<Double> extractFullRow(final Integer row, final Map<Issue, Matrix> oneHotMatrix) {
        return this.domainIssues.stream().map(issue -> oneHotMatrix.get(issue).getArray()[row])
                .flatMapToDouble(Arrays::stream).boxed().collect(Collectors.toList());
    }

    private List<Integer> extractAllValuesForIssue(List<Bid> lBids, IssueDiscrete issue) {
        return lBids.stream().map(bid -> (ValueDiscrete) bid.getValue(issue)).map(value -> issue.getValueIndex(value))
                .collect(Collectors.toList());
    }

    private Matrix[] predictGaussianProcess(Matrix observedX, Matrix observedY, Matrix unObservedX) {
        Matrix K = computeCovarianceMatrix(observedX, observedY);
        Matrix K_stable = K.plus(Matrix.identity(K.getRowDimension(), K.getColumnDimension()).times(0.00001));
        Matrix K_star = computeCovarianceMatrix(unObservedX, observedX);
        Matrix K_star_star = computeCovarianceMatrix(unObservedX, unObservedX);

        Matrix predictedY = K_star.times(K_stable.inverse()).times(observedY);
        Matrix variances = K_star_star.minus(K_star.times(K_stable.inverse()).times(K_star.transpose()));
        Matrix[] results = { predictedY, variances };
        return results;
    }

    private Matrix computeCovarianceMatrix(Matrix A, Matrix B) {
        // Double distance = euclidianDistance(A, B);
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
        // Double aSquared = A.norm2();
        // Double bSquared = B.norm2();
        // Double interaction = A.times(B.transpose()).times(2).get(0, 0);
        double distance = aSquared.plus(bSquared).minus(interaction).get(0, 0);
        // double[][] distance = aSquared.plus(bSquared).minus(interaction).getArray();
        return distance;
    }

    private class DataSet {
        Matrix X;
        Matrix Y;
        Matrix X_star;

        public DataSet(Matrix x, Matrix y, Matrix x_star) {
            X = x;
            Y = y;
            X_star = x_star;
        }

        public Matrix getX() {
            return X;
        }

        public void setX(Matrix x) {
            X = x;
        }

        public Matrix getY() {
            return Y;
        }

        public void setY(Matrix y) {
            Y = y;
        }

        public Matrix getX_star() {
            return X_star;
        }

        public void setX_star(Matrix x_star) {
            X_star = x_star;
        }

    }

}