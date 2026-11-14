package lvluplife.quality

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.test.*
import kotlinx.coroutines.*
import lvluplife.api.*
import lvluplife.application.AuthoritativeClock
import lvluplife.fixtures.FaultPoint
import lvluplife.fixtures.InjectedFault
import lvluplife.model.*
import lvluplife.persistence.*
import lvluplife.progression.ReplayEngine

/** Local diagnostic harness, not a production benchmark or a claim of p95/availability compliance. */
class PerformanceScenarios {
    private val json = ObjectMapper()
    private data class Options(val profile: String, val volumes: List<Int>, val n: Int, val warmup: Int,
        val workers: Int, val seed: Long, val coldN: Int) {
        companion object {
            fun read(env: Map<String,String> = System.getenv()): Options {
                val profile=env["LVLUP_PERF_PROFILE"] ?: "smoke"
                val volumes=when(profile) { "smoke" -> listOf(12); "all" -> listOf(0,1000,10000)
                    "0","1000","10000" -> listOf(profile.toInt()); else -> error("Invalid LVLUP_PERF_PROFILE") }
                fun number(key: String, default: Int, minimum: Int): Int =
                    (env[key]?.let { requireNotNull(it.toIntOrNull()) { "Invalid $key" } } ?: default)
                        .also { require(it>=minimum) { "Invalid $key" } }
                return Options(profile,volumes,number("LVLUP_PERF_N",3,1),number("LVLUP_PERF_WARMUP",1,0),
                    number("LVLUP_PERF_WORKERS",2,2),env["LVLUP_PERF_SEED"]?.toLong() ?: 81001L,
                    number("LVLUP_PERF_COLD_N",1,1))
            }
        }
    }
    private data class Sample(val phase: String, val operation: String, val millis: Double,
        val attempts: Int, val status: Int, val expectedStatus: Int, val responseBytes: Int)
    private data class Reply(val status: Int, val body: JsonNode, val bytes: Int)
    private fun percentile(values: List<Double>, p: Double): Double? =
        values.sorted().let { if(it.isEmpty()) null else it[(ceil(p*it.size).toInt()-1).coerceAtLeast(0)] }

    @Test fun configurationAndQuantilesHaveExplicitSmallSampleSemantics() {
        assertEquals(listOf(0,1000,10000),Options.read(mapOf("LVLUP_PERF_PROFILE" to "all")).volumes)
        assertEquals(listOf(0),Options.read(mapOf("LVLUP_PERF_PROFILE" to "0")).volumes)
        for(pair in listOf("LVLUP_PERF_PROFILE" to "500", "LVLUP_PERF_N" to "0",
            "LVLUP_PERF_WORKERS" to "1", "LVLUP_PERF_WARMUP" to "-1", "LVLUP_PERF_COLD_N" to "0"))
            assertFails { Options.read(mapOf(pair)) }
        assertNull(percentile(emptyList(),0.95))
        assertEquals(7.0,percentile(listOf(7.0),0.95))
        assertEquals(2.0,percentile(listOf(4.0,1.0,3.0,2.0),0.5))
        assertEquals(4.0,percentile(listOf(4.0,1.0,3.0,2.0),0.95))
    }

    @Test fun selectedProfilesMeasureRealLocalServiceAndVerifyConsistency() = runBlocking {
        val config=ServerConfig.fromEnvironment()
        check(config.mode==BackendMode.EMULATOR && config.projectId=="demo-lvluplife")
        check(config.firestoreEmulatorHost=="127.0.0.1:8080" && config.authEmulatorHost=="127.0.0.1:9099")
        val options=Options.read()
        val output=Path.of("build/reports/performance").toAbsolutePath()
        Files.createDirectories(output)
        for(volume in options.volumes) runProfile(config,options,volume,output)
    }

