package net.librec.job;

import net.librec.common.LibrecException;
import net.librec.conf.Configuration;
import net.librec.conf.HybridConfiguration;
import net.librec.data.DataModel;
import net.librec.data.DataSplitter;
import net.librec.data.splitter.KCVDataSplitter;
import net.librec.data.splitter.LOOCVDataSplitter;
import net.librec.eval.EvalContext;
import net.librec.eval.HybridEvalContext;
import net.librec.eval.Measure;
import net.librec.eval.RecommenderEvaluator;
import net.librec.math.algorithm.Randoms;
import net.librec.recommender.AbstractRecommender;
import net.librec.recommender.HybridContext;
import net.librec.recommender.Recommender;
import net.librec.recommender.RecommenderContext;
import net.librec.recommender.hybrid.AbstractHybridRecommender;
import net.librec.recommender.hybrid.WeightedHybridRecommender;
import net.librec.recommender.item.RecommendedItem;
import net.librec.similarity.RecommenderSimilarity;
import net.librec.util.DriverClassUtil;
import net.librec.util.ReflectionUtil;

import java.io.IOException;
import java.util.*;

/**
 * @author Jan Tuitjer
 */
public class HybridRecommenderJob extends RecommenderJob {
    private HybridConfiguration hybridConfig;
    private HybridContext hybridContext;
    private long seed_to_use = 0L;
    private ArrayList<DataModel> dataModels;
    private ArrayList<RecommenderContext> contexts;
    private AbstractHybridRecommender hybridRecommender;

    public HybridRecommenderJob(HybridConfiguration hybridConfiguration) throws LibrecException, IOException, ClassNotFoundException {
        super(new Configuration());
        hybridConfig = hybridConfiguration;
        assert (hybridConfig.getConfigs().size() > 1);
        initalizeComponents();
    }

    /**
     * initializes all components necessary for the execution of the hybrid job
     *
     * @throws LibrecException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void initalizeComponents() throws LibrecException, IOException, ClassNotFoundException {
        Long seed = hybridConfig.getLong("rec.random.seed");
        if (seed != null) {
            Randoms.seed(seed);
        }
        //setJobId(JobUtil.generateNewJobId());
        initHybridRecommender();
        initializeDataModels();
        initHybridContext();
    }

    /**
     * creates the HybridContext object
     */
    private void initHybridContext() {
        hybridContext = new HybridContext(hybridConfig.getConfigs(), dataModels);
        hybridRecommender.setHybridContext(hybridContext);
        contexts = new ArrayList<>(hybridConfig.getConfigs().size());
    }

    /**
     * Executes the hybrid job - execution steps similar to normal RecommenderJob
     *
     * @throws LibrecException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void runJob() throws LibrecException, IOException, ClassNotFoundException {
        assert (sameFolds());
        cvEvalResults = new HashMap<>();
        while (haveNextFolds()) {
            nextDataModel();
            nextSimilarities();
            trainHybridRecommender();
            evaluateHybrid(hybridRecommender);
        }
        printCVAverageResult();
        boolean isRanking = hybridConfig.getBoolean("rec.recommender.isranking");
        List<RecommendedItem> recommendedList = null;
        if (isRanking) {
            recommendedList = hybridRecommender.getRecommendedList(hybridRecommender.recommendRank());
        } else {
            recommendedList = hybridRecommender.getRecommendedList(hybridRecommender.recommendRating(hybridRecommender.getCommonTestDataSet()));
        }
        recommendedList = filterResult(recommendedList);
        saveResult(recommendedList);
    }

    private void evaluateHybrid(AbstractHybridRecommender hybridRecommender) throws LibrecException {
        EvalContext evalContext = new HybridEvalContext(hybridConfig, hybridRecommender, hybridRecommender.getCommonTestDataSet());
        evaluatedMap = new HashMap<>();
        boolean isRanking = hybridConfig.getBoolean("rec.recommender.isranking");
        int topN = 10;
        if (isRanking) {
            topN = hybridConfig.getInt("rec.recommender.ranking.topn", 10);
            if (topN <= 0) {
                throw new IndexOutOfBoundsException("rec.recommender.ranking.topn should be more than 0!");
            }
        }
        List<Measure.MeasureValue> measureValueList = Measure.getMeasureEnumListHybrid(isRanking, topN);
        if (measureValueList != null) {
            for (Measure.MeasureValue measureValue : measureValueList) {
                RecommenderEvaluator evaluator = ReflectionUtil
                        .newInstance(measureValue.getMeasure().getEvaluatorClass());
                if (isRanking && measureValue.getTopN() != null && measureValue.getTopN() > 0) {
                    evaluator.setTopN(measureValue.getTopN());
                }
                double evaluatedValue = evaluator.evaluate(evalContext);
                evaluatedMap.put(measureValue, evaluatedValue);
            }
        }
        if (evaluatedMap.size() > 0) {
            for (Map.Entry<Measure.MeasureValue, Double> entry : evaluatedMap.entrySet()) {
                String evalName = null;
                if (entry != null && entry.getKey() != null) {
                    if (entry.getKey().getTopN() != null && entry.getKey().getTopN() > 0) {
                        LOG.info("Evaluator value:" + entry.getKey().getMeasure() + " top " + entry.getKey().getTopN() + " is " + entry.getValue());
                        evalName = entry.getKey().getMeasure() + " top " + entry.getKey().getTopN();
                    } else {
                        LOG.info("Evaluator value:" + entry.getKey().getMeasure() + " is " + entry.getValue());
                        evalName = entry.getKey().getMeasure() + "";
                    }
                    if (null != cvEvalResults) {
                        collectCVResults(evalName, entry.getValue());
                    }
                }
            }
        }
    }

    private void collectCVResults(String _evalName, double _value) {
        DataSplitter splitter = dataModels.get(0).getDataSplitter();
        if (splitter != null && (splitter instanceof KCVDataSplitter || splitter instanceof LOOCVDataSplitter)) {
            if (cvEvalResults.containsKey(_evalName)) {
                cvEvalResults.get(_evalName).add(_value);
            } else {
                List<Double> newList = new ArrayList<>();
                newList.add(_value);
                cvEvalResults.put(_evalName, newList);
            }
        }
    }

    private void printCVAverageResult() {
        DataSplitter splitter = dataModels.get(0).getDataSplitter();
        if (splitter != null && (splitter instanceof KCVDataSplitter || splitter instanceof LOOCVDataSplitter)) {
            LOG.info("Average Evaluation Result of Cross Validation:");
            for (Map.Entry<String, List<Double>> entry : cvEvalResults.entrySet()) {
                String evalName = entry.getKey();
                List<Double> evalList = entry.getValue();
                double sum = 0.0;
                for (double value : evalList) {
                    sum += value;
                }
                double avgEvalResult = sum / evalList.size();
                LOG.info("Evaluator value:" + evalName + " is " + avgEvalResult);
            }
        }
    }

    /**
     * trains each contained recommender
     *
     * @throws LibrecException
     */
    private void trainHybridRecommender() throws LibrecException {
        hybridRecommender.trainModel();
    }

