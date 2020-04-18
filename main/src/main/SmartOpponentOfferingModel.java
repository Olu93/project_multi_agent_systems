package main;

import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OMStrategy;
import genius.core.boaframework.OpponentModel;
import genius.core.boaframework.OutcomeSpace;
import genius.core.boaframework.SortedOutcomeSpace;
import genius.core.issue.*;
import genius.core.misc.Range;
import math.Matrix;
import misc.BidEncoder;
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

import agents.anac.y2015.agenth.BidHistory.Entry;

/**
 * SmartOpponentOfferingModel
 */
public class SmartOpponentOfferingModel extends OMStrategy {

    private BidHistory opponentBiddingHistory;
    private BidHistory myBiddingHistory;
    private HashMap<Issue, Matrix> issueStatistics;
    private Double[] issueWeights;
    private Map<Issue, ISSUETYPE> issueTypes;
    private List<ValueDiscrete> listOfAllPossibleValues;
    private BidEncoder encoder;

    @Override
    public void init(final NegotiationSession negotiationSession, final OpponentModel model,
            final Map<String, Double> parameters) {
        super.init(negotiationSession, model, parameters);
        this.myBiddingHistory = negotiationSession.getOwnBidHistory();
        this.opponentBiddingHistory = negotiationSession.getOpponentBidHistory();

        // this.listOfAllPossibleValues = negotiationSession.getIssues()
        // .stream()
        // .map(issue -> (IssueDiscrete) issue)
        // .flatMap(issue -> issue.getValues().stream())
        // .collect(Collectors.toList()); // (is1v1, i1v2, i2v1) <- i1v2? => 1

        this.encoder = new BidEncoder(negotiationSession);

        // this.issueTypes = this.domainIssues.stream().map(issue -> new
        // AbstractMap.SimpleEntry<Issue, ISSUETYPE>(issue,
        // issue.getType())).collect(Collectors.toMap(Map.Entry::getKey,
        // Map.Entry::getValue));

    }

    @Override
    public BidDetails getBid(final List<BidDetails> bidsInRange) {
        return getBid();
    }

    public BidDetails getBid() {
        if (this.opponentBiddingHistory.size() == 0)
            return this.negotiationSession.getMaxBidinDomain();
        List<BidDetails> o = this.opponentBiddingHistory.getHistory();
        System.out.println(o.get(o.size() - 1).getBid());
        List<BidDetails> a = this.myBiddingHistory.getHistory();
        return getBidbyHistory(o, a);
    }

    @Override
    public BidDetails getBid(OutcomeSpace space, Range range) {
        BidDetails tmp = super.getBid(space, range);
        return tmp == null ? this.getBid() : tmp;
    }

    @Override
    public BidDetails getBid(SortedOutcomeSpace space, double targetUtility) {
        BidDetails tmp = super.getBid(space, targetUtility);
        return tmp == null ? this.getBid() : tmp;
    }

    public DataSet getMatrixRepresentation(List<BidDetails> oppBidList, List<BidDetails> agBidList) {
        // System.out.println("");
        // System.out.println("==========New Round=======================");

        List<BidDetails> opponent = oppBidList; // Xo + Xo*
        List<BidDetails> agent = agBidList; // Xa + Xa*
        // TODOs: Use skip instead
        int osize = opponent.size();
        int asize = agent.size();
        if (asize < osize) {
            agent.add(0, agent.get(0));
            asize = agent.size();
        }

        BidHistory slicedXOpponent = new BidHistory(opponent.subList(0, osize > 1 ? osize - 1 : osize)); // Xo
        BidHistory shiftedOpponentBidHistory = new BidHistory(opponent.subList(osize > 1 ? 1 : 0, osize)); // Y

        BidHistory newXOpponent = new BidHistory(opponent.subList(osize - 1, osize)); // X*
        BidHistory newXAgent = new BidHistory(agent.subList(asize - 1, asize));
        BidHistory slicedXAgent = new BidHistory(agent.subList(0, asize > 1 ? asize - 1 : asize)); // Xa

        Matrix observedXOpponent = this.encoder.encode(slicedXOpponent);
        Matrix observedXAgent = this.encoder.encode(slicedXAgent);
        Integer numRow = observedXOpponent.getRowDimension();
        Integer shift = observedXOpponent.getColumnDimension();
        Integer numCol = observedXOpponent.getColumnDimension() + observedXAgent.getColumnDimension();

        Matrix X = new Matrix(numRow, numCol + 1);
        X.setMatrix(0, numRow - 1, 0, shift - 1, observedXOpponent);
        X.setMatrix(0, numRow - 1, shift, numCol - 1, observedXAgent);
        for (int i = 0; i < numRow; i++) {
            X.set(i, numCol, new Double(i) / new Double(numRow));
        }

        // System.out.println("X");
        // Utils.printMatrix(X);
        Matrix Y = this.encoder.encode(shiftedOpponentBidHistory);
        // System.out.println("Y");
        // Utils.printMatrix(Y);
        Matrix opponentX_star = this.encoder.encode(newXOpponent);
        Matrix agentX_star = this.encoder.encode(newXAgent);

        Matrix X_star = new Matrix(1, numCol + 1);
        X_star.setMatrix(0, 0, 0, shift - 1, opponentX_star);
        X_star.setMatrix(0, 0, shift, numCol - 1, agentX_star);
        X_star.set(0, numCol, 1.0);
        // System.out.println("X*");
        // Utils.printMatrix(X_star);
        // TODO: Consider doing gaussian regression per issue.

        return new DataSet(X, Y, X_star);
    }

