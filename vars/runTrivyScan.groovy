// vars/runTrivyScan.groovy
// ─────────────────────────────────────────────────────────────────────────────
// Trivy Container Vulnerability Scan
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def fullImage = config.fullImage
    def failOnCritical = config.get('failOnCritical', true)
    // Optional: unique report name to avoid overwrites in monorepos running multiple scans
    def reportName = config.get('reportName', 'trivy-report.json')

    if (!fullImage) {
        error("runTrivyScan: 'fullImage' reference is required to verify image layers.")
    }

    echo "Initiating image scan with Trivy on: ${fullImage}"

    // Command 1: Break pipeline execution if any critical CVE is found
    def exitCode = failOnCritical ? 1 : 0
    echo "Step 1: Running check for CRITICAL CVEs (pipeline fails if exit status = 1)"
    sh """
        trivy image \
            --exit-code ${exitCode} \
            --severity CRITICAL \
            --no-progress \
            --format table \
            ${fullImage}
    """

    // Command 2: Capture scan results in JSON format for external logging
    echo "Step 2: Generating full JSON scan audit log (does not block build execution)"
    sh """
        trivy image \
            --exit-code 0 \
            --severity HIGH,CRITICAL \
            --no-progress \
            --format json \
            --output ${reportName} \
            ${fullImage}
    """
    echo "Trivy scan stage complete. Report: ${reportName}"
}
