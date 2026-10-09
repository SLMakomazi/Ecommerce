"""Exercise real APIs and persisted state. Creates uniquely named local test products.
Optional --outages stops/restarts only this Compose project's catalog/payment services.
"""
import argparse, concurrent.futures, json, time, uuid, urllib.request, urllib.error, urllib.parse, subprocess
from pathlib import Path
from decimal import Decimal
ROOT=Path(__file__).resolve().parents[1]
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
BASE='http://127.0.0.1:18080'
secrets=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))

def call(path,method='GET',body=None,token=None,key=None,base=BASE,expected=200):
 headers={}
 if body is not None: headers['Content-Type']='application/json'
 if token: headers['Authorization']='Bearer '+token
 if key: headers['Idempotency-Key']=key
 if base!=BASE and '/internal/' in path: headers['X-Service-Key']=secrets['INTERNAL_API_KEY']
 data=json.dumps(body).encode() if body is not None else None
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=data,headers=headers,method=method),timeout=15) as response:
   status=response.status;raw=response.read()
 except urllib.error.HTTPError as error: status=error.code;raw=error.read()
 assert status==expected,f'{method} {path}: expected {expected}, got {status}: {raw[:300]!r}'
 return json.loads(raw,parse_float=Decimal) if raw else None

def token(user):
 data=urllib.parse.urlencode({'grant_type':'password','client_id':'ecommerce-cli','username':user,'password':user+'-local-only'}).encode()
 with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8180/realms/ecommerce/protocol/openid-connect/token',data=data),timeout=30) as r:return json.load(r)['access_token']

def wait(fn,timeout=90):
 end=time.monotonic()+timeout;last=None
 while time.monotonic()<end:
  try:
   result=fn()
   if result:return result
  except Exception as error:last=error
  time.sleep(1)
 raise AssertionError(f'Timed out: {last}')

def compose(*args):subprocess.run(['docker','compose',*args],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)

