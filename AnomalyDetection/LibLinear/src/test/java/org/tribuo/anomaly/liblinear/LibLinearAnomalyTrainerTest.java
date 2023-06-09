/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
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

package org.tribuo.anomaly.liblinear;

import org.junit.jupiter.api.Test;
import org.tribuo.DataSource;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.evaluation.AnomalyEvaluation;
import org.tribuo.anomaly.evaluation.AnomalyEvaluator;
import org.tribuo.anomaly.example.GaussianAnomalyDataSource;
import org.tribuo.common.liblinear.LibLinearModel;
import org.tribuo.protos.core.ModelProto;
import org.tribuo.test.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LibLinearAnomalyTrainerTest {
    private static final Logger logger = Logger.getLogger(LibLinearAnomalyTrainerTest.class.getName());

    @Test
    public void gaussianDataTest() {
        DataSource<Event> trainSource = new GaussianAnomalyDataSource(1000, 0.0f, 1);
        DataSource<Event> testSource = new GaussianAnomalyDataSource(1000, 0.2f, 1);

        Dataset<Event> trainData = new MutableDataset<>(trainSource);
        Dataset<Event> testData = new MutableDataset<>(testSource);

        LinearAnomalyType type = new LinearAnomalyType(LinearAnomalyType.LinearType.ONECLASS_SVM);

        LibLinearAnomalyTrainer trainer = new LibLinearAnomalyTrainer(type,1.0,1000,0.01,0.05);

        LibLinearModel<Event> model = trainer.train(trainData);

        AnomalyEvaluator evaluator = new AnomalyEvaluator();

        AnomalyEvaluation evaluation = evaluator.evaluate(model,testData);

        assertEquals(196,evaluation.getTruePositives());
        assertEquals(761,evaluation.getTrueNegatives());
        assertEquals(0,evaluation.getFalseNegatives());
        assertEquals(43,evaluation.getFalsePositives());

        String confusion = evaluation.confusionString();
        String output = evaluation.toString();

        // logging a few outputs for easy checking, and to prevent dead code elimination.
        logger.finer(confusion);
        logger.finer(output);

        // Test serialization
        Helpers.testModelSerialization(model,Event.class);
        Helpers.testModelProtoSerialization(model,Event.class,testData);
    }

    @Test
    public void loadProtobufModel() throws IOException, URISyntaxException {
        Path path = Paths.get(LibLinearAnomalyTrainerTest.class.getResource("liblinear-anomaly-431.tribuo").toURI());
        try (InputStream fis = Files.newInputStream(path)) {
            ModelProto proto = ModelProto.parseFrom(fis);
            LibLinearAnomalyModel model = (LibLinearAnomalyModel) Model.deserialize(proto);

            assertEquals("4.3.1", model.getProvenance().getTribuoVersion());

            DataSource<Event> testSource = new GaussianAnomalyDataSource(1000, 0.2f, 1);
            Dataset<Event> testData = new MutableDataset<>(testSource);
            List<Prediction<Event>> output = model.predict(testData);
            assertEquals(output.size(), testData.size());
        }
    }

    /**
     * Test protobuf generation method.
     * @throws IOException If the write failed.
     */
    public void generateModel() throws IOException {
        DataSource<Event> trainSource = new GaussianAnomalyDataSource(1000, 0.0f, 1);
        Dataset<Event> trainData = new MutableDataset<>(trainSource);
        LinearAnomalyType type = new LinearAnomalyType(LinearAnomalyType.LinearType.ONECLASS_SVM);
        LibLinearAnomalyTrainer trainer = new LibLinearAnomalyTrainer(type,1.0,1000,0.01,0.05);
        LibLinearModel<Event> model = trainer.train(trainData);
        Helpers.writeProtobuf(model, Paths.get("src","test","resources","org","tribuo","anomaly","liblinear","liblinear-anomaly-431.tribuo"));
    }

}
