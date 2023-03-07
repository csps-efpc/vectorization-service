package ca.gc.cspsefpc.sentencevectorizer;

import com.github.cliftonlabs.json_simple.JsonArray;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
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
        //TODO: preload some models.
        //JavalinConfig config = new JavalinConfig();
        Javalin javalin = Javalin.create();
        javalin.get("/list", this::getList);
        javalin.get("/{locale}/vectorize", this::getProjection);
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
        if (ctx.queryParam("text") != null) {
            String text = normalizeText(ctx.queryParam("text"));
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

    private String normalizeText(String body) {
        return body.replaceAll("[^\\p{L}]+", " ").replaceAll(" +", " ");
    }

    private static class InvalidLocaleException extends Exception {

        public InvalidLocaleException() {
        }
    }
}
