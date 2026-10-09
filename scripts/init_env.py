"""Create local infrastructure secrets once; never overwrite an existing .env."""
from pathlib import Path
import secrets,os
path=Path(__file__).resolve().parents[1]/'.env'
if path.exists():
 print('.env already exists; left unchanged')
else:
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as file:
  for key in ['DB_PASSWORD','RABBIT_PASSWORD','KEYCLOAK_ADMIN_PASSWORD','INTERNAL_API_KEY']:
   file.write(f'{key}={secrets.token_hex(32)}\n')
 print('Created .env with local infrastructure secrets')
