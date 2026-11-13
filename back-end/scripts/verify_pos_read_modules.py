#!/usr/bin/env python3
"""Live regression check against a disposable clone of the local demo database.
Run a separate backend on port 18080 connected to pos_codex_todo_verify_* first.
Never targets the working restaurant database. Test records remain only in that disposable clone.
"""
import argparse,json,subprocess,urllib.request,urllib.error,urllib.parse,uuid
from pathlib import Path
from decimal import Decimal
import yaml
p=argparse.ArgumentParser();p.add_argument('--database',required=True);p.add_argument('--base-url',default='http://127.0.0.1:18080');args=p.parse_args()
assert args.database.startswith('pos_codex_todo_verify_') and args.database.replace('_','').isalnum()
assert args.base_url=='http://127.0.0.1:18080', 'Use the isolated verification backend'
R='10000000-0000-0000-0000-000000000001';B='37d536b7-acb7-4ac2-8922-09bfa5af34ed';U='1f1b5974-cf89-6b66-aa4c-01e3b13c887d'
def sql(statement):
 r=subprocess.run(['podman','exec','-i','pos-db','psql','-U','pos_user','-d',args.database,'-v','ON_ERROR_STOP=1','-At'],input=statement,text=True,capture_output=True)
 if r.returncode:raise RuntimeError(r.stderr)
 return r.stdout.strip()
def ident():return str(uuid.uuid4())
def q(value):return "'"+str(value).replace("'","''")+"'"
other=sql(f"select id from foundation_local.users where restaurant_id='{R}' and id<>'{U}' limit 1;")
table=sql(f"select id from foundation_local.tables where restaurant_id='{R}' and branch_id='{B}' limit 1;")
menu=sql('select menu_item_id from foundation_local.order_line_items limit 1;')
assert other and table and menu
shift=ident();prefix='VERIFY-'+ident()[:8];oids={};seed=[]
seed.append(f"insert into foundation_local.shifts (id,restaurant_id,branch_id,user_id,status,started_at,ended_at,regular_minutes,overtime_minutes,declared_cash_tips,declared_card_tips,sales_total,cash_sales_total,card_sales_total,opening_drawer_amount,expected_drawer_amount,created_at,updated_at) values ('{shift}','{R}','{B}','{U}','CLOSED','2040-02-10T07:00Z','2040-02-10T21:00Z',840,0,0,0,0,0,0,0,0,now(),now());")
def order(name,total,currency='EUR',status='CLOSED',staff=U,at='2040-02-10T10:00:00Z',item=False):
 oid=ident();oids[name]=oid
 closed=q(at) if status=='CLOSED' else 'null'
 seed.append(f"insert into foundation_local.orders (id,restaurant_id,branch_id,table_id,order_number,currency,order_type,source,status,fulfillment_status,payment_status,guest_count,subtotal,discount_total,tax_total,service_charge_total,total,opened_at,closed_at,created_at,updated_at,created_by) values ('{oid}','{R}','{B}','{table}','{prefix}-{name}','{currency}','DINE_IN','POS','{status}','FULFILLED','UNPAID',2,{total},0,0,0,{total},'{at}',{closed},now(),now(),'{staff}');")
 if item:
  seed.append(f"insert into foundation_local.order_line_items(id,order_id,menu_item_id,item_name_snapshot,quantity,unit_price_snapshot,price_delta_total,discount_total,tax_total,line_total,status,created_at,updated_at) values ('{ident()}','{oid}','{menu}','Verification soup',1,{total},0,0,0,{total},'FULFILLED',now(),now());")
 return oid
def payment(orderid,amount,tip=0,refund=0,status='CAPTURED',currency='EUR',method='CARD',at='2040-02-10T12:00:00Z',shiftid=shift):
 seed.append(f"insert into foundation_local.payments(id,restaurant_id,branch_id,order_id,shift_id,reference_number,method,status,amount,tip_amount,surcharge_amount,refunded_amount,currency,paid_at,created_at,updated_at,created_by) values ('{ident()}','{R}','{B}','{orderid}',{q(shiftid) if shiftid else 'null'},'{ident()}','{method}','{status}',{amount},{tip},0,{refund},'{currency}','{at}',now(),now(),'{other}');")
