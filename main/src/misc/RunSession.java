package misc;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.xml.bind.JAXBException;

import genius.AgentsInstaller;
import genius.ProtocolsInstaller;
import genius.cli.Runner;
import genius.core.config.MultilateralTournamentConfiguration;
import genius.core.config.MultilateralTournamentsConfiguration;
import genius.core.events.BrokenPartyException;
import genius.core.events.NegotiationEvent;
import genius.core.events.SessionFailedEvent;
import genius.core.exceptions.InstantiateException;
import genius.core.listener.DefaultListenable;
import genius.core.logging.ConsoleLogger;
import genius.core.misc.ConsoleHelper;
import genius.core.parties.NegotiationPartyInternal;
import genius.core.parties.SessionsInfo;
import genius.core.persistent.PersistentDataType;
import genius.core.protocol.MultilateralProtocol;
import genius.core.repository.MultiPartyProtocolRepItem;
import genius.core.session.ExecutorWithTimeout;
import genius.core.session.Session;
import genius.core.session.SessionConfiguration;
import genius.core.session.SessionManager;
import genius.core.session.TournamentManager;
import genius.core.timeline.Timeline;
import genius.core.tournament.SessionConfigurationList;
import genius.domains.DomainInstaller;
import genius.gui.About;
import genius.gui.negosession.MultiPartyDataModel;
import genius.gui.progress.MultipartyNegoEventLogger;

/**
 * RunSession
 */
public class RunSession extends Runner {

    private static DefaultListenable<NegotiationEvent> listeners = new DefaultListenable<>();

    public static void main(String[] args) throws JAXBException, IOException, InstantiateException {
        ProtocolsInstaller.run();
        DomainInstaller.run();
        AgentsInstaller.run();

        // print welcome message
        System.out.println("This is the Genius multilateral tournament runner command line tool");
        System.out
                .println("Currently you are using using Genius " + About.class.getPackage().getImplementationVersion());

        // request input and output files
        Scanner sc = new Scanner(System.in);
        String input = requestInputFile(args, sc);
        String output = requestOutputFile(args, sc);
        sc.close();

        // run xml configuration
        MultilateralTournamentsConfiguration multiconfig = MultilateralTournamentsConfiguration.load(new File(input));

        // init data model, GUI, logger.
        Integer numParties = multiconfig.getMaxNumNonMediators();
        MultiPartyDataModel dataModel = new MultiPartyDataModel(numParties);
        MultipartyNegoEventLogger myLogger = new MultipartyNegoEventLogger(output, numParties, dataModel);
        dataModel.addTableModelListener(myLogger);

        MultilateralTournamentConfiguration config = multiconfig.getTournaments().get(0);
        SessionConfigurationList sessionConfigurationsList = new SessionConfigurationList(config);
        SessionConfiguration sessionConfig = sessionConfigurationsList.get(0); 
        List<NegotiationPartyInternal> partyList = null;
        ExecutorWithTimeout executor = new ExecutorWithTimeout(1000 * config.getDeadline().getTimeOrDefaultTimeout());
        SessionsInfo sessionsInfo = new SessionsInfo(TournamentManager.getProtocol(config.getProtocolItem()),
                config.getPersistentDataType(), true);
        Session session = new Session(config.getDeadline(), sessionsInfo);

        try {
            partyList = TournamentManager.getPartyList(executor, sessionConfig, sessionsInfo, session);
        } catch (TimeoutException | ExecutionException e) {
            e.printStackTrace();
            listeners.notifyChange(
                    new SessionFailedEvent(new BrokenPartyException("failed to construct agent ", sessionConfig, session, e)));
			return;// do not run any further if we don't have the agents.
		}

        if (partyList == null || partyList.isEmpty()) {
			throw new IllegalArgumentException(
					"parties list doesn't contain a party");
		}
		for (NegotiationPartyInternal party : partyList) {
			if (party == null) {
				throw new IllegalArgumentException(
						"parties contains a null party:" + partyList);
			}
		}
		// Session session = partyList.get(0).getSession();

		Timeline timeline = partyList.get(0).getTimeLine();
		session.setTimeline(timeline);
        SessionManager manager = new SessionManager(sessionConfig, partyList, session, executor);
        ConsoleHelper.useConsoleOut(true);
        
		manager.runAndWait();
		// setPrinting(true, config.isPrintEnabled());
		System.out.println("Runner completed succesfully.");  
    }

    /** Requests the input file from System.in (or from args[0] if defined) */
    private static String requestInputFile(String[] args, Scanner sc) {
        // init
        File f = null;
        String filename = null;

        // if in args
        if (args.length >= 1) {
            System.out.println("Input file: " + args[0]);
            return args[0];
        }

        // Request filename
        System.out.print("Provide path to xml input file: ");
        while (f == null || !f.exists()) {
            if (f != null) {
                System.out.println("Xml input file could not be found: " + f.getName());
                System.out.print("Provide path to xml input file: ");
            }
            filename = sc.nextLine();
            f = new File(filename);
        }

        // return filename
        return filename;
    }

    /** Requests the output file from System.in (or from args[1] if defined) */
    private static String requestOutputFile(String[] args, Scanner sc) {
        // init
        File f = null;
        String filename = null;
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String defaultName = String.format("logs/Log-XmlRunner-%s.csv", dateFormat.format(new Date()));

        // if in args
        if (args.length >= 2) {
            System.out.println("Output file: " + args[1]);
            return args[1];
        }

        // Request filename
        System.out.print(String.format("Provide path to output logfile [default: %s]: ", defaultName));
        while (f == null) {
            filename = sc.nextLine();
            if (filename.isEmpty()) {
                filename = defaultName;
            }
            f = new File(filename);
        }

        // return filename
        return filename;
    }

    public static MultilateralProtocol getProtocol(MultiPartyProtocolRepItem protocolRepItem)
            throws InstantiateException {

        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> protocolClass;
        try {
            protocolClass = loader.loadClass(protocolRepItem.getClassPath());

            Constructor<?> protocolConstructor = protocolClass.getConstructor();

            return (MultilateralProtocol) protocolConstructor.newInstance();
        } catch (Exception e) {
            throw new InstantiateException("failed to instantiate " + protocolRepItem, e);
        }

    }
}