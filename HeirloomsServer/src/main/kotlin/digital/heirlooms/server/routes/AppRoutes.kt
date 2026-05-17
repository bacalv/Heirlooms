package digital.heirlooms.server.routes

import digital.heirlooms.server.Database
import digital.heirlooms.server.filters.authUserId
import digital.heirlooms.server.service.upload.MediaMetadata
import digital.heirlooms.server.service.upload.MetadataExtractor
import digital.heirlooms.server.service.upload.generateThumbnail
import digital.heirlooms.server.storage.DirectUploadSupport
import digital.heirlooms.server.storage.FileStore
import digital.heirlooms.server.repository.auth.AuthRepository
import digital.heirlooms.server.repository.auth.PostgresAuthRepository
import digital.heirlooms.server.repository.capsule.CapsuleRepository
import digital.heirlooms.server.repository.capsule.PostgresCapsuleRepository
import digital.heirlooms.server.repository.diag.DiagRepository
import digital.heirlooms.server.repository.diag.PostgresDiagRepository
import digital.heirlooms.server.repository.keys.KeyRepository
import digital.heirlooms.server.repository.keys.PostgresKeyRepository
import digital.heirlooms.server.repository.plot.TrellisRepository
import digital.heirlooms.server.repository.plot.PlotItemRepository
import digital.heirlooms.server.repository.plot.PlotMemberRepository
import digital.heirlooms.server.repository.plot.PlotRepository
import digital.heirlooms.server.repository.plot.PostgresTrellisRepository
import digital.heirlooms.server.repository.plot.PostgresPlotItemRepository
import digital.heirlooms.server.repository.plot.PostgresPlotMemberRepository
import digital.heirlooms.server.repository.plot.PostgresPlotRepository
// Backward-compat imports for test code and external references
import digital.heirlooms.server.repository.plot.FlowRepository
import digital.heirlooms.server.repository.plot.PostgresFlowRepository
import digital.heirlooms.server.repository.social.SocialRepository
import digital.heirlooms.server.repository.social.PostgresSocialRepository
import digital.heirlooms.server.repository.storage.BlobRepository
import digital.heirlooms.server.repository.storage.PostgresBlobRepository
import digital.heirlooms.server.repository.upload.UploadRepository
import digital.heirlooms.server.repository.upload.PostgresUploadRepository
import digital.heirlooms.server.filters.AuthRateLimiter
import digital.heirlooms.server.filters.rateLimitFilter
import digital.heirlooms.server.routes.auth.authRoutes
import digital.heirlooms.server.routes.capsule.capsuleReverseLookupRoute
import digital.heirlooms.server.routes.capsule.capsuleRoutes
import digital.heirlooms.server.routes.capsule.executorShareRoutes
import digital.heirlooms.server.routes.keys.keysRoutes
import digital.heirlooms.server.routes.plot.trellisRoutes
import digital.heirlooms.server.routes.plot.plotItemRoutes
import digital.heirlooms.server.routes.plot.plotRoutes
import digital.heirlooms.server.routes.plot.sharedPlotRoutes
// Backward-compat import
import digital.heirlooms.server.routes.plot.flowRoutes
import digital.heirlooms.server.routes.social.friendsRoutes
import digital.heirlooms.server.routes.social.sharingKeyRoutes
import digital.heirlooms.server.routes.connection.connectionRoutes
import digital.heirlooms.server.routes.connection.nominationRoutes
import digital.heirlooms.server.routes.capsule.recipientLinkRoute
import digital.heirlooms.server.repository.connection.ConnectionRepository
import digital.heirlooms.server.repository.connection.PostgresConnectionRepository
import digital.heirlooms.server.repository.connection.NominationRepository
import digital.heirlooms.server.repository.connection.PostgresNominationRepository
import digital.heirlooms.server.repository.capsule.RecipientLinkRepository
import digital.heirlooms.server.repository.capsule.PostgresRecipientLinkRepository
import digital.heirlooms.server.repository.capsule.ExecutorShareRepository
import digital.heirlooms.server.repository.capsule.PostgresExecutorShareRepository
import digital.heirlooms.server.repository.capsule.SealRepository
import digital.heirlooms.server.repository.capsule.PostgresSealRepository
import digital.heirlooms.server.repository.capsule.TlockKeyRepository
import digital.heirlooms.server.repository.capsule.PostgresTlockKeyRepository
import digital.heirlooms.server.repository.capsule.CapsuleRecipientKeyRepository
import digital.heirlooms.server.repository.capsule.PostgresCapsuleRecipientKeyRepository
import digital.heirlooms.server.crypto.tlock.TimeLockProvider
import digital.heirlooms.server.crypto.tlock.DisabledTimeLockProvider
import digital.heirlooms.server.service.capsule.SealCapsuleService
import digital.heirlooms.server.service.capsule.TlockKeyService
import digital.heirlooms.server.routes.capsule.sealCapsuleRoutes
import digital.heirlooms.server.routes.capsule.tlockKeyRoute
import digital.heirlooms.server.routes.capsule.capsuleRecipientKeysRoute
import digital.heirlooms.server.service.connection.ConnectionService
import digital.heirlooms.server.service.connection.NominationService
import digital.heirlooms.server.service.capsule.ExecutorShareService
import digital.heirlooms.server.routes.upload.checkContentHashContractRoute
import digital.heirlooms.server.routes.upload.compostUploadContractRoute
import digital.heirlooms.server.routes.upload.confirmUploadContractRoute
import digital.heirlooms.server.routes.upload.fileProxyContractRoute
import digital.heirlooms.server.routes.upload.getUploadByIdContractRoute
import digital.heirlooms.server.routes.upload.initiateUploadContractRoute
import digital.heirlooms.server.routes.upload.listCompostedUploadsContractRoute
import digital.heirlooms.server.routes.upload.listTagsContractRoute
import digital.heirlooms.server.routes.upload.listUploadsContractRoute
import digital.heirlooms.server.routes.upload.migrateUploadContractRoute
import digital.heirlooms.server.routes.upload.prepareUploadContractRoute
import digital.heirlooms.server.routes.upload.previewProxyContractRoute
import digital.heirlooms.server.routes.upload.readUrlContractRoute
import digital.heirlooms.server.routes.upload.restoreUploadContractRoute
import digital.heirlooms.server.routes.upload.resumableUploadContractRoute
import digital.heirlooms.server.routes.upload.rotationContractRoute
import digital.heirlooms.server.routes.upload.shareUploadContractRoute
import digital.heirlooms.server.routes.upload.tagsContractRoute
import digital.heirlooms.server.routes.upload.thumbProxyContractRoute
import digital.heirlooms.server.routes.upload.uploadContractRoute
import digital.heirlooms.server.routes.upload.viewUploadContractRoute
import digital.heirlooms.server.service.upload.UploadService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.contract.contract
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.then
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static

