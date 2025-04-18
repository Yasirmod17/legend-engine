// Copyright 2025 Goldman Sachs
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
//

package org.finos.legend.engine.plan.execution.stores.deephaven.plugin;

import org.finos.legend.engine.plan.execution.stores.StoreExecutor;

public class DeephavenStoreExecutor implements StoreExecutor
{
    private final DeephavenStoreState state;

    private final DeephavenStoreExecutorConfiguration deephavenStoreExecutorConfiguration;

    public DeephavenStoreExecutor(DeephavenStoreState state, DeephavenStoreExecutorConfiguration deephavenStoreExecutorConfiguration)
    {
        this.state = state;
        this.deephavenStoreExecutorConfiguration = deephavenStoreExecutorConfiguration;
    }

    @Override
    public DeephavenStoreExecutionState buildStoreExecutionState()
    {
        return new DeephavenStoreExecutionState(this.state, this.deephavenStoreExecutorConfiguration);
    }

    @Override
    public DeephavenStoreState getStoreState()
    {
        return this.state;
    }
}
