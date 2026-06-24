// vars/buildDockerImage.groovy
// ─────────────────────────────────────────────────────────────────────────────
// Build local container image with trace labels
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def fullImage = config.fullImage
    def dockerfile = config.get('dockerfile', 'Dockerfile')
    def buildArgs = config.get('buildArgs', '')
    def context = config.get('context', '.')

    if (!fullImage) {
        error("buildDockerImage: 'fullImage' path is required to register build tags.")
    }

    echo "Building container target: ${fullImage} (Context: ${context}, Dockerfile: ${dockerfile})"

    // Structure optional build arguments
    def buildArgsFlag = ""
    if (buildArgs) {
        buildArgsFlag = buildArgs.split(' ').collect { "--build-arg ${it}" }.join(' ')
    }

    // Embed build provenance details directly in the image manifest metadata
    sh """
        docker build ${buildArgsFlag} \
            -f ${dockerfile} \
            --label "git.commit=\$GIT_COMMIT_SHORT" \
            --label "build.number=\$BUILD_NUMBER" \
            --label "app.name=\$APP_NAME" \
            -t ${fullImage} ${context}
    """
    echo "Docker build completed successfully."
}
