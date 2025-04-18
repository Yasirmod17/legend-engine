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

package org.finos.legend.engine.language.pure.grammar.from.runtime;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserUtility;
import org.finos.legend.engine.language.pure.grammar.from.antlr4.runtime.RuntimeParserGrammar;
import org.finos.legend.engine.language.pure.grammar.from.connection.ConnectionParser;
import org.finos.legend.engine.protocol.pure.m3.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementType;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.ConnectionPointer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.ConnectionStores;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.EngineRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.IdentifiedConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.SingleConnectionEngineRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.PackageableRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.StoreConnections;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.section.ImportAwareCodeSection;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

public class RuntimeParseTreeWalker
{
    private final ParseTreeWalkerSourceInformation walkerSourceInformation;
    private final Consumer<PackageableElement> elementConsumer;
    private final ImportAwareCodeSection section;
    private final ConnectionParser connectionParser;

    public RuntimeParseTreeWalker(ParseTreeWalkerSourceInformation walkerSourceInformation, Consumer<PackageableElement> elementConsumer, ImportAwareCodeSection section, ConnectionParser connectionParser)
    {
        this.walkerSourceInformation = walkerSourceInformation;
        this.elementConsumer = elementConsumer;
        this.section = section;
        this.connectionParser = connectionParser;
    }

    public void visit(RuntimeParserGrammar.DefinitionContext ctx)
    {
        ListIterate.collect(ctx.imports().importStatement(), importCtx -> PureGrammarParserUtility.fromPath(importCtx.packagePath().identifier()), this.section.imports);
        ctx.runtime().stream().map(this::visitRuntime).peek(e -> this.section.elements.add(e.getPath())).forEach(this.elementConsumer);
        ctx.singleConnectionRuntime().stream().map(this::visitSingleConnectionRuntime).peek(e -> this.section.elements.add(e.getPath())).forEach(this.elementConsumer);
    }

    public PackageableRuntime visitSingleConnectionRuntime(RuntimeParserGrammar.SingleConnectionRuntimeContext ctx)
    {
        PackageableRuntime runtime = new PackageableRuntime();
        runtime.name = PureGrammarParserUtility.fromIdentifier(ctx.qualifiedName().identifier());
        runtime._package = ctx.qualifiedName().packagePath() == null ? "" : PureGrammarParserUtility.fromPath(ctx.qualifiedName().packagePath().identifier());
        runtime.sourceInformation = walkerSourceInformation.getSourceInformation(ctx);
        runtime.runtimeValue = new SingleConnectionEngineRuntime();
        runtime.runtimeValue.sourceInformation = runtime.sourceInformation;
        // mappings
        RuntimeParserGrammar.MappingsContext mappingsContext = PureGrammarParserUtility.validateAndExtractRequiredField(ctx.mappings(), "mappings", runtime.sourceInformation);
        runtime.runtimeValue.mappings = visitMappings(mappingsContext);
        RuntimeParserGrammar.SingleConnectionContext singleConnectionContext = PureGrammarParserUtility.validateAndExtractRequiredField(ctx.singleConnection(), "connection", runtime.sourceInformation);
        runtime.runtimeValue.connectionStores.add(visitSingleConnectionRuntimeSingleConnection(singleConnectionContext));
        return runtime;
    }

    public ConnectionStores visitSingleConnectionRuntimeSingleConnection(RuntimeParserGrammar.SingleConnectionContext singleConnectionContext)
    {
        ConnectionStores connectionStores = new ConnectionStores();
        connectionStores.sourceInformation = walkerSourceInformation.getSourceInformation(singleConnectionContext.packageableElementPointer());
        connectionStores.connectionPointer = new ConnectionPointer();
        connectionStores.connectionPointer.connection = PureGrammarParserUtility.fromQualifiedName(singleConnectionContext.packageableElementPointer().qualifiedName().packagePath() == null ? Collections.emptyList() : singleConnectionContext.packageableElementPointer().qualifiedName().packagePath().identifier(), singleConnectionContext.packageableElementPointer().qualifiedName().identifier());
        connectionStores.connectionPointer.sourceInformation = walkerSourceInformation.getSourceInformation(singleConnectionContext.packageableElementPointer());
        return connectionStores;
    }

