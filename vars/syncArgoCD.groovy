// vars/syncArgoCD.groovy
// ─────────────────────────────────────────────────────────────────────────────
// ArgoCD Sync and Watch pipeline stage
// ─────────────────────────────────────────────────────────────────────────────

def call(Map config = [:]) {
    def argoApp = config.argoApp
    def argoAutoSync = config.get('argoAutoSync', false)
    def syncTimeout = config.get('syncTimeout', '120')
    def waitTimeout = config.get('waitTimeout', '300')

    if (!argoApp) {
        error("syncArgoCD: 'argoApp' is required to specify target deployment.")
    }

    withCredentials([
        string(credentialsId: 'argocd-token', variable: 'ARGOCD_TOKEN'),
        string(credentialsId: 'argocd-server', variable: 'ARGOCD_SERVER')
    ]) {
        // Trigger manual synchronization if auto-sync is disabled
        if (!argoAutoSync) {
            echo "Auto-sync disabled. Triggering manual synchronization for application: ${argoApp}"
            sh "argocd app sync ${argoApp} --server \$ARGOCD_SERVER --auth-token \$ARGOCD_TOKEN --grpc-web --timeout ${syncTimeout}"
        } else {
            echo "ArgoCD auto-sync is enabled. Skipping manual sync step."
        }

        // Monitor deployment rollout to ensure container is running healthily
        echo "Waiting for app ${argoApp} health verification..."
        sh "argocd app wait ${argoApp} --server \$ARGOCD_SERVER --auth-token \$ARGOCD_TOKEN --health --grpc-web --timeout ${waitTimeout}"
    }
    echo "ArgoCD deployment completed successfully."
}
