pipeline {
    agent { label 'local' }

    environment {
        VAULT_ADDR = 'http://localhost:8200'
        VAULT_TPKEN = 'root'
        IMAGE_NAME = 'microservice'
        DEV_CONTEXT = 'kind-development'
    }

    stages{
        stage('1. Leer secreto de vault'){
            steps {
                script{
                    def secret = powershell(
                        returnStdout: true,
                        script: '''
                            $resp = curl.exe -s -H "X-Vault-Token: $env:VAULT_TOKEN" "$env:VAULT_ADDR/v1/secret/data/microservice"
                            $json = $resp | ConvertFrom-Json
                            $json.data.data.APP_SECRET
                        '''
                    ).trim()
                    env.APP_SECRET = secret
                    echo "Secreto leido de Vault correctamente"
                }
            }
        }

        stage('2. Build de la imagen'){
            steps{
                dir('microservice'){
                    bat "docker build -t %IMAGE_NAME% ."
                }
            }
        }

        stage('3. Carga Imagen al cluster development'){
            steps{
                bat "kind load docker-image %IMAGE_NAME% --name development"
            }
        }

        stage('4. Deply en development'){
            steps{
                bat "kubectl --context %DEV_CONTEXT% apply -f k8s-manifests/"
                bat "kubectl --context %DEV_CONTEXT% set env deployment/microservice APP_SECRET=%APP_SECRET%"
                bat "kubectl --context %DEV_CONTEXT% rollout status deployment/microservice --timeout=120s"
            }
        }
    }

    post{
        failure{
            echo "Ocurrio un error al ejecutar el pipeline. Realizando rollback*****"
            bat "kubectl --context %DEV_CONTEXT% rollout undo deployment/microservice || exit 0"
        }
        success {
            echo 'Pipeline completado con exito.'
        }
    }
}