    public PackageableRuntime visitRuntime(RuntimeParserGrammar.RuntimeContext ctx)
    {
        PackageableRuntime runtime = new PackageableRuntime();
        runtime.name = PureGrammarParserUtility.fromIdentifier(ctx.qualifiedName().identifier());
        runtime._package = ctx.qualifiedName().packagePath() == null ? "" : PureGrammarParserUtility.fromPath(ctx.qualifiedName().packagePath().identifier());
        runtime.sourceInformation = walkerSourceInformation.getSourceInformation(ctx);
        runtime.runtimeValue = new EngineRuntime();
        runtime.runtimeValue.sourceInformation = runtime.sourceInformation;
        RuntimeParserGrammar.MappingsContext mappingsContext = PureGrammarParserUtility.validateAndExtractRequiredField(ctx.mappings(), "mappings", runtime.sourceInformation);
        runtime.runtimeValue.mappings = visitMappings(mappingsContext);

        // connections (optional)
        RuntimeParserGrammar.ConnectionsContext connectionsContext = PureGrammarParserUtility.validateAndExtractOptionalField(ctx.connections(), "connections", runtime.sourceInformation);
        this.addConnectionsByStore(connectionsContext, runtime.runtimeValue);

        // connectionPointerStores (optional)
        RuntimeParserGrammar.ConnectionStoresListContext connectionStoresListContext = PureGrammarParserUtility.validateAndExtractOptionalField(ctx.connectionStoresList(), "connectionPointerStores", runtime.sourceInformation);
        this.addStoresByConnection(connectionStoresListContext, runtime.runtimeValue);
        return runtime;
    }

    private MutableList<PackageableElementPointer> visitMappings(RuntimeParserGrammar.MappingsContext mappingsContext)
    {
        return ListIterate.collect(mappingsContext.qualifiedName(), pathCtx ->
        {
            PackageableElementPointer pointer = new PackageableElementPointer();
            pointer.type = PackageableElementType.MAPPING;
            pointer.path = PureGrammarParserUtility.fromQualifiedName(pathCtx.packagePath() == null ? Collections.emptyList() : pathCtx.packagePath().identifier(), pathCtx.identifier());
            pointer.sourceInformation = walkerSourceInformation.getSourceInformation(pathCtx);
            return pointer;
        });
    }

    private void addStoresByConnection(RuntimeParserGrammar.ConnectionStoresListContext connectionStoresListContext, EngineRuntime runtimeValue)
    {
        if (connectionStoresListContext == null)
        {
            return;
        }
        ListIterate.forEach(connectionStoresListContext.connectionStores(), connectionStoresContext ->
        {
            ConnectionStores connectionStores = new ConnectionStores();

            connectionStores.connectionPointer = new ConnectionPointer();
            connectionStores.connectionPointer.connection = PureGrammarParserUtility.fromQualifiedName(connectionStoresContext.connection().packageableElementPointer().qualifiedName().packagePath() == null ? Collections.emptyList() : connectionStoresContext.connection().packageableElementPointer().qualifiedName().packagePath().identifier(), connectionStoresContext.connection().packageableElementPointer().qualifiedName().identifier());
            connectionStores.connectionPointer.sourceInformation = walkerSourceInformation.getSourceInformation(connectionStoresContext.connection().packageableElementPointer());

            connectionStores.sourceInformation = walkerSourceInformation.getSourceInformation(connectionStoresContext);
            connectionStores.storePointers =
                    connectionStoresContext.storeProviderPointer() == null
                            ? Collections.emptyList()
                            : ListIterate.collect(connectionStoresContext.storeProviderPointer(), ctx -> StoreProviderPointerFactory.create(ctx, walkerSourceInformation.getSourceInformation(ctx)));
            runtimeValue.connectionStores.add(connectionStores);
        });
    }

