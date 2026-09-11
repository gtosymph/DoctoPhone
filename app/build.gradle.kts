import org.gradle.api.tasks.PathSensitivity
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Lit une variable d'environnement, puis à défaut une propriété Gradle (`-Pnom=...`).
 * Les deux voies existent parce que la CI passe les secrets en variables d'environnement
 * (pour ne jamais les écrire dans une commande visible du journal), alors qu'un lancement
 * manuel les passe plus naturellement en `-P`.
 */
fun envOrProperty(name: String): String? =
    System.getenv(name) ?: (findProperty(name) as String?)

/**
 * Le numéro de build vient de la chaîne de construction : l'action GitHub passe
 * `-PversionCode=${{ github.run_number }}` et `-PversionName="<base>.${{ github.run_number }}"`,
 * où `github.run_number` ne fait que croître, même après un `git revert` — c'est ce qui
 * garantit qu'Android accepte toujours l'APK suivant comme une mise à jour. Une
 * construction locale sans propriété retombe sur les valeurs historiques du projet.
 */
val releaseVersionCode = envOrProperty("versionCode")?.toIntOrNull() ?: 1
val releaseVersionName = envOrProperty("versionName") ?: "0.1.0"

/**
 * Signature de publication : le keystore de débogage `~/.android/debug.keystore`, choix
 * assumé par l'utilisateur. L'APK déjà installé sur son appareil porte cette signature,
 * et Android refuse une mise à jour dont la signature diffère — utiliser un autre
 * keystore forcerait une désinstallation à chaque publication.
 *
 * Le chemin et les trois mots de passe viennent uniquement de l'environnement ou d'une
 * propriété Gradle : aucun secret ne doit jamais être écrit dans ce fichier versionné.
 * En CI, le secret `ANDROID_KEYSTORE_BASE64` est décodé dans un fichier temporaire du
 * runner, dont le chemin est passé ici via `ANDROID_KEYSTORE_PATH`.
 */
val releaseKeystorePath = envOrProperty("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = envOrProperty("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = envOrProperty("ANDROID_KEY_ALIAS")
val releaseKeyPassword = envOrProperty("ANDROID_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.kmt.healthanalyzer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kmt.healthanalyzer"
        minSdk = 29
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans les quatre variables ci-dessus (poste de développement ordinaire), la
            // release retombe sur la signature debug : `./gradlew assembleRelease` reste
            // utilisable sans rien configurer. Ce repli n'affaiblit rien en pratique, car
            // le keystore de CI est justement une copie encodée du même debug.keystore.
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)


    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

/**
 * Synchronise les moteurs web partagés (`web/report/`, `web/lib/report-model.js`,
 * `web/chat/`) dans les assets de l'app.
 *
 * Chaque moteur est écrit une seule fois et sert à la fois à la version web et à sa
 * WebView Android (voir `web/report/report.html` et `web/chat/chat.html`). Cette tâche
 * le fait entrer dans l'APK sans geste manuel : `app/src/main/assets/` est un dossier
 * généré, jamais versionné (voir `.gitignore`). Les chemins relatifs `../lib/report-model.js`
 * et `../lib/markdown.js` utilisés par `report.html` et `chat.html` restent valides, car la
 * structure `report/` + `lib/` + `chat/` est reproduite telle quelle sous `assets/`. La
 * conversation dépend aussi de `report/chart-catalog.js`, `report/chart-spec.js` et
 * `report/chat-prompt.txt`, déjà couverts par la synchronisation de `web/report/`.
 *
 * `Sync` plutôt que `Copy` : un fichier renommé ou enlevé côté `web/` doit disparaître
 * des assets, pas s'y accumuler comme fichier mort.
 *
 * Les pages d'essai des moteurs (`smoke.html`, `smoke-fixture.js`, `chat-smoke.html`, et
 * tout fichier dont le nom contient `smoke`) sont utiles en développement seulement.
 * Elles grossiraient l'APK et exposeraient un écran de test dans l'app livrée : exclues.
 *
 * `web/report/fonts/` suit le même chemin, sans règle particulière. Deux choses à ne pas
 * « optimiser » :
 * - les `.woff2` doivent entrer dans l'APK, sinon la WebView n'a aucune police à charger —
 *   elle n'a pas d'accès réseau et ne peut pas aller les chercher ;
 * - les deux fichiers `OFL-*.txt` aussi. La licence SIL OFL exige d'être distribuée avec
 *   la fonte. Ils pèsent 9 Ko à eux deux.
 */
val syncReportAssets by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.dir("web/report")) {
        into("report")
        // Le motif couvre `smoke.html`, `smoke-fixture.js` et `chart-spec-smoke.html`, que
        // l'exclusion nommée laissait passer jusqu'ici malgré l'intention du commentaire.
        exclude("**/*smoke*")
    }
    from(rootProject.layout.projectDirectory.dir("web/lib")) {
        into("lib")
        include("report-model.js", "markdown.js")
    }
    from(rootProject.layout.projectDirectory.dir("web/chat")) {
        into("chat")
        exclude("**/*smoke*")
    }
    into(layout.projectDirectory.dir("src/main/assets"))
}

/**
 * Le catalogue de séries vit en deux exemplaires : `ChatSeriesCatalog.kt` et
 * `web/report/chart-catalog.js`. `ChatSeriesCatalogParityTest` vérifie qu'ils déclarent
 * les mêmes clés — mais Gradle ne relançait pas ce test quand seul le fichier JavaScript
 * changeait, puisqu'il ne le connaissait pas. La garde était donc muette au moment même
 * où elle aurait servi. On déclare le fichier comme entrée des tests pour y remédier.
 */
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.layout.projectDirectory.file("web/report/chart-catalog.js"))
        .withPropertyName("chartCatalogJs")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // Même raison pour la feuille de style : `ThemeTokenParityTest` la relit pour comparer
    // la palette de Compose à celle du rapport. Sans cette déclaration, une couleur changée
    // dans le CSS seul ne relancerait pas le test qui existe précisément pour l'attraper.
    inputs.file(rootProject.layout.projectDirectory.file("web/report/report.css"))
        .withPropertyName("reportCss")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named("preBuild") {
    dependsOn(syncReportAssets)
}