private const val SWAGGER_UI_VERSION = "5.11.8"

private val swaggerInitializerJs = """
window.onload = function() {
  window.ui = SwaggerUIBundle({
    url: "/docs/api.json",
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
    layout: "StandaloneLayout",
    persistAuthorization: true,
    tryItOutEnabled: true
  });
};
""".trimIndent()

fun buildApp(
    storage: FileStore,
    database: Database,
    thumbnailGenerator: (ByteArray, String) -> ByteArray? = ::generateThumbnail,
    metadataExtractor: (ByteArray, String) -> MediaMetadata = MetadataExtractor()::extract,
    previewDurationSeconds: Int = 15,
    authSecret: ByteArray = ByteArray(32),
    timeLockProvider: TimeLockProvider = DisabledTimeLockProvider,
): HttpHandler = buildApp(
    storage = storage,
    uploadRepo = PostgresUploadRepository(database.dataSource),
    authRepo = PostgresAuthRepository(database.dataSource),
    capsuleRepo = PostgresCapsuleRepository(database.dataSource),
    plotRepo = PostgresPlotRepository(database.dataSource),
    flowRepo = PostgresTrellisRepository(database.dataSource),
    itemRepo = PostgresPlotItemRepository(database.dataSource),
    memberRepo = PostgresPlotMemberRepository(database.dataSource),
    keyRepo = PostgresKeyRepository(database.dataSource),
    socialRepo = PostgresSocialRepository(database.dataSource),
    blobRepo = PostgresBlobRepository(database.dataSource),
    diagRepo = PostgresDiagRepository(database.dataSource),
    connectionRepo = PostgresConnectionRepository(database.dataSource),
    nominationRepo = PostgresNominationRepository(database.dataSource),
    recipientLinkRepo = PostgresRecipientLinkRepository(database.dataSource),
    executorShareRepo = PostgresExecutorShareRepository(database.dataSource),
    sealRepo = PostgresSealRepository(database.dataSource),
    tlockKeyRepo = PostgresTlockKeyRepository(database.dataSource),
    capsuleRecipientKeyRepo = PostgresCapsuleRecipientKeyRepository(database.dataSource),
    thumbnailGenerator = thumbnailGenerator,
    metadataExtractor = metadataExtractor,
    previewDurationSeconds = previewDurationSeconds,
    authSecret = authSecret,
    timeLockProvider = timeLockProvider,
    dataSource = database.dataSource,
)

