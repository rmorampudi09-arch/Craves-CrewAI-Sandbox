# APIM policy safety gate

Parses all APIM policy XML, validates Bash configuration scripts, blocks wildcard CORS, likely credentials, mixed `backend-id`/`base-url` routing, and write confirmations that appear to default to true.

This is a static source gate. It does not contact or modify Azure API Management.