    /**
     * initializes the hybrid recommender object and all used recommender objects
     * has to be modified if a new type of hybrid recommender is used
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void initHybridRecommender() throws IOException, ClassNotFoundException {
        hybridRecommender = (AbstractHybridRecommender) ReflectionUtil.newInstance((Class<Recommender>) getRecommenderClass(), hybridConfig);
        hybridRecommender.setHybridConfiguration(hybridConfig);
        ArrayList<AbstractRecommender> usedRecommenders = new ArrayList<>();
        for (Configuration c : hybridConfig.getConfigs()) {
            AbstractRecommender rec = ReflectionUtil.newInstance((Class<AbstractRecommender>) getRecommenderClass(c));
            usedRecommenders.add(rec);
        }
        hybridRecommender.setRecommenders(usedRecommenders);
        //extend for other hybrid recommender implementations
        if (hybridRecommender instanceof WeightedHybridRecommender) {
            if (null != hybridConfig.get("rec.hybrid.weights")) {
                String[] weights = hybridConfig.get("rec.hybrid.weights").split(":");
                double[] realWeights = new double[weights.length];
                for (int i = 0; i < weights.length; i++) {
                    realWeights[i] = Double.parseDouble(weights[i]);
                }
                assert (realWeights.length == hybridRecommender.getRecommenders().size());
                ((WeightedHybridRecommender) hybridRecommender).setWeights(realWeights);
            }
        }
    }

    /**
     * loads the hybrid recommender object of the class stated within the hybrid config
     *
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    @Override
    public Class<? extends Recommender> getRecommenderClass() throws ClassNotFoundException, IOException {
        return (Class<? extends Recommender>) DriverClassUtil.getClass(hybridConfig.get("rec.hybrid.class"));
    }

    /**
     * loads the recommender object of the class stated with in the given configuration file
     *
     * @param _c configuration to use
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private Class<? extends Recommender> getRecommenderClass(Configuration _c) throws ClassNotFoundException, IOException {
        return (Class<? extends Recommender>) DriverClassUtil.getClass(_c.get("rec.recommender.class"));
    }

    /**
     * loads the data into the data models
     * if the hybrid configuration has the flag 'data.model.sync' set
     * each data model will have the same entries in its train and test set
     * if the hybrid configuration has a value for 'rec.random.seed' set
     * this seed will be used to split the data into the train and test set
     *
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws LibrecException
     */
    private void initializeDataModels() throws ClassNotFoundException, IOException, LibrecException {
        if (null == dataModels) {
            dataModels = new ArrayList<>();
            for (int i = 0; i < hybridConfig.getConfigs().size(); i++) {
                if (hybridConfig.getBoolean("data.model.sync")) {
                    if (seed_to_use == 0L) {
                        Random r = new Random();
                        seed_to_use = r.nextLong();
                    }
                    Long seed = hybridConfig.getLong("rec.random.seed", seed_to_use);
                    if (seed != null) {
                        Randoms.seed(seed);
                    }
                }
                DataModel data = ReflectionUtil.newInstance((Class<DataModel>) this.getDataModelClass(i), hybridConfig.getConfigs().get(i));
                data.buildDataModel();
                dataModels.add(data);
            }
        }
    }

