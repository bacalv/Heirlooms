package digital.heirlooms.server

import digital.heirlooms.server.domain.plot.PlotRecord
import digital.heirlooms.server.domain.plot.TrellisRecord
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.plot.TrellisRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import digital.heirlooms.server.service.plot.TrellisService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TrellisServiceTest {

    private val plotRepo = mockk<PlotRepository>(relaxed = true)
    private val trellisRepo = mockk<TrellisRepository>(relaxed = true)
    private val itemRepo = mockk<PlotItemRepository>(relaxed = true)
    private val uploadRepo = mockk<UploadRepository>(relaxed = true)
    private val mapper = ObjectMapper()

    private val service = TrellisService(trellisRepo, plotRepo, itemRepo, uploadRepo)

    private val ownerId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val strangerId = UUID.randomUUID()
    private val sharedPlotId = UUID.randomUUID()
    private val privatePlotId = UUID.randomUUID()

    private fun sharedCollectionPlot(id: UUID = sharedPlotId, ownerUserId: UUID = ownerId) = PlotRecord(
        id = id,
        ownerUserId = ownerUserId,
        name = "Test share",
        sortOrder = 0,
        isSystemDefined = false,
        createdAt = Instant.parse("2026-05-01T10:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T10:00:00Z"),
        criteria = null,   // collection plot (no criteria)
        showInGarden = true,
        visibility = "shared",
    )

    private fun privateCollectionPlot(id: UUID = privatePlotId, ownerUserId: UUID = ownerId) = PlotRecord(
        id = id,
        ownerUserId = ownerUserId,
        name = "My private plot",
        sortOrder = 0,
        isSystemDefined = false,
        createdAt = Instant.parse("2026-05-01T10:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T10:00:00Z"),
        criteria = null,
        showInGarden = true,
        visibility = "private",
    )

    private fun trellis(userId: UUID, targetPlotId: UUID) = TrellisRecord(
        id = UUID.randomUUID(),
        userId = userId,
        name = "My trellis",
        criteria = """{"type":"tag","tag":"family"}""",
        targetPlotId = targetPlotId,
        requiresStaging = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private val simpleCriteriaNode = mapper.readTree("""{"type":"tag","tag":"family"}""")

    // Criteria validation: make withCriteriaValidation a no-op (valid criteria)
    private fun allowCriteria() {
        every { plotRepo.withCriteriaValidation(any(), any()) } returns Unit
    }

    // ---- Owner can always create a trellis targeting their own plot -----------

    @Test
    fun `owner can create trellis targeting their own shared plot`() {
        allowCriteria()
        every { plotRepo.getPlotById(sharedPlotId) } returns sharedCollectionPlot(ownerUserId = ownerId)
        every { trellisRepo.createTrellis(any(), any(), any(), any(), any(), any()) } returns
            TrellisRepository.TrellisCreateResult.Success(trellis(ownerId, sharedPlotId))

        val result = service.createTrellis("My trellis", simpleCriteriaNode, sharedPlotId, true, ownerId)

        assertInstanceOf(TrellisService.CreateTrellisResult.Created::class.java, result)
    }

    @Test
    fun `owner can create trellis targeting their own private plot`() {
        allowCriteria()
        every { plotRepo.getPlotById(privatePlotId) } returns privateCollectionPlot(ownerUserId = ownerId)
        every { trellisRepo.createTrellis(any(), any(), any(), any(), any(), any()) } returns
            TrellisRepository.TrellisCreateResult.Success(trellis(ownerId, privatePlotId))

        val result = service.createTrellis("My trellis", simpleCriteriaNode, privatePlotId, false, ownerId)

        assertInstanceOf(TrellisService.CreateTrellisResult.Created::class.java, result)
    }

    // ---- BUG-010: joined member can create trellis targeting a shared plot ----

    @Test
    fun `joined member can create trellis targeting shared plot they belong to`() {
        allowCriteria()
        every { plotRepo.getPlotById(sharedPlotId) } returns sharedCollectionPlot(ownerUserId = ownerId)
        every { plotRepo.isMember(sharedPlotId, memberId) } returns true
        every { trellisRepo.createTrellis(any(), any(), any(), any(), any(), any()) } returns
            TrellisRepository.TrellisCreateResult.Success(trellis(memberId, sharedPlotId))

        val result = service.createTrellis("My trellis", simpleCriteriaNode, sharedPlotId, true, memberId)

        assertInstanceOf(TrellisService.CreateTrellisResult.Created::class.java, result,
            "A joined plot member must be allowed to create a trellis targeting the shared plot")
    }

    // ---- Non-member / stranger is still rejected ----------------------------

    @Test
    fun `non-member is rejected when creating trellis targeting shared plot`() {
        allowCriteria()
        every { plotRepo.getPlotById(sharedPlotId) } returns sharedCollectionPlot(ownerUserId = ownerId)
        every { plotRepo.isMember(sharedPlotId, strangerId) } returns false

        val result = service.createTrellis("My trellis", simpleCriteriaNode, sharedPlotId, true, strangerId)

        assertInstanceOf(TrellisService.CreateTrellisResult.Invalid::class.java, result,
            "A user who is not a member of the shared plot must be rejected")
    }

    @Test
    fun `non-owner cannot create trellis targeting a private plot`() {
        allowCriteria()
        every { plotRepo.getPlotById(privatePlotId) } returns privateCollectionPlot(ownerUserId = ownerId)
        // isMember not called for private plots, but ensure it returns false if ever called
        every { plotRepo.isMember(privatePlotId, memberId) } returns false

        val result = service.createTrellis("My trellis", simpleCriteriaNode, privatePlotId, false, memberId)

        assertInstanceOf(TrellisService.CreateTrellisResult.Invalid::class.java, result,
            "Private plots must remain restricted to the owner only")
    }
}
