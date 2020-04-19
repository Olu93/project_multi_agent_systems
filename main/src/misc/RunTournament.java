package misc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.bind.JAXBException;

import org.apache.commons.math3.util.Pair;

import genius.cli.Runner;
import genius.core.exceptions.InstantiateException;

public class RunTournament {

    final String HEADER = "Run time (s);Round;Exception;deadline;Agreement;Discounted;#agreeing;min.util.;max.util.;Dist. to Pareto;Dist. to Nash;Social Welfare;Agent 1;Agent 2;Utility 1;Utility 2;Disc. Util. 1;Disc. Util. 2;Perceived. Util. 1;Perceived. Util. 2;Profile 1;Profile 2";

    public static void main(String[] args) throws JAXBException, IOException, InstantiateException {
        // String[] params = { "lib\\tournament.xml", "logs\\loggedTournamentStatistics"
        // };

        Runner.main(args);
        Reader in = new FileReader(args[1] + ".csv");
        BufferedReader csvReader = new BufferedReader(in);
        List<DataLine> extractedData = new ArrayList<>();
        String row = null;
        String[] header = null;

        while ((row = csvReader.readLine()) != null) {
            if (header == null || header.length < 3) {
                header = row.split(";");
                continue;
            }
            String[] data = row.replace(",", ".").split(";");
            extractedData.add(new DataLine(header, data));
        }

        csvReader.close();
        // System.out.println(Arrays.toString(header));
        // printAllStatistics("AgentBoaParty", extractedData);
        // printAllStatistics("SmartAgent", extractedData);
        // printAllStatistics("NiceTitForTat", extractedData);
        // printAllStatistics("BoulwareNegotiationParty", extractedData);
        // printAllStatistics("ConcederNegotiationParty", extractedData);
        // printAllStatistics("BRAMAgent", extractedData);
        // printAllStatistics("KLH", extractedData);
        // printAllStatistics("IAMhaggler2012", extractedData);
        // printAllStatistics("BayesianAgent", extractedData);
        // printAllStatistics("BayesianAgent", extractedData);

        printRanking(extractedData);
    }

    private static void printRanking(List<DataLine> extractedData) {
        System.out.println("");
        List<String> agentNames = extractedData.stream().map(row -> row.get("Agent 1").split("@")[0]).distinct()
                .collect(Collectors.toList());
        agentNames.stream().forEach(name -> printAllStatistics(name, extractedData));
        System.out.println("============= RANKING =============");
        agentNames.stream().map(name -> new SimpleEntry<>(name, getSumUtility(extractedData, name)))
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .peek(entry -> System.out.println(entry.getKey() + ":" + entry.getValue()))
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
    }

    private static void printAllStatistics(String agent, List<DataLine> extractedData) {
        if (extractedData.stream().map(row -> row.get("Agent 1").split("@")[0]).filter(name -> name.contains(agent))
                .collect(Collectors.toList()).size() < 1)
            return;

        System.out.println("==========================");
        System.out.println(agent + " played " + getNumberOfNegotiations(extractedData, agent) + " games");
        System.out.println("                Sum of " + agent + ": " + getSumUtility(extractedData, agent));
        System.out.println("    Perceveived Sum of " + agent + ": " + getSumPerceivedUtility(extractedData, agent));
        System.out.println("         Robustness of " + agent + ": " + getUtilityRobustness(extractedData, agent));
        System.out.println("     Avg. Agreement of " + agent + ": " + getAvgAgreement(extractedData, agent));
        System.out.println("Avg. ParetoDistance of " + agent + ": " + getAvgDistanceToPareto(extractedData, agent));
        System.out.println("Avg. Agreement time of " + agent + ": " + getAvgTimeToAgree(extractedData, agent));
    }

    private static Double getSumUtility(List<DataLine> data, String extract) {
        return data.stream().filter(row -> row.get("Agent 1").contains(extract))
                .mapToDouble(row -> Double.parseDouble(row.get("Utility 1"))).sum()
                + data.stream().filter(row -> row.get("Agent 2").contains(extract))
                        .mapToDouble(row -> Double.parseDouble(row.get("Utility 2"))).sum();
    }

    private static Long getNumberOfNegotiations(List<DataLine> data, String extract) {
        return data.stream().filter(row -> row.get("Agent 1").contains(extract) || row.get("Agent 2").contains(extract))
                .mapToInt(row -> 1).count();
    }

