import java.io.File

private data class CosmoReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

private fun Project.resolveCosmoReleaseSigning(): CosmoReleaseSigning? {
    fun envOrGradle(envKey: String, propKey: String): String? = providers.environmentVariable(envKey).orNull?.takeIf { it.isNotBlank() }
        ?: (findProperty(propKey) as String?)?.takeIf { it.isNotBlank() }

    val path = envOrGradle("COSMO_RELEASE_STORE_FILE", "cosmo.release.storeFile") ?: return null
    val storePassword = envOrGradle("COSMO_RELEASE_STORE_PASSWORD", "cosmo.release.storePassword") ?: return null
    val keyAlias = envOrGradle("COSMO_RELEASE_KEY_ALIAS", "cosmo.release.keyAlias") ?: return null
    val keyPassword = envOrGradle("COSMO_RELEASE_KEY_PASSWORD", "cosmo.release.keyPassword") ?: return null
    val candidate = File(path)
    val storeFile =
        if (candidate.isAbsolute) {
            candidate
        } else {
            rootProject.layout.projectDirectory.file(path).asFile
        }
    return CosmoReleaseSigning(storeFile, storePassword, keyAlias, keyPassword)
}

private val cosmoReleaseSigning: CosmoReleaseSigning? = project.resolveCosmoReleaseSigning()

private fun String?.isTruthyFlag(): Boolean = this?.let { value ->
    value == "1" || value.equals("true", ignoreCase = true) || value.equals("yes", ignoreCase = true)
} == true

private fun Project.allowMissingRecoveredBinaries(): Boolean = providers.environmentVariable("COSMO_ALLOW_MISSING_BINARIES").orNull.isTruthyFlag() ||
    (findProperty("cosmo.allowMissingBinaries") as String?).isTruthyFlag()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.dependencycheck)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.protobuf)
    id("jacoco")
}

