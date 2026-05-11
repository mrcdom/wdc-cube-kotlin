plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "wdc-cube-kotlin"

// Framework modules
include(":framework-commons")
include(":framework-cube")

// Shopping modules
include(":shopping-domain")
include(":shopping-persistence")
include(":shopping-scripts")
include(":shopping-presentation")
include(":shopping-tests")

// Map module names to actual directory paths
project(":framework-commons").projectDir      = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.commons")
project(":framework-cube").projectDir         = file("br.com.wdc.kt.framework/br.com.wdc.kt.framework.cube")
project(":shopping-domain").projectDir        = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.domain")
project(":shopping-persistence").projectDir   = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.persistence")
project(":shopping-scripts").projectDir       = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.scripts")
project(":shopping-presentation").projectDir  = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.presentation")
project(":shopping-tests").projectDir         = file("br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.tests")
