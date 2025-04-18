// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.language.pure.grammar.test;

import org.antlr.v4.runtime.Vocabulary;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.ListAdapter;
import org.finos.legend.engine.language.pure.grammar.from.antlr4.connection.ConnectionParserGrammar;
import org.finos.legend.engine.language.pure.grammar.from.antlr4.connection.RelationalDatabaseConnectionParserGrammar;
import org.junit.Test;

import java.util.List;

public class TestRelationalConnectionGrammarParser extends TestGrammarParser.TestGrammarParserTestSuite
{
    @Override
    public Vocabulary getParserGrammarVocabulary()
    {
        return ConnectionParserGrammar.VOCABULARY;
    }

    @Override
    public List<Vocabulary> getDelegatedParserGrammarVocabulary()
    {
        return FastList.newListWith(
                RelationalDatabaseConnectionParserGrammar.VOCABULARY
        );
    }

    @Override
    public String getParserGrammarIdentifierInclusionTestCode(List<String> keywords)
    {
        return "###Connection\n" +
                "RelationalDatabaseConnection " + ListAdapter.adapt(keywords).makeString("::") + "\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  timezone: +3000;\n" +
                "  type: H2;\n" +
                "  auth: DefaultH2;\n" +
                "}\n\n";
    }

    private String getTemplateConnectionWithTz(String offsetOrCode)
    {
        return "###Connection\n" +
            "RelationalDatabaseConnection meta::mySimpleConnection\n" +
            "{\n" +
            "  store: model::firm::Person;\n" +
            "  timezone: " + offsetOrCode + ";\n" +
            "  type: H2;\n" +
            "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
            "  auth: DefaultH2;\n" +
            "}\n\n";
    }