internal fun buildApp(
    storage: FileStore,
    uploadRepo: UploadRepository,
    authRepo: AuthRepository,
    capsuleRepo: CapsuleRepository,
    plotRepo: PlotRepository,
    flowRepo: TrellisRepository,
    itemRepo: PlotItemRepository,
    memberRepo: PlotMemberRepository,
    keyRepo: KeyRepository,
    socialRepo: SocialRepository,
    blobRepo: BlobRepository,
    diagRepo: DiagRepository,
    connectionRepo: ConnectionRepository = object : ConnectionRepository {
        override fun listConnections(ownerUserId: java.util.UUID) = emptyList<digital.heirlooms.server.domain.connection.ConnectionRecord>()
        override fun getConnection(id: java.util.UUID, ownerUserId: java.util.UUID) = null
        override fun createConnection(ownerUserId: java.util.UUID, contactUserId: java.util.UUID?, displayName: String, email: String?, sharingPubkey: String?, roles: List<String>) = throw UnsupportedOperationException("no-op stub")
        override fun updateConnection(id: java.util.UUID, ownerUserId: java.util.UUID, displayName: String?, roles: List<String>?, sharingPubkey: String?, clearSharingPubkey: Boolean) = null
        override fun deleteConnection(id: java.util.UUID, ownerUserId: java.util.UUID) = ConnectionRepository.DeleteResult.NotFound
        override fun lookupSharingPubkey(contactUserId: java.util.UUID) = null
    },
    nominationRepo: NominationRepository = object : NominationRepository {
        override fun createNomination(ownerUserId: java.util.UUID, connectionId: java.util.UUID, message: String?) = throw UnsupportedOperationException("no-op stub")
        override fun listByOwner(ownerUserId: java.util.UUID) = emptyList<digital.heirlooms.server.domain.connection.NominationRecord>()
        override fun listReceived(nomineeUserId: java.util.UUID) = emptyList<digital.heirlooms.server.domain.connection.NominationRecord>()
        override fun getById(id: java.util.UUID) = null
        override fun getContactUserId(connectionId: java.util.UUID) = null
        override fun hasActiveNomination(connectionId: java.util.UUID) = false
        override fun setRespondedStatus(id: java.util.UUID, status: String) = null
        override fun setRevoked(id: java.util.UUID) = null
        override fun getConnectionOwnerUserId(connectionId: java.util.UUID) = null
    },
    recipientLinkRepo: RecipientLinkRepository = object : RecipientLinkRepository {
        override fun linkRecipient(capsuleId: java.util.UUID, recipientId: java.util.UUID, connectionId: java.util.UUID, callerUserId: java.util.UUID) = RecipientLinkRepository.LinkResult.CapsuleNotFound
    },
    executorShareRepo: ExecutorShareRepository = object : ExecutorShareRepository {
        override fun getCapsuleShareConfig(capsuleId: java.util.UUID, ownerUserId: java.util.UUID) = null
        override fun isAcceptedNominationForOwner(nominationId: java.util.UUID, ownerUserId: java.util.UUID) = false
        override fun insertSharesBatch(capsuleId: java.util.UUID, shares: List<ExecutorShareRepository.ShareRow>) {}
        override fun findShareForExecutor(capsuleId: java.util.UUID, callerUserId: java.util.UUID) = ExecutorShareRepository.MineQueryResult.NotAnExecutor
        override fun findAllShares(capsuleId: java.util.UUID) = emptyList<digital.heirlooms.server.domain.capsule.ExecutorShareRecord>()
        override fun getCapsuleShamirConfig(capsuleId: java.util.UUID) = null
    },
    sealRepo: SealRepository = object : SealRepository {
        override fun loadCapsuleForSeal(capsuleId: java.util.UUID, ownerUserId: java.util.UUID) = null
        override fun isConnectionBoundAndOwned(connectionId: java.util.UUID, ownerUserId: java.util.UUID) = false
        override fun countAcceptedNominations(ownerUserId: java.util.UUID) = 0
        override fun writeSealAtomically(capsuleId: java.util.UUID, ownerUserId: java.util.UUID, params: SealRepository.SealWriteParams) = java.time.Instant.now()
    },
    tlockKeyRepo: TlockKeyRepository = object : TlockKeyRepository {
        override fun isRecipient(capsuleId: java.util.UUID, callerUserId: java.util.UUID) = false
        override fun loadTlockFields(capsuleId: java.util.UUID) = null
        override fun getCapsuleOwnerId(capsuleId: java.util.UUID) = null
    },
    capsuleRecipientKeyRepo: CapsuleRecipientKeyRepository = object : CapsuleRecipientKeyRepository {
        override fun isCapsuleOwner(capsuleId: java.util.UUID, callerUserId: java.util.UUID) = false
        override fun isAuthenticatedRecipient(capsuleId: java.util.UUID, callerUserId: java.util.UUID) = false
        override fun capsuleExists(capsuleId: java.util.UUID) = false
        override fun findAllRows(capsuleId: java.util.UUID) = emptyList<CapsuleRecipientKeyRepository.RecipientKeyRow>()
        override fun findOwnRow(capsuleId: java.util.UUID, callerUserId: java.util.UUID) = null
    },
    thumbnailGenerator: (ByteArray, String) -> ByteArray? = ::generateThumbnail,
    metadataExtractor: (ByteArray, String) -> MediaMetadata = MetadataExtractor()::extract,
    previewDurationSeconds: Int = 15,
    authSecret: ByteArray = ByteArray(32),
    timeLockProvider: TimeLockProvider = DisabledTimeLockProvider,
    dataSource: javax.sql.DataSource? = null,
): HttpHandler {
    val directUpload = storage as? DirectUploadSupport

    // Construct service instances
    val uploadService = UploadService(uploadRepo, blobRepo, socialRepo, plotRepo, flowRepo, storage, thumbnailGenerator, metadataExtractor)
    val authService = digital.heirlooms.server.service.auth.AuthService(authRepo, keyRepo, socialRepo, plotRepo, authSecret, dataSource)
    val capsuleService = digital.heirlooms.server.service.capsule.CapsuleService(capsuleRepo)
    val plotService = digital.heirlooms.server.service.plot.PlotService(plotRepo)
    val flowService = digital.heirlooms.server.service.plot.TrellisService(flowRepo, plotRepo, itemRepo, uploadRepo)
    val sharedPlotService = digital.heirlooms.server.service.plot.SharedPlotService(plotRepo, memberRepo)
    val keyService = digital.heirlooms.server.service.keys.KeyService(keyRepo)
    val socialService = digital.heirlooms.server.service.social.SocialService(socialRepo)
    val connectionService = ConnectionService(connectionRepo)
    val nominationService = NominationService(nominationRepo)
    val executorShareService = ExecutorShareService(executorShareRepo)
    val sealCapsuleService = SealCapsuleService(sealRepo, timeLockProvider)
    val tlockKeyService = TlockKeyService(tlockKeyRepo, timeLockProvider)

    val contentContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(
            uploadContractRoute(uploadService),
            listUploadsContractRoute(uploadService),
            listTagsContractRoute(uploadService),
            checkContentHashContractRoute(uploadService),
            listCompostedUploadsContractRoute(uploadService),
            getUploadByIdContractRoute(uploadService),
            prepareUploadContractRoute(uploadService),
            initiateUploadContractRoute(uploadService),
            resumableUploadContractRoute(uploadService),
            confirmUploadContractRoute(uploadService),
            migrateUploadContractRoute(uploadService),
            fileProxyContractRoute(storage, uploadService),
            thumbProxyContractRoute(storage, uploadService),
            previewProxyContractRoute(storage, uploadService),
            readUrlContractRoute(directUpload, uploadService),
            rotationContractRoute(uploadService),
            tagsContractRoute(uploadService),
            viewUploadContractRoute(uploadService),
            capsuleReverseLookupRoute(capsuleService),
            compostUploadContractRoute(uploadService),
            restoreUploadContractRoute(uploadService),
            shareUploadContractRoute(uploadService),
        )
    }

    val capsuleContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += capsuleRoutes(capsuleService) + plotRoutes(plotService) + trellisRoutes(flowService) + plotItemRoutes(flowService) + sharedPlotRoutes(sharedPlotService) + executorShareRoutes(executorShareService) + sealCapsuleRoutes(sealCapsuleService) + listOf(tlockKeyRoute(tlockKeyService), capsuleRecipientKeysRoute(capsuleRecipientKeyRepo))
    }

    val keysContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += keysRoutes(keyService) + sharingKeyRoutes(socialService)
    }

    val socialContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += friendsRoutes(socialService)
    }

    val connectionContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += connectionRoutes(connectionService) + nominationRoutes(nominationService)
    }

    val nominationCapsuleContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(recipientLinkRoute(recipientLinkRepo))
    }

    val authContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += authRoutes(authService)
    }

    val diagContract = contract {
        renderer = OpenApi3(ApiInfo("Heirlooms API", "v1"), Jackson)
        descriptionPath = "/openapi.json"
        routes += listOf(
            "/diagnostics/events" meta { summary = "Post a diagnostic event" } bindContract POST to { req ->
                try {
                    val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(req.bodyString())
                    diagRepo.insertDiagEvent(
                        deviceLabel = node?.get("deviceLabel")?.asText() ?: "",
                        tag         = node?.get("tag")?.asText()         ?: "unknown",
                        message     = node?.get("message")?.asText()     ?: "",
                        detail      = node?.get("detail")?.asText()      ?: "",
                        userId      = req.authUserId(),
                    )
                    Response(CREATED)
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body(e.message ?: "error")
                }
            },
            "/diagnostics/events" meta { summary = "List diagnostic events" } bindContract GET to { req ->
                try {
                    val events = diagRepo.listDiagEvents(userId = req.authUserId())
                    val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(events)
                    Response(OK).header("Content-Type", "application/json").body(json)
                } catch (e: Exception) {
                    Response(INTERNAL_SERVER_ERROR).body(e.message ?: "error")
                }
            },
        )
    }

    return routes(
        "/api/content" bind contentContract,
        "/api/keys" bind keysContract,
        "/api/auth" bind rateLimitFilter(AuthRateLimiter.challengeAndLogin, "/challenge", "/login").then(authContract),
        "/api" bind capsuleContract,
        "/api" bind socialContract,
        "/api" bind connectionContract,
        "/api" bind nominationCapsuleContract,
        "/api" bind diagContract,
        "/health" bind GET to { Response(OK).body("ok") },
        "/api/settings" bind GET to { Response(OK).header("Content-Type", "application/json").body("""{"previewDurationSeconds":$previewDurationSeconds}""") },
        "/docs/api.json" bind GET to { mergedSpecWithApiKeyAuth(contentContract, capsuleContract, keysContract) },
        "/docs" bind GET to { Response(FOUND).header("Location", "/docs/index.html") },
        "/docs/swagger-initializer.js" bind GET to {
            Response(OK).header("Content-Type", "application/javascript").body(swaggerInitializerJs)
        },
        "/docs" bind static(ResourceLoader.Classpath("META-INF/resources/webjars/swagger-ui/$SWAGGER_UI_VERSION")),
    )
}

