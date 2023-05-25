package ca.gc.cspsefpc.sentencevectorizer;

import au.com.bytecode.opencsv.CSVReader;
import com.github.cliftonlabs.json_simple.JsonArray;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.activation.MimetypesFileTypeMap;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

/**
 *
 * @author JTurner
 */
public class SentenceVectorizerService {

    private static final String FILE_SUFFIX = ".word2vec";
    private final Path embeddingPath;
    private final Map<String, Word2Vec> wordVectors = new HashMap<>();
    private final Map<String, Map<String, Double[]>> searchIndexes = new HashMap<>(); // keyed by locale
    private MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

    private synchronized Word2Vec getWord2Vec(String locale) throws InvalidLocaleException {
        if (wordVectors.containsKey(locale)) {
            return wordVectors.get(locale);
        }
        if (locale.matches("^[a-z][a-z]_[a-z][a-z]$")) {
            File fileSpec = new File(embeddingPath.toFile(), locale + FILE_SUFFIX);
            Word2Vec newModel = WordVectorSerializer.readWord2VecModel(fileSpec, false);
            wordVectors.put(locale, newModel);
            return newModel;
        } else {
            throw new InvalidLocaleException();
        }
    }

    public SentenceVectorizerService(String embeddingPathSpec, int port) {
        this.embeddingPath = Paths.get(embeddingPathSpec);
        Arrays.asList("en_ca", "fr_ca").forEach(lang -> {
            try {
                searchIndexes.put(lang, preloadSearch(lang));
            } catch (InvalidLocaleException | IOException ex) {
                Logger.getLogger(SentenceVectorizerService.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        //JavalinConfig config = new JavalinConfig();
        Javalin javalin = Javalin.create();

        javalin.get("/list", this::getList);

        javalin.get("/{locale}/vectorize", this::getProjection);
        javalin.get("/{locale}/search", this::search);
        javalin.get("/{locale}/nearest/{term}", this::getNearest);
        javalin.get("/<path>", this::serveStatic);
        javalin.get("/", this::firstRedirect);
        javalin.start(port);
    }

    public void getList(Context ctx) {
        ArrayList<String> fileNames = new ArrayList<>();
        if (embeddingPath.toFile().isDirectory()) {

            for (File file : embeddingPath.toFile().listFiles((File file) -> {
                return (file.isFile() && file.canRead() && file.getName().endsWith(".word2vec"));
            })) {
                fileNames.add(file.getName().substring(0, file.getName().lastIndexOf(".word2vec")));
            }
        }
        JsonArray array = new JsonArray(fileNames);
        ctx.contentType(ContentType.APPLICATION_JSON);
        ctx.result(array.toJson());
    }

    public void firstRedirect(Context ctx) {
        ctx.redirect("index.html");
    }

    public void serveStatic(Context ctx) {
        String path = ctx.pathParam("path");
        if (path.contains("..") || path.contains("//")) {
            ctx.status(HttpStatus.IM_A_TEAPOT);
            ctx.result("Bad hacker, shoo!");
        }
        URL resource = getClass().getResource("/www/" + path);
        if (resource != null) {

            ctx.contentType(fileTypeMap.getContentType(resource.getFile()));

            try {
                ctx.result(resource.openStream());
            } catch (IOException ex) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                ctx.result(ex.getMessage());
            }
        } else {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.result("Resource not found");
        }
    }

    public void getNearest(Context ctx) throws InvalidLocaleException {
        String term = ctx.pathParam("term");
        Word2Vec w2v = getWord2Vec(ctx.pathParam("locale"));
        if (w2v.hasWord(term)) {
            Collection<String> wordsNearest = w2v.wordsNearest(term, 10);
            JsonArray array = new JsonArray(wordsNearest);
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.result(array.toJson());

        } else {
            ctx.status(404);
            ctx.result("Unknown term: " + term);
        }
    }

    public void getProjection(Context ctx) throws InvalidLocaleException {
        String locale = ctx.pathParam("locale");
        String textParam = ctx.queryParam("text");
        if (textParam != null) {
            String text = normalizeText(textParam);
            Double[] vectors = sentenceToVector(text, locale);
            JsonArray array = new JsonArray();
            array.addAll(Arrays.asList(vectors));
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.result(array.toJson());
            return;
        }
        ctx.status(HttpStatus.BAD_REQUEST);
        ctx.result("Missing 'text' parameter");
    }

    private Double[] sentenceToVector(String text, String locale) throws InvalidLocaleException {
        StringTokenizer st = new StringTokenizer(text, " ", false);
        ArrayList<String> words = new ArrayList<>();
        Word2Vec w2v = getWord2Vec(locale);
        while (st.hasMoreTokens()) {
            String word = st.nextToken();
            if (w2v.hasWord(word)) {
                words.add(word);
            }
        }
        double[] features = new double[w2v.vectorSize()];
        double[][] matrix = w2v.getWordVectors(words).toDoubleMatrix(); // 2D array [words][features]
        // Sum the features for the words.
        for (double[] wordFeatures : matrix) {
            for (int j = 0; j < features.length; j++) {
                features[j] = features[j] + wordFeatures[j];
            }
        }
        Double[] vectors = new Double[features.length];
        for (int i = 0; i < features.length; i++) {
            vectors[i] = features[i];
        }
        return vectors;
    }

    public void search(Context ctx) throws InvalidLocaleException {
        String term = ctx.queryParam("q");
        String locale = ctx.pathParam("locale");
        if (term != null) {
            String text = normalizeText(term);
            Double[] vectors = sentenceToVector(text, locale);
            List<String> closest = getClosestRefsTo(vectors, locale, 10);
            JsonArray array = new JsonArray();
            array.addAll(Arrays.asList(closest));
            ctx.contentType(ContentType.APPLICATION_JSON);
            ctx.result(array.toJson());
            return;
        }
        ctx.status(HttpStatus.BAD_REQUEST);
        ctx.result("Missing 'q' parameter");
    }

    private List<String> getClosestRefsTo(Double[] vectors, String lang, int numResults) throws InvalidLocaleException {
        TreeMap<Double, String> returnable = new TreeMap<>();
        try {
            Map<String, Double[]> index = searchIndexes.get(lang);
            if (index == null) {
                throw new InvalidLocaleException();
            }
            for (Map.Entry<String, Double[]> it : index.entrySet()) {
                Double[] rowVector = it.getValue();
                double similarity = cosineSimilarity(vectors, rowVector);
                returnable.put(similarity, it.getKey());
                while (returnable.size() > numResults) {
                    returnable.pollFirstEntry();
                }
            }

        } catch (InvalidLocaleException ex) {
            Logger.getLogger(SentenceVectorizerService.class.getName()).log(Level.SEVERE, null, ex);
        }

        return new ArrayList<>(returnable.values());
    }

    private double cosineSimilarity(Double[] vectorA, Double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String normalizeText(String body) {
        return body.replaceAll("[^\\p{L}]+", " ").replaceAll(" +", " ");
    }

    private Map<String, Double[]> preloadSearch(String lang) throws IOException, InvalidLocaleException {
        int descriptionIndex;
        switch (lang) {
            case "en_ca" ->
                descriptionIndex = 3;
            case "fr_ca" ->
                descriptionIndex = 7;
            default ->
                throw new InvalidLocaleException();
        }

        TreeMap<String, Double[]> returnable = new TreeMap<>();
        try {
            CSVReader reader = new CSVReader(new FileReader("./data.csv"));
            String[] row = reader.readNext(); // Header row.
            row = reader.readNext(); // Actual first row.
            while (row != null) {
                if (row[descriptionIndex] != null && !row[descriptionIndex].isBlank()) {
                    Double[] rowVector = sentenceToVector(row[descriptionIndex], lang);
                    returnable.put(row[0], rowVector);
                }
                row = reader.readNext();
            }
        } catch (FileNotFoundException | InvalidLocaleException ex) {
            Logger.getLogger(SentenceVectorizerService.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        return returnable;

    }

    private static class InvalidLocaleException extends Exception {

        public InvalidLocaleException() {
        }
    }
}
