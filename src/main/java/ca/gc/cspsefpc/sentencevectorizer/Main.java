/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package ca.gc.cspsefpc.sentencevectorizer;

import java.io.IOException;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author JTurner
 */
public class Main {
    
    private static final int DEFAULT_SERVICE_PORT = 8080;
    private static final int DEFAULT_NUMBER_OF_FEATURES = 200;
    private static final int DEFAULT_NUMBER_OF_OCCURRENCES = 5;
    private static final int DEFAULT_WINDOW_SIZE = 5;
    private static final String DEFAULT_LOCALE = "en_ca";
    private static final String DEFAULT_WORKING_DIR = ".";
    
    public static void main(String[] arrghs) {
        String command = "undefined";
        Options options = buildOptions();
        DefaultParser parser = new DefaultParser();
        String[] fileSpec = new String[]{"."};
        String outputLocale = DEFAULT_LOCALE;
        String workingDir = DEFAULT_WORKING_DIR;
        int numFeatures = DEFAULT_NUMBER_OF_FEATURES;
        int numOccurrences = DEFAULT_NUMBER_OF_OCCURRENCES;
        int windowsSize = DEFAULT_WINDOW_SIZE;
        int servicePort = DEFAULT_SERVICE_PORT;
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, arrghs);
            String[] args = line.getArgs();
            if (args.length == 1) {
                command = args[0];
            }
            if (line.hasOption("h")) {
                printHelp(options);
                return;
            }
            if (line.hasOption("s")) {
                fileSpec = line.getOptionValues("s");
            }
            if (line.hasOption("l")) {
                outputLocale = line.getOptionValue("l");
            }
            if (line.hasOption("d")) {
                workingDir = line.getOptionValue("d");
            }
            if (line.hasOption("n")) {
                numFeatures = ((Number) line.getParsedOptionValue("n")).intValue();
            }
            if (line.hasOption("w")) {
                windowsSize = ((Number) line.getParsedOptionValue("w")).intValue();
            }
            if (line.hasOption("m")) {
                numOccurrences = ((Number) line.getParsedOptionValue("m")).intValue();
            }
            if (line.hasOption("p")) {
                servicePort = ((Number) line.getParsedOptionValue("p")).intValue();
            }
        } catch (ParseException exp) {
            System.err.println("Parsing failed.  Reason: " + exp.toString());
            printHelp(options);
            System.exit(-1);
        }
        switch (command) {
            case "build":
                try {
                SentenceVectorizerTrainer trainer = new SentenceVectorizerTrainer();
                trainer.trainModel(numOccurrences, numFeatures, windowsSize, fileSpec, workingDir, outputLocale);
            } catch (IOException ex) {
                ex.printStackTrace();
                System.exit(-1);
            }
            break;
            case "service":
                System.out.println("Starting on port " + servicePort + " with locale files in " + workingDir);
                SentenceVectorizerService bertifierService = new SentenceVectorizerService(workingDir, servicePort);
                break;
            default:
                System.err.println("Must specify one of either 'build' or 'service' as the first parameter.");
                printHelp(options);
                System.exit(-1);
        }
        
    }
    
    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder("s").hasArgs().longOpt("source-directory").type(String.class).desc("Directory from which to read the text corpus. Defaults to working directory.").build());
        options.addOption(Option.builder("l").hasArg().longOpt("output-locale").type(String.class).desc("Locale for which the embedding is to be trained. Default: " + DEFAULT_LOCALE).build());
        options.addOption(Option.builder("d").hasArg().longOpt("working-directory").type(String.class).desc("Directory from which to load/save locale embeddings. Default: " + DEFAULT_WORKING_DIR).build());
        options.addOption(Option.builder("n").hasArg().longOpt("num-features").type(Number.class).desc("Number of output features/dimensions. Default: " + DEFAULT_NUMBER_OF_FEATURES).build());
        options.addOption(Option.builder("m").hasArg().longOpt("min-occurrences").type(Number.class).desc("The minimum number of occurrences that a word must have to be considered. Default:  " + DEFAULT_NUMBER_OF_OCCURRENCES).build());
        options.addOption(Option.builder("w").hasArg().longOpt("window-size").type(Number.class).desc("The width of the rolling window applied to word contexts. Default:  " + DEFAULT_WINDOW_SIZE).build());
        options.addOption(Option.builder("h").longOpt("help").desc("Display this help.").build());
        options.addOption(Option.builder("p").hasArg().longOpt("service-port").type(Number.class).desc("The port on which to listen for incoming calls. Default: " + DEFAULT_SERVICE_PORT).build());
        return options;
    }
    
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar SentenceVectorizer.jar [ service | build ] [OPTIONS]", options);
    }
}