private fun mergedSpecWithApiKeyAuth(
    contentContract: HttpHandler,
    capsuleContract: HttpHandler,
    keysContract: HttpHandler,
): Response {
    val mapper = ObjectMapper()
    val factory = JsonNodeFactory.instance

    val contentSpec = mapper.readTree(
        contentContract(Request(GET, "/openapi.json")).bodyString()
    ) as? ObjectNode ?: return Response(INTERNAL_SERVER_ERROR).body("Failed to generate content spec")

    val capsuleSpec = mapper.readTree(
        capsuleContract(Request(GET, "/openapi.json")).bodyString()
    ) as? ObjectNode ?: return Response(INTERNAL_SERVER_ERROR).body("Failed to generate capsule spec")

    val keysSpec = mapper.readTree(
        keysContract(Request(GET, "/openapi.json")).bodyString()
    ) as? ObjectNode ?: return Response(INTERNAL_SERVER_ERROR).body("Failed to generate keys spec")

    val mergedPaths = factory.objectNode()
    (contentSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (path, item) ->
        mergedPaths.set<ObjectNode>("/api/content$path", item)
    }
    (capsuleSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (path, item) ->
        mergedPaths.set<ObjectNode>("/api$path", item)
    }
    (keysSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (path, item) ->
        mergedPaths.set<ObjectNode>("/api/keys$path", item)
    }
    contentSpec.set<ObjectNode>("paths", mergedPaths)

    val contentComponents = contentSpec.get("components") as? ObjectNode ?: factory.objectNode()
    val contentSchemas = contentComponents.get("schemas") as? ObjectNode ?: factory.objectNode()
    (capsuleSpec.get("components") as? ObjectNode)?.get("schemas")?.fields()?.forEach { (name, schema) ->
        contentSchemas.set<ObjectNode>(name, schema)
    }
    (keysSpec.get("components") as? ObjectNode)?.get("schemas")?.fields()?.forEach { (name, schema) ->
        contentSchemas.set<ObjectNode>(name, schema)
    }
    contentComponents.set<ObjectNode>("schemas", contentSchemas)

    val apiKeyScheme = factory.objectNode().apply {
        put("type", "apiKey")
        put("in", "header")
        put("name", "X-Api-Key")
    }
    contentComponents.set<ObjectNode>("securitySchemes", factory.objectNode().apply {
        set<ObjectNode>("ApiKeyAuth", apiKeyScheme)
    })
    contentSpec.set<ObjectNode>("components", contentComponents)
    contentSpec.set<ArrayNode>("security", factory.arrayNode().add(
        factory.objectNode().apply { set<ArrayNode>("ApiKeyAuth", factory.arrayNode()) }
    ))
    contentSpec.set<ArrayNode>("servers", factory.arrayNode().add(
        factory.objectNode().apply { put("url", "/") }
    ))

    (contentSpec.get("paths") as? ObjectNode)?.fields()?.forEach { (_, pathItem) ->
        (pathItem as? ObjectNode)?.fields()?.forEach { (_, operation) ->
            (operation as? ObjectNode)?.remove("security")
        }
    }

    return Response(OK).header("Content-Type", "application/json").body(contentSpec.toString())
}