    private void initializeDataModelsConcurrently() throws ClassNotFoundException, IOException, LibrecException, InterruptedException {
        if (null == dataModels) {
            dataModels = new ArrayList<>();
            if (hybridConfig.getBoolean("data.model.sync")) {
                Long seed = hybridConfig.getLong("rec.random.seed", 42l);
                if (seed != null) {
                    Randoms.seed(seed);
                }
            }
            ArrayList<Thread> dataModelThreads = new ArrayList<>();
            for (int i = 0; i < hybridConfig.getConfigs().size(); i++) {
                final int counter = i;
                DataModel data = ReflectionUtil.newInstance((Class<DataModel>) this.getDataModelClass(counter), hybridConfig.getConfigs().get(counter));
                dataModelThreads.add(new Thread(() -> {
                    try {
                        System.out.println("start building datamodel " + data);
                        assert data != null;
                        data.buildDataModel();
                        System.out.println("thrtead finished data model building");
                    } catch (LibrecException e) {
                        e.printStackTrace();
                    }
                }));
                dataModels.add(data);
            }
            for (Thread t : dataModelThreads) {
                t.start();
            }
            for (Thread t : dataModelThreads) {
                t.join();
            }
        }
    }

    /**
     * loads the DataModel object with the type stated in the selected config file
     *
     * @param _index
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public Class<? extends DataModel> getDataModelClass(int _index) throws ClassNotFoundException, IOException {
        return (Class<? extends DataModel>) DriverClassUtil.getClass(hybridConfig.getConfigs().get(_index).get("data.model.format"));
    }

    private boolean haveNextFolds() {
        boolean folds = true;
        for (DataModel model : dataModels) {
            folds &= model.hasNextFold();
        }
        return folds;
    }

    /**
     * creates the next similarities for all recommenders
     * and updates the hybrid context with these similarities
     */
    private void nextSimilarities() {
        //calculates similarities if the hybrid context does not already contain similarities
        //or the hybrid configuration file states that the similarities must always be recalculated
        //this is the case if "rec.calcSimilarities.once" is set to 'false'
        if (hybridContext.getSimilarityList().size() == 0 || !hybridConfig.getBoolean("rec.calcSimilarities.once", false)) {
            System.out.println("HybridRecommenderJob.nextSimilarities");
            contexts = hybridContext.getContexts();
            ArrayList<RecommenderSimilarity> similarities = new ArrayList<>();
            for (int i = 0; i < contexts.size(); i++) {
                contexts.get(i).setDataModel(dataModels.get(i));
                generateSimilarity(contexts.get(i));
                similarities.add(contexts.get(i).getSimilarity());
            }
            hybridContext.setSimilarityList(similarities);
        }
    }

    /**
     * loads the RecommenderSimilarity stated within the config file
     *
     * @param _conf
     * @return
     */
    public Class<? extends RecommenderSimilarity> getSimilarityClass(Configuration _conf) {
        try {
            return (Class<? extends RecommenderSimilarity>) DriverClassUtil.getClass(_conf.get("rec.similarity.class"));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Generates the similarity matrices for the given data model for each recommender
     *
     * @param context
     */
    private void generateSimilarity(RecommenderContext context) {
        context.resetSimilarities();
        int index = contexts.indexOf(context);
        Configuration conf = hybridConfig.getConfigs().get(index);
        String[] similarityKeys = conf.getStrings("rec.recommender.similarities");
        if (similarityKeys != null && similarityKeys.length > 0) {
            for (int i = 0; i < similarityKeys.length; i++) {
                if (getSimilarityClass(conf) != null) {
                    RecommenderSimilarity similarity = ReflectionUtil.newInstance(getSimilarityClass(conf), conf);
                    conf.set("rec.recommender.similarity.key", similarityKeys[i]);
                    similarity.buildSimilarityMatrix(dataModels.get(index));
                    if (i == 0) {
                        context.setSimilarity(similarity);
                    }
                    context.addSimilarities(similarityKeys[i], similarity);
                }
            }
        }
    }

    /**
     * sets all data models to the next fold in kcv mode
     * if kcv mode is not used this step does nothing
     * refer to AbstractDataModel.nextFold() implementation
     *
     * @throws LibrecException
     */
    private void nextDataModel() throws LibrecException {
        for (DataModel model : dataModels) {
            model.nextFold();
        }
        hybridContext.setDataModelList(dataModels);
    }

    /**
     * @return true if all data models have the same amount of folds
     */
    private boolean sameFolds() {
        int[] numFolds = new int[hybridConfig.getConfigs().size()];
        for (int i = 0; i < numFolds.length; i++) {
            numFolds[i] = Integer.parseInt(hybridConfig.getConfigs().get(i).get("data.splitter.cv.number"));
        }
        boolean check;
        int len = numFolds.length;
        int a = 0;
        while (a < len && numFolds[a] == numFolds[0]) {
            a++;
        }
        check = (a == len);
        return check;
    }
}