def main(outages):
 for port in range(18101,18106):
  wait(lambda:call('/actuator/health/readiness',base=f'http://127.0.0.1:{port}')['status']=='UP',180)
 customer=wait(lambda:token('customer'));admin=wait(lambda:token('admin'));other=wait(lambda:token('other'))
 call('/api/orders',expected=401)
 call('/api/customers/me','PUT',{'name':'Smoke Customer','address':'Local test address'},customer)
 call('/api/customers/me',token=customer)
 product=call('/api/products','POST',{'sku':'smoke-'+str(uuid.uuid4()),'name':'Smoke Product','description':'API verification','price':'10.50','currency':'ZAR','stock':4},admin,expected=201)
 pid=product['id']
 call('/api/products','POST',{'sku':'unauthorized','name':'Invalid','price':'1.00','currency':'ZAR','stock':1},customer,expected=403)
 call(f'/api/products/{pid}/restock','POST',{'quantity':1},customer,expected=403)
 call('/api/cart','PUT',{'items':[{'productId':pid,'quantity':1}]},customer)
 key='smoke-'+str(uuid.uuid4());details={'address':'Local test address','paymentToken':'sandbox-success','total':'0.01'}
 order=call('/api/orders','POST',details,customer,key,expected=202);oid=order['id']
 repeated=call('/api/orders','POST',details,customer,key,expected=202);assert repeated['id']==oid
 call('/api/orders','POST',{**details,'address':'Changed address'},customer,key,expected=409)
 result=wait(lambda:(r if (r:=call('/api/orders/'+oid,token=customer))['status']=='CONFIRMED' else None))
 assert str(result['total'])=='10.50',result
 assert call('/api/products/'+pid)['availableStock']==3
 call('/api/products/'+pid,'PUT',{'name':'Updated Product','price':'11.00'},admin)
 assert str(call('/api/orders/'+oid,token=customer)['total'])=='10.50','Historical order price changed'
 call('/api/orders/'+oid,token=other,expected=404)
 payments=call('/api/payments/me',token=customer);assert len([p for p in payments if p['orderId']==oid])==1
 wait(lambda:any(n['orderId']==oid and n['type']=='order.confirmed' for n in call('/api/notifications/me',token=customer)))
 call('/api/orders/'+oid+'/refund','POST',{},customer,expected=403)
 call('/api/orders/'+oid+'/refund','POST',{},admin)
 wait(lambda:call('/api/orders/'+oid,token=customer)['status']=='REFUNDED')
 assert next(p for p in call('/api/payments/me',token=customer) if p['orderId']==oid)['status']=='REFUNDED'
 assert call('/api/products/'+pid)['availableStock']==3,'Refund must not automatically restock shipped goods'
 # Payment decline compensates inventory.
 call('/api/cart','PUT',{'items':[{'productId':pid,'quantity':1}]},customer)
 declined=call('/api/orders','POST',{**details,'paymentToken':'sandbox-decline'},customer,'decline-'+str(uuid.uuid4()),expected=202)
 wait(lambda:call('/api/orders/'+declined['id'],token=customer)['status']=='REJECTED')
 assert call('/api/products/'+pid)['availableStock']==3
 # Simultaneous stock reservations cannot oversell.
 ids=[str(uuid.uuid4()),str(uuid.uuid4())]
 def reserve(orderid):
  try:return call('/internal/reservations/'+orderid,'POST',{'items':[{'productId':pid,'quantity':3}]},base='http://127.0.0.1:18102')
  except AssertionError as e:
   assert 'got 409' in str(e);return None
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:reservations=list(pool.map(reserve,ids))
 assert sum(r is not None for r in reservations)==1,'Oversold stock'
 winner=next(r for r in reservations if r)
 call('/internal/reservations/'+winner['orderId'],'POST',{'items':[{'productId':pid,'quantity':3}]},base='http://127.0.0.1:18102')
 assert call('/api/products/'+pid)['availableStock']==0
 call('/internal/reservations/'+winner['orderId']+'/release','POST',base='http://127.0.0.1:18102')
 call('/internal/reservations/'+winner['orderId']+'/release','POST',base='http://127.0.0.1:18102')
 assert call('/api/products/'+pid)['availableStock']==3
 # A release tombstone prevents a delayed request from reserving after cancellation.
 tombstone=str(uuid.uuid4())
 call('/internal/reservations/'+tombstone+'/release','POST',base='http://127.0.0.1:18102')
 call('/internal/reservations/'+tombstone,'POST',{'items':[{'productId':pid,'quantity':1}]},base='http://127.0.0.1:18102',expected=409)
 assert call('/api/products/'+pid)['availableStock']==3
 print('PASS: authentication, ownership, checkout, exact money, idempotency, refunds, decline compensation, notification delivery, concurrent stock safety, cancellation tombstones')
 if outages:
  try:
   compose('stop','catalog-service')
   call('/api/customers/me',token=customer)
   call('/api/cart','PUT',{'items':[{'productId':pid,'quantity':1}]},customer)
   pending=call('/api/orders','POST',details,customer,'outage-'+str(uuid.uuid4()),expected=202)
   time.sleep(3)
   assert call('/api/orders/'+pending['id'],token=customer)['status']=='RECEIVED'
  finally:compose('start','catalog-service')
  wait(lambda:call('/api/orders/'+pending['id'],token=customer)['status']=='CONFIRMED',120)
  try:
   compose('stop','payment-service')
   assert call('/api/products/'+pid)['id']==pid
   call('/api/customers/me',token=customer)
   call('/api/cart','PUT',{'items':[{'productId':pid,'quantity':1}]},customer)
   pending=call('/api/orders','POST',details,customer,'payment-outage-'+str(uuid.uuid4()),expected=202)
   wait(lambda:call('/api/orders/'+pending['id'],token=customer)['status']=='RESERVED')
  finally:compose('start','payment-service')
  wait(lambda:call('/api/orders/'+pending['id'],token=customer)['status']=='CONFIRMED',120)
  assert len([p for p in call('/api/payments/me',token=customer) if p['orderId']==pending['id']])==1
  print('PASS: independent customer/catalog availability and checkout recovery after catalog/payment outages')

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--outages',action='store_true');main(parser.parse_args().outages)
