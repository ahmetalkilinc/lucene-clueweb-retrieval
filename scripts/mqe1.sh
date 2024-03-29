#!/usr/bin/env bash

if [ -z "$TFD_HOME" ]; then
    TFD_HOME=~/TFD_HOME
fi

if [ -z "$1" ]; then
	RUNS=runs
    EVALS=evals
else
	RUNS="$1_runs"
    EVALS="$1_evals"
fi

echo "starting MQE1 evaluator with RUNS = $RUNS and EVALS = $EVALS ..."

 qrels[1]=qrels.mq.51-250.txt
 qrels[2]=qrels.mq.20251_20750.txt

# Extended TREC 2009 Million Query (1MQ) Track
# http://ir.cis.udel.edu/million/data.html

for E in 1 2; do
set="MQE${E}"

for tag in KStemAnalyzer KStemAnalyzerAnchor; do
mkdir -p "$TFD_HOME/$set/${EVALS}/$tag"


    if [ ! -d "${TFD_HOME}/${set}/${RUNS}/${tag}/${set}" ]; then
        # Control will enter here if $DIRECTORY does not exist.
        echo "${TFD_HOME}/${set}/${RUNS}/${tag}/${set} does not exist!"
        continue
    fi

    mkdir -p "$TFD_HOME/$set/${EVALS}/$tag/${set}"
    mkdir -p "$TFD_HOME/$set/${EVALS}/$tag/${set}/trec_eval"

    for f in ${TFD_HOME}/${set}/${RUNS}/${tag}/${set}/*.txt; do
        ${TFD_HOME}/scripts/trec_eval -M1000 -q ${TFD_HOME}/topics-and-qrels/${qrels[${E}]} ${f} > "${TFD_HOME}/${set}/${EVALS}/${tag}/${set}/trec_eval/${f##/*/}" &
        for k in 20 100 1000; do
          mkdir -p "$TFD_HOME/$set/${EVALS}/$tag/$set/$k"
          ${TFD_HOME}/scripts/gdeval.pl -k ${k} ${TFD_HOME}/topics-and-qrels/${qrels[${E}]} ${f} > "${TFD_HOME}/${set}/${EVALS}/${tag}/${set}/${k}/${f##/*/}" &
        done
    done
    wait

done
done