## development-efk

Development-grade EFK on Kubernetes (EKS):

- **E**: Elasticsearch (Bitnami Helm chart)
- **F**: Fluent Bit (Fluent Helm chart)
- **K**: Kibana (Bitnami Helm chart)

This repo provides:

- An **umbrella Helm chart** (`helm/efk`) that installs Elasticsearch + Kibana + Fluent Bit.
- **CloudFormation** templates to create a minimal dev **VPC** + **EKS** cluster + managed node group.

### Prereqs

- AWS CLI configured (`aws sts get-caller-identity` works)
- `kubectl`
- `helm` v3+

### 1) Create infra (CloudFormation)

Create the VPC stack:

```bash
aws cloudformation deploy \
  --stack-name dev-efk-vpc \
  --template-file cloudformation/vpc.yaml \
  --capabilities CAPABILITY_NAMED_IAM
```

Create the EKS stack (uses outputs from the VPC stack via exported values):

```bash
aws cloudformation deploy \
  --stack-name dev-efk-eks \
  --template-file cloudformation/eks.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides ClusterName=dev-efk
```

Update kubeconfig:

```bash
aws eks update-kubeconfig --name dev-efk --region "$(aws configure get region)"
kubectl get nodes
```

### 2) Install EFK via the umbrella chart

```bash
helm dependency update helm/efk
helm upgrade --install efk helm/efk -n observability --create-namespace
```

Wait for pods:

```bash
kubectl -n observability get pods
```

### 3) Access Kibana (dev)

By default this chart uses `ClusterIP` services. Port-forward:

```bash
kubectl -n observability port-forward svc/efk-kibana 5601:5601
```

Then open `http://localhost:5601`.

### Notes (dev defaults)

- Elasticsearch is configured as a **single-node** for development.
- Persistence is **disabled** by default (easy to enable in `helm/efk/values.yaml`).
- Fluent Bit is configured to ship container logs to Elasticsearch in the same namespace.

