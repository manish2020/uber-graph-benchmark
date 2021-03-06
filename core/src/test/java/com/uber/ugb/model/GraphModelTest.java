/*
 *
 *  * Copyright 2018 Uber Technologies Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.uber.ugb.model;

import com.uber.ugb.model.distro.ConstantDegreeDistribution;
import com.uber.ugb.model.distro.LogNormalDegreeDistribution;
import com.uber.ugb.schema.InvalidSchemaException;
import com.uber.ugb.schema.QualifiedName;
import com.uber.ugb.schema.SchemaBuilder;
import com.uber.ugb.schema.Vocabulary;
import com.uber.ugb.schema.model.dto.EntityTypeDTO;
import com.uber.ugb.schema.model.dto.RelationTypeDTO;
import com.uber.ugb.schema.model.dto.SchemaDTO;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class GraphModelTest {
    private static Vocabulary createVocabulary() throws InvalidSchemaException, IOException {
        SchemaDTO schemaDTO = new SchemaDTO("test");
        schemaDTO.setIncludes(new String[]{Vocabulary.CORE_SCHEMA_NAME});

        EntityTypeDTO monkey = new EntityTypeDTO("Monkey");
        monkey.setExtends(new String[]{"core.Thing"});
        EntityTypeDTO weasel = new EntityTypeDTO("Weasel");
        EntityTypeDTO mulberryBush = new EntityTypeDTO("MulberryBush");
        weasel.setExtends(new String[]{"core.Thing"});
        schemaDTO.setEntities(new EntityTypeDTO[]{monkey, weasel, mulberryBush});

        RelationTypeDTO chased = new RelationTypeDTO("chased");
        chased.setFrom(monkey.getLabel());
        chased.setTo(weasel.getLabel());
        RelationTypeDTO popped = new RelationTypeDTO("popped");
        popped.setFrom(weasel.getLabel());
        popped.setTo(monkey.getLabel());
        schemaDTO.setRelations(new RelationTypeDTO[]{chased, popped});

        SchemaBuilder builder = new SchemaBuilder();
        builder.addSchema(GraphModelTest.class.getClassLoader().getResourceAsStream("trips/concepts/core.yaml"));
        builder.addSchemas(schemaDTO);

        return builder.toVocabulary();
    }

    private static EdgeModel edge(final Incidence domain,
                                  final Incidence range) {
        return new EdgeModel(domain, range);
    }

    private static Incidence domain(final QualifiedName vertexLabel,
                                    final double existenceProbability) {
        return new Incidence(
            vertexLabel, existenceProbability, new ConstantDegreeDistribution(1));
    }

    private static Incidence domain(final QualifiedName vertexLabel,
                                    final double existenceProbability,
                                    final double logMean,
                                    final double logSD) {
        return new Incidence(
            vertexLabel, existenceProbability, new LogNormalDegreeDistribution(logMean, logSD));
    }

    @Test
    public void modelHashesAreUnique() throws Exception {
        Vocabulary vocabulary = createVocabulary();

        Partitioner vertexPartitioner = new Partitioner();
        vertexPartitioner.put(new QualifiedName("Monkey"), 1);
        vertexPartitioner.put(new QualifiedName("Weasel"), 1);

        LinkedHashMap<QualifiedName, EdgeModel> edgeModel = new LinkedHashMap<>();
        edgeModel.put(new QualifiedName("chased"), edge(
            domain(new QualifiedName("Monkey"), 0.9543, 0.7813873, 1.0293729),
            domain(new QualifiedName("Weasel"), 1.0000)));
        LinkedHashMap<QualifiedName, PropertyModel> vertexPropertyModels = new LinkedHashMap<>();
        LinkedHashMap<QualifiedName, PropertyModel> edgePropertyModels = new LinkedHashMap<>();

        GraphModel model = new GraphModel(
            vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);

        String firstHash = model.getHash();
        String secondHash = model.getHash();
        assertEquals(firstHash, secondHash);

        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        secondHash = model.getHash();
        assertEquals(firstHash, secondHash);

        vertexPartitioner.put(new QualifiedName("MulberryBush"), 5);
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        secondHash = model.getHash();
        assertNotEquals(firstHash, secondHash);

        vertexPartitioner.put(new QualifiedName("MulberryBush"), 5);
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        String thirdHash = model.getHash();
        assertEquals(secondHash, thirdHash);

        vertexPartitioner.put(new QualifiedName("MulberryBush"), 7);
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        thirdHash = model.getHash();
        assertNotEquals(secondHash, thirdHash);

        /*
        RelationType uuid = vocabulary.getRelationTypes().get(new QualifiedName("core", "uuid"));
        SimpleProperty<String> prop = new SimpleProperty<>(uuid, random -> "pop!");
        PropertyModel propStats = new PropertyModel();
        propStats.addProperty(prop);
        vertexPropertyModels.put("Weasel", propStats);
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        String fourthHash = model.getHash();
        assertNotEquals(thirdHash, fourthHash);
        */

        edgeModel = new LinkedHashMap<>();
        edgeModel.put(new QualifiedName("chased"), edge(
            domain(new QualifiedName("Monkey"), 0.9543, 0.7813873, 1.0293729),
            domain(new QualifiedName("Weasel"), 0.5))); // change probability
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        String fifthHash = model.getHash();
        assertNotEquals(thirdHash, fifthHash);

        edgeModel = new LinkedHashMap<>();
        edgeModel.put(new QualifiedName("chased"), edge(
            domain(new QualifiedName("Monkey"), 0.9543, 0.5, 1.5), // change log-normal params
            domain(new QualifiedName("Weasel"), 0.5)));
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        String sixthHash = model.getHash();
        assertNotEquals(fifthHash, sixthHash);

        edgeModel.put(new QualifiedName("popped"), edge(
            domain(new QualifiedName("Weasel"), 0.5),
            domain(new QualifiedName("Monkey"), 0.5)));
        model = new GraphModel(vocabulary, vertexPartitioner, edgeModel, vertexPropertyModels, edgePropertyModels);
        String seventhHash = model.getHash();
        assertNotEquals(sixthHash, seventhHash);
    }
}
