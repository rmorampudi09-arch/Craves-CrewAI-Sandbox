# Service health smoke gate

Resolves each Container App ingress FQDN through Azure read APIs and checks supported health endpoints over HTTPS.

Accepted responses are HTTP 200 with a recognized healthy JSON status. The script never prints response bodies, tokens, secrets, or application data and performs no mutating request.
