//  Copyright 2022 Goldman Sachs
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.connection.specification;

public class SnowflakeDatasourceSpecification extends DatasourceSpecification
{
    public String accountName;
    public String region;
    public String warehouseName;
    public String databaseName;
    public String cloudType;

    public Boolean quotedIdentifiersIgnoreCase;
    public Boolean enableQueryTags;
    public String proxyHost;
    public String proxyPort;
    public String nonProxyHosts;
    public String organization;
    public String accountType;

    public String tempTableDb;
    public String tempTableSchema;

    public String role;

    @Override
    public <T> T accept(DatasourceSpecificationVisitor<T> datasourceSpecificationVisitor)
    {
        return datasourceSpecificationVisitor.visit(this);
    }
}
