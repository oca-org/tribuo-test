/*
 * Copyright (c) 2015-2021, Oracle and/or its affiliates. All rights reserved.
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

package org.tribuo.impl;

import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.FeatureMap;
import org.tribuo.Output;
import org.tribuo.VariableInfo;
import org.tribuo.transform.Transformer;
import org.tribuo.transform.TransformerMap;
import org.tribuo.util.Merger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class will not be performant until value types are available in Java. Prefer {@link ArrayExample}.
 * <p>
 * An example that's a simple list of features. It is not guaranteed that feature instances are preserved.
 * @param <T> the type of the features in this example.
 */
public class ListExample<T extends Output<T>> extends Example<T> implements Serializable {
    private static final Logger logger = Logger.getLogger(ListExample.class.getName());
    private static final long serialVersionUID = 1L;

    private final List<Feature> features = new ArrayList<>();

    public ListExample(T output, float weight) {
        super(output,weight);
    }
     
    public ListExample(T output) {
        super(output);
    }

    public ListExample(Example<T> other) {
        super(other);
        for (Feature f : other) {
            addWithoutSort(f.clone());
        }
        sort();
    }

    public ListExample(T output, List<? extends Feature> features) {
        super(output);
        for (Feature f : features) {
            addWithoutSort(f.clone());
        }
        sort();
    }

    public ListExample(T output, String[] featureNames, double[] featureValues) {
        super(output);
        if (featureNames.length != featureValues.length) {
            throw new IllegalArgumentException("Supplied names have length " + featureNames.length + ", supplied values have length " + featureValues.length);
        }
        for (int i = 0; i < featureNames.length; i++) {
            addWithoutSort(new Feature(featureNames[i],featureValues[i]));
        }
        sort();
    }

    /**
     * Adds a feature without sorting the feature list. Used in the mass add methods and constructors.
     * @param feature The feature to add.
     */
    private void addWithoutSort(Feature feature) {
        features.add(feature);
    }

    @Override
    public void add(Feature feature) {
        addWithoutSort(feature);
        sort();
    }

    @Override
    public void addAll(Collection<? extends Feature> features) {
        this.features.addAll(features);
        sort();
    }
    
    public void clear() {
        features.clear();
    }
    
    @Override
    public int size() {
        return features.size();
    }

    @Override
    public ListExample<T> copy() {
        return new ListExample<>(this);
    }

    @Override
    public Iterator<Feature> iterator() {
        return features.iterator();
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("ListExample(numFeatures=");
        builder.append(features.size());
        builder.append(",output=");
        builder.append(output);
        builder.append(",weight=");
        builder.append(weight);
        if (metadata != null) {
            builder.append(",metadata=");
            builder.append(metadata.toString());
        }
        builder.append(",features=");
        builder.append(features.toString());
        builder.append(")");

        return builder.toString();
    }

    /**
     * Sorts the feature list to maintain the lexicographic order invariant.
     */
    @Override
    protected void sort() {
        features.sort(Feature.featureNameComparator());
    }

    @Override
    public void removeFeatures(List<Feature> featureList) {
        features.removeAll(featureList);
    }

    @Override
    public void reduceByName(Merger merger) {
        if (features.size() > 0) {
            List<Feature> featuresToRemove = new ArrayList<>();
            int buffer = 0;
            String name = features.get(buffer).getName();
            for (int i = 1; i < features.size(); i++) {
                Feature f = features.get(i);
                if (f.getName().equals(name)) {
                    // Found repeated name, add to removal list and merge into the original value.
                    featuresToRemove.add(f);
                    Feature old = features.remove(buffer);
                    features.add(buffer,new Feature(old.getName(),merger.merge(old.getValue(),f.getValue())));
                } else {
                    // Found new name, update buffer and carry on.
                    name = f.getName();
                    buffer = i;
                }
            }
            features.removeAll(featuresToRemove);
        } else {
            logger.finer("Reducing an example with no features.");
        }
    }

    @Override
    public Feature lookup(String i) {
        int index = Collections.binarySearch(features,new Feature(i,1.0));
        if (index < 0) {
            return null;
        } else {
            return features.get(index);
        }
    }

    @Override
    public void set(Feature feature) {
        int index = Collections.binarySearch(features,feature);
        if (index < 0) {
            throw new IllegalArgumentException("Feature " + feature + " not found in example.");
        } else {
            features.set(index,feature);
        }
    }

    @Override
    public boolean validateExample() {
        if (features.isEmpty()) {
            return false;
        } else {
            Set<String> names = new HashSet<>();
            for (Feature f : features) {
                names.add(f.getName());
                if (Double.isNaN(f.getValue())) {
                    return false;
                }
            }
            return names.size() == features.size();
        }
    }

    @Override
    public void transform(TransformerMap transformerMap) {
        for (Map.Entry<String,List<Transformer>> e : transformerMap.entrySet()) {
            int index = Collections.binarySearch(features, new Feature(e.getKey(),1.0));
            if (index >= 0) {
                double value = features.get(index).getValue();
                for (Transformer t : e.getValue()) {
                    value = t.transform(value);
                }
                features.set(index,new Feature(e.getKey(),value));
            }
        }
    }

    @Override
    public boolean isDense(FeatureMap fMap) {
        if (fMap.size() == size()) {
            // We've got the right number of features
            for (Feature feature : features) {
                if (fMap.get(feature.getName()) == null) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void densify(List<String> featureList) {
        int oldSize = features.size();
        int curPos = 0;
        for (String curName : featureList) {
            // If we've reached the end of our old feature set, just insert.
            if (curPos == oldSize) {
                features.add(new Feature(curName,0.0));
            } else {
                // Check to see if our insertion candidate is the same as the current feature name.
                int comparison = curName.compareTo(features.get(curPos).getName());
                if (comparison < 0) {
                    // If it's earlier, insert it.
                    features.add(new Feature(curName,0.0));
                } else if (comparison == 0) {
                    // Otherwise just bump our pointer, we've already got this feature.
                    curPos++;
                }
            }
        }
        // Sort the features
        sort();
    }

    @Override
    public void canonicalize(FeatureMap featureMap) {
        List<Feature> remadeFeatures = new ArrayList<>();
        for(Feature f: features) {
            VariableInfo vi = featureMap.get(f.getName());
            if(vi != null) {
                remadeFeatures.add(new Feature(vi.getName(), f.getValue()));
            } else {
                remadeFeatures.add(f);
            }
        }
        features.clear();
        features.addAll(remadeFeatures);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListExample)) return false;
        ListExample<?> that = (ListExample<?>) o;
        if (Objects.equals(metadata,that.metadata) && output.getClass().equals(that.output.getClass())) {
            @SuppressWarnings("unchecked") //guarded by a getClass.
            boolean outputTest = output.fullEquals((T) that.output);
            return outputTest && features.equals(that.features);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(features);
    }
}
