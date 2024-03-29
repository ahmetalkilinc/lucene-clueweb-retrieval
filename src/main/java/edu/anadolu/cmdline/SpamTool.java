package edu.anadolu.cmdline;

import edu.anadolu.datasets.Collection;
import edu.anadolu.datasets.CollectionFactory;
import edu.anadolu.datasets.DataSet;
import edu.anadolu.eval.Evaluator;
import edu.anadolu.spam.SubmissionFile;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.solr.common.params.CommonParams.HEADER_ECHO_PARAMS;
import static org.apache.solr.common.params.CommonParams.OMIT_HEADER;

/**
 * Tool for integration Waterloo Spam Rankings
 */
public final class SpamTool extends CmdLineTool {

    @Option(name = "-tag", metaVar = "[KStem|KStemAnchor]", required = false, usage = "Index Tag")
    private String tag = "KStem";

    @Option(name = "-collection", required = true, usage = "Collection")
    private edu.anadolu.datasets.Collection collection;

    @Override
    public String getShortDescription() {
        return "Tool for integration Waterloo Spam Rankings";
    }

    @Override
    public String getHelp() {
        return "Following properties must be defined in config.properties for " + CLI.CMD + " " + getName() + " paths.spam paths.docs files.ids files.spam";
    }

    @Override
    public void run(Properties props) throws Exception {

        if (parseArguments(props) == -1) return;

        final String tfd_home = props.getProperty("tfd.home");

        if (tfd_home == null) {
            System.out.println(getHelp());
            return;
        }

        DataSet dataset = CollectionFactory.dataset(collection, tfd_home);

        if (!dataset.spamAvailable()) {
            System.out.println(dataset.toString() + " do not have spam filtering option!");
            return;
        }

        final HttpSolrClient solr;
        if (Collection.CW09A.equals(collection) || Collection.CW09B.equals(collection) || Collection.MQ09.equals(collection) || Collection.MQE1.equals(collection)) {
            solr = new HttpSolrClient.Builder().withBaseSolrUrl("http://irra-micro.nas.ceng.local:8983/solr/spam09A").build();
        } else if (Collection.CW12B.equals(collection))
            solr = new HttpSolrClient.Builder().withBaseSolrUrl("http://irra-micro.nas.ceng.local:8983/solr/spam12A").build();
        else {
            System.out.println("spam filtering is only applicable to ClueWeb09 and ClueWeb12 collections!");
            return;
        }

        List<Path> pathList = Evaluator.discoverTextFiles(dataset.collectionPath().resolve("base_spam_runs"), ".txt");

        System.out.println("there are " + pathList.size() + " many TREC submission files found to be processed...");

        final int numThreads = Integer.parseInt(props.getProperty("numThreads", "4"));
        final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);

        long start = System.nanoTime();

        for (Path submission : pathList) {

            executor.execute(() -> {
                try {
                    filterTRECSubmissionFile(dataset, submission, solr);
                } catch (SolrServerException | IOException ioe) {
                    System.out.println(Thread.currentThread().getName() + ": ERROR: unexpected IOException:");
                    ioe.printStackTrace();
                }
            });

        }

        //add some delay to let some threads spawn by scheduler
        Thread.sleep(30000);
        executor.shutdown(); // Disable new tasks from being submitted

        try {
            // Wait for existing tasks to terminate
            while (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                Thread.sleep(1000);
                System.out.println(String.format("%.2f percentage completed in ", (double) executor.getCompletedTaskCount() / executor.getTaskCount() * 100.0d) + execution(start));
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }

        System.out.println("Percolator completed in " + execution(start));
        solr.close();
    }


    /**
     * Filter documents from a TREC submission file
     */
    private static void filterTRECSubmissionFile(DataSet dataset, Path submission, HttpSolrClient solr) throws IOException, SolrServerException {

        final SubmissionFile submissionFile = new SubmissionFile(submission);

        Path relPath = dataset.collectionPath().resolve("base_spam_runs").relativize(submission);

        Map<Integer, List<SubmissionFile.Tuple>> submissionFileMap = submissionFile.entryMap();
        String runTag = submissionFile.runTag();

        Map<Integer, PrintWriter> writerMap = new HashMap<>(19);

        for (int threshold = 5; threshold <= 95; threshold += 5) {

            Path parallel = dataset.collectionPath().resolve("spam_" + threshold + "_runs").resolve(relPath);

            if (!Files.exists(parallel.getParent()))
                Files.createDirectories(parallel.getParent());

            PrintWriter out = new PrintWriter(Files.newBufferedWriter(parallel, StandardCharsets.US_ASCII));
            writerMap.put(threshold, out);

        }

        for (Map.Entry<Integer, List<SubmissionFile.Tuple>> entry : submissionFileMap.entrySet()) {

            Integer qID = entry.getKey();

            Map<Integer, Integer> countMap = new HashMap<>(19);

            for (int threshold = 5; threshold <= 95; threshold += 5) {
                countMap.put(threshold, 0);
            }

            List<SubmissionFile.Tuple> list = entry.getValue();

            for (SubmissionFile.Tuple tuple : list) {

                int percentile = percentile(solr, tuple.docID);

                for (int threshold = 5; threshold <= 95; threshold += 5) {

                    if (percentile < threshold || countMap.get(threshold) == 1000) {
                        continue;
                    }

                    int counter = countMap.get(threshold) + 1;
                    countMap.put(threshold, counter);

                    writerMap.get(threshold).print(qID);
                    writerMap.get(threshold).print("\tQ0\t");
                    writerMap.get(threshold).print(tuple.docID);
                    writerMap.get(threshold).print("\t");
                    writerMap.get(threshold).print(counter);
                    writerMap.get(threshold).print("\t");
                    writerMap.get(threshold).print(tuple.score);
                    writerMap.get(threshold).print("\t");
                    writerMap.get(threshold).print(runTag);
                    writerMap.get(threshold).println();


                }
            }

            /*
             * TREC submission system requires you to submit documents for every topic.
             * If there are no documents for a certain topic, please insert 'clueweb12-0000wb-00-00000' as DOC-ID with a dummy score.
             * If you are returning zero documents for a query, instead return the single document "clueweb09-en0000-00-00000".
             */
            for (int threshold = 5; threshold <= 95; threshold += 5) {
                if (countMap.get(threshold) == 0) {
                    writerMap.get(threshold).println(qID + "\tQ0\t" + dataset.getNoDocumentsID() + "\t1\t0\t" + runTag);
                }
            }
        }

        for (int threshold = 5; threshold <= 95; threshold += 5) {
            writerMap.get(threshold).flush();
            writerMap.get(threshold).close();
        }

        writerMap.clear();
        submissionFile.clear();
    }


    /**
     * Retrieve spam score of a given document id
     */
    static int percentile(HttpSolrClient solr, String docID) throws IOException, SolrServerException {

        SolrQuery query = new SolrQuery(docID).setFields("percentile");
        query.set(HEADER_ECHO_PARAMS, CommonParams.EchoParamStyle.NONE.toString());
        query.set(OMIT_HEADER, true);
        SolrDocumentList resp = solr.query(query).getResults();


        if (resp.size() == 0) {
            System.out.println("cannot find docID " + docID + " in " + solr.getBaseURL());
        }

        if (resp.size() != 1) {
            System.out.println("docID " + docID + " returned " + resp.size() + " many hits!");
        }

        int percentile = (int) resp.get(0).getFieldValue("percentile");

        resp.clear();
        query.clear();

        if (percentile >= 0 && percentile < 100)
            return percentile;
        else throw new RuntimeException("percentile invalid " + percentile);
    }
}