    public BidDetails getBidbyHistory(List<BidDetails> oppBidList, List<BidDetails> agBidList) {
        // BidHistory shiftedOpponentBidHistory = new BidHistory(tmp.subList(1,
        // tmp.size()));
        // BidHistory slicedX = new BidHistory(tmp.subList(0, tmp.size() - 1));
        // BidHistory newX = new BidHistory(tmp.subList(tmp.size() - 1, tmp.size()));
        DataSet ds = getMatrixRepresentation(oppBidList, agBidList);
        AtomicInteger ai = new AtomicInteger();
        List<Integer> sizes = this.encoder.getDomainIssues().stream().map(issue -> issue.getNumberOfValues())
                .map(ai::addAndGet).collect(Collectors.toList());
        sizes.add(0, 0);

        // Matrix observedXa = converHistoryToMatrix(slicedX);
        // Matrix observedYa = converHistoryToMatrix(shiftedOpponentBidHistory);
        // Matrix unObservedXa = converHistoryToMatrix(newX);

        Matrix observedX = ds.getX();
        Matrix observedY = ds.getY(); // TODO: Consider gaussian regression per issue.
        Matrix unObservedX = ds.getX_star();

        Matrix[] prediction = predictGaussianProcess(observedX, observedY, unObservedX);

        // System.out.println("X: "+Arrays.toString(observedX.getArray()));
        // System.out.println("Y: "+Arrays.toString(observedY.getArray()));
        // System.out.println("X_star: "+Arrays.toString(unObservedX.getArray()));

        // System.out.println("Predictions:
        // "+Arrays.toString(prediction[0].getRowPackedCopy()));

        // Map<IssueDiscrete, Matrix> splittedPredictions = IntStream.range(0,
        // sizes.size() - 1)
        // .mapToObj(idx -> new SimpleEntry<IssueDiscrete,
        // Matrix>(this.domainIssues.get(idx),
        // prediction[0].getMatrix(0, prediction[0].getRowDimension() - 1,
        // sizes.get(idx),
        // sizes.get(idx) - 1)))
        // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        // Map<IssueDiscrete, List<Integer>> tmpList =
        // splittedPredictions.entrySet().stream()
        // .map(e -> new SimpleEntry<IssueDiscrete, List>(e.getKey(),
        // simplifiedClosestIssueValues(e.getKey(), e.getValue())))
        // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        Matrix closestBid = getClosestOneHotRow(prediction[0]);

        Bid nextBid = this.encoder.decode(closestBid);
        BidDetails result = new BidDetails(nextBid, negotiationSession.getUtilitySpace().getUtility(nextBid));
        // System.out.println(Arrays.toString(prediction[0].getRowPackedCopy()));
        // System.out.println(ds.setPredictedResult(nextBid));

        // TODO:
        // Deal
        // with
        // preference
        // uncertainty!
        // System.out.println(nextBid.toStringCSV());
        return result;
    }

