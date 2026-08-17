@description('Deployment location')
param location string = resourceGroup().location

@description('Environment name. For the current low-capacity production deployment use prodlow.')
param environmentName string = 'prodlow'

@description('Project prefix')
param projectName string = 'craves'

@description('Globally unique Azure Container Registry name. Lowercase letters and numbers only.')
param acrName string

@description('PostgreSQL administrator login')
param postgresAdminLogin string = 'cravesadmin'

@secure()
@description('PostgreSQL administrator password. Supply from GitHub Actions secret POSTGRES_ADMIN_PASSWORD. Rotate before production use.')
param postgresAdminPassword string

@description('API Management publisher email')
param apimPublisherEmail string = 'contact@craves.in'

@description('API Management publisher name')
param apimPublisherName string = 'Craves'

var env = toLower(environmentName)
var project = toLower(projectName)
var shortHash = take(uniqueString(resourceGroup().id), 6)
var cleanProject = replace(project, '-', '')
var cleanEnv = replace(env, '-', '')
var suffix = '${project}-${env}'

var logAnalyticsName = 'law-${suffix}-${shortHash}'
var appInsightsName = 'appi-${suffix}-${shortHash}'
var storageName = take(toLower('st${cleanProject}${cleanEnv}${shortHash}'), 24)
var keyVaultName = take(toLower('kv${cleanProject}${cleanEnv}${shortHash}'), 24)
var postgresName = take(toLower('pg-${project}-${env}-${shortHash}'), 63)
var serviceBusName = take(toLower('sb-${project}-${env}-${shortHash}'), 50)
var acaEnvName = take(toLower('cae-${project}-${env}-${shortHash}'), 32)
var apimName = take(toLower('apim-${project}-${env}-${shortHash}'), 50)

var commonTags = {
  project: projectName
  environment: environmentName
  workload: 'craves-full-app-low-capacity'
  managedBy: 'github-actions-bicep'
}

var commandQueues = [
  'payment-command'
  'delivery-command'
  'notification-command'
  'subscription-schedule'
]

// CPU values are strings and converted with json(app.cpu) inside the resource.
// This avoids Bicep parser issues seen with direct 0.25 / 0.5 decimal literals.
// To control idle cost, only web and auth-service stay warm in prodlow.
// Other placeholder services scale to zero until their real modules are built.
var containerApps = [
  {
    name: 'web'
    containerName: 'web'
    external: true
    minReplicas: 1
    maxReplicas: 2
    cpu: '0.25'
    memory: '0.5Gi'
  }
  {
    name: 'auth-service'
    containerName: 'auth-service'
    external: false
    minReplicas: 1
    maxReplicas: 2
    cpu: '0.25'
    memory: '0.5Gi'
  }
  {
    name: 'user-chef-service'
    containerName: 'user-chef-service'
    external: false
    minReplicas: 0
    maxReplicas: 2
    cpu: '0.25'
    memory: '0.5Gi'
  }
  {
    name: 'catalog-service'
    containerName: 'catalog-service'
    external: false
    minReplicas: 0
    maxReplicas: 2
    cpu: '0.25'
    memory: '0.5Gi'
  }
  {
    name: 'order-service'
    containerName: 'order-service'
    external: false
    minReplicas: 0
    maxReplicas: 3
    cpu: '0.5'
    memory: '1Gi'
  }
  {
    name: 'subscription-service'
    containerName: 'subscription-service'
    external: false
    minReplicas: 0
    maxReplicas: 2
    cpu: '0.25'
    memory: '0.5Gi'
  }
  {
    name: 'integration-service'
    containerName: 'integration-service'
    external: false
    minReplicas: 0
    maxReplicas: 2
    cpu: '0.5'
    memory: '1Gi'
  }
  {
    name: 'notification-service'
    containerName: 'notification-service'
    external: false
    minReplicas: 0
    maxReplicas: 2
    cpu: '0.25'
    memory: '0.5Gi'
  }
]

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: logAnalyticsName
  location: location
  tags: commonTags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: appInsightsName
  location: location
  kind: 'web'
  tags: commonTags
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logAnalytics.id
  }
}

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: toLower(acrName)
  location: location
  tags: commonTags
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

resource storage 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageName
  location: location
  tags: commonTags
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    allowBlobPublicAccess: false
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    accessTier: 'Hot'
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-01-01' = {
  name: 'default'
  parent: storage
  properties: {
    deleteRetentionPolicy: {
      enabled: true
      days: 7
    }
    containerDeleteRetentionPolicy: {
      enabled: true
      days: 7
    }
  }
}

