// Copyright 2024 Goldman Sachs
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

package org.finos.legend.engine.persistence.components.ingestmode;

import org.finos.legend.engine.persistence.components.PostgresTestArtifacts;
import org.finos.legend.engine.persistence.components.common.DedupAndVersionErrorSqlType;
import org.finos.legend.engine.persistence.components.relational.RelationalSink;
import org.finos.legend.engine.persistence.components.relational.api.DataSplitRange;
import org.finos.legend.engine.persistence.components.relational.api.GeneratorResult;
import org.finos.legend.engine.persistence.components.relational.postgres.PostgresSink;
import org.finos.legend.engine.persistence.components.testcases.ingestmode.unitemporal.UnitmemporalDeltaBatchIdBasedTestCases;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static org.finos.legend.engine.persistence.components.common.DedupAndVersionErrorSqlType.DATA_ERROR_ROWS;
import static org.finos.legend.engine.persistence.components.common.DedupAndVersionErrorSqlType.MAX_DATA_ERRORS;

public class UnitemporalDeltaBatchIdBasedTest extends UnitmemporalDeltaBatchIdBasedTestCases
{
    @Override
    public void verifyUnitemporalDeltaNoDeleteIndNoDedupNoVersion(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();

        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
                "WHERE (sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage " +
                "WHERE ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND " +
                "(sink.\"digest\" <> stage.\"digest\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')," +
                "999999999 " +
                "FROM \"mydb\".\"staging\" as stage " +
                "WHERE NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink " +
                "WHERE (sink.\"batch_id_out\" = 999999999) " +
                "AND (sink.\"digest\" = stage.\"digest\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")))))";

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), metadataIngestSql.get(0));

        String incomingRecordCount = "SELECT COUNT(*) as \"incomingRecordCount\" FROM \"mydb\".\"staging\" as stage";
        String rowsUpdated = "SELECT COUNT(*) as \"rowsUpdated\" FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1";
        String rowsDeleted = "SELECT 0 as \"rowsDeleted\"";
        String rowsInserted = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) as \"rowsInserted\"";
        String rowsTerminated = "SELECT 0 as \"rowsTerminated\"";

        verifyStats(operations, incomingRecordCount, rowsUpdated, rowsDeleted, rowsInserted, rowsTerminated);
    }

    @Override
    public void verifyUnitemporalDeltaNoDeleteIndNoDedupAllVersionsWithoutPerform(List<GeneratorResult> operations, List<DataSplitRange> dataSplitRanges)
    {
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
                "WHERE (sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage " +
                "WHERE ((stage.\"data_split\" >= '{DATA_SPLIT_LOWER_BOUND_PLACEHOLDER}') AND (stage.\"data_split\" <= '{DATA_SPLIT_UPPER_BOUND_PLACEHOLDER}')) AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND " +
                "(sink.\"digest\" <> stage.\"digest\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')," +
                "999999999 " +
                "FROM \"mydb\".\"staging\" as stage " +
                "WHERE ((stage.\"data_split\" >= '{DATA_SPLIT_LOWER_BOUND_PLACEHOLDER}') AND (stage.\"data_split\" <= '{DATA_SPLIT_UPPER_BOUND_PLACEHOLDER}')) AND " +
                "(NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink " +
                "WHERE (sink.\"batch_id_out\" = 999999999) " +
                "AND (sink.\"digest\" = stage.\"digest\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\"))))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, operations.get(0).preActionsSql().get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, operations.get(0).preActionsSql().get(1));

        Assertions.assertEquals(enrichSqlWithDataSplits(expectedMilestoneQuery, dataSplitRanges.get(0)), operations.get(0).ingestSql().get(0));
        Assertions.assertEquals(enrichSqlWithDataSplits(expectedUpsertQuery, dataSplitRanges.get(0)), operations.get(0).ingestSql().get(1));
        Assertions.assertEquals(enrichSqlWithDataSplits(expectedMilestoneQuery, dataSplitRanges.get(1)), operations.get(1).ingestSql().get(0));
        Assertions.assertEquals(enrichSqlWithDataSplits(expectedUpsertQuery, dataSplitRanges.get(1)), operations.get(1).ingestSql().get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), operations.get(0).metadataIngestSql().get(0));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), operations.get(1).metadataIngestSql().get(0));
        Assertions.assertEquals(2, operations.size());

        String incomingRecordCount = "SELECT COUNT(*) as \"incomingRecordCount\" FROM \"mydb\".\"staging\" as stage WHERE (stage.\"data_split\" >= '{DATA_SPLIT_LOWER_BOUND_PLACEHOLDER}') AND (stage.\"data_split\" <= '{DATA_SPLIT_UPPER_BOUND_PLACEHOLDER}')";
        String rowsUpdated = "SELECT COUNT(*) as \"rowsUpdated\" FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1";
        String rowsDeleted = "SELECT 0 as \"rowsDeleted\"";
        String rowsInserted = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) as \"rowsInserted\"";
        String rowsTerminated = "SELECT 0 as \"rowsTerminated\"";

        verifyStats(operations.get(0), enrichSqlWithDataSplits(incomingRecordCount, dataSplitRanges.get(0)), rowsUpdated, rowsDeleted, rowsInserted, rowsTerminated);
    }

    @Override
    public void verifyUnitemporalDeltaWithDeleteIndFilterDupsNoVersion(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();

        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink SET \"batch_id_out\" = " +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
                "WHERE " +
                "(sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) " +
                "AND ((sink.\"digest\" <> stage.\"digest\") OR (stage.\"delete_indicator\" IN ('yes','1','true')))))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')," +
                "999999999 FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE (NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink " +
                "WHERE (sink.\"batch_id_out\" = 999999999) AND (sink.\"digest\" = stage.\"digest\") " +
                "AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\"))))) AND " +
                "(stage.\"delete_indicator\" NOT IN ('yes','1','true')))";

        String expectedMetadataTableIngestQuery = "INSERT INTO batch_metadata " +
            "(\"table_name\", \"table_batch_id\", \"batch_start_ts_utc\", \"batch_end_ts_utc\", \"batch_status\", \"ingest_request_id\", \"batch_statistics\") " +
            "(SELECT 'main',(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),'2000-01-01 00:00:00.000000',CURRENT_TIMESTAMP(),'DONE','123456789',TO_JSON('{BATCH_STATISTICS_PLACEHOLDER}'::json))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));
        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(expectedMetadataTableIngestQuery, metadataIngestSql.get(0));

        String incomingRecordCount = "SELECT COUNT(*) as \"incomingRecordCount\" FROM \"mydb\".\"staging\" as stage";
        String rowsUpdated = "SELECT COUNT(*) as \"rowsUpdated\" FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) AND (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink2 WHERE ((sink2.\"id\" = sink.\"id\") AND (sink2.\"name\" = sink.\"name\")) AND (sink2.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))))";
        String rowsDeleted = "SELECT 0 as \"rowsDeleted\"";
        String rowsInserted = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) AND (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink2 WHERE ((sink2.\"id\" = sink.\"id\") AND (sink2.\"name\" = sink.\"name\")) AND (sink2.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))))) as \"rowsInserted\"";
        String rowsTerminated = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1)-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) AND (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink2 WHERE ((sink2.\"id\" = sink.\"id\") AND (sink2.\"name\" = sink.\"name\")) AND (sink2.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))))) as \"rowsTerminated\"";
        verifyStats(operations, incomingRecordCount, rowsUpdated, rowsDeleted, rowsInserted, rowsTerminated);
    }

    @Override
    public void verifyUnitemporalDeltaWithDeleteIndNoDedupAllVersion(List<GeneratorResult> operations, List<DataSplitRange> dataSplitRanges)
    {
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink SET \"batch_id_out\" = " +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
                "WHERE " +
                "(sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE ((stage.\"data_split\" >= '{DATA_SPLIT_LOWER_BOUND_PLACEHOLDER}') AND (stage.\"data_split\" <= '{DATA_SPLIT_UPPER_BOUND_PLACEHOLDER}')) AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) " +
                "AND ((sink.\"digest\" <> stage.\"digest\") OR (stage.\"delete_indicator\" IN ('yes','1','true')))))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')," +
                "999999999 FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE ((stage.\"data_split\" >= '{DATA_SPLIT_LOWER_BOUND_PLACEHOLDER}') AND (stage.\"data_split\" <= '{DATA_SPLIT_UPPER_BOUND_PLACEHOLDER}')) AND " +
                "(NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink " +
                "WHERE (sink.\"batch_id_out\" = 999999999) AND (sink.\"digest\" = stage.\"digest\") " +
                "AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\"))))) AND " +
                "(stage.\"delete_indicator\" NOT IN ('yes','1','true')))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, operations.get(0).preActionsSql().get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, operations.get(0).preActionsSql().get(1));

        Assertions.assertEquals(enrichSqlWithDataSplits(expectedMilestoneQuery, dataSplitRanges.get(0)), operations.get(0).ingestSql().get(0));
        Assertions.assertEquals(enrichSqlWithDataSplits(expectedUpsertQuery, dataSplitRanges.get(0)), operations.get(0).ingestSql().get(1));
        Assertions.assertEquals(enrichSqlWithDataSplits(expectedMilestoneQuery, dataSplitRanges.get(1)), operations.get(1).ingestSql().get(0));
        Assertions.assertEquals(enrichSqlWithDataSplits(expectedUpsertQuery, dataSplitRanges.get(1)), operations.get(1).ingestSql().get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), operations.get(0).metadataIngestSql().get(0));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), operations.get(1).metadataIngestSql().get(0));
        Assertions.assertEquals(2, operations.size());

        String incomingRecordCount = "SELECT COUNT(*) as \"incomingRecordCount\" FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage WHERE (stage.\"data_split\" >= '{DATA_SPLIT_LOWER_BOUND_PLACEHOLDER}') AND (stage.\"data_split\" <= '{DATA_SPLIT_UPPER_BOUND_PLACEHOLDER}')";
        String rowsUpdated = "SELECT COUNT(*) as \"rowsUpdated\" FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) AND (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink2 WHERE ((sink2.\"id\" = sink.\"id\") AND (sink2.\"name\" = sink.\"name\")) AND (sink2.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))))";
        String rowsDeleted = "SELECT 0 as \"rowsDeleted\"";
        String rowsInserted = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) AND (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink2 WHERE ((sink2.\"id\" = sink.\"id\") AND (sink2.\"name\" = sink.\"name\")) AND (sink2.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))))) as \"rowsInserted\"";
        String rowsTerminated = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1)-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) AND (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink2 WHERE ((sink2.\"id\" = sink.\"id\") AND (sink2.\"name\" = sink.\"name\")) AND (sink2.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))))) as \"rowsTerminated\"";
        verifyStats(operations.get(0), enrichSqlWithDataSplits(incomingRecordCount, dataSplitRanges.get(0)), rowsUpdated, rowsDeleted, rowsInserted, rowsTerminated);
    }

    @Override
    public void verifyUnitemporalDeltaWithUpperCaseOptimizer(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();

        String expectedMilestoneQuery = "UPDATE \"MYDB\".\"MAIN\" as sink " +
                "SET \"BATCH_ID_OUT\" = (SELECT COALESCE(MAX(BATCH_METADATA.\"TABLE_BATCH_ID\"),0)+1 " +
                "FROM BATCH_METADATA as BATCH_METADATA WHERE UPPER(BATCH_METADATA.\"TABLE_NAME\") = 'MAIN')-1 " +
                "WHERE (sink.\"BATCH_ID_OUT\" = 999999999) " +
                "AND (EXISTS (SELECT * FROM \"MYDB\".\"STAGING\" as stage " +
                "WHERE ((sink.\"ID\" = stage.\"ID\") AND (sink.\"NAME\" = stage.\"NAME\")) " +
                "AND (sink.\"DIGEST\" <> stage.\"DIGEST\")))";

        String expectedUpsertQuery = "INSERT INTO \"MYDB\".\"MAIN\" " +
                "(\"ID\", \"NAME\", \"AMOUNT\", \"BIZ_DATE\", \"DIGEST\", \"BATCH_ID_IN\", \"BATCH_ID_OUT\") " +
                "(SELECT stage.\"ID\",stage.\"NAME\",stage.\"AMOUNT\",stage.\"BIZ_DATE\"," +
                "stage.\"DIGEST\",(SELECT COALESCE(MAX(BATCH_METADATA.\"TABLE_BATCH_ID\"),0)+1 FROM BATCH_METADATA as BATCH_METADATA WHERE UPPER(BATCH_METADATA.\"TABLE_NAME\") = 'MAIN')," +
                "999999999 FROM \"MYDB\".\"STAGING\" as stage " +
                "WHERE NOT (EXISTS (SELECT * FROM \"MYDB\".\"MAIN\" as sink " +
                "WHERE (sink.\"BATCH_ID_OUT\" = 999999999) AND (sink.\"DIGEST\" = stage.\"DIGEST\") " +
                "AND ((sink.\"ID\" = stage.\"ID\") AND (sink.\"NAME\" = stage.\"NAME\")))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQueryWithUpperCase, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQueryWithUpperCase, preActionsSql.get(1));
        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQueryWithUpperCase(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithCleanStagingData(GeneratorResult operations)
    {
        List<String> postActionsSql = operations.postActionsSql();
        Assertions.assertEquals(PostgresTestArtifacts.expectedStagingCleanupQuery, postActionsSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaNoDeleteIndWithOptimizationFilters(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();

        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 WHERE " +
                "(sink.\"batch_id_out\" = 999999999) AND ((sink.\"id\" >= '{id_legend_persistence_lower}') AND (sink.\"id\" <= '{id_legend_persistence_upper}')) " +
                "AND (EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage WHERE ((sink.\"id\" = stage.\"id\") " +
                "AND (sink.\"name\" = stage.\"name\")) AND (sink.\"digest\" <> stage.\"digest\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging\" as stage " +
                "WHERE NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) AND " +
                "(sink.\"digest\" = stage.\"digest\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND " +
                "((sink.\"id\" >= '{id_legend_persistence_lower}') AND (sink.\"id\" <= '{id_legend_persistence_upper}')))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaNoDeleteIndWithOptimizationFiltersIncludesNullValues(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();

        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
            "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 WHERE " +
            "(sink.\"batch_id_out\" = 999999999) AND (((sink.\"id\" >= '{id_legend_persistence_lower}') AND (sink.\"id\" <= '{id_legend_persistence_upper}')) OR (sink.\"id\" IS NULL)) " +
            "AND (EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage WHERE ((sink.\"id\" = stage.\"id\") " +
            "AND (sink.\"name\" = stage.\"name\")) AND (sink.\"digest\" <> stage.\"digest\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
            "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
            "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging\" as stage " +
            "WHERE NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) AND " +
            "(sink.\"digest\" = stage.\"digest\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND " +
            "(((sink.\"id\" >= '{id_legend_persistence_lower}') AND (sink.\"id\" <= '{id_legend_persistence_upper}')) OR (sink.\"id\" IS NULL)))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithNoVersionAndStagingFilter(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 WHERE (sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage WHERE (((sink.\"id\" = stage.\"id\") AND " +
                "(sink.\"name\" = stage.\"name\")) AND (sink.\"digest\" <> stage.\"digest\")) AND (stage.\"batch_id_in\" > 5)))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging\" as stage " +
                "WHERE (NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) " +
                "AND (sink.\"digest\" = stage.\"digest\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\"))))) " +
                "AND (stage.\"batch_id_in\" > 5))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQueryWithStagingFiltersAndAdditionalMetadata("{\"staging_filters\":{\"batch_id_in\":{\"GT\":5}}}"), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithNoVersionAndFilteredDataset(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
            "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 WHERE (sink.\"batch_id_out\" = 999999999) AND " +
            "(EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage WHERE (((sink.\"id\" = stage.\"id\") AND " +
            "(sink.\"name\" = stage.\"name\")) AND (sink.\"digest\" <> stage.\"digest\")) AND (stage.\"batch_id_in\" > 5)))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
            "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"batch_id_in\", \"batch_id_out\") " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
            "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging\" as stage " +
            "WHERE (NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) " +
            "AND (sink.\"digest\" = stage.\"digest\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\"))))) " +
            "AND (stage.\"batch_id_in\" > 5))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithFilterDupsMaxVersionWithStagingFilter(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
                "WHERE (sink.\"batch_id_out\" = 999999999) AND (EXISTS (SELECT * FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND (stage.\"version\" > sink.\"version\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"version\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" " +
                "as stage WHERE NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) " +
                "AND (stage.\"version\" <= sink.\"version\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdAndVersionBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));
        Assertions.assertEquals(PostgresTestArtifacts.expectedBaseTempStagingTableWithVersionAndCount, preActionsSql.get(2));

        String expectedInsertIntoBaseTempStagingWithMaxVersionFilterDupsWithStagingFilters = "INSERT INTO \"mydb\".\"staging_temp_staging_lp_yosulf\" " +
            "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"version\", \"legend_persistence_count\") " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
            "stage.\"legend_persistence_count\" as \"legend_persistence_count\" FROM " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
            "stage.\"legend_persistence_count\" as \"legend_persistence_count\",DENSE_RANK() OVER " +
            "(PARTITION BY stage.\"id\",stage.\"name\" ORDER BY stage.\"version\" DESC) as \"legend_persistence_rank\" " +
            "FROM (SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
            "stage.\"version\",COUNT(*) as \"legend_persistence_count\" FROM \"mydb\".\"staging\" as stage " +
            "WHERE stage.\"batch_id_in\" > 5 GROUP BY stage.\"id\", stage.\"name\", stage.\"amount\", stage.\"biz_date\", " +
            "stage.\"digest\", stage.\"version\") as stage) as stage WHERE stage.\"legend_persistence_rank\" = 1)";

        Assertions.assertEquals(PostgresTestArtifacts.expectedTempStagingCleanupQuery, operations.deduplicationAndVersioningSql().get(0));
        Assertions.assertEquals(expectedInsertIntoBaseTempStagingWithMaxVersionFilterDupsWithStagingFilters, operations.deduplicationAndVersioningSql().get(1));
        Assertions.assertEquals(PostgresTestArtifacts.dataErrorCheckSqlWithVersionVersion, operations.deduplicationAndVersioningErrorChecksSql().get(MAX_DATA_ERRORS));
        Assertions.assertEquals(PostgresTestArtifacts.dataErrorsSql, operations.deduplicationAndVersioningErrorChecksSql().get(DedupAndVersionErrorSqlType.DATA_ERROR_ROWS));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestWithStagingFiltersQuery(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithFilterDupsMaxVersionWithFilteredDataset(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
            "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
            "WHERE (sink.\"batch_id_out\" = 999999999) AND (EXISTS (SELECT * FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
            "WHERE ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND (stage.\"version\" > sink.\"version\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
            "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"version\", \"batch_id_in\", \"batch_id_out\") " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
            "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" " +
            "as stage WHERE NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) " +
            "AND (stage.\"version\" <= sink.\"version\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdAndVersionBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));
        Assertions.assertEquals(PostgresTestArtifacts.expectedBaseTempStagingTableWithVersionAndCount, preActionsSql.get(2));

        String expectedInsertIntoBaseTempStagingWithMaxVersionFilterDupsWithStagingFilters = "INSERT INTO \"mydb\".\"staging_temp_staging_lp_yosulf\" " +
            "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"version\", \"legend_persistence_count\") " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
            "stage.\"legend_persistence_count\" as \"legend_persistence_count\" FROM " +
            "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
            "stage.\"legend_persistence_count\" as \"legend_persistence_count\",DENSE_RANK() OVER " +
            "(PARTITION BY stage.\"id\",stage.\"name\" ORDER BY stage.\"version\" DESC) as \"legend_persistence_rank\" " +
            "FROM (SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\"," +
            "stage.\"version\",COUNT(*) as \"legend_persistence_count\" FROM \"mydb\".\"staging\" as stage " +
            "WHERE stage.\"batch_id_in\" > 5 GROUP BY stage.\"id\", stage.\"name\", stage.\"amount\", stage.\"biz_date\", " +
            "stage.\"digest\", stage.\"version\") as stage) as stage WHERE stage.\"legend_persistence_rank\" = 1)";

        Assertions.assertEquals(PostgresTestArtifacts.expectedTempStagingCleanupQuery, operations.deduplicationAndVersioningSql().get(0));
        Assertions.assertEquals(expectedInsertIntoBaseTempStagingWithMaxVersionFilterDupsWithStagingFilters, operations.deduplicationAndVersioningSql().get(1));
        Assertions.assertEquals(PostgresTestArtifacts.dataErrorCheckSqlWithVersionVersion, operations.deduplicationAndVersioningErrorChecksSql().get(MAX_DATA_ERRORS));
        Assertions.assertEquals(PostgresTestArtifacts.dataErrorsSql, operations.deduplicationAndVersioningErrorChecksSql().get(DATA_ERROR_ROWS));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQuery(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithNoDedupMaxVersionWithoutPerformAndStagingFilters(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 WHERE (sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging\" as stage WHERE (((sink.\"id\" = stage.\"id\") AND " +
                "(sink.\"name\" = stage.\"name\")) AND (stage.\"version\" > sink.\"version\")) AND (stage.\"batch_id_in\" > 5)))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"version\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
                "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'),999999999 FROM \"mydb\".\"staging\" as stage " +
                "WHERE (NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink WHERE (sink.\"batch_id_out\" = 999999999) " +
                "AND (stage.\"version\" <= sink.\"version\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\"))))) " +
                "AND (stage.\"batch_id_in\" > 5))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdAndVersionBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestWithStagingFiltersQuery(), metadataIngestSql.get(0));

        String incomingRecordCount = "SELECT COUNT(*) as \"incomingRecordCount\" FROM \"mydb\".\"staging\" as stage WHERE stage.\"batch_id_in\" > 5";
        String rowsUpdated = "SELECT COUNT(*) as \"rowsUpdated\" FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1";
        String rowsDeleted = "SELECT 0 as \"rowsDeleted\"";
        String rowsInserted = "SELECT (SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_in\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN'))-(SELECT COUNT(*) FROM \"mydb\".\"main\" as sink WHERE sink.\"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1) as \"rowsInserted\"";
        String rowsTerminated = "SELECT 0 as \"rowsTerminated\"";

        verifyStats(operations, incomingRecordCount, rowsUpdated, rowsDeleted, rowsInserted, rowsTerminated);
    }

    @Override
    public void verifyUnitemporalDeltaWithFailOnDupsMaxVersioningWithoutPerform(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"mydb\".\"main\" as sink " +
                "SET \"batch_id_out\" = (SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')-1 " +
                "WHERE (sink.\"batch_id_out\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")) AND " +
                "(stage.\"version\" > sink.\"version\")))";

        String expectedUpsertQuery = "INSERT INTO \"mydb\".\"main\" " +
                "(\"id\", \"name\", \"amount\", \"biz_date\", \"digest\", \"version\", \"batch_id_in\", \"batch_id_out\") " +
                "(SELECT stage.\"id\",stage.\"name\",stage.\"amount\",stage.\"biz_date\",stage.\"digest\",stage.\"version\"," +
                "(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')," +
                "999999999 " +
                "FROM \"mydb\".\"staging_temp_staging_lp_yosulf\" as stage " +
                "WHERE NOT (EXISTS (SELECT * FROM \"mydb\".\"main\" as sink " +
                "WHERE (sink.\"batch_id_out\" = 999999999) " +
                "AND (stage.\"version\" <= sink.\"version\") AND ((sink.\"id\" = stage.\"id\") AND (sink.\"name\" = stage.\"name\")))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdAndVersionBasedCreateQuery, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQuery, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQueryWithBatchSuccessValue(), metadataIngestSql.get(0));
    }

    @Override
    public void verifyUnitemporalDeltaWithNoDedupMaxVersioningAndUpperCaseWithoutStagingFilters(GeneratorResult operations)
    {
        List<String> preActionsSql = operations.preActionsSql();
        List<String> milestoningSql = operations.ingestSql();
        List<String> metadataIngestSql = operations.metadataIngestSql();
        String expectedMilestoneQuery = "UPDATE \"MYDB\".\"MAIN\" as sink SET \"BATCH_ID_OUT\" = " +
                "(SELECT COALESCE(MAX(BATCH_METADATA.\"TABLE_BATCH_ID\"),0)+1 FROM BATCH_METADATA " +
                "as BATCH_METADATA WHERE UPPER(BATCH_METADATA.\"TABLE_NAME\") = 'MAIN')-1 " +
                "WHERE (sink.\"BATCH_ID_OUT\" = 999999999) AND " +
                "(EXISTS (SELECT * FROM \"MYDB\".\"STAGING_TEMP_STAGING_LP_YOSULF\" as stage " +
                "WHERE ((sink.\"ID\" = stage.\"ID\") AND (sink.\"NAME\" = stage.\"NAME\")) AND (stage.\"VERSION\" >= sink.\"VERSION\")))";

        String expectedUpsertQuery = "INSERT INTO \"MYDB\".\"MAIN\" " +
                "(\"ID\", \"NAME\", \"AMOUNT\", \"BIZ_DATE\", \"DIGEST\", \"VERSION\", \"BATCH_ID_IN\", \"BATCH_ID_OUT\") " +
                "(SELECT stage.\"ID\",stage.\"NAME\",stage.\"AMOUNT\",stage.\"BIZ_DATE\",stage.\"DIGEST\",stage.\"VERSION\"," +
                "(SELECT COALESCE(MAX(BATCH_METADATA.\"TABLE_BATCH_ID\"),0)+1 FROM BATCH_METADATA as BATCH_METADATA " +
                "WHERE UPPER(BATCH_METADATA.\"TABLE_NAME\") = 'MAIN'),999999999 FROM \"MYDB\".\"STAGING_TEMP_STAGING_LP_YOSULF\" as stage " +
                "WHERE NOT (EXISTS (SELECT * FROM \"MYDB\".\"MAIN\" as sink WHERE (sink.\"BATCH_ID_OUT\" = 999999999) AND " +
                "(stage.\"VERSION\" < sink.\"VERSION\") AND ((sink.\"ID\" = stage.\"ID\") AND (sink.\"NAME\" = stage.\"NAME\")))))";

        Assertions.assertEquals(PostgresTestArtifacts.expectedMainTableBatchIdAndVersionBasedCreateQueryWithUpperCase, preActionsSql.get(0));
        Assertions.assertEquals(PostgresTestArtifacts.expectedMetadataTableCreateQueryWithUpperCase, preActionsSql.get(1));

        Assertions.assertEquals(expectedMilestoneQuery, milestoningSql.get(0));
        Assertions.assertEquals(expectedUpsertQuery, milestoningSql.get(1));
        Assertions.assertEquals(getExpectedMetadataTableIngestQueryWithAdditionalMetadataWithUpperCase(), metadataIngestSql.get(0));
    }

    @Override
    public RelationalSink getRelationalSink()
    {
        return PostgresSink.get();
    }

    protected String getExpectedMetadataTableIngestQuery()
    {
        return PostgresTestArtifacts.expectedMetadataTableIngestQuery;
    }

    protected String getExpectedMetadataTableIngestQueryWithBatchSuccessValue()
    {
        return PostgresTestArtifacts.expectedMetadataTableIngestQueryWithBatchSuccessValue;
    }

    protected String getExpectedMetadataTableIngestWithStagingFiltersQuery()
    {
        return PostgresTestArtifacts.expectedMetadataTableIngestQueryWithAdditionalMetadataBatchId;
    }

    protected String getExpectedMetadataTableIngestQueryWithUpperCase()
    {
        return PostgresTestArtifacts.expectedMetadataTableIngestQueryWithUpperCase;
    }

    protected String getExpectedMetadataTableIngestQueryWithAdditionalMetadataWithUpperCase()
    {
        return PostgresTestArtifacts.expectedMetadataTableIngestQueryWithAdditionalMetadataWithUpperCase;
    }

    protected String getExpectedMetadataTableIngestQueryWithStagingFiltersAndAdditionalMetadata(String stagingFilters)
    {
        return "INSERT INTO batch_metadata " +
            "(\"table_name\", \"table_batch_id\", \"batch_start_ts_utc\", \"batch_end_ts_utc\", \"batch_status\", \"batch_source_info\", \"additional_metadata\") " +
            "(SELECT 'main',(SELECT COALESCE(MAX(batch_metadata.\"table_batch_id\"),0)+1 FROM batch_metadata as batch_metadata " +
            "WHERE UPPER(batch_metadata.\"table_name\") = 'MAIN')," +
            "'2000-01-01 00:00:00.000000',CURRENT_TIMESTAMP(),'DONE'," +
            String.format("TO_JSON('%s'", stagingFilters) + "::json)," +
            "TO_JSON('{\"watermark\":\"my_watermark_value\"}'::json))";
    }
}