    private static Double getUtilityRobustness(List<DataLine> data, String extract) {
        Stream<Double> stream1 = data.stream().filter(row -> row.get("Agent 1").contains(extract))
                .mapToDouble(row -> Double.parseDouble(row.get("Utility 1"))).boxed();
        Stream<Double> stream2 = data.stream().filter(row -> row.get("Agent 2").contains(extract))
                .mapToDouble(row -> Double.parseDouble(row.get("Utility 2"))).boxed();
        Stream<Double> val = Stream.concat(stream1, stream2);
        return Math.sqrt(val.collect(VARIANCE_COLLECTOR));
    }

    private static Double getSumPerceivedUtility(List<DataLine> data, String extract) {
        try {
            return data.stream().filter(row -> row.get("Agent 1").contains(extract))
                    .filter(row -> !row.get("Perceived. Util. 1").isEmpty())
                    .mapToDouble(row -> Double.parseDouble(row.get("Perceived. Util. 1"))).sum()
                    + data.stream().filter(row -> row.get("Agent 2").contains(extract))
                            .filter(row -> !row.get("Perceived. Util. 2").isEmpty())
                            .mapToDouble(row -> Double.parseDouble(row.get("Perceived. Util. 2"))).sum();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private static Double getAvgAgreement(List<DataLine> data, String extract) {
        return data.stream().filter(row -> row.get("Agent 1").contains(extract) || row.get("Agent 2").contains(extract))
                .filter(row -> !row.get("Agreement").isEmpty())
                .mapToDouble(row -> row.get("Agreement").contains("Yes") ? 1 : 0).average().getAsDouble();
    }

    private static Double getAvgDistanceToPareto(List<DataLine> data, String extract) {
        return data.stream().filter(row -> row.get("Agent 1").contains(extract) || row.get("Agent 2").contains(extract))
                .filter(row -> !row.get("Dist. to Pareto").isEmpty())
                .mapToDouble(row -> Double.parseDouble(row.get("Dist. to Pareto"))).average().getAsDouble();
    }

    private static Double getAvgTimeToAgree(List<DataLine> data, String extract) {
        return data.stream().filter(row -> row.get("Agent 1").contains(extract) || row.get("Agent 2").contains(extract))
                .filter(row -> !row.get("Round").isEmpty()).mapToDouble(row -> Double.parseDouble(row.get("Round")))
                .average().getAsDouble();
    }

    private static final Collector<Double, double[], Double> VARIANCE_COLLECTOR = Collector.of( // See
                                                                                                // https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
            () -> new double[3], // {count, mean, M2}
            (acu, d) -> { // See chapter about Welford's online algorithm and
                          // https://math.stackexchange.com/questions/198336/how-to-calculate-standard-deviation-with-streaming-inputs
                acu[0]++; // Count
                double delta = d - acu[1];
                acu[1] += delta / acu[0]; // Mean
                acu[2] += delta * (d - acu[1]); // M2
            }, (acuA, acuB) -> { // See chapter about "Parallel algorithm" : only called if stream is parallel
                                 // ...
                double delta = acuB[1] - acuA[1];
                double count = acuA[0] + acuB[0];
                acuA[2] = acuA[2] + acuB[2] + delta * delta * acuA[0] * acuB[0] / count; // M2
                acuA[1] += delta * acuB[0] / count; // Mean
                acuA[0] = count; // Count
                return acuA;
            }, acu -> acu[2] / (acu[0] - 1.0) // Var = M2 / (count - 1)
    );

    private static class DataLine {
        Map<String, String> innerMap = new HashMap<>();

        public DataLine(String[] header, String[] values) {
            this.innerMap = IntStream.range(0, header.length).boxed()
                    .map(i -> new Pair<>(header[i].trim(), values[i].trim().replace(",", ".")))
                    .map(pair -> new SimpleEntry<>(pair.getFirst(), pair.getSecond()))
                    .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
        }

        public String get(String key) {
            return innerMap.get(key);
        }

        @Override
        public String toString() {
            return Arrays
                    .asList("Run time (s) " + innerMap.get("Run time (s)"), "Agreement " + innerMap.get("Agreement"),
                            "Round " + innerMap.get("Round"), "Exception " + innerMap.get("Exception"),
                            "Dist. to Pareto " + innerMap.get("Dist. to Pareto"),
                            "Agents " + innerMap.get("Agent 1") + " vs. " + innerMap.get("Agent 2"),
                            "Util " + innerMap.get("Utility 1") + " vs. " + innerMap.get("Utility 2"), "Perceived Util "
                                    + innerMap.get("Perceived. Util. 1") + " vs. " + innerMap.get("Perceived. Util. 2"))
                    .toString();
        }

    }
}