    private Matrix closestBid(Matrix matrix) {
        return null;
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
    private Matrix getClosestOneHotRow(Matrix prediction) {
        double[] row = prediction.getRowPackedCopy();
        HashMap<IssueDiscrete, Integer> highestIdx = new HashMap<>();
        HashMap<IssueDiscrete, Double> highestVal = new HashMap<>();
        List<Integer> currIndices = null;
        Double currentValue = null;
        IssueDiscrete tmpIssueDiscrete = null;
        Matrix oneHotRow = new Matrix(1, row.length);

        for (IssueDiscrete issue : this.encoder.getDomainIssues()) {
            highestIdx.put(issue, this.encoder.getIndicesByIssue(issue).get(0));
            highestVal.put(issue, 0.0);
            currIndices = this.encoder.getIndicesByIssue(issue);
            for (Integer i : currIndices) {
                currentValue = row[i];
                tmpIssueDiscrete = this.encoder.getIssueByIndex(i);
                if (highestVal.get(tmpIssueDiscrete).compareTo(currentValue) <= 0) {
                    highestIdx.put(tmpIssueDiscrete, i);
                    highestVal.put(tmpIssueDiscrete, currentValue);
                }
            }
            if (highestIdx == null || oneHotRow == null || highestIdx.get(issue) == null)
                System.out.println("Something");
            oneHotRow.set(0, highestIdx.get(issue), 1);
        }
        return oneHotRow;
    }

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

    // private Matrix converHistoryToMatrix(final BidHistory bidHistory) {
    // final List<Bid> lBids = bidHistory.getHistory().stream().map(bd ->
    // bd.getBid()).collect(Collectors.toList());

    // final Map<Issue, Matrix> oneHotEncodedMatrixByIssue =
    // this.domainIssues.stream() // Order might be lost
    // .map(issue -> new SimpleEntry<IssueDiscrete, List<Integer>>(issue,
    // extractAllValuesForIssue(lBids, issue)))
    // .map(entry -> new SimpleEntry<IssueDiscrete, Matrix>(entry.getKey(),
    // dummyEncode(entry.getKey(), entry.getValue())))
    // .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

    // final List<List<Double>> tmp = IntStream.range(0, lBids.size())
    // .mapToObj(i -> extractFullRow(i,
    // oneHotEncodedMatrixByIssue)).collect(Collectors.toList());
    // final double[][] preFullMatrix = tmp.stream()
    // .map(arr ->
    // arr.stream().mapToDouble(Double::doubleValue).toArray()).collect(Collectors.toList())
    // .stream().toArray(double[][]::new);
    // final Matrix fullMatrix = new Matrix(preFullMatrix);

    // return fullMatrix;
    // }

    // private Matrix converHistoryToMatrix(final BidHistory bidHistory) {
    // List<Bid> lBids = bidHistory.getHistory().stream().map(b ->
    // b.getBid()).collect(Collectors.toList());
    // Matrix fullMatrix = new Matrix(lBids.size(), listOfAllPossibleValues.size());
    // for (int i = 0; i < lBids.size(); i++) {
    // for (int j = 0; j < lBids.get(i).getIssues().size(); j++) {
    // IssueDiscrete currIssue = (IssueDiscrete) lBids.get(i).getIssues().get(j);
    // ValueDiscrete val = (ValueDiscrete) lBids.get(i).getValue(currIssue);
    // Integer pos = listOfAllPossibleValues.indexOf(val);
    // fullMatrix.set(i, pos, 1);
    // }
    // }
    // return fullMatrix;
    // }

    // private Bid convertPredictionToBid(final Matrix row){
    // double[] oneHot = row.getRowPackedCopy();
    // HashMap<Integer,Value> bidValues = new HashMap<>();
    // for (int i = 0; i < oneHot.length; i++) {
    // ValueDiscrete val = listOfAllPossibleValues.get(i);
    // for (IssueDiscrete issueDiscrete : this.domainIssues) {
    // if(issueDiscrete.checkInRange(val)) bidValues.put(issueDiscrete.getNumber(),
    // val);
    // }
    // }

    // return new Bid(negotiationSession.getDomain(), bidValues);
    // }

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
        return this.encoder.getDomainIssues().stream().map(issue -> oneHotMatrix.get(issue).getArray()[row])
                .flatMapToDouble(Arrays::stream).boxed().collect(Collectors.toList());
    }

    private List<Integer> extractAllValuesForIssue(List<Bid> lBids, IssueDiscrete issue) {
        return lBids.stream().map(bid -> (ValueDiscrete) bid.getValue(issue)).map(value -> issue.getValueIndex(value))
                .collect(Collectors.toList());
    }

    private Matrix[] predictGaussianProcess(Matrix observedX, Matrix observedY, Matrix unObservedX) {
        Matrix K = computeCovarianceMatrix(observedX, observedX);
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
        Bid predictedResult;

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

        public Bid getPredictedResult() {
            return predictedResult;
        }

        public DataSet setPredictedResult(Bid predictedResult) {
            this.predictedResult = predictedResult;
            return this;
        }

        @Override
        public String toString() {
            // return Arrays.toString(X_star.getRowPackedCopy()) + " =>\n " +
            // predictedResult;
            return predictedResult + "";
        }

    }

}