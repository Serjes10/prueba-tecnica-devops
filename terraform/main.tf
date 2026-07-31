terraform {
  required_providers {
    kind = {
        source = "tehcyx/kind"
        version = "0.5.1"
    }
  }
  
}

provider "kind" {}


#Cluster Deployment
resource "kind_cluster" "deployment"{
    name = "deployment"
    wait_for_ready = true

    kind_config {
        kind = "Cluster"
        api_version = "kind.x-k8s.io/v1alpha"

        node {
            role = "control-plane"
            extra_port_mappings {
                container_port = 30080
                host_port = 8081
            }

            extra_port_mappings{
                container_port = 30200
                host_port = 8200
            }
        }
    }
}


resource "kind_cluster" "development" {
    name = "development"
    wait_for_ready = true

    kind_config{
        kind = "Cluster"
        api_version = "kind.x-k8s.io/v1alpha4"

        node{
            role = "control-plane"
            extra_port_mappings{
                container_port = 30090
                host_port = 8090
            }
        }
    }
}
