/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.tribuo.multilabel.sgd.linear;

import ai.onnxruntime.OrtException;
import com.oracle.labs.mlrg.olcut.util.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.Trainer;
import org.tribuo.common.sgd.AbstractLinearSGDModel;
import org.tribuo.common.sgd.AbstractLinearSGDTrainer;
import org.tribuo.common.sgd.AbstractSGDTrainer;
import org.tribuo.interop.onnx.OnnxTestUtils;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.multilabel.MultiLabel;
import org.tribuo.multilabel.evaluation.MultiLabelEvaluation;
import org.tribuo.multilabel.example.MultiLabelDataGenerator;
import org.tribuo.multilabel.sgd.objectives.Hinge;
import org.tribuo.multilabel.sgd.objectives.BinaryCrossEntropy;
import org.tribuo.protos.core.ModelProto;
import org.tribuo.test.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSGDLinear {
    private static final Logger logger = Logger.getLogger(TestSGDLinear.class.getName());

    private static final LinearSGDTrainer hinge = new LinearSGDTrainer(new Hinge(),new AdaGrad(0.1,0.1),5,1000, Trainer.DEFAULT_SEED);
    private static final LinearSGDTrainer sigmoid = new LinearSGDTrainer(new BinaryCrossEntropy(),new AdaGrad(0.1,0.1),5,1000, Trainer.DEFAULT_SEED);

    @BeforeAll
    public static void setup() {
        Class<?>[] classes = new Class<?>[]{AbstractSGDTrainer.class, AbstractLinearSGDTrainer.class,LinearSGDTrainer.class};
        for (Class<?> c : classes) {
            Logger logger = Logger.getLogger(c.getName());
            logger.setLevel(Level.WARNING);
        }
    }

    @Test
    public void testPredictions() {
        Dataset<MultiLabel> train = MultiLabelDataGenerator.generateTrainData();
        Dataset<MultiLabel> test = MultiLabelDataGenerator.generateTestData();

        testTrainer(train,test,hinge);
        testTrainer(train,test,sigmoid);
    }

    private static void testTrainer(Dataset<MultiLabel> train, Dataset<MultiLabel> test, LinearSGDTrainer trainer) {
        AbstractLinearSGDModel<MultiLabel> model = trainer.train(train);

        List<Prediction<MultiLabel>> predictions = model.predict(test);
        Prediction<MultiLabel> first = predictions.get(0);
        MultiLabel trueLabel = train.getOutputFactory().generateOutput("MONKEY,PUZZLE,TREE");
        assertEquals(trueLabel, first.getOutput(), "Predicted labels not equal");
        Map<String, List<Pair<String, Double>>> features = model.getTopFeatures(2);
        Assertions.assertNotNull(features);
        Assertions.assertFalse(features.isEmpty());

        MultiLabelEvaluation evaluation = (MultiLabelEvaluation) train.getOutputFactory().getEvaluator().evaluate(model,test);

        Assertions.assertEquals(1.0, evaluation.microAveragedRecall());

        Helpers.testModelSerialization(model, MultiLabel.class);
        Helpers.testModelProtoSerialization(model, MultiLabel.class, test);
    }

    @Test
    public void testOnnxSerialization() throws IOException, OrtException {
        Dataset<MultiLabel> train = MultiLabelDataGenerator.generateTrainData();
        Dataset<MultiLabel> test = MultiLabelDataGenerator.generateTestData();
        LinearSGDModel model = sigmoid.train(train);

        // Write out model
        Path onnxFile = Files.createTempFile("tribuo-sgd-test",".onnx");
        model.saveONNXModel("org.tribuo.multilabel.sgd.linear.test",1,onnxFile);

        OnnxTestUtils.onnxMultiLabelComparison(model,onnxFile,test,1e-6);

        onnxFile.toFile().delete();
    }

    @Test
    public void loadProtobufModel() throws IOException, URISyntaxException {
        Path path = Paths.get(TestSGDLinear.class.getResource("lin-ml-431.tribuo").toURI());
        try (InputStream fis = Files.newInputStream(path)) {
            ModelProto proto = ModelProto.parseFrom(fis);
            LinearSGDModel model = (LinearSGDModel) Model.deserialize(proto);

            assertEquals("4.3.1", model.getProvenance().getTribuoVersion());

            Dataset<MultiLabel> test = MultiLabelDataGenerator.generateTestData();
            List<Prediction<MultiLabel>> predictions = model.predict(test);
            assertEquals(test.size(), predictions.size());
        }
    }

    public void generateProtobuf() throws IOException {
        LinearSGDTrainer sigmoid = new LinearSGDTrainer(new BinaryCrossEntropy(),new AdaGrad(0.1,0.1),5,1000, Trainer.DEFAULT_SEED);
        Dataset<MultiLabel> train = MultiLabelDataGenerator.generateTrainData();
        LinearSGDModel model = sigmoid.train(train);
        Helpers.writeProtobuf(model, Paths.get("src","test","resources","org","tribuo","multilabel","sgd","linear","lin-ml-431.tribuo"));
    }

}
