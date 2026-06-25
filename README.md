# Jenkins Shared Library for bushido-brand

This library houses declarative orchestration scripts used to handle the testing and deployment lifecycle of bushido-brand microservices.

## Pipeline Architecture Flow

```
[Developer Push]
       │
       ▼
 ┌───────────┐      ┌─────────────────────────┐      ┌─────────────────┐      ┌──────────────┐
 │ Checkout  │ ───> │ OWASP Dependency Check  │ ───> │ SonarQube SAST  │ ───> │ Quality Gate │
 └───────────┘      └─────────────────────────┘      └─────────────────┘      └──────────────┘
                                                                                      │
                                                                                      ▼
 ┌───────────┐      ┌─────────────────────────┐      ┌─────────────────┐      ┌──────────────┐
 │ Slack Msg │ <─── │      ArgoCD Sync        │ <─── │  Update GitOps  │ <─── │ Docker Build │
 └───────────┘      └─────────────────────────┘      └─────────────────┘      └──────────────┘
                                                                                      │
                                                                                      ▼
                                                                              ┌──────────────┐
                                                                              │  Trivy Scan  │
                                                                              └──────────────┘
```

## Directory Structure
```
jenkins-shared-lib/
├── vars/
│   ├── buildPipeline.groovy        ← main orchestrator, called from each Jenkinsfile
│   ├── runOwaspCheck.groovy        ← OWASP Dependency-Check stage
│   ├── runSonarScan.groovy         ← SonarQube SAST stage
│   ├── buildDockerImage.groovy     ← docker build stage
│   ├── runTrivyScan.groovy         ← Trivy image vulnerability scan
│   ├── pushToDockerHub.groovy      ← Docker Hub login + push
│   ├── updateGitOps.groovy         ← clone gitops repo, bump tag, commit, push
│   ├── syncArgoCD.groovy           ← argocd app sync + wait for Healthy
│   └── notifySlack.groovy          ← Slack success/failure notification
├── resources/
│   └── owasp-suppressions.xml      ← OWASP CVE suppressions for known FPs
└── README.md
```

## Required Jenkins Credentials Setup

These credentials must be registered in the Jenkins system configuration before executing the pipelines:

| Credential ID | Type | Description |
|---|---|---|
| `dockerhub-creds` | Username + Password | Docker Hub auth (Username + Access Token) |
| `OWASP` | Secret Text | NVD database query access key for OWASP Dependency-Check |
| `SONAR` | Secret Text | SonarQube server URL (token configured in Jenkins global SonarQube config) |
| `Github-cred` | Username + Password | GitHub token with repo write scope (Username + PAT) |
| `argocd-token` | Secret Text | ArgoCD CLI authorization token |
| `argocd-server` | Secret Text | ArgoCD server endpoint hostname |

## Required Plugins
1. **Pipeline: Stage View**
2. **AnsiColor**
3. **OWASP Dependency-Check Plugin**
4. **SonarQube Scanner for Jenkins**
5. **Slack Notification Plugin**

## Agent Environment Prerequisites
The build agent labelled `docker-agent` must have the following binaries installed globally:
- `docker` (daemon running)
- `trivy` (vulnerability scanner)
- `yq` (YAML parser v4+)
- `sonar-scanner`
- `argocd` (CLI binary)
- `curl`

Install commands (Ubuntu):
```bash
# Docker
sudo apt-get update && sudo apt-get install -y docker.io

# Trivy
sudo apt-get install -y wget apt-transport-https gnupg lsb-release
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo "deb https://aquasecurity.github.io/trivy-repo/deb \$(lsb_release -sc) main" | sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt-get update && sudo apt-get install -y trivy

# YQ (v4)
sudo wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/bin/yq && sudo chmod +x /usr/bin/yq

# ArgoCD CLI
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
rm argocd-linux-amd64
```

## How to Register Library in Jenkins
1. Navigate to **Manage Jenkins** -> **System** -> **Global Pipeline Libraries**.
2. Add a new Library:
   - Name: `jenkins-shared-lib`
   - Default Version: `main`
   - Retrieval Method: Modern SCM (Git)
   - Project Repository: `https://github.com/BushidoBrand/jenkins-shared-lib.git`
   - Select credentials if private.

## How to Generate ArgoCD API Token
```bash
# Login to ArgoCD server
argocd login <argocd-server> --username admin --password <password>

# Create service account mapping
# (Add service account name to argocd-cm configmap first)
argocd account generate-token --account jenkins
```

## Onboarding a New Microservice
Copy `Jenkinsfile.template` from this project's root folder into the root directory of your new microservice codebase, rename it to `Jenkinsfile`, and configure the config keys inside.