android {
    namespace = "com.google.research.air.cosmo"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.google.research.air.cosmo"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-genesis"
        testInstrumentationRunner = "com.google.research.air.cosmo.GenesisTestRunner"

        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "REMOTE_INFERENCE_BASE_URL", "\"${providers.gradleProperty("cosmo.remoteInference.baseUrl").orNull.orEmpty()}\"")
        buildConfigField("String", "REMOTE_INFERENCE_CERT_PIN", "\"${providers.gradleProperty("cosmo.remoteInference.certPin").orNull.orEmpty()}\"")
        buildConfigField("String", "REMOTE_INFERENCE_AUDIENCE", "\"${providers.gradleProperty("cosmo.remoteInference.audience").orNull ?: "cosmo-remote-inference"}\"")
        buildConfigField("String", "LOCAL_LITERTLM_BACKEND", "\"${providers.gradleProperty("cosmo.localLlm.litertLmBackend").orNull ?: "AUTO"}\"")
        buildConfigField("int", "LOCAL_LITERTLM_MAX_TOKENS", providers.gradleProperty("cosmo.localLlm.litertLmMaxTokens").orNull ?: "4096")
        buildConfigField("String", "LOCAL_LITERTLM_MODEL_ID", "\"${providers.gradleProperty("cosmo.localLlm.litertLmModelId").orNull ?: "google-gemma-4-E4B-it-litertlm"}\"")
        buildConfigField("boolean", "AICORE_EXPERIMENTAL_NON_PUBLIC", "${providers.gradleProperty("cosmo.aicore.experimentalNonPublic").orNull ?: "false"}")
        buildConfigField("String", "SODA_PACK_CPU_URL", "\"${providers.gradleProperty("cosmo.soda.languagePack.cpuUrl").orNull.orEmpty()}\"")
        buildConfigField("String", "SODA_PACK_CPU_SHA256", "\"${providers.gradleProperty("cosmo.soda.languagePack.cpuSha256").orNull ?: "fac23ca956f473c5025621784a1657a1663a16b0754886b975f3cde3f1345f04"}\"")
        buildConfigField("long", "SODA_PACK_CPU_SIZE_BYTES", "${providers.gradleProperty("cosmo.soda.languagePack.cpuSizeBytes").orNull ?: "56458465"}L")
        buildConfigField("String", "SODA_PACK_LGA_TPU_URL", "\"${providers.gradleProperty("cosmo.soda.languagePack.lgaTpuUrl").orNull.orEmpty()}\"")
        buildConfigField("String", "SODA_PACK_LGA_TPU_SHA256", "\"${providers.gradleProperty("cosmo.soda.languagePack.lgaTpuSha256").orNull ?: "b10205a4fe668123c17c3f1405e8acc6d072894574c875db05af6372e9993d2c"}\"")
        buildConfigField("long", "SODA_PACK_LGA_TPU_SIZE_BYTES", "${providers.gradleProperty("cosmo.soda.languagePack.lgaTpuSizeBytes").orNull ?: "145607304"}L")
        buildConfigField("String", "SODA_PACK_ZUMA_TPU_URL", "\"${providers.gradleProperty("cosmo.soda.languagePack.zumaTpuUrl").orNull.orEmpty()}\"")
        buildConfigField("String", "SODA_PACK_ZUMA_TPU_SHA256", "\"${providers.gradleProperty("cosmo.soda.languagePack.zumaTpuSha256").orNull ?: "c808fd151178adfffd99906f9290935ba189898e9700e47e8fa37a1701effd41"}\"")
        buildConfigField("long", "SODA_PACK_ZUMA_TPU_SIZE_BYTES", "${providers.gradleProperty("cosmo.soda.languagePack.zumaTpuSizeBytes").orNull ?: "146456436"}L")
        buildConfigField("boolean", "DEBUG_TIMER_FLOW_AUTOMATION", "false")
    }

    signingConfigs {
        create("genesisDebug") {
            storeFile = rootProject.file("config/signing/genesis-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (cosmoReleaseSigning != null) {
            create("releaseProduction") {
                val signing = checkNotNull(cosmoReleaseSigning)
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("genesisDebug")
            buildConfigField("boolean", "DEBUG_TIMER_FLOW_AUTOMATION", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (cosmoReleaseSigning != null) {
                signingConfig = signingConfigs.getByName("releaseProduction")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("emuDebug") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true
            signingConfig = signingConfigs.getByName("genesisDebug")
            matchingFallbacks += listOf("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG_TIMER_FLOW_AUTOMATION", "true")
            ndk {
                abiFilters += "x86_64"
            }
        }
        create("releaseInternal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("genesisDebug")
            matchingFallbacks += listOf("release")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("emuDebug") {
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += setOf("**/libtensorflowlite_jni.so")
        }
        resources {
            excludes +=
                setOf(
                    "META-INF/AL2.0",
                    "META-INF/DEPENDENCIES",
                    "META-INF/INDEX.LIST",
                    "META-INF/LGPL2.1",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                    "META-INF/LICENSE.txt",
                    "META-INF/NOTICE.md",
                    "META-INF/NOTICE.txt"
                )
        }
    }

    androidResources {
        noCompress += setOf("bin", "jinja2", "task", "textproto", "tflite", "zip")
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        disable +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                "OldTargetApi",
                "UnusedResources"
            )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("emuDebug")) { variant ->
        variant.packaging.jniLibs.excludes.add("**/liblitertlm_jni.so")
        variant.packaging.jniLibs.excludes.add("**/libLiteRt.so")
        variant.packaging.jniLibs.excludes.add("**/libLiteRtClGlAccelerator.so")
    }
}

tasks.register<Exec>("verifyCosmoReleaseModelManifest") {
    group = "verification"
    description =
        "Release-only gate: non-empty model manifest and required bundled assets must match checksum/size (scripts/download-models.sh --verify-only)."
    workingDir = rootProject.layout.projectDirectory.asFile
    val args = mutableListOf("scripts/download-models.sh", "--verify-only")
    if (allowMissingRecoveredBinaries()) {
        args += "--allow-missing-binaries"
    }
    commandLine("bash", *args.toTypedArray())
}

tasks.register<Exec>("verifySodaAssets") {
    group = "verification"
    description = "Verifies bundled recovered SODA language-pack assets by checksum and size."
    workingDir = rootProject.layout.projectDirectory.asFile
    val args = mutableListOf("scripts/verify-soda-assets.sh")
    if (allowMissingRecoveredBinaries()) {
        args += "--allow-missing-binaries"
    }
    commandLine("bash", *args.toTypedArray())
}

afterEvaluate {
    tasks
        .matching { it.name in setOf("assembleDebug", "assembleRelease", "bundleRelease") }
        .configureEach { dependsOn("verifyCosmoReleaseModelManifest") }
    tasks
        .matching {
            it.name in setOf(
                "assembleDebug",
                "assembleRelease",
                "assembleReleaseInternal",
                "bundleRelease"
            )
        }
        .configureEach { dependsOn("verifySodaAssets") }
}

gradle.taskGraph.whenReady {
    val packagingProdRelease =
        allTasks.any {
            it.project.path == ":app" && (it.name == "packageRelease" || it.name == "bundleRelease")
        }
    if (!packagingProdRelease) return@whenReady
    val signing =
        cosmoReleaseSigning
            ?: throw GradleException(
                """
                Production release packaging requires signing credentials (docs/release-signing-policy.md).
                Set COSMO_RELEASE_STORE_FILE, COSMO_RELEASE_STORE_PASSWORD, COSMO_RELEASE_KEY_ALIAS, COSMO_RELEASE_KEY_PASSWORD
                or cosmo.release.storeFile / cosmo.release.storePassword / cosmo.release.keyAlias / cosmo.release.keyPassword in Gradle properties.
                For minified CI/local builds without Play keys, use assembleReleaseInternal or bundleReleaseInternal.
                """.trimIndent()
            )
    require(signing.storeFile.isFile) {
        "Release keystore must exist at ${signing.storeFile.absolutePath} (COSMO_RELEASE_STORE_FILE / cosmo.release.storeFile)"
    }
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.okhttp.bom))

    // Recovered SODA Java surface: com.google.android.libraries.assistant.soda.*,
    // com.google.speech.soda.*. The genesis-owned com.google.research.air.cosmo.lib.soda.*
    // classes were stripped from the jar before checkin to avoid colliding with the genesis
    // Kotlin sources in the same package. Loaded by JniSodaCapability when bound; the
    // production wiring keeps the binding on UnavailableSodaCapability until on-device
    // verification on Pixel 9/10 lands.
    implementation(files("libs/recovered-soda.jar"))

    implementation(libs.aicore)
    // Required by the recovered SODA `SodaJniLoader.attemptToLoadFullNativeLibrary` flow,
    // which uses ReLinker as its robust System.loadLibrary fallback.
    implementation(libs.relinker)
    implementation(libs.ai.edge.litertlm.android)
    implementation(libs.tensorflow.lite)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.haze)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.firebase.analytics)
    implementation(libs.gms.wearable)
    implementation(libs.guava)
    implementation(libs.hilt.android)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.okhttp)
    implementation(libs.protobuf.javalite)
    implementation(libs.sqlcipher.android)

    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.hilt.testing)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)

    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    kspTest(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    baselineProfile(project(":baselineprofile"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

ktlint {
    android.set(true)
    baseline.set(file("$rootDir/config/ktlint/baseline.xml"))
}

dependencyCheck {
    failBuildOnCVSS = 9.0f
    suppressionFile = "$rootDir/config/dependency-check/suppressions.xml"
    scanBuildEnv = false
    skipTestGroups = true
    scanConfigurations = listOf("releaseRuntimeClasspath")
    formats = listOf("HTML", "XML", "JSON")
    failBuildOnUnusedSuppressionRule = true
    analyzers.assemblyEnabled = false
}

// JaCoCo - Track C: fast feedback loop for engineers (run after unit tests)
jacoco {
    toolVersion = "0.8.11"
    reportsDirectory = layout.buildDirectory.dir("reports/jacoco")
}

tasks.withType<Test>().configureEach {
    finalizedBy("jacocoTestReport", "jacocoTestCoverageVerification")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates JaCoCo coverage report for debug unit tests"
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.class", "**/Manifest*")
        }
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.dir("outputs/unit_test_code_coverage/debugUnitTest")).matching {
            include("**/*.exec")
        }
    )
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    group = "verification"
    description = "Fails build if coverage below 60% line / 50% branch (raise after more tests land)"
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal()
                counter = "LINE"
            }
            limit {
                minimum = "0.50".toBigDecimal()
                counter = "BRANCH"
            }
        }
    }
    classDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().classDirectories)
    sourceDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().sourceDirectories)
    executionData.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().executionData)
}
