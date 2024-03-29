package edu.anadolu.knn;

import edu.anadolu.eval.Metric;

/**
 * Retrieval Effectiveness Measures that we optimize and report
 */
public enum Measure {

    NDCG20(Metric.NDCG, 20),
    NDCG100(Metric.NDCG, 100),
//  NDCG1000(Metric.NDCG, 1000),

    ERR20(Metric.ERR, 20),
    ERR100(Metric.ERR, 100),
//    ERR1000(Metric.ERR, 1000),

    MAP(Metric.MAP, 1000);

    Measure(Metric metric, int k) {
        this.metric = metric;
        this.k = k;
    }

    private final Metric metric;
    private final int k;

    public Metric metric() {
        return this.metric;
    }

    public int k() {
        return this.k;
    }
}
