"""Verify the monitoring contract against actual query APIs (requires smoke traffic)."""
import json,time,urllib.request,urllib.parse
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
SERVICES=['customer-service','catalog-service','order-service','payment-service','notification-service']
def get(base,path,params=None):
 query='?'+urllib.parse.urlencode(params) if params else ''
 with urllib.request.urlopen(urllib.request.Request(base+path+query,headers={"Accept":"application/json"}),timeout=10) as r:return json.load(r)
def wait(fn,seconds=90):
 end=time.monotonic()+seconds;last=None
 while time.monotonic()<end:
  try:
   value=fn()
   if value:return value
  except Exception as e:last=e
  time.sleep(2)
 raise AssertionError(f'Telemetry check timed out: {last}')

def metrics():
 result=get('http://127.0.0.1:19090','/api/v1/query',{'query':'up{application="ecommerce"}'})['data']['result']
 up={r['metric']['service'] for r in result if r['value'][1]=='1'}
 return set(SERVICES).issubset(up) and {s.replace('-service','-db') for s in SERVICES}.issubset(up) and 'rabbitmq' in up
wait(metrics)
print('PASS: five services, five PostgreSQL exporters and RabbitMQ metrics')
for service in SERVICES:
 wait(lambda:get('http://127.0.0.1:13100','/loki/api/v1/query_range',{'query':f'{{service_name="{service}",deployment_environment_name="local"}}','limit':1,'start':int((time.time()-1800)*1e9),'end':int(time.time()*1e9)})['data']['result'])
 wait(lambda:get('http://127.0.0.1:13200','/api/search',{'q':f'{{ resource.service.name = "{service}" }}','limit':20}) .get('traces'))
print('PASS: all five service log streams and traces')

def trace_services(trace):
 names=set()
 for batch in trace.get('batches',trace.get('resourceSpans',[])):
  for attr in batch.get('resource',{}).get('attributes',[]):
   if attr.get('key')=='service.name':names.add(attr.get('value',{}).get('stringValue'))
 return names

def checkout_trace():
 traces=get('http://127.0.0.1:13200','/api/search',{'q':'{ resource.service.name = "order-service" }','limit':100}).get('traces',[])
 for trace in traces:
  data=get('http://127.0.0.1:13200','/api/traces/'+trace['traceID'])
  if {'order-service','payment-service'}.issubset(trace_services(data)):return True
 return False
wait(checkout_trace)
print('PASS: propagated order-to-payment distributed trace')