    private void addConnectionsByStore(RuntimeParserGrammar.ConnectionsContext connectionsContext, EngineRuntime engineRuntime)
    {
        if (connectionsContext != null)
        {
            ListIterate.forEach(connectionsContext.storeConnections(), storeConnectionsContext ->
            {
                String store = storeConnectionsContext.qualifiedName().getText().equals("ModelStore") ? "ModelStore" : PureGrammarParserUtility.fromQualifiedName(storeConnectionsContext.qualifiedName().packagePath() == null ? Collections.emptyList() : storeConnectionsContext.qualifiedName().packagePath().identifier(), storeConnectionsContext.qualifiedName().identifier());
                StoreConnections storeConnections = new StoreConnections();
                storeConnections.sourceInformation = walkerSourceInformation.getSourceInformation(storeConnectionsContext);
                PackageableElementPointer storePointer = new PackageableElementPointer();
                storePointer.type = PackageableElementType.STORE;
                storePointer.path = store;
                storePointer.sourceInformation = walkerSourceInformation.getSourceInformation(storeConnectionsContext.qualifiedName());
                storeConnections.store = storePointer;
                if (engineRuntime.getStoreConnections(store) != null)
                {
                    throw new EngineException("Connections for store '" + store + "' is already specified", storeConnections.sourceInformation, EngineErrorType.PARSER);
                }

                // walk each connection
                ListIterate.forEach(storeConnectionsContext.identifiedConnection(), identifiedConnectionContext ->
                {
                    IdentifiedConnection identifiedConnection = new IdentifiedConnection();
                    identifiedConnection.sourceInformation = walkerSourceInformation.getSourceInformation(identifiedConnectionContext);
                    identifiedConnection.id = PureGrammarParserUtility.fromIdentifier(identifiedConnectionContext.identifier());
                    if (identifiedConnectionContext.packageableElementPointer() != null)
                    {
                        RuntimeParserGrammar.PackageableElementPointerContext connectionPointerContext = identifiedConnectionContext.packageableElementPointer();
                        ConnectionPointer connectionPointer = new ConnectionPointer();
                        connectionPointer.connection = PureGrammarParserUtility.fromQualifiedName(connectionPointerContext.qualifiedName().packagePath() == null ? Collections.emptyList() : connectionPointerContext.qualifiedName().packagePath().identifier(), connectionPointerContext.qualifiedName().identifier());
                        connectionPointer.sourceInformation = walkerSourceInformation.getSourceInformation(connectionPointerContext.qualifiedName());
                        identifiedConnection.connection = connectionPointer;
                    }
                    else if (identifiedConnectionContext.embeddedConnection() != null)
                    {
                        RuntimeParserGrammar.EmbeddedConnectionContext embeddedConnectionContext = identifiedConnectionContext.embeddedConnection();
                        StringBuilder embeddedConnectionText = new StringBuilder();
                        for (RuntimeParserGrammar.EmbeddedConnectionContentContext fragment : embeddedConnectionContext.embeddedConnectionContent())
                        {
                            embeddedConnectionText.append(fragment.getText());
                        }
                        String embeddedConnectionParsingText = embeddedConnectionText.length() > 0 ? embeddedConnectionText.substring(0, embeddedConnectionText.length() - 2) : embeddedConnectionText.toString();
                        // prepare island grammar walker source information
                        int startLine = embeddedConnectionContext.ISLAND_OPEN().getSymbol().getLine();
                        int lineOffset = walkerSourceInformation.getLineOffset() + startLine - 1;
                        // only add current walker source information column offset if this is the first line
                        int columnOffset = (startLine == 1 ? walkerSourceInformation.getColumnOffset() : 0) + embeddedConnectionContext.ISLAND_OPEN().getSymbol().getCharPositionInLine() + embeddedConnectionContext.ISLAND_OPEN().getSymbol().getText().length();
                        ParseTreeWalkerSourceInformation embeddedConnectionWalkerSourceInformation = new ParseTreeWalkerSourceInformation.Builder(walkerSourceInformation.getSourceId(), lineOffset, columnOffset).withReturnSourceInfo(this.walkerSourceInformation.getReturnSourceInfo()).build();
                        SourceInformation embeddedConnectionSourceInformation = walkerSourceInformation.getSourceInformation(embeddedConnectionContext);
                        identifiedConnection.connection = this.connectionParser.parseEmbeddedRuntimeConnections(embeddedConnectionParsingText, embeddedConnectionWalkerSourceInformation, embeddedConnectionSourceInformation);
                    }
                    else
                    {
                        throw new UnsupportedOperationException();
                    }

                    if (engineRuntime.getStoreConnections(store) == null)
                    {
                        // NOTE: only add this store runtime connections if it has child connections
                        engineRuntime.connections.add(storeConnections);
                    }
                    engineRuntime.getStoreConnections(store).storeConnections.add(identifiedConnection);
                });
            });
        }
    }

    public EngineRuntime visitEmbeddedRuntime(RuntimeParserGrammar.EmbeddedRuntimeContext ctx, SourceInformation sourceInformation)
    {
        EngineRuntime engineRuntime = new EngineRuntime();
        if (ctx == null || ctx.children == null)
        {
            throw new EngineException("Embedded runtime must not be empty", sourceInformation, EngineErrorType.PARSER);
        }
        engineRuntime.sourceInformation = walkerSourceInformation.getSourceInformation(ctx);
        // mappings
        RuntimeParserGrammar.MappingsContext mappingsContext = PureGrammarParserUtility.validateAndExtractOptionalField(ctx.mappings(), "mappings", engineRuntime.sourceInformation);
        if (mappingsContext != null)
        {
            engineRuntime.mappings = ListIterate.collect(mappingsContext.qualifiedName(), pathCtx ->
            {
                PackageableElementPointer pointer = new PackageableElementPointer();
                pointer.type = PackageableElementType.MAPPING;
                pointer.path = PureGrammarParserUtility.fromQualifiedName(pathCtx.packagePath() == null ? Collections.emptyList() : pathCtx.packagePath().identifier(), pathCtx.identifier());
                pointer.sourceInformation = walkerSourceInformation.getSourceInformation(pathCtx);
                return pointer;
            });
        }
        else
        {
            engineRuntime.mappings = new ArrayList<>();
        }
        // connections (optional)
        RuntimeParserGrammar.ConnectionsContext connectionsContext = PureGrammarParserUtility.validateAndExtractOptionalField(ctx.connections(), "connections", engineRuntime.sourceInformation);
        this.addConnectionsByStore(connectionsContext, engineRuntime);
        return engineRuntime;
    }
}
