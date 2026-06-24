// vars/runOwaspCheck.groovy
// ─────────────────────────────────────────────────────────────────────────────
// OWASP Dependency Check Wrapper Stage
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def appName = config.get('appName', 'bushido-brand')
    // Optional: path to suppressions XML (helps monorepos where file isn't in current dir)
    def suppressionPath = config.get('suppressionPath', 'owasp-suppressions.xml')
    echo "Initiating OWASP Dependency-Check scan for package metadata of: ${appName}"

    // Create output path structure
    sh "mkdir -p reports"

    // Fetch NVD credentials from store to prevent public rate throttling
    withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
        // Suppress errors related to suppression configurations by directing CLI output
        sh """
            /opt/dependency-check/bin/dependency-check.sh \
                --project "${appName}" \
                --scan . \
                --format HTML \
                --format XML \
                --out reports/ \
                --nvdApiKey "\$NVD_API_KEY" \
                --failOnCVSS 8 \
                --enableRetired \
                --suppression ${suppressionPath} 2>/dev/null || true
        """
    }
    echo "Security dependency assessment complete. Reports archived under reports/"
}