    @Test
    public void testConnectionWithTimeOut()
    {
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  queryTimeOutInSeconds: 5000;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  auth: DefaultH2;\n" +
                "}\n\n");
    }

    @Test
    public void testTimezoneConfiguration()
    {
        // With Offset
        test(getTemplateConnectionWithTz("+0700"), null);
        // With zone id
        test(getTemplateConnectionWithTz("'EST'"), null);
        test(getTemplateConnectionWithTz("'US/Arizona'"), null);
    }

    @Test
    public void testRelationalDatabaseConnection()
    {
        // Missing fields
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "}\n\n", "PARSER error at [2:1-4:1]: Field 'type' is required");
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "}\n\n", "PARSER error at [2:1-5:1]: Field 'type' is required");
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  type: H2;\n" +
                "  store: model::firm::Person;\n" +
                "}\n\n", "PARSER error at [2:1-6:1]: Field 'specification' is required");
    }

    @Test
    public void testLocalH2DatasourceConfiguration()
    {
        //Duplicate field
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 {\n" +
                "    testDataSetupCSV: 'testCSV';\n" +
                "    testDataSetupCSV: 'testCSV';\n" +
                "  };\n" +
                "  auth: DefaultH2;\n" +
                "}\n\n", "PARSER error at [6:3-9:4]: Field 'testDataSetupCsv' should be specified only once");
    }

    @Test
    public void testMapperPostProcessorsTableMissingFrom()
    {
        testPostProcessor(
                "PARSER error at [15:9-56]: Field 'from' is required",
                "    mapper\n" +
                        "    {\n" +
                        "      mappers:\n" +
                        "      [\n" +
                        "        table {to: 'A'; schemaFrom: 'b'; schemaTo: 'B';}\n" +
                        "      ];\n" +
                        "    }");
    }

    @Test
    public void testMapperPostProcessorsTableMissingTo()
    {
        testPostProcessor(
                "PARSER error at [15:9-58]: Field 'to' is required",
                "    mapper\n" +
                        "    {\n" +
                        "      mappers:\n" +
                        "      [\n" +
                        "        table {from: 'a'; schemaFrom: 'b'; schemaTo: 'B';}\n" +
                        "      ];\n" +
                        "    }");
    }


    @Test
    public void testMapperPostProcessorsTableMissingSchemaFrom()
    {
        testPostProcessor(
                "PARSER error at [15:9-50]: Field 'schemaFrom' is required",
                "    mapper\n" +
                        "    {\n" +
                        "      mappers:\n" +
                        "      [\n" +
                        "        table {from: 'a'; to: 'A'; schemaTo: 'B';}\n" +
                        "      ];\n" +
                        "    }");
    }

    @Test
    public void testMapperPostProcessorsSchemaMissingFrom()
    {
        testPostProcessor(
                "PARSER error at [15:9-25]: Field 'from' is required",
                "    mapper\n" +
                        "    {\n" +
                        "      mappers:\n" +
                        "      [\n" +
                        "        schema {to: 'A';}\n" +
                        "      ];\n" +
                        "    }");
    }

    @Test
    public void testMapperPostProcessorsSchemaMissingTo()
    {
        testPostProcessor(
                "PARSER error at [15:9-27]: Field 'to' is required",
                "    mapper\n" +
                        "    {\n" +
                        "      mappers:\n" +
                        "      [\n" +
                        "        schema {from: 'a';}\n" +
                        "      ];\n" +
                        "    }");
    }

    private void testPostProcessor(String error, String... postProcessors)
    {
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 {\n" +
                "    testDataSetupCSV: 'testCSV';\n" +
                "  };\n" +
                "  auth: DefaultH2;\n" +
                "  postProcessors:\n" +
                "  [\n" +
                String.join(",\n", postProcessors) + "\n" +
                "  ];\n" +
                "}\n", error);
    }

    @Test
    public void testQueryGenerationConfigs()
    {
        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  queryTimeOutInSeconds: 5000;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  auth: DefaultH2;\n" +
                "  queryGenerationConfigs: [];\n" +
                "  queryGenerationConfigs: [];\n" +
                "}\n\n", "PARSER error at [2:1-11:1]: Field 'queryGenerationConfigs' should be specified only once");

        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  queryTimeOutInSeconds: 5000;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  auth: DefaultH2;\n" +
                "  queryGenerationConfigs: [\n" +
                "    UnknownConfig{}\n" +
                "  ];\n" +
                "}\n\n", "PARSER error at [10:5-19]: Unsupported Relational Query Generation Config type 'UnknownConfig'");

        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  queryTimeOutInSeconds: 5000;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  auth: DefaultH2;\n" +
                "  queryGenerationConfigs: [\n" +
                "    GenerationFeaturesConfig\n" +
                "    {\n" +
                "      unknownProp: [];\n" +
                "    }\n" +
                "  ];\n" +
                "}\n\n", "PARSER error at [12:7-17]: Unexpected token 'unknownProp'. Valid alternatives: ['enabled', 'disabled']");

        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  queryTimeOutInSeconds: 5000;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  auth: DefaultH2;\n" +
                "  queryGenerationConfigs: [\n" +
                "    GenerationFeaturesConfig\n" +
                "    {\n" +
                "      enabled: [];\n" +
                "      enabled: [];\n" +
                "    }\n" +
                "  ];\n" +
                "}\n\n", "PARSER error at [10:5-14:5]: Field 'enabled' should be specified only once");

        test("###Connection\n" +
                "RelationalDatabaseConnection meta::mySimpleConnection\n" +
                "{\n" +
                "  store: model::firm::Person;\n" +
                "  queryTimeOutInSeconds: 5000;\n" +
                "  type: H2;\n" +
                "  specification: LocalH2 { testDataSetupCSV: 'testCSV'; };\n" +
                "  auth: DefaultH2;\n" +
                "  queryGenerationConfigs: [\n" +
                "    GenerationFeaturesConfig\n" +
                "    {\n" +
                "      enabled: ['feat1', 'feat 2'];\n" +
                "      disabled: ['feat3'];\n" +
                "    }\n" +
                "  ];\n" +
                "}\n\n");
    }
}
