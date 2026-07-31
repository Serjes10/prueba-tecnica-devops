# Prueba Técnica DevOps 
## Infraestructura Automatizada con Kubernetes, Vault y Jenkins.
 
**Autor:** Sergio Jesus Ortega Matute
 
Prueba tenica implementado kubernetes, vault y jenkins, para ejecutar dos clusteres de kubernetes utilizando infraestrucutra como Código (IAC - Terraform). 
Clúster *deployment* tiene el objetivo de la automización de CI/CD y manejo de secretos con JenKins y Vault
Clúster *development* Despliega el microservicio. 


El microservicio está desarrollado en **Java 21** (Spring Boot). 
El pipeline de CI/CD se conecta a Vault para leer un secreto, lo inyecta como variable de entorno antes de construir y desplegar la imagen, y el microservicio expone endpoints que muestran estos valores de forma controlada.
 
---
 
## Tabla de contenidos
 
- [Arquitectura](#arquitectura)
- [Flujo CI/CD](#flujo-cicd)
- [Requisitos previos](#requisitos-previos)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Instrucciones paso a paso](#instrucciones-paso-a-paso)
- [Validación del secreto](#validación-del-secreto)
- [Endpoints del microservicio](#endpoints-del-microservicio)
- [Extra: CronJob de auditoría](#extra-cronjob-de-auditoría)
- [Decisiones de diseño](#decisiones-de-diseño)
- [Troubleshooting](#troubleshooting)
---
 
## Arquitectura
 
```
┌───────────────────────────────────────────────────────────────────────┐
│                              WINDOWS (host)                             │
│                                                                         │
│   Docker Desktop                                                        │
│                                                                         │
│  ┌────────────────────────────────┐   ┌────────────────────────────┐   │
│  │  CLÚSTER "deployment" (Kind)    │   │  CLÚSTER "development"(Kind)│   │
│  │  contenedor Docker              │   │  contenedor Docker          │   │
│  │                                 │   │                             │   │
│  │  ┌──────────┐   ┌───────────┐   │   │   ┌─────────────────────┐   │   │
│  │  │  Jenkins │   │   Vault    │  │   │   │  Service (NodePort) │   │   │
│  │  │  (CI/CD) │   │ (secretos) │  │   │   │   balanceador       │   │   │
│  │  └────┬─────┘   └─────┬─────┘   │   │   └──────────┬──────────┘   │   │
│  │       │               │         │   │              │              │   │
│  └───────┼───────────────┼─────────┘   │      ┌───────┴───────┐      │   │
│          │               │             │      │ réplica 1     │      │   │
│          │               │             │      │ microservicio │      │   │
│          │               │             │      ├───────────────┤      │   │
│          │               │             │      │ réplica 2     │      │   │
│          │               │             │      │ microservicio │      │   │
│          │               │             │      └───────────────┘      │   │
│          │               │             └─────────────▲───────────────┘   │
│          │               │                           │                   │
│          │  Agente Jenkins (host): docker build,     │                   │
│          └──kind load, kubectl ──────────────────────┘                   │
│                                                                          │
│   Los dos clústeres se crean 100% con Terraform                          │
└──────────────────────────────────────────────────────────────────────────┘
```
 
---
 
## Flujo CI/CD
 
```
   ┌──────────────────┐
   │  git push         │
   │  (GitHub)         │
   └────────┬─────────┘
            │  "Construir ahora en JenKins" (disparo manual)
            ▼
   ┌─────────────────────────────────────────────────────────┐
   │  PIPELINE JENKINS (Jenkinsfile, ejecuta en agente local) │
   │                                                          │
   │  0. Checkout        → clona el repositorio               │
   │  1. Leer secreto     → consulta Vault (API KV v2) y       │
   │                        obtiene APP_SECRET                 │
   │  2. Build            → docker build de la imagen          │
   │  3. Cargar imagen    → kind load al clúster development    │
   │  4. Deploy           → kubectl apply + set env (inyecta   │
   │                        el secreto) + rollout status       │
   │                                                          │
   │  post.failure        → rollout undo (ROLLBACK)            │
   └─────────────────────────────────────────────────────────┘
            │
            ▼
   Microservicio desplegado con 2 réplicas y el secreto inyectado
```
 
---
 
## Requisitos previos
 
El proyecto se desarrolló y probó en **Windows 11** con **PowerShell**. Herramientas necesarias:
 
| Herramienta | Uso |
|-------------|-----|
| Docker Desktop | Motor de contenedores; Kind lo usa para crear los clústeres |
| kind | Crea los clústeres de Kubernetes dentro de Docker |
| kubectl | Cliente de Kubernetes |
| Terraform | Infraestructura como Código (crea los clústeres) |
| Helm | Instala Vault y Jenkins mediante charts |
| Java 21 + Maven | Compilar el microservicio (opcional en local; el pipeline lo compila en la imagen) |
| Git | Control de versiones |
 
Instalación con Chocolatey (PowerShell como administrador):
 
```powershell
choco install docker-desktop kind kubernetes-cli terraform kubernetes-helm temurin21 maven git -y
```
 
> **Importante:** abrir Docker Desktop y esperar a que el motor esté en ejecución antes de continuar.
 
---
 
## Estructura del repositorio
 
```
.
├── Jenkinsfile                     # Pipeline CI/CD (declarativo)
├── .gitignore
├── README.md
├── terraform/
│   └── main.tf                     # Crea los 2 clústeres Kind (IaC)
├── microservice/
│   ├── Dockerfile                  # Multi-stage build (JDK build → JRE runtime)
│   ├── pom.xml
│   └── src/main/java/com/banco/microservice/
│       ├── MicroserviceApplication.java
│       └── ConfigController.java   # Endpoints /secret, /config, /health
├── jenkins/
│   └── jenkins-values.yaml         # Configuración de Jenkins como código (Helm)
└── k8s-manifests/
    ├── deployment.yaml             # 2 réplicas + probes + resources
    ├── service.yaml                # Service NodePort (balanceador)
    └── cronjob-audit.yaml          # EXTRA: auditoría de eventos del kubelet + RBAC
```
 
---
 
## Instrucciones paso a paso
 
### 1. Crear los clústeres con Terraform
 
```powershell
cd terraform
terraform init
terraform apply -auto-approve
```
 
Verificar:
 
```powershell
kubectl config get-contexts    
```
 
### 2. Instalar Vault (clúster deployment) y cargar el secreto
 
```powershell
kubectl config use-context kind-deployment
 
helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update
helm install vault hashicorp/vault --set "server.dev.enabled=true" --set "server.dev.devRootToken=root" --create-namespace -n vault
```
 
Esperar a que `vault-0` esté `Running`, luego cargar el secreto:
 
```powershell
kubectl -n vault exec -it vault-0 -- /bin/sh
# dentro del pod:
vault kv put secret/microservice APP_SECRET="key-test-devops-ultra-secreta"
vault kv get secret/microservice
exit
```
 
### 3. Instalar Jenkins (clúster deployment)
 
```powershell
helm repo add jenkins https://charts.jenkins.io
helm repo update
helm install jenkins jenkins/jenkins -f jenkins/jenkins-values.yaml -n jenkins --create-namespace
```
 
Esperar a que `jenkins-0` esté `Running (2/2)`.
 
### 4. Exponer Jenkins y Vault hacia el host (port-forward)
 
En dos ventanas de PowerShell separadas (cada una queda ocupada):
 
```powershell
kubectl -n jenkins port-forward svc/jenkins 8081:8080
```
 
```powershell
kubectl -n vault port-forward svc/vault 8200:8200
```
 
Jenkins queda accesible en `http://localhost:8081` (usuario `admin`, contraseña `admin-devops`).
 
### 5. Conectar el agente local
 
En Jenkins: **Administrar Jenkins → Nodos → Nuevo nodo** (`agente-local`, etiqueta `local`, método "Lanzar agente conectándolo al controlador"). Luego, en el host:
 
```powershell
mkdir C:\jenkins-agent
cd C:\jenkins-agent
curl.exe -sO http://localhost:8081/jnlpJars/agent.jar
java -jar agent.jar -url http://localhost:8081/ -secret <SECRET_DEL_NODO> -name "agente-local" -webSocket -workDir "C:\jenkins-agent"
```
 
> La URL se ajusta a `localhost:8081` (port-forward) en lugar del nombre interno `jenkins:8080`.
 
### 6. Crear el job del pipeline
 
En Jenkins: **Nueva Tarea → Pipeline**. En la configuración:
 
- **Definition:** Pipeline script from SCM
- **SCM:** Git
- **Repository URL:** URL del repositorio
- **Credentials:** token de GitHub (Personal Access Token con scope `repo`)
- **Branch Specifier:** `*/main`
- **Script Path:** `Jenkinsfile`
### 7. Ejecutar
 
**Construir ahora**. El pipeline ejecuta las etapas de checkout, lectura de Vault, build, carga y deploy.
 
> **Nota:** el disparo es **manual** (Construir ahora). El pipeline lee siempre la última versión del repositorio, por lo que cualquier cambio debe hacerse `git push` antes de ejecutar.
 
---
 
## Validación del secreto
 
El objetivo central de la prueba es demostrar que el secreto viaja desde Vault hasta el microservicio. Se valida así:
 
**1. El secreto existe en Vault:**
 
```powershell
curl.exe -s -H "X-Vault-Token: root" http://localhost:8200/v1/secret/data/microservice
```
 
**2. Las réplicas están corriendo:**
 
```powershell
kubectl --context kind-development get pods
# 2 pods microservice-... en Running 1/1
```
 
**3. El secreto llegó al microservicio (prueba definitiva):**
 
```powershell
curl.exe http://localhost:8090/secret
```
 
Respuesta esperada:
 
```json
{"source":"vault-env-var","secret":"key-test-devops-ultra-secreta"}
```
 
El valor `key-test-devops-ultra-secreta` (y no `NO_SECRET_FOUND`) confirma el flujo completo: **Vault → pipeline → variable de entorno → microservicio desplegado**.
 
> **Capturas:** ver carpeta `/capturas` (pipeline en verde, réplicas Running, respuestas de los endpoints, ejecución del CronJob).
 
---
 
## Endpoints del microservicio
 
Expuestos en `http://localhost:8090` (NodePort mapeado por Terraform).
 
| Endpoint | Método | Descripción | Respuesta de ejemplo |
|----------|--------|-------------|----------------------|
| `/secret` | GET | Retorna el secreto inyectado desde Vault vía variable de entorno|
| `/config` | GET | Retorna una propiedad de configuración local simulada |
| `/health` | GET | Health check usado por los probes de Kubernetes  |
 
Comprobar el balanceo entre réplicas:
 
```powershell
1..6 | ForEach-Object { curl.exe -s http://localhost:8090/health; "" }
```
 
---
 
## Extra: CronJob de auditoría
 
`k8s-manifests/cronjob-audit.yaml` define un **CronJob** que cada 10 minutos audita los eventos del kubelet:
 
- **ServiceAccount `audit-sa`:** identidad propia para el job.
- **ClusterRole `event-reader`:** solo permite `get/list/watch` sobre `events`.
- **ClusterRoleBinding:** enlaza la cuenta con el rol.
Aplicar y probar:
 
```powershell
kubectl --context kind-development apply -f k8s-manifests/cronjob-audit.yaml
kubectl --context kind-development get cronjob
 
# ejecución manual sin esperar 10 minutos:
kubectl --context kind-development create job --from=cronjob/kubelet-event-audit audit-manual-1
kubectl --context kind-development logs job/audit-manual-1
```
 
---
 
## Decisiones de diseño
 
Estas decisiones se tomaron buscando **calidad proporcional al objetivo** de la prueba (una prueba DevOps), priorizando el esfuerzo en la infraestructura y la automatización.
 
- **Kind como plataforma de clústeres:** permite dos clústeres reales de Kubernetes en local sin dependencia de servicios en la nube evitando costos, creados íntegramente con Terraform.
- **NodePort como balanceador:** en un entorno cloud se usaría `type: LoadBalancer`, pero en Kind eso requiere componentes adicionales. Un `Service` de tipo `NodePort` cumple la misma función de balancear entre las réplicas y exponer el microservicio, y es lo apropiado para Kind.
- **Agente local de Jenkins:** el pipeline se ejecuta en un agente sobre el host, ya que la alternativa Docker-in-Docker dentro del pod añade complejidad y fragilidad.
- **Microservicio deliberadamente simple:** al ser el medio para demostrar el flujo DevOps, se mantuvo minimalista pero limpio (controlador separado del arranque, serialización JSON nativa de Spring). Se evitó sobre-ingeniería (capas/interfaces innecesarias, Swagger) que no aportaría a una prueba de infraestructura.
- **Multi-stage build en el Dockerfile:** compila con JDK+Maven en una etapa y ejecuta con un JRE ligero (Alpine) en la final, reduciendo tamaño y superficie de ataque de la imagen.
- **El secreto nunca se persiste en el repositorio ni en la imagen:** se inyecta en tiempo de despliegue como variable de entorno. El `.gitignore` excluye estados de Terraform, kubeconfigs y cualquier archivo de secretos.
---
 
