/*
 * Copyright (c) 2015-2020, Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tribuo.classification.sequence;

import com.oracle.labs.mlrg.olcut.config.ConfigurationManager;
import com.oracle.labs.mlrg.olcut.config.Option;
import com.oracle.labs.mlrg.olcut.config.Options;
import com.oracle.labs.mlrg.olcut.config.UsageException;
import com.oracle.labs.mlrg.olcut.util.LabsLogFormatter;
import org.tribuo.classification.Label;
import org.tribuo.classification.sequence.example.SequenceDataGenerator;
import org.tribuo.sequence.SequenceDataset;
import org.tribuo.sequence.SequenceModel;
import org.tribuo.sequence.SequenceTrainer;
import org.tribuo.util.Util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Build and run a sequence classifier on a generated or serialized dataset using the trainer specified in the configuration file.
 */
public class SeqTrainTest {

    private static final Logger logger = Logger.getLogger(SeqTrainTest.class.getName());

    public static class SeqTrainTestOptions implements Options {
        @Override
        public String getOptionsDescription() {
            return "Trains and tests a sequence classification model on the specified dataset.";
        }

        @Option(charName = 'd', longName = "dataset-name", usage = "Name of the example dataset, options are {gorilla}.")
        public String datasetName = "";
        @Option(charName = 'f', longName = "output-path", usage = "Path to serialize model to.")
        public Path outputPath;
        @Option(charName = 'u', longName = "train-dataset", usage = "Path to a serialised SequenceDataset used for training.")
        public Path trainDataset = null;
        @Option(charName = 'v', longName = "test-dataset", usage = "Path to a serialised SequenceDataset used for testing.")
        public Path testDataset = null;
        @Option(charName = 't', longName = "trainer-name", usage = "Name of the trainer in the configuration file.")
        public SequenceTrainer<Label> trainer;
    }

    /**
     * @param args the command line arguments
     * @throws ClassNotFoundException if it failed to load the model.
     * @throws IOException            if there is any error reading the examples.
     */
    public static void main(String[] args) throws ClassNotFoundException, IOException {

        //
        // Use the labs format logging.
        LabsLogFormatter.setAllLogFormatters();

        SeqTrainTestOptions o = new SeqTrainTestOptions();
        ConfigurationManager cm;
        try {
            cm = new ConfigurationManager(args, o);
        } catch (UsageException e) {
            logger.info(e.getMessage());
            return;
        }

        SequenceDataset<Label> train;
        SequenceDataset<Label> test;
        switch (o.datasetName) {
            case "Gorilla":
            case "gorilla":
                logger.info("Generating gorilla dataset");
                train = SequenceDataGenerator.generateGorillaDataset(1);
                test = SequenceDataGenerator.generateGorillaDataset(1);
                break;
            default:
                if ((o.trainDataset != null) && (o.testDataset != null)) {
                    logger.info("Loading training data from " + o.trainDataset);
                    try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(o.trainDataset.toFile())));
                         ObjectInputStream oits = new ObjectInputStream(new BufferedInputStream(new FileInputStream(o.testDataset.toFile())))) {
                        @SuppressWarnings("unchecked") // deserialising a generic dataset.
                        SequenceDataset<Label> tmpTrain = (SequenceDataset<Label>) ois.readObject();
                        train = tmpTrain;
                        logger.info(String.format("Loaded %d training examples for %s", train.size(), train.getOutputs().toString()));
                        logger.info("Found " + train.getFeatureIDMap().size() + " features");
                        logger.info("Loading testing data from " + o.testDataset);
                        @SuppressWarnings("unchecked") // deserialising a generic dataset.
                        SequenceDataset<Label> tmpTest = (SequenceDataset<Label>) oits.readObject();
                        test = tmpTest;
                        logger.info(String.format("Loaded %d testing examples", test.size()));
                    }
                } else {
                    logger.warning("Unknown dataset " + o.datasetName);
                    logger.info(cm.usage());
                    return;
                }
        }

        logger.info("Training using " + o.trainer.toString());
        final long trainStart = System.currentTimeMillis();
        SequenceModel<Label> model = o.trainer.train(train);
        final long trainStop = System.currentTimeMillis();
        logger.info("Finished training classifier " + Util.formatDuration(trainStart, trainStop));

        LabelSequenceEvaluator labelEvaluator = new LabelSequenceEvaluator();
        final long testStart = System.currentTimeMillis();
        LabelSequenceEvaluation evaluation = labelEvaluator.evaluate(model,test);
        final long testStop = System.currentTimeMillis();
        logger.info("Finished evaluating model " + Util.formatDuration(testStart, testStop));
        System.out.println(evaluation.toString());
        System.out.println();
        System.out.println(evaluation.getConfusionMatrix().toString());

        if (o.outputPath != null) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(o.outputPath.toFile()))) {
                oos.writeObject(model);
                logger.info("Serialized model to file: " + o.outputPath);
            }
        }
    }
}
