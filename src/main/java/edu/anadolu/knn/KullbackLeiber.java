package edu.anadolu.knn;

import static org.apache.lucene.search.similarities.ModelBase.log2;

/**
 * the Kullback–Leibler divergence is a measure of the difference between two probability distributions P and Q.
 */
public class KullbackLeiber extends ChiBase {

    public KullbackLeiber(boolean divide) {
        super(divide, false);
    }

    @Override
    public <T extends Number> double chiCDF(T[] R, T[] S) {
        throw new UnsupportedOperationException("Kullback-Leiber divergence works on pdf only!");
    }

    @Override
    public <T extends Number> double chiPDF(T[] R, T[] S) {

        double kl = 0.0;

        int counter = 0;

        for (int i = 0; i < R.length; i++) {

            if (R[i].doubleValue() == 0 || S[i].doubleValue() == 0) continue;
            counter++;

            kl += R[i].doubleValue() * log2(R[i].doubleValue() / S[i].doubleValue());
        }

        if (divide)
            return kl / (double) counter;
        else
            return kl;
    }

    @Override
    String name() {
        return "KL";
    }

    public String toString() {
        return ",KL" + super.toString();
    }
}