resource mediaContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  name: 'media'
  parent: blobService
  properties: {
    publicAccess: 'None'
  }
}

resource documentsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  name: 'documents'
  parent: blobService
  properties: {
    publicAccess: 'None'
  }
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  tags: commonTags
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enabledForDeployment: false
    enabledForDiskEncryption: false
    enabledForTemplateDeployment: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    publicNetworkAccess: 'Enabled'
  }
}

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2023-06-01-preview' = {
  name: postgresName
  location: location
  tags: commonTags
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: postgresAdminLogin
    administratorLoginPassword: postgresAdminPassword
    version: '16'
    storage: {
      storageSizeGB: 32
      autoGrow: 'Enabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
    authConfig: {
      activeDirectoryAuth: 'Disabled'
      passwordAuth: 'Enabled'
    }
  }
}

resource postgresAllowAzureServices 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-06-01-preview' = {
  name: 'AllowAzureServices'
  parent: postgres
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource authDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-06-01-preview' = {
  name: 'craves_auth_db'
  parent: postgres
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource businessDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-06-01-preview' = {
  name: 'craves_business_db'
  parent: postgres
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource integrationDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-06-01-preview' = {
  name: 'craves_integration_db'
  parent: postgres
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// Redis is intentionally not deployed in this first safe foundation pass.
// Old Azure Cache for Redis was rejected by Azure. Add Azure Managed Redis later after SKU/provider availability is confirmed.

resource serviceBus 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' = {
  name: serviceBusName
  location: location
  tags: commonTags
  sku: {
    name: 'Standard'
    tier: 'Standard'
  }
  properties: {
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
    disableLocalAuth: false
  }
}

resource domainEventsTopic 'Microsoft.ServiceBus/namespaces/topics@2022-10-01-preview' = {
  name: 'craves-domain-events'
  parent: serviceBus
  properties: {
    defaultMessageTimeToLive: 'P14D'
    enablePartitioning: true
    requiresDuplicateDetection: true
    duplicateDetectionHistoryTimeWindow: 'PT10M'
  }
}

resource serviceBusQueues 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = [for queueName in commandQueues: {
  name: queueName
  parent: serviceBus
  properties: {
    defaultMessageTimeToLive: 'P14D'
    deadLetteringOnMessageExpiration: true
    lockDuration: 'PT1M'
    maxDeliveryCount: 10
    requiresDuplicateDetection: true
    duplicateDetectionHistoryTimeWindow: 'PT10M'
  }
}]

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: acaEnvName
  location: location
  tags: commonTags
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
  }
}

resource acaApps 'Microsoft.App/containerApps@2023-05-01' = [for app in containerApps: {
  name: take(toLower('ca-${project}-${app.name}-${env}'), 32)
  location: location
  tags: union(commonTags, { service: app.name })
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: app.external
        targetPort: 80
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: app.containerName
          image: 'mcr.microsoft.com/k8se/quickstart:latest'
          resources: {
            cpu: json(app.cpu)
            memory: app.memory
          }
          env: [
            {
              name: 'CRAVES_ENVIRONMENT'
              value: environmentName
            }
            {
              name: 'APPLICATIONINSIGHTS_CONNECTION_STRING'
              value: appInsights.properties.ConnectionString
            }
          ]
        }
      ]
      scale: {
        minReplicas: app.minReplicas
        maxReplicas: app.maxReplicas
      }
    }
  }
}]

resource apiManagement 'Microsoft.ApiManagement/service@2022-08-01' = {
  name: apimName
  location: location
  tags: commonTags
  sku: {
    name: 'Consumption'
    capacity: 0
  }
  properties: {
    publisherEmail: apimPublisherEmail
    publisherName: apimPublisherName
  }
}

output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
output keyVaultName string = keyVault.name
output postgresServerName string = postgres.name
output postgresAdminLogin string = postgresAdminLogin
output authDatabaseName string = authDb.name
output businessDatabaseName string = businessDb.name
output integrationDatabaseName string = integrationDb.name
output storageAccountName string = storage.name
output serviceBusNamespaceName string = serviceBus.name
output redisStatus string = 'not-deployed-in-initial-foundation'
output containerAppsEnvironmentName string = containerAppsEnvironment.name
output apiManagementName string = apiManagement.name
output webAppFqdn string = acaApps[0].properties.configuration.ingress.fqdn
output applicationInsightsName string = appInsights.name
output logAnalyticsName string = logAnalytics.name