    private inner class Fixture(val resources: ServerResources, val options: Options, val volume: Int) {
        val samples=java.util.Collections.synchronizedList(mutableListOf<Sample>())
        val traffic=ConcurrentHashMap<String,AtomicLong>()
        val fault=AtomicReference<String?>(null)
        val sequence=AtomicLong()
        val run=UUID.randomUUID().toString()
        val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val identity: JsonNode = kotlin.run {
            val request=HttpRequest.newBuilder(URI("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key"))
                .header("Content-Type","application/json").timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString("""{"returnSecureToken":true}""")).build()
            val response=http.send(request,HttpResponse.BodyHandlers.ofString())
            assertEquals(200,response.statusCode());json.readTree(response.body())
        }
        val uid=Uid(identity.path("localId").textValue())
        val token=identity.path("idToken").textValue()
        @Volatile var now=Instant.parse("2025-11-12T18:00:00Z")
        var baseUrl=""
        var cycle=""
        var created=AtomicLong()
        var coldProbes=0
        var seedMillis=0.0
        var consistencyVerified=false
        var duplicateVerified=false
        val seeded=mutableMapOf<String,Map<String,Any?>>()
        fun id()=UUID.nameUUIDFromBytes("$run:${sequence.incrementAndGet()}".toByteArray()).toString()
        fun documents(collection: String)=resources.firestore.collection("users/${uid.value}/$collection")
            .get().get(60,TimeUnit.SECONDS).documents
        suspend fun request(method: String, path: String, body: String?=null, phase: String="setup", base: String=baseUrl): Reply = withContext(Dispatchers.IO) {
            traffic.computeIfAbsent("$phase:$method") { AtomicLong() }.incrementAndGet()
            val builder=HttpRequest.newBuilder(URI(base+path)).timeout(Duration.ofSeconds(120))
                .header("Authorization","Bearer $token").header("Content-Type","application/json")
            val response=http.send(builder.method(method,body?.let(HttpRequest.BodyPublishers::ofString)
                ?: HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray())
            Reply(response.statusCode(),json.readTree(response.body()),response.body().size)
        }
        suspend fun measured(phase: String, operation: String, method: String, path: String,
            body: String?=null, expected: Int=200, retry: Boolean=true): JsonNode {
            val start=System.nanoTime();var attempts=0;var reply: Reply
            do {
                attempts++;reply=request(method,path,body,phase)
                if(reply.status==503 && retry && attempts<3) delay(25L*attempts)
            } while(reply.status==503 && retry && attempts<3)
            if(phase!="warmup") samples+=Sample(phase,operation,(System.nanoTime()-start)/1e6,attempts,reply.status,expected,reply.bytes)
            assertEquals(expected,reply.status,"$phase/$operation status")
            if(method=="POST" && expected==200) assertEquals("CONFIRMED",reply.body.path("outcome").textValue())
            return reply.body
        }
        fun envelope(type: String, payload: Map<String,Any?>, operation: String=id())=json.writeValueAsString(
            mapOf("operationId" to operation,"type" to type,"cycleId" to cycle,"payload" to payload))
        fun createPayload()=mapOf("activityId" to "strength","activityDate" to BusinessTime.date(now).toString(),"hours" to 0,"minutes" to 30)
        fun editPayload(record: String)=createPayload()+mapOf("recordId" to record,"expectedVersion" to 1,"minutes" to 45)
        fun deletePayload(record: String)=mapOf("recordId" to record,"expectedVersion" to 2)
        suspend fun crud(phase: String) {
            val made=measured(phase,"CREATE","POST","/v1/commands",envelope("CREATE_RECORD",createPayload()))
            val record=made.path("result").path("recordId").textValue();created.incrementAndGet()
            assertEquals(1L,made.path("result").path("version").longValue())
            val edited=measured(phase,"EDIT","POST","/v1/commands",envelope("EDIT_RECORD",editPayload(record)))
            assertEquals(2L,edited.path("result").path("version").longValue())
            val deleted=measured(phase,"DELETE","POST","/v1/commands",envelope("DELETE_RECORD",deletePayload(record)))
            assertEquals(3L,deleted.path("result").path("version").longValue())
        }
        suspend fun reads(phase: String) {
            measured(phase,"progress","GET","/v1/progress")
            val history=measured(phase,"history","GET","/v1/history?period=month&date=${BusinessTime.date(now)}")
            assertTrue(history.path("complete").booleanValue());assertTrue(history.path("items").isArray)
        }
        fun seed() {
            val start=System.nanoTime();val random=Random(options.seed)
            val definition=ActivityCatalog.find(ActivityId("strength"))!!
            val store=RecordStore(resources.firestore)
            for(chunk in (0 until volume).toList().chunked(400)) {
                val batch=resources.firestore.batch()
                for(index in chunk) {
                    val date=BusinessTime.date(now).minusDays(random.nextInt(365).toLong())
                    val born=date.atTime(12,0).toInstant(BusinessTime.offset)
                    val record=ActivityRecord.create(RecordId("synthetic-perf-$index"),CycleId(cycle),definition,date,1+random.nextInt(60),born)
                    val fields=store.fields(record)
                    seeded[record.id.value]=fields
                    batch.set(resources.firestore.document("users/${uid.value}/records/${record.id.value}"),fields)
                }
                batch.commit().get(60,TimeUnit.SECONDS)
            }
            seedMillis=(System.nanoTime()-start)/1e6
        }
        suspend fun lostReplies() {
            var record=""
            for(type in listOf("CREATE","EDIT","DELETE")) {
                val operation=id()
                val data=when(type) { "CREATE" -> createPayload(); "EDIT" -> editPayload(record); else -> deletePayload(record) }
                val body=envelope("${type}_RECORD",data,operation)
                fault.set(operation)
                measured("postcommit","$type.lost","POST","/v1/commands",body,503,false)
                assertNull(fault.get(),"Injected fault must occur after the real commit")
                val receipt=measured("postcommit","$type.receipt","GET","/v1/operations/$operation")
                assertEquals("CONFIRMED",receipt.path("outcome").textValue())
                val replay=measured("postcommit","$type.retry","POST","/v1/commands",body)
                assertEquals(receipt,replay,"Same UUID must return the original receipt")
                if(type=="CREATE") { record=replay.path("result").path("recordId").textValue();created.incrementAndGet() }
                assertEquals(when(type) { "CREATE" -> 1L; "EDIT" -> 2L; else -> 3L },
                    resources.documents.read(uid,listOf("records",record))!!["version"])
                assertEquals(if(type=="CREATE") 30L else 45L,
                    resources.documents.read(uid,listOf("records",record))!!["durationMinutes"])
            }
        }
        suspend fun duplicateContention() = coroutineScope {
            val body=envelope("CREATE_RECORD",createPayload())
            val replies=(0 until options.workers).map { async { measured("same-uuid","CREATE","POST","/v1/commands",body) } }.awaitAll()
            assertTrue(replies.all { it==replies.first() })
            val record=replies.first().path("result").path("recordId").textValue();created.incrementAndGet()
            measured("same-uuid","EDIT","POST","/v1/commands",envelope("EDIT_RECORD",editPayload(record)))
            measured("same-uuid","DELETE","POST","/v1/commands",envelope("DELETE_RECORD",deletePayload(record)))
            duplicateVerified=true
        }
        suspend fun verifyFinal() {
            val rows=documents("records")
            assertEquals(volume.toLong()+created.get(),rows.size.toLong(),"No duplicate records")
            assertEquals(volume,rows.count { !it.data.containsKey("deletedAt") },"All measured records end deleted; seeds remain")
            assertEquals(1L+3*created.get(),documents("operations").size.toLong(),"No duplicate receipts")
            val stored=rows.associate { it.id to it.data }
            seeded.forEach { (id,fields) -> assertEquals(fields,stored[id],"Seeded records must remain unchanged") }
            assertTrue(rows.filter { it.id !in seeded }.all { it.data["version"]==3L && it.data["durationMinutes"]==45L })
            val store=RecordStore(resources.firestore)
            val root=resources.documents.read(uid,emptyList())!!
            val cycleValue=store.cycle(uid,CycleId(cycle),resources.documents.read(uid,listOf("cycles",cycle)))
            val replay=ReplayEngine.rebuild(cycleValue,rows.map { store.record(it.id,it.data) },now)
            val expected=lvluplife.api.progressPayload(Progression.derived(cycleValue,Revision((root["revision"] as Long)+1),now,
                replay.attributes,replay.general,replay.appearance.classId,replay.appearance.stage))
            val actual=measured("verification","progress","GET","/v1/progress")
            assertEquals(json.readTree(json.writeValueAsString(expected)),actual,"Published projection must equal full-cycle replay")
            val history=measured("verification","history","GET","/v1/history?period=month&date=${BusinessTime.date(now)}")
            val month=BusinessTime.date(now).withDayOfMonth(1)
            val expectedHistory=rows.filter { !it.data.containsKey("deletedAt") && java.time.LocalDate.parse(it.data["activityDate"] as String).let { date -> !date.isBefore(month) && date.isBefore(month.plusMonths(1)) } }.map { it.id }.toSet()
            assertEquals(expectedHistory,history.path("items").map { it.path("recordId").textValue() }.toSet())
            consistencyVerified=true
        }
        suspend fun cold(output: Path) {
            val classpath=Files.readString(Path.of(requireNotNull(System.getProperty("lvlup.perf.classpathFile"))))
            val java=Path.of(System.getProperty("java.home"),"bin",if(System.getProperty("os.name").startsWith("Windows")) "javaw.exe" else "java").toString()
            // An argument file avoids Windows' command-line length limit for the test runtime classpath.
            val arguments=output.resolve("cold-classpath.args")
            Files.writeString(arguments,"-classpath\n\""+classpath.replace('\\','/')+"\"\nlvluplife.api.ServerKt\n")
            repeat(options.coldN) { index ->
                val port=ServerSocket(0).use { it.localPort }
                val start=System.nanoTime()
                val builder=ProcessBuilder(java,"@"+arguments.toAbsolutePath())
                    .redirectErrorStream(true).redirectOutput(output.resolve("cold-$volume-$index.log").toFile())
                builder.environment()["PORT"]=port.toString()
                val process=builder.start()
                var success: Reply?=null;var attempts=0
                try {
                    val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90)
                    while(System.nanoTime()<deadline && success==null) {
                        check(process.isAlive) { "Cold service exited before readiness" };attempts++
                        try { request("GET","/v1/progress",phase="cold",base="http://127.0.0.1:$port").let { if(it.status==200) success=it } }
                        catch(_:java.io.IOException) { /* Startup probe: connection not ready yet. */ }
                        if(success==null) delay(50)
                    }
                    val result=requireNotNull(success) { "Cold service did not become ready" }
                    coldProbes+=attempts
                    samples+=Sample("cold-jvm","first-progress",(System.nanoTime()-start)/1e6,attempts,result.status,200,result.bytes)
                } finally {
                    process.destroy()
                    withContext(Dispatchers.IO) { if(!process.waitFor(10,TimeUnit.SECONDS)) { process.destroyForcibly();check(process.waitFor(10,TimeUnit.SECONDS)) } }
                }
            }
        }
    }

    private suspend fun runProfile(config: ServerConfig, options: Options, volume: Int, output: Path) {
        val start=System.nanoTime()
        ServerResources.open(config).use { resources ->
            assertEquals("127.0.0.1:8080",resources.firestore.options.host)
            val f=Fixture(resources,options,volume)
            val base=accountHandlers(resources.firestore,AuthoritativeClock { f.now })
            val engine=embeddedServer(Netty,host="127.0.0.1",port=0) {
                serverModule(base.copy(command={ uid,command ->
                    val response=base.command(uid,command)
                    val pending=f.fault.get()
                    if(response.body.path("outcome").textValue()=="CONFIRMED" && pending==command.operationId.value && f.fault.compareAndSet(pending,null))
                        throw InjectedFault(FaultPoint.AFTER_COMMIT)
                    response
                }),verifyToken={ resources.identityVerifier.verify(it)?.value })
            }.start(wait=false)
            var failure: String?=null
            try {
                f.baseUrl="http://127.0.0.1:${engine.engine.resolvedConnectors().single().port}"
                val profile=json.writeValueAsString(mapOf("operationId" to f.id(),"type" to "COMPLETE_PROFILE",
                    "payload" to mapOf("username" to ("P"+f.run.replace("-","").take(12)))))
                val made=f.request("POST","/v1/commands",profile)
                assertEquals(200,made.status);assertEquals("CONFIRMED",made.body.path("outcome").textValue())
                f.cycle=resources.documents.read(f.uid,emptyList())!!["currentCycleId"] as String
                f.now=Instant.parse("2026-11-12T18:00:00Z");f.seed()
                repeat(options.warmup) { f.reads("warmup");f.crud("warmup") }
                repeat(options.n) { f.reads("sequential");f.crud("sequential") }
                repeat(options.n) { coroutineScope {
                    (0 until options.workers).map { async { f.reads("concurrent");f.crud("concurrent") } }.awaitAll()
                } }
                f.duplicateContention();f.lostReplies();f.verifyFinal();f.cold(output)
            } catch(error: Throwable) { failure=error.javaClass.name;throw error }
            finally {
                engine.stop(500,3000)
                writeReport(f,output,(System.nanoTime()-start)/1e6,failure)
            }
        }
    }

    private fun writeReport(f: Fixture, output: Path, duration: Double, failure: String?) {
        val samples=f.samples.toList()
        val summaries=samples.groupBy { it.phase to it.operation }.map { (key,rows) -> mapOf(
            "phase" to key.first,"operation" to key.second,"n" to rows.size,
            "warmupIterations" to if(key.first=="cold-jvm") 0 else f.options.warmup,
            "p50Ms" to percentile(rows.map { it.millis },0.50),"p95Ms" to percentile(rows.map { it.millis },0.95),
            "errors" to rows.count { it.status!=it.expectedStatus },"clientRetries" to if(key.first=="cold-jvm") 0 else rows.sumOf { it.attempts-1 },
            "httpErrors" to rows.count { it.status>=400 },"expectedFailures" to rows.count { it.status>=400 && it.status==it.expectedStatus },
            "durationMs" to rows.sumOf { it.millis }) }
        val report=linkedMapOf<String,Any?>("schemaVersion" to 1,"seed" to f.options.seed,"profile" to f.options.profile,
            "volume" to f.volume,"datasetDates" to 365,"cycleScope" to "all seeded records in current cycle, including tombstones",
            "environment" to mapOf("mode" to "local-emulators-loopback-http","project" to "demo-lvluplife",
                "java" to System.getProperty("java.version"),"os" to System.getProperty("os.name"),"processors" to Runtime.getRuntime().availableProcessors(),
                "revision" to (System.getenv("LVLUP_PERF_REVISION") ?: "UNSPECIFIED"),
                "ktor" to System.getProperty("lvlup.perf.ktorVersion"),"firestoreClient" to System.getProperty("lvlup.perf.firestoreVersion")),
            "warmupIterations" to f.options.warmup,"iterations" to f.options.n,"workers" to f.options.workers,
            "coldJvmSamples" to f.options.coldN,"coldReadinessProbes" to f.coldProbes,"durationMs" to duration,"seedDurationMs" to f.seedMillis,
            "reads" to mapOf("httpRequestsByPhaseAndMethod" to f.traffic.mapValues { it.value.get() },
                "seededCycleRecords" to f.volume,"fullCycleRecordsBeforeMeasuredWorkload" to (f.volume+f.options.warmup),
                "fullCycleRecordsAfter" to (f.volume+f.created.get()),
                "firestoreDocumentReads" to null,"internalTransactionRetries" to null),
            "explicitPostcommitReplays" to samples.count { it.phase=="postcommit" && it.operation.endsWith(".retry") },
            "checks" to mapOf("fullCycleReplayAndHistory" to f.consistencyVerified,"sameUuidContention" to f.duplicateVerified),
            "authoritativeWarmClock" to f.now.toString(),
            "failureClass" to failure,"summaries" to summaries,"samples" to samples,
            "limits" to listOf("Frozen authoritative clock in warm workloads; real clock in child JVM",
                "Seeded directly in Firestore; seeding excluded from request percentiles",
                "HTTP counts and explicit client retries measured; Firestore RPC/billable reads and internal retries NOT instrumented",
                "Cold JVM is new; host OS, emulator, JVM parent and file caches remain warm",
                "No TLS, Android/network latency or production p95/availability/session compliance claim"))
        val target=output.resolve("profile-${f.options.profile}-${f.volume}")
        Files.writeString(Path.of("$target.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n")
        val environment=report["environment"] as Map<*,*>
        val columns=listOf("schema_version","seed","profile","volume","environment","revision","java","warmup_iterations",
            "iterations","workers","phase","operation","n","p50_ms","p95_ms","errors","http_errors","expected_failures",
            "client_retries","http_get_requests","firestore_document_reads","internal_transaction_retries","duration_ms","total_duration_ms")
        fun csvValue(value: Any?)="\""+value.toString().replace("\"","\"\"")+"\""
        val csv=columns.joinToString(",")+"\n"+summaries.joinToString("\n") { row ->
            (listOf(1,f.options.seed,f.options.profile,f.volume,environment["mode"],environment["revision"],environment["java"],
                row["warmupIterations"],f.options.n,f.options.workers)+
                listOf("phase","operation","n","p50Ms","p95Ms","errors","httpErrors","expectedFailures","clientRetries").map { row[it] }+
                listOf(f.traffic["${row["phase"]}:GET"]?.get() ?: 0,"NOT_INSTRUMENTED","NOT_INSTRUMENTED",row["durationMs"],duration))
                .joinToString(",",transform=::csvValue)
        }+"\n"
        Files.writeString(Path.of("$target.csv"),csv)
        // Independent report invariants: quantities, required types, finite timings and CSV cardinality.
        val parsed=json.readTree(Files.readString(Path.of("$target.json")))
        assertEquals(1,parsed.path("schemaVersion").intValue());assertEquals(samples.size,parsed.path("samples").size())
        assertEquals(samples.size,parsed.path("summaries").sumOf { it.path("n").intValue() })
        assertEquals(summaries.size+1,Files.readAllLines(Path.of("$target.csv")).size)
        assertTrue(parsed.path("reads").path("firestoreDocumentReads").isNull)
        assertTrue(samples.all { it.millis.isFinite() && it.millis>=0 && it.attempts>=1 })
        if(failure==null) {
            assertTrue(parsed.path("failureClass").isNull)
            assertTrue(parsed.path("checks").all { it.booleanValue() })
            assertEquals(3,parsed.path("explicitPostcommitReplays").intValue())
            assertTrue(parsed.path("summaries").all { it.path("errors").intValue()==0 })
            assertEquals(f.options.n,parsed.path("summaries").single { it.path("phase").textValue()=="sequential" && it.path("operation").textValue()=="CREATE" }.path("n").intValue())
            assertEquals(f.options.n*f.options.workers,parsed.path("summaries").single { it.path("phase").textValue()=="concurrent" && it.path("operation").textValue()=="CREATE" }.path("n").intValue())
        }
    }
}
