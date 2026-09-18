plugins {
    id("sellout.spring-service")
}

description = "Fake payment provider with fault injection (skeleton in slice 000)"

// No domain/application packages yet: nothing for the coverage rule to measure.
tasks.jacocoTestCoverageVerification { enabled = false }