a=order('A',100,item=True);b=order('B',50,item=True);c=order('C',20,item=True)
payment(a,60,6,method='CASH');payment(a,40,4,10,status='PARTIALLY_REFUNDED');payment(c,20,2,22,status='REFUNDED')
for status in ['PENDING','FAILED','AUTHORIZED','VOIDED']:payment(a,999,status=status)
d=order('D',80,currency='USD');payment(d,80,8,currency='USD',shiftid=None)
order('J',30,currency='GBP',status='OPEN');order('E',200,staff=other);order('F',999,status='CANCELLED');order('G',999,status='OPEN')
h=order('H',7,at='2040-02-10T23:00:00Z');payment(h,7,at='2040-02-10T23:00:00Z')
sql('begin;\n'+'\n'.join(seed)+'\ncommit;')
config=yaml.safe_load((Path(__file__).resolve().parents[1]/'src/main/resources/application-local.yml').read_text())
def locate(node):
 if isinstance(node,dict):
  if 'super-admin' in node:return node['super-admin']
  for child in node.values():
   found=locate(child)
   if found:return found
 return None
creds=locate(config)
def request(path,data=None,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(args.base_url+path,headers=headers,data=json.dumps(data).encode() if data is not None else None)
 try:
  with urllib.request.urlopen(req,timeout=30) as r:return r.status,json.load(r,parse_float=Decimal)
 except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
status,login=request('/auth/device/login',{'identifier':creds['email'],'password':creds['password']});assert status==200,status
token=login['accessToken'];root=f'/restaurants/{R}/branches/{B}';checks=0
def check(condition,message):
 global checks
 if not condition:raise AssertionError(message)
 checks+=1;print('PASS',message,flush=True)
def get(path):
 status,data=request(root+path,token=token);check(status==200,path+' responds successfully');return data
report=get('/sales/mine?date=2040-02-10');totals={t['currency']:t for t in report['currencies']};eur=totals['EUR'];usd=totals['USD']
check(eur['sales']==170,'split payments do not multiply order sales')
check(eur['ordersServed']==3 and eur['tablesServed']==1,'closed orders and distinct tables')
check(eur['recordedTips']==12 and eur['refunds']==32 and eur['collected']==100,'recorded tips, full/partial refunds and net collections')
check(eur['paymentCount']==3,'pending, failed, authorized and voided payments excluded')
check(eur['ordersWithoutPayments']==1,'closed orders without a payment are identified')
check(usd['sales']==80 and usd['collected']==88,'currencies remain separate')
check(eur['openOrders']==1,'open orders counted independently of closed sales')
check(totals['GBP']['openOrders']==1 and totals['GBP']['sales']==0,'currency with only open orders remains visible')
check(sum(x['sales'] for x in eur['hourly'])==170,'hourly totals match sales')
check(sum(x['collected'] for x in eur['paymentMethods'])==100,'payment methods match net collection')
check(eur['topItems'][0]['quantity']==3 and eur['topItems'][0]['sales']==170,'top item aggregates without payment duplication')
check(eur['areas'][0]['tables']==1 and eur['areas'][0]['sales']==170,'floor totals and distinct table count')
check(len(eur['recentPayments'])==3,'recent payments include only recorded captures/refunds')
scoped=get('/sales/mine?date=2040-02-10&shiftId='+shift);curr={t['currency']:t for t in scoped['currencies']}
check(curr['EUR']['collected']==100 and curr['USD']['collected']==0,'shift collections require an explicit matching shift ID')
check(scoped['from'].startswith('2040-02-10T07:00') and scoped['to'].startswith('2040-02-10T21:00'),'shift report uses actual attendance window')
nextday=get('/sales/mine?date=2040-02-11');check(nextday['currencies'][0]['sales']==7,'exclusive end boundary belongs to next local date')
filters=urllib.parse.urlencode({'from':'2040-02-09T23:00:00Z','to':'2040-02-10T23:00:00Z','search':prefix,'size':2})
pages=[get('/orders/history/page?'+filters+'&page='+str(i)) for i in range(3)]
check(all(p['totalElements']==6 for p in pages),'history totals include closed and cancelled, exclude open and end boundary')
ids=[x['id'] for p in pages for x in p['items']];check(len(ids)==6 and len(set(ids))==6,'stable history pagination has no duplicates across tied timestamps')
check(not pages[-1]['hasNext'],'last history page ends correctly')
mine=get('/orders/history/page?'+filters+'&staffId='+U);check(mine['totalElements']==5,'history staff filter happens on the server')
closed=get('/orders/history/page?'+filters+'&status=CLOSED');check(closed['totalElements']==5,'history status filter happens on the server')
for query in ['size=101','size=0','page=-1','status=OPEN','from=2040-02-11T00:00:00Z&to=2040-02-10T00:00:00Z']:
 status,_=request(root+'/orders/history/page?'+query,token=token);check(status==400,'history rejects '+query)
status,_=request(root+'/sales/mine');check(status in (401,403),'sales rejects unauthenticated callers')
status,_=request(root+'/orders/history/page');check(status in (401,403),'history rejects unauthenticated callers')
print(f'{checks} live API checks passed; test records exist only in {args.database}.',flush=True)
