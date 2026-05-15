package digital.heirlooms.server.repository.plot

import digital.heirlooms.server.domain.auth.FOUNDING_USER_ID
import digital.heirlooms.server.domain.plot.TrellisRecord
import digital.heirlooms.server.domain.plot.PlotRecord
import java.util.UUID

// Backward-compat aliases — all production code should use TrellisRepository
typealias FlowRepository = TrellisRepository
typealias PostgresFlowRepository = PostgresTrellisRepository
