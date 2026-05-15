package digital.heirlooms.server.routes.plot

import digital.heirlooms.server.service.plot.TrellisService
import org.http4k.contract.ContractRoute

// Backward-compat alias — use trellisRoutes for all new code
fun flowRoutes(trellisService: TrellisService): List<ContractRoute> = trellisRoutes(trellisService)
