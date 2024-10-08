// Copyright 2022 Goldman Sachs
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

public interface IngestModeVisitor<T>
{
    T visitAppendOnly(AppendOnlyAbstract appendOnly);

    T visitNontemporalSnapshot(NontemporalSnapshotAbstract nontemporalSnapshot);

    T visitNontemporalDelta(NontemporalDeltaAbstract nontemporalDelta);

    T visitUnitemporalSnapshot(UnitemporalSnapshotAbstract unitemporalSnapshot);

    T visitUnitemporalDelta(UnitemporalDeltaAbstract unitemporalDelta);

    T visitBitemporalSnapshot(BitemporalSnapshotAbstract bitemporalSnapshot);

    T visitBitemporalDelta(BitemporalDeltaAbstract bitemporalDelta);

    T visitBulkLoad(BulkLoadAbstract bulkLoad);

    T visitNoOp(NoOpAbstract noOpAbstract);
}
