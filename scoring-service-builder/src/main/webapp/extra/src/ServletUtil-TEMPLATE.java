import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
//import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import hex.genmodel.easy.*;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.AbstractPrediction;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.easy.prediction.MultinomialModelPrediction;
import hex.genmodel.*;

class ServletUtil {
  private final static Logger logger = Logging.getLogger(ServletUtil.class);

  // load model
  static String modelName = "REPLACE_THIS_WITH_PREDICTOR_CLASS_NAME";
//  static GenModel rawModel = new REPLACE_THIS_WITH_PREDICTOR_CLASS_NAME();
  static GenModel rawModel = null;

  static {
    if (REPLACE_THIS_WITH_POJO_BOOLEAN)
      rawModel = new REPLACE_THIS_WITH_PREDICTOR_CLASS_NAME();
    else
      rawModel = RawModel.load("REPLACE_THIS_WITH_PREDICTOR_CLASS_NAME" + ".zip");
    if (rawModel == null)
      logger.error("Can't instantiate model");
  }

  public static EasyPredictModelWrapper model = new EasyPredictModelWrapper(rawModel);
    // load preprocessing
  public static Transform transform = REPLACE_THIS_WITH_TRANSFORMER_OBJECT;

  public static final Type MAP_TYPE = new TypeToken<HashMap<String, Object>>(){}.getType();
  public static final Type ROW_DATA_TYPE = new TypeToken<RowData>(){}.getType();
  public static final int warmUpCount = 5;

  private static Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

  public static class Times {
    private long count = 0;
    private double totalTimeMs = 0;
    private double totalTimeSquaredMs = 0;
    private double warmupTimeMs = 0;
    private double warmupTimeSquaredMs = 0;
    private double lastMs = 0;

    public void add(long startNs, long endNs, int n) {
      double elapsed = (endNs - startNs) / 1.0e6;
      add(elapsed, n);
    }

    public void add(long startNs, long endNs) {
      add(startNs, endNs, 1);
    }

    public synchronized void add(double timeMs, int n) {
      count += n;
      totalTimeMs += timeMs; // n * timeMs/n
      double tt = timeMs * timeMs / n; // n * (timeMs/n)^2
      totalTimeSquaredMs += tt;
      if (count <= warmUpCount) {
        warmupTimeMs += timeMs;
        warmupTimeSquaredMs += tt;
      }
      lastMs = timeMs / n;
    }

    public double avg() {
      return count > 0 ? totalTimeMs / count : 0.0;
    }

//    public double sdev() { }

    public double avgAfterWarmup() {
      return count > warmUpCount ? (totalTimeMs - warmupTimeMs) / (count - warmUpCount) : 0.0;
    }

    public String toJson() {
      return gson.toJson(toMap());
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = classToMap();
      map.put("averageTime", avg());
      map.put("averageAfterWarmupTime", avgAfterWarmup());
      return map;
    }

    private Map<String, Object> classToMap() {
      return gson.fromJson(gson.toJson(this), MAP_TYPE);
    }

    public String toString() {
      return String.format("n %d  last %.3f  avg %.3f after warmup %.3f [ms]", count, lastMs, avg(), avgAfterWarmup());
    }
  }

  public static synchronized AbstractPrediction predict(RowData row) throws PredictException {
    long start = System.nanoTime();
    AbstractPrediction pr = model.predict(row);
    long done = System.nanoTime();
    ServletUtil.lastTime = System.currentTimeMillis();
    ServletUtil.predictionTimes.add(start, done);

//    String label = null;
//    if (pr instanceof BinomialModelPrediction) {
//      label = ((BinomialModelPrediction) pr).label;
//    } else if (pr instanceof MultinomialModelPrediction) {
//      label = ((MultinomialModelPrediction) pr).label;
//    }
//    if (label != null) {
//      ServletUtil.incrementOutputLabel(label);
//    }

    logger.debug("Prediction time {}", ServletUtil.predictionTimes);
    return pr;
  }


//  public static Map<String, Integer> outputLabels = new ConcurrentHashMap<String, Integer>();
//
//  public static synchronized void incrementOutputLabel(String key) {
//    Integer value = outputLabels.putIfAbsent(key, 1);
//    if (value != null)
//      outputLabels.replace(key, value + 1);
//  }

  public static long startTime = System.currentTimeMillis();
  public static long lastTime = 0;
  public static Times predictionTimes = new Times();
  public static Times getTimes = new Times();
  public static Times postTimes = new Times();
  public static Times getPythonTimes = new Times();
  public static Times postPythonTimes = new Times